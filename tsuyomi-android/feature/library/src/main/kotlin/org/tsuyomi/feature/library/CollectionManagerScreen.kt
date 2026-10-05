/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiDropdownMenu
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiSlider
import org.tsuyomi.core.ui.components.TsuyomiSwitchVisual
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection

enum class SmartField {
    SOURCE, MANUAL_COLLECTION, TAG, FACET, TITLE, AUTHOR, STATUS, RATING,
    ADDED_WITHIN_DAYS, LAST_READ_WITHIN_DAYS, METADATA_UPDATED_WITHIN_DAYS,
    PROGRESS, UNRESOLVED_UPDATE, DORMANT_SOURCE,
}

data class SmartConditionDraft(
    val field: SmartField = SmartField.TAG,
    val value: String = "",
    val excluded: Boolean = false,
    val matchAllTags: Boolean = false,
    val facetSourceId: String = "",
)

/** Negations are counted so even double-NOT nodes survive an untouched edit. */
sealed interface SmartDraftNode {
    val negations: Int

    data class Group(
        val matchAll: Boolean,
        val children: List<SmartDraftNode>,
        override val negations: Int = 0,
        val syntheticRoot: Boolean = false,
    ) : SmartDraftNode

    data class Condition(
        val draft: SmartConditionDraft,
        override val negations: Int = 0,
        val originalPredicateJson: String? = null,
    ) : SmartDraftNode
}

fun SmartDraftNode.Group.conditions(): List<SmartConditionDraft> = buildList {
    fun visit(node: SmartDraftNode) {
        when (node) {
            is SmartDraftNode.Group -> node.children.forEach(::visit)
            is SmartDraftNode.Condition -> add(node.draft.copy(excluded = node.negations % 2 != 0))
        }
    }
    visit(this@conditions)
}

fun SmartDraftNode.Group.nodeCount(): Int {
    fun count(node: SmartDraftNode): Int = 1 + node.negations + when (node) {
        is SmartDraftNode.Group -> node.children.sumOf(::count)
        is SmartDraftNode.Condition -> 0
    }
    return count(this)
}

/** CSV-style quoting keeps literal commas, Chinese commas and whitespace inside current-version terms. */
fun formatSmartTerms(values: Set<String>): String = values.joinToString(",") { value ->
    if (value.any { it == ',' || it == '，' || it == '"' } || value != value.trim())
        "\"${value.replace("\"", "\"\"")}\"" else value
}

fun parseSmartTerms(raw: String): List<String>? {
    val result = mutableListOf<String>()
    val value = StringBuilder()
    var quoted = false
    var wasQuoted = false
    var index = 0
    while (index < raw.length) {
        when (val char = raw[index]) {
            '"' -> if (quoted && index + 1 < raw.length && raw[index + 1] == '"') {
                value.append('"')
                index++
            } else if (quoted) {
                quoted = false
                wasQuoted = true
            } else if (value.isEmpty()) quoted = true else return null
            ',', '，' -> if (quoted) value.append(char) else {
                val term = if (wasQuoted) value.toString() else value.toString().trim()
                if (term.isNotEmpty()) result += term
                value.clear()
                wasQuoted = false
            }
            else -> if (wasQuoted && !char.isWhitespace()) return null else if (!wasQuoted || quoted) value.append(char)
        }
        index++
    }
    if (quoted) return null
    val term = if (wasQuoted) value.toString() else value.toString().trim()
    if (term.isNotEmpty()) result += term
    return result
}

