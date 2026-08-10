/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle

enum class SmartField {
    SOURCE,
    MANUAL_COLLECTION,
    TAG,
    TITLE,
    AUTHOR,
    STATUS,
    RATING,
    ADDED_WITHIN_DAYS,
    LAST_READ_WITHIN_DAYS,
    METADATA_UPDATED_WITHIN_DAYS,
    PROGRESS,
    UNREAD_UPDATE,
    SOURCE_UPDATE,
    DORMANT_SOURCE,
}

data class SmartConditionDraft(
    val field: SmartField = SmartField.TAG,
    val value: String = "",
    val excluded: Boolean = false,
)

@Composable
fun CollectionManagerScreen(
    collections: List<LibraryCollection>,
    modifier: Modifier = Modifier,
    message: String? = null,
    onCreateManual: (String) -> Unit,
    onCreateSmart: (String, Boolean, List<SmartConditionDraft>) -> Unit,
    onDelete: (LibraryCollection) -> Unit,
) {
    var manualTitle by remember { mutableStateOf("") }
    var smartTitle by remember { mutableStateOf("") }
    var matchAll by remember { mutableStateOf(true) }
    var conditions by remember { mutableStateOf(listOf(SmartConditionDraft())) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        message?.let { Text(it) }
        Text(stringResource(R.string.collection_existing_title))
        if (collections.isEmpty()) {
            Text(stringResource(R.string.collection_existing_empty))
        } else {
            collections.forEach { collection ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(collection.title)
                        Text(
                            stringResource(
                                when (collection.kind) {
                                    CollectionKind.MANUAL -> R.string.collection_kind_manual
                                    CollectionKind.SMART -> R.string.collection_kind_smart
                                    CollectionKind.SUBSCRIPTION -> R.string.collection_kind_disabled_draft
                                },
                            ),
                        )
                    }
                    TsuyomiButton(
                        text = stringResource(R.string.collection_delete_action),
                        onClick = { onDelete(collection) },
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
                HorizontalDivider()
            }
        }

        Text(stringResource(R.string.collection_manual_title))
        OutlinedTextField(
            value = manualTitle,
            onValueChange = { manualTitle = it.take(256) },
            label = { Text(stringResource(R.string.collection_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TsuyomiButton(
            text = stringResource(R.string.collection_create_manual_action),
            onClick = { if (manualTitle.isNotBlank()) { onCreateManual(manualTitle); manualTitle = "" } },
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.PRIMARY,
        )

        HorizontalDivider()
        Text(stringResource(R.string.collection_smart_title))
        Text(stringResource(R.string.collection_smart_notice))
        OutlinedTextField(
            value = smartTitle,
            onValueChange = { smartTitle = it.take(256) },
            label = { Text(stringResource(R.string.collection_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(if (matchAll) R.string.collection_match_all else R.string.collection_match_any))
            Switch(checked = matchAll, onCheckedChange = { matchAll = it })
        }
        conditions.forEachIndexed { index, condition ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TsuyomiButton(
                    text = stringResource(R.string.collection_condition_type, condition.field.localizedName()),
                    onClick = {
                        val fields = SmartField.entries
                        conditions = conditions.toMutableList().also { list ->
                            list[index] = condition.copy(field = fields[(condition.field.ordinal + 1) % fields.size], value = "")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
                if (condition.field.requiresValue()) {
                    OutlinedTextField(
                        value = condition.value,
                        onValueChange = { value ->
                            conditions = conditions.toMutableList().also { it[index] = condition.copy(value = value.take(1024)) }
                        },
                        label = { Text(stringResource(condition.field.hintResource())) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.collection_exclude_condition))
                    Switch(
                        checked = condition.excluded,
                        onCheckedChange = { excluded ->
                            conditions = conditions.toMutableList().also { it[index] = condition.copy(excluded = excluded) }
                        },
                    )
                }
                if (conditions.size > 1) {
                    TsuyomiButton(
                        text = stringResource(R.string.collection_remove_condition),
                        onClick = { conditions = conditions.filterIndexed { current, _ -> current != index } },
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
            }
        }
        TsuyomiButton(
            text = stringResource(R.string.collection_add_condition),
            onClick = { if (conditions.size < 64) conditions = conditions + SmartConditionDraft() },
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.SECONDARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.collection_create_smart_action),
            onClick = {
                if (smartTitle.isNotBlank()) {
                    onCreateSmart(smartTitle, matchAll, conditions)
                    smartTitle = ""
                    conditions = listOf(SmartConditionDraft())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.PRIMARY,
        )
    }
}

@Composable
private fun SmartField.localizedName(): String = stringResource(
    when (this) {
        SmartField.SOURCE -> R.string.smart_field_source
        SmartField.MANUAL_COLLECTION -> R.string.smart_field_manual_collection
        SmartField.TAG -> R.string.smart_field_tag
        SmartField.TITLE -> R.string.smart_field_title
        SmartField.AUTHOR -> R.string.smart_field_author
        SmartField.STATUS -> R.string.smart_field_status
        SmartField.RATING -> R.string.smart_field_rating
        SmartField.ADDED_WITHIN_DAYS -> R.string.smart_field_added
        SmartField.LAST_READ_WITHIN_DAYS -> R.string.smart_field_last_read
        SmartField.METADATA_UPDATED_WITHIN_DAYS -> R.string.smart_field_metadata
        SmartField.PROGRESS -> R.string.smart_field_progress
        SmartField.UNREAD_UPDATE -> R.string.smart_field_unread
        SmartField.SOURCE_UPDATE -> R.string.smart_field_source_update
        SmartField.DORMANT_SOURCE -> R.string.smart_field_dormant
    },
)

private fun SmartField.requiresValue(): Boolean = this !in setOf(SmartField.UNREAD_UPDATE, SmartField.SOURCE_UPDATE, SmartField.DORMANT_SOURCE)
private fun SmartField.hintResource(): Int = when (this) {
    SmartField.RATING -> R.string.smart_hint_rating
    SmartField.ADDED_WITHIN_DAYS, SmartField.LAST_READ_WITHIN_DAYS, SmartField.METADATA_UPDATED_WITHIN_DAYS -> R.string.smart_hint_days
    SmartField.STATUS -> R.string.smart_hint_status
    SmartField.PROGRESS -> R.string.smart_hint_progress
    else -> R.string.smart_hint_terms
}