/** Returns precisely the failing condition indices; no malformed predicate reaches persistence. */
fun invalidSmartConditions(conditions: List<SmartConditionDraft>): Set<Int> =
    conditions.indices.filterTo(linkedSetOf()) { index ->
        val condition = conditions[index]
        val values = parseSmartTerms(condition.value)
        val invalidTerms = values == null || values.isEmpty() || values.size > 64 ||
            values.any { it.codePointCount(0, it.length) !in 1..256 }

        when (condition.field) {
            SmartField.UNRESOLVED_UPDATE, SmartField.DORMANT_SOURCE -> false
            SmartField.RATING -> {
                val bounds = condition.value.split(',', '，').map { it.trim() }
                val first = bounds.getOrNull(0)?.toDoubleOrNull()
                val last = bounds.getOrNull(1)?.toDoubleOrNull()
                bounds.size != 2 || (first == null && last == null) ||
                    (bounds[0].isNotEmpty() && first == null) || (bounds[1].isNotEmpty() && last == null) ||
                    first?.isFinite() == false || last?.isFinite() == false ||
                    first?.let { it !in 0.0..5.0 } == true || last?.let { it !in 0.0..5.0 } == true ||
                    (first != null && last != null && first > last)
            }
            SmartField.ADDED_WITHIN_DAYS, SmartField.LAST_READ_WITHIN_DAYS,
            SmartField.METADATA_UPDATED_WITHIN_DAYS -> condition.value.trim().toLongOrNull()?.let { it in 0L..36_500L } != true
            SmartField.STATUS -> invalidTerms || values.orEmpty().any {
                it.uppercase() !in setOf("UNKNOWN", "ONGOING", "COMPLETED", "HIATUS", "CANCELLED")
            }
            SmartField.PROGRESS -> invalidTerms || values.orEmpty().any {
                it.uppercase() !in setOf("UNSTARTED", "READING", "FINISHED")
            }
            SmartField.FACET -> invalidTerms || condition.facetSourceId.codePointCount(0, condition.facetSourceId.length) !in 1..256
            else -> invalidTerms
        }
    }

fun SmartDraftNode.Group.invalidConditionPaths(): Set<List<Int>> {
    val leaves = mutableListOf<Pair<List<Int>, SmartConditionDraft>>()
    fun visit(node: SmartDraftNode, path: List<Int>) {
        when (node) {
            is SmartDraftNode.Group -> node.children.forEachIndexed { index, child -> visit(child, path + index) }
            is SmartDraftNode.Condition -> leaves += path to node.draft
        }
    }
    visit(this, emptyList())
    return invalidSmartConditions(leaves.map { it.second }).mapTo(linkedSetOf()) { leaves[it].first }
}

/** Updates one nested node without flattening its siblings or changing their typed identities. */
fun SmartDraftNode.Group.updateAt(path: List<Int>, change: (SmartDraftNode) -> SmartDraftNode?): SmartDraftNode.Group {
    fun update(group: SmartDraftNode.Group, remaining: List<Int>): SmartDraftNode.Group {
        val index = remaining.first()
        val children = group.children.toMutableList()
        val current = children[index]
        val next = if (remaining.size == 1) change(current) else update(current as SmartDraftNode.Group, remaining.drop(1))
        if (next == null) children.removeAt(index) else children[index] = next
        return group.copy(children = children)
    }
    return update(this, path)
}

@Composable
fun CollectionRuleScreen(
    title: String,
    tree: SmartDraftNode.Group,
    collections: List<LibraryCollection>,
    sourceIds: List<String>,
    sourceLabels: Map<String, String>,
    tagChoices: List<String>,
    nameError: Boolean,
    invalidConditions: Set<List<Int>>,
    saving: Boolean,
    onTitleChange: (String) -> Unit,
    onTreeChange: (SmartDraftNode.Group) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailure: String? = null,
    focusedPath: List<Int> = emptyList(),
    onFocusChange: (List<Int>) -> Unit = {},
    creation: Boolean = false,
    advancedExpanded: Boolean = false,
    selectedBookCount: Int = 0,
    onAdvancedExpandedChange: (Boolean) -> Unit = {},
    onChooseBooks: () -> Unit = {},
) {
    val errorText = stringResource(R.string.collection_rule_invalid_condition)
    val expandedDescription = stringResource(R.string.collection_rule_group_expanded)
    val collapsedDescription = stringResource(R.string.collection_rule_group_collapsed)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TsuyomiSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
    ) {
        val nameErrorText = stringResource(
            if (creation && !advancedExpanded) R.string.collection_create_invalid_name
            else R.string.collection_rule_invalid_name,
        )
        Column(Modifier.fillMaxWidth().then(if (nameError) Modifier.semantics { error(nameErrorText) } else Modifier)) {
        TsuyomiTextField(
            value = title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.collection_name_label),
            modifier = Modifier.fillMaxWidth().then(if (nameError) Modifier.semantics { error(nameErrorText) } else Modifier),
            isError = nameError,
            supportingText = nameErrorText.takeIf { nameError },
            singleLine = true,
        )
        }
        if (creation) {
            TsuyomiButton(
                text = stringResource(R.string.collection_advanced_options) + if (advancedExpanded) " ⌄" else " ›",
                onClick = { onAdvancedExpandedChange(!advancedExpanded) },
                modifier = Modifier.fillMaxWidth().semantics {
                    stateDescription = if (advancedExpanded) expandedDescription else collapsedDescription
                },
                style = TsuyomiButtonStyle.SECONDARY,
            )
            if (!advancedExpanded) TsuyomiButton(
                text = stringResource(R.string.collection_choose_books, selectedBookCount),
                onClick = onChooseBooks,
                modifier = Modifier.fillMaxWidth(),
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
        if (!creation || advancedExpanded) {
            if (focusedPath.isNotEmpty()) TsuyomiButton(
                text = stringResource(R.string.collection_rule_parent_group),
                onClick = { onFocusChange(focusedPath.dropLast(1)) },
                style = TsuyomiButtonStyle.SECONDARY,
            )
            val active = focusedPath.fold(tree as SmartDraftNode) { node, index ->
                (node as SmartDraftNode.Group).children[index]
            } as SmartDraftNode.Group
            RuleGroupEditor(active, focusedPath, tree, collections, sourceIds, sourceLabels, tagChoices, invalidConditions, errorText,
                onTreeChange, onFocusChange, 0, focusedPath.isEmpty())
        }
        saveFailure?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TsuyomiButton(
            text = stringResource(when {
                !creation -> R.string.collection_rule_save_action
                advancedExpanded -> R.string.collection_create_smart_action
                else -> R.string.collection_create_action
            }),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            style = TsuyomiButtonStyle.PRIMARY,
        )
    }
}

@Composable
private fun RuleGroupEditor(
    group: SmartDraftNode.Group,
    path: List<Int>,
    root: SmartDraftNode.Group,
    collections: List<LibraryCollection>,
    sourceIds: List<String>,
    sourceLabels: Map<String, String>,
    tagChoices: List<String>,
    invalid: Set<List<Int>>,
    errorText: String,
    onChange: (SmartDraftNode.Group) -> Unit,
    onFocusChange: (List<Int>) -> Unit,
    visibleDepth: Int,
    showHeader: Boolean,
) {
    fun change(updated: SmartDraftNode.Group) {
        onChange(if (path.isEmpty()) updated else root.updateAt(path) { updated })
    }
    var expanded by rememberSaveable(path) { mutableStateOf(!showHeader || path.isEmpty()) }
    SettingsGroup(Modifier.fillMaxWidth().padding(horizontal = if (path.isEmpty()) 0.dp else TsuyomiSpacing.Md)) {
        Column(Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
        val disclosureState = stringResource(if (expanded) R.string.collection_rule_group_expanded else R.string.collection_rule_group_collapsed)
        if (showHeader && path.isNotEmpty()) TsuyomiButton(
            text = stringResource(R.string.collection_rule_group_toggle,
                stringResource(if (group.matchAll) R.string.collection_match_all else R.string.collection_match_any)) +
                if (expanded) " ⌄" else " ›",
            onClick = { expanded = !expanded },
            style = TsuyomiButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = disclosureState },
        )
        if (!expanded) return@Column
        Row(
            modifier = Modifier.heightIn(min = TsuyomiSpacing.Xxl)
                .toggleable(value = group.matchAll, role = Role.Switch,
                    onValueChange = { change(group.copy(matchAll = it, syntheticRoot = false)) })
                .padding(horizontal = TsuyomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            Text(stringResource(if (group.matchAll) R.string.collection_match_all else R.string.collection_match_any))
            TsuyomiSwitchVisual(checked = group.matchAll, enabled = true)
        }
        if (path.isEmpty()) Text(stringResource(if (group.matchAll)
            R.string.collection_rule_match_all_hint else R.string.collection_rule_match_any_hint))
        if (path.isNotEmpty() || group.negations > 0) {
            TsuyomiCheckboxRow(
                label = stringResource(R.string.collection_rule_group_negate),
                checked = group.negations % 2 != 0,
                onCheckedChange = { change(group.copy(negations = if (it) 1 else 0)) },
            )
        }
        group.children.forEachIndexed { index, child ->
            val childPath = path + index
            when (child) {
                is SmartDraftNode.Group -> if (visibleDepth >= 1) TsuyomiButton(
                    text = stringResource(R.string.collection_rule_open_group) + "：" +
                        stringResource(if (child.matchAll) R.string.collection_match_all else R.string.collection_match_any) + " ›",
                    onClick = { onFocusChange(childPath) },
                    style = TsuyomiButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                ) else RuleGroupEditor(child, childPath, root, collections, sourceIds, sourceLabels, tagChoices, invalid, errorText,
                    onChange, onFocusChange, visibleDepth + 1, true)
                is SmartDraftNode.Condition -> RuleConditionEditor(child, childPath, root, collections, sourceIds,
                    sourceLabels, tagChoices, childPath in invalid, errorText, onChange)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.4f
            val canAddCondition = root.nodeCount() < 128 && path.size + group.negations < 6
            val canAddGroup = root.nodeCount() < 127 && path.size + group.negations < 5
            val addCondition = {
                change(group.copy(children = group.children + SmartDraftNode.Condition(SmartConditionDraft())))
            }
            val addGroup = {
                change(group.copy(children = group.children + SmartDraftNode.Group(true,
                    listOf(SmartDraftNode.Condition(SmartConditionDraft())))))
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                    RuleAddButton(false, canAddCondition, addCondition, Modifier.fillMaxWidth())
                    RuleAddButton(true, canAddGroup, addGroup, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                    RuleAddButton(false, canAddCondition, addCondition, Modifier.weight(1f))
                    RuleAddButton(true, canAddGroup, addGroup, Modifier.weight(1f))
                }
            }
        }
        if (path.isNotEmpty()) {
            TsuyomiButton(
                text = stringResource(R.string.collection_rule_remove_group),
                onClick = { onChange(root.updateAt(path) { null }); onFocusChange(path.dropLast(1)) },
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
        if (group.children.isEmpty()) Text(errorText, Modifier.semantics { error(errorText) })
    }
    }
}

@Composable
private fun RuleAddButton(group: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TsuyomiButton(
        text = stringResource(if (group) R.string.collection_rule_add_group else R.string.collection_add_condition),
        onClick = onClick,
        enabled = enabled,
        style = TsuyomiButtonStyle.SECONDARY,
        modifier = modifier,
    )
}

@Composable
private fun RuleConditionEditor(
    node: SmartDraftNode.Condition,
    path: List<Int>,
    root: SmartDraftNode.Group,
    collections: List<LibraryCollection>,
    sourceIds: List<String>,
    sourceLabels: Map<String, String>,
    tagChoices: List<String>,
    invalid: Boolean,
    errorText: String,
    onChange: (SmartDraftNode.Group) -> Unit,
) {
    val condition = node.draft
    fun change(draft: SmartConditionDraft = condition, negations: Int = node.negations) {
        onChange(root.updateAt(path) { node.copy(draft = draft.copy(excluded = false), negations = negations) })
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md)
        .then(if (invalid) Modifier.semantics { error(errorText) } else Modifier),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
        var fieldExpanded by remember(path) { mutableStateOf(false) }
        TsuyomiButton(
            text = stringResource(R.string.collection_condition_type, condition.field.localizedName()),
            onClick = { fieldExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.SECONDARY,
        )
        TsuyomiDropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
            SmartField.entries.forEach { field ->
                TsuyomiMenuItem(label = field.localizedName(), onClick = {
                    fieldExpanded = false
                    if (field != condition.field) change(condition.copy(field = field,
                        value = if (field == SmartField.RATING) "0,5" else "",
                        matchAllTags = false, facetSourceId = ""))
                })
            }
        }
        val sourceLength = condition.facetSourceId.codePointCount(0, condition.facetSourceId.length)
        val sourceInvalid = invalid && sourceLength !in 1..256
        val valueInvalid = invalid && (condition.field != SmartField.FACET ||
            invalidSmartConditions(listOf(condition.copy(facetSourceId = "source"))).isNotEmpty())
        if (condition.field == SmartField.FACET) {
            TsuyomiTextField(
                value = condition.facetSourceId,
                onValueChange = { change(condition.copy(facetSourceId = it)) },
                label = stringResource(R.string.collection_rule_facet_source),
                modifier = Modifier.fillMaxWidth().then(if (sourceInvalid) Modifier.semantics { error(errorText) } else Modifier),
                isError = sourceInvalid,
                supportingText = errorText.takeIf { sourceInvalid },
            )
        }
        if (condition.field.requiresValue()) {
            val namedValues = when (condition.field) {
                SmartField.STATUS -> mapOf(
                    "ongoing" to stringResource(R.string.smart_status_ongoing),
                    "completed" to stringResource(R.string.smart_status_completed),
                    "hiatus" to stringResource(R.string.smart_status_hiatus),
                    "cancelled" to stringResource(R.string.smart_status_cancelled),
                    "unknown" to stringResource(R.string.smart_status_unknown),
                )
                SmartField.PROGRESS -> mapOf(
                    "unstarted" to stringResource(R.string.smart_progress_unstarted),
                    "reading" to stringResource(R.string.smart_progress_reading),
                    "finished" to stringResource(R.string.smart_progress_finished),
                )
                else -> emptyMap()
            }
            val choices = when (condition.field) {
                SmartField.SOURCE -> sourceIds
                SmartField.MANUAL_COLLECTION -> collections.filter { it.kind == CollectionKind.MANUAL }.map { it.collectionId }
                SmartField.STATUS, SmartField.PROGRESS -> namedValues.keys.toList()
                else -> emptyList()
            }
            fun labelFor(choice: String): String = when (condition.field) {
                SmartField.SOURCE -> sourceLabels[choice]?.takeIf(String::isNotBlank)?.let { name ->
                    if (sourceLabels.any { (id, label) -> id != choice && label == name }) "$name（$choice）" else name
                } ?: choice
                SmartField.MANUAL_COLLECTION -> collections.firstOrNull { it.collectionId == choice }?.title ?: choice
                SmartField.STATUS, SmartField.PROGRESS -> namedValues[choice.lowercase()] ?: choice
                else -> choice
            }
            if (condition.field == SmartField.TAG) {
                val selectedTags = parseSmartTerms(condition.value)
                val availableTags = remember(tagChoices, condition.value) {
                    (tagChoices + selectedTags.orEmpty()).filter(String::isNotBlank).distinct()
                }
                var expanded by remember(path) { mutableStateOf(false) }
                var tagFilter by rememberSaveable(path) { mutableStateOf("") }
                var visibleCount by rememberSaveable(path) { mutableIntStateOf(40) }
                var customInput by rememberSaveable(path) {
                    mutableStateOf(selectedTags == null || selectedTags.any { it !in tagChoices })
                }
                val tagSummary = if (selectedTags.isNullOrEmpty()) stringResource(R.string.collection_rule_choose_tags)
                    else stringResource(R.string.collection_rule_selected_tags, selectedTags.size,
                        selectedTags.take(2).joinToString("、") + if (selectedTags.size > 2) "…" else "")
                if (availableTags.isEmpty()) Text(stringResource(R.string.collection_rule_no_existing_tags))
                if (availableTags.isNotEmpty()) {
                    TsuyomiButton(
                        text = tagSummary,
                        onClick = { tagFilter = ""; visibleCount = 40; expanded = true },
                        modifier = Modifier.fillMaxWidth().then(if (valueInvalid) Modifier.semantics { error(errorText) } else Modifier),
                        enabled = selectedTags != null,
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                    TsuyomiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (!selectedTags.isNullOrEmpty()) Text(tagSummary, Modifier.padding(TsuyomiSpacing.Sm))
                        TsuyomiMenuItem(label = stringResource(R.string.collection_rule_tags_done), onClick = { expanded = false })
                        if (availableTags.size > 12) TsuyomiTextField(
                            value = tagFilter,
                            onValueChange = { tagFilter = it; visibleCount = 40 },
                            label = stringResource(R.string.collection_rule_find_tag),
                        )
                        val matching = availableTags.filter { it.contains(tagFilter, ignoreCase = true) }
                        if (matching.isEmpty()) Text(stringResource(R.string.collection_rule_no_matching_tags),
                            Modifier.padding(TsuyomiSpacing.Sm))
                        matching.take(visibleCount).forEach { tag ->
                            TsuyomiCheckboxRow(
                                label = tag,
                                checked = tag in selectedTags.orEmpty(),
                                onCheckedChange = { checked ->
                                    val values = selectedTags.orEmpty().toMutableSet()
                                    if (checked) values.add(tag) else values.remove(tag)
                                    change(condition.copy(value = formatSmartTerms(values)))
                                },
                            )
                        }
                        if (matching.size > visibleCount) TsuyomiMenuItem(
                            label = stringResource(R.string.collection_rule_more_tags, minOf(40, matching.size - visibleCount)),
                            onClick = { visibleCount = minOf(visibleCount + 40, matching.size) },
                        )
                    }
                }
                if (availableTags.isNotEmpty()) TsuyomiButton(
                    text = stringResource(if (customInput) R.string.collection_rule_hide_other_tags else R.string.collection_rule_other_tags),
                    onClick = { customInput = !customInput },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.TEXT,
                )
                if (customInput || availableTags.isEmpty()) TsuyomiTextField(
                    value = condition.value,
                    onValueChange = { change(condition.copy(value = it)) },
                    label = stringResource(if (availableTags.isEmpty()) R.string.collection_rule_new_tag
                        else R.string.collection_rule_other_tags_label),
                    modifier = Modifier.fillMaxWidth().then(if (valueInvalid) Modifier.semantics { error(errorText) } else Modifier),
                    isError = valueInvalid,
                    supportingText = if (valueInvalid) errorText else stringResource(R.string.collection_rule_tag_separator_hint),
                ) else if (valueInvalid) Text(errorText, Modifier.semantics { error(errorText) })
            } else if (condition.field in setOf(SmartField.SOURCE, SmartField.MANUAL_COLLECTION, SmartField.STATUS, SmartField.PROGRESS)) {
                var expanded by remember(path) { mutableStateOf(false) }
                val displayValue = condition.value.takeIf(String::isNotBlank)?.let { value ->
                    parseSmartTerms(value)?.joinToString("、", transform = ::labelFor) ?: value
                } ?: stringResource(R.string.collection_rule_select_value)
                TsuyomiButton(
                    text = displayValue,
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().then(if (invalid) Modifier.semantics { error(errorText) } else Modifier),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
                TsuyomiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { choice ->
                        TsuyomiMenuItem(label = labelFor(choice), onClick = {
                            expanded = false
                            change(condition.copy(value = choice))
                        })
                    }
                }
            } else if (condition.field == SmartField.RATING) {
                val bounds = condition.value.split(',', '，')
                val lowerRaw = bounds.getOrNull(0)?.trim().orEmpty()
                val upperRaw = bounds.getOrNull(1)?.trim().orEmpty()
                val lower = lowerRaw.toFloatOrNull()?.coerceIn(0f, 5f) ?: 0f
                val upper = upperRaw.toFloatOrNull()?.coerceIn(lower, 5f) ?: 5f
                TsuyomiSlider(
                    value = lower,
                    onValueChange = { change(condition.copy(value = "${it.coerceAtMost(upper)},$upperRaw")) },
                    label = stringResource(R.string.smart_rating_min, lower.toString()),
                    valueRange = 0f..5f,
                    steps = 9,
                    modifier = Modifier.then(if (invalid) Modifier.semantics { error(errorText) } else Modifier),
                )
                TsuyomiSlider(
                    value = upper,
                    onValueChange = { change(condition.copy(value = "$lowerRaw,${it.coerceAtLeast(lower)}")) },
                    label = stringResource(R.string.smart_rating_max, upper.toString()),
                    valueRange = 0f..5f,
                    steps = 9,
                )
                if (invalid) Text(errorText)
            } else {
                TsuyomiTextField(
                    value = condition.value,
                    onValueChange = { change(condition.copy(value = it)) },
                    label = stringResource(if (condition.field == SmartField.FACET) R.string.collection_rule_facet_ids else condition.field.hintResource()),
                    modifier = Modifier.fillMaxWidth().then(if (valueInvalid) Modifier.semantics { error(errorText) } else Modifier),
                    isError = valueInvalid,
                    supportingText = errorText.takeIf { valueInvalid },
                )
            }
        }
        TsuyomiCheckboxRow(
            label = stringResource(R.string.collection_exclude_condition),
            checked = node.negations % 2 != 0,
            onCheckedChange = { change(negations = if (it) 1 else 0) },
        )
        if (condition.field == SmartField.TAG) {
            TsuyomiCheckboxRow(
                label = stringResource(if (condition.matchAllTags) R.string.collection_tag_match_all else R.string.collection_tag_match_any),
                checked = condition.matchAllTags,
                onCheckedChange = { change(condition.copy(matchAllTags = it)) },
            )
        }
        if (root.nodeCount() > 1) TsuyomiButton(
            text = stringResource(R.string.collection_remove_condition),
            onClick = { onChange(root.updateAt(path) { null }) },
            style = TsuyomiButtonStyle.SECONDARY,
        )
    }
}

@Composable
internal fun SmartField.localizedName(): String = stringResource(when (this) {
    SmartField.SOURCE -> R.string.smart_field_source
    SmartField.MANUAL_COLLECTION -> R.string.smart_field_manual_collection
    SmartField.TAG -> R.string.smart_field_tag
    SmartField.FACET -> R.string.smart_field_facet
    SmartField.TITLE -> R.string.smart_field_title
    SmartField.AUTHOR -> R.string.smart_field_author
    SmartField.STATUS -> R.string.smart_field_status
    SmartField.RATING -> R.string.smart_field_rating
    SmartField.ADDED_WITHIN_DAYS -> R.string.smart_field_added
    SmartField.LAST_READ_WITHIN_DAYS -> R.string.smart_field_last_read
    SmartField.METADATA_UPDATED_WITHIN_DAYS -> R.string.smart_field_metadata
    SmartField.PROGRESS -> R.string.smart_field_progress
    SmartField.UNRESOLVED_UPDATE -> R.string.smart_field_unresolved_update
    SmartField.DORMANT_SOURCE -> R.string.smart_field_dormant
})

private fun SmartField.requiresValue(): Boolean = this != SmartField.UNRESOLVED_UPDATE && this != SmartField.DORMANT_SOURCE
private fun SmartField.hintResource(): Int = when (this) {
    SmartField.RATING -> R.string.smart_hint_rating
    SmartField.ADDED_WITHIN_DAYS, SmartField.LAST_READ_WITHIN_DAYS, SmartField.METADATA_UPDATED_WITHIN_DAYS -> R.string.smart_hint_days
    else -> R.string.smart_hint_terms
}
