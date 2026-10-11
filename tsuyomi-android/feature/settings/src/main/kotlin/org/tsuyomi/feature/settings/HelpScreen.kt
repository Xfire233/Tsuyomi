/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import org.tsuyomi.core.ui.components.TsuyomiTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

private data class HelpPagerItem(
    val key: String,
    val content: @Composable () -> Unit,
)

@Composable
fun HelpScreen(
    introductionsEnabled: Boolean,
    seenVersions: Set<String>,
    onIntroductionsEnabledChanged: (Boolean) -> Unit,
    selectedIntroductionId: String?,
    onIntroductionSelected: (String?) -> Unit,
    onIntroductionSeen: (String, Int) -> Unit,
    onResetSeenVersions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var expandedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedIntroduction = selectedIntroductionId?.let { featureIntroductionDefinition(it) }
    val introductions = featureIntroductionDefinitions()
    val questions = helpQuestions().filter { (question, answer) ->
        query.text.isBlank() || question.contains(query.text, ignoreCase = true) || answer.contains(query.text, ignoreCase = true)
    }
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK

    Box(modifier = modifier.fillMaxSize()) {
        val items = listOf(
            HelpPagerItem("help:search") {
                Column {
                    SettingsSectionHeader(stringResource(R.string.settings_help_search_section))
                    TsuyomiTextField(
                        value = query,
                        onValueChange = { query = it.copy(text = it.text.take(100)) },
                        label = stringResource(R.string.settings_help_search_label),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
                    )
                }
            },
            HelpPagerItem("help:questions") {
                SettingsGroup(
                    modifier = Modifier
                        // Reserve the group's padding before the nested pager measures its pages.
                        .heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity)
                        .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                ) {
                    TsuyomiMeasuredPager(
                        itemCount = questions.size.coerceAtLeast(1),
                        itemKey = { index -> questions.getOrNull(index)?.first ?: "help:no-results" },
                        fillViewport = false,
                        itemContent = { index ->
                            val questionAndAnswer = questions.getOrNull(index)
                            if (questionAndAnswer == null) {
                                SettingsActionRow(
                                    title = stringResource(R.string.settings_help_no_results),
                                    onClick = { query = TextFieldValue() },
                                    summary = stringResource(R.string.settings_help_clear_search),
                                )
                            } else {
                                val (question, answer) = questionAndAnswer
                                val expanded = expandedQuestion == question
                                Column(Modifier.heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity)) {
                                    SettingsActionRow(
                                        title = question,
                                        modifier = Modifier.semantics {
                                            if (expanded) collapse { expandedQuestion = null; true }
                                            else expand { expandedQuestion = question; true }
                                        },
                                        onClick = { expandedQuestion = if (expanded) null else question },
                                    )
                                    if (expanded) TsuyomiMeasuredTextPager(
                                        answer, fillViewport = false, alwaysShowPageControls = false,
                                        modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md).weight(1f, fill = false),
                                    )
                                }
                            }
                        },
                    )
                }
            },
            HelpPagerItem("help:introductions") {
                Column(Modifier.heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity)) {
                    SettingsSectionHeader(stringResource(R.string.settings_help_introductions_section))
                    SettingsGroup(modifier = Modifier.weight(1f, fill = false)) {
                        TsuyomiMeasuredPager(
                            itemCount = introductions.size + 2,
                            itemKey = { index -> when (index) {
                                0 -> "help:introductions-enabled"
                                1 -> "help:reset-seen"
                                else -> "help:introduction:${introductions[index - 2].id}"
                            } },
                            fillViewport = false,
                            itemContent = { index -> when (index) {
                                0 -> SettingsSwitchRow(
                                    title = stringResource(R.string.settings_help_introductions_enabled),
                                    summary = stringResource(R.string.settings_help_introductions_enabled_summary),
                                    checked = introductionsEnabled,
                                    onCheckedChange = onIntroductionsEnabledChanged,
                                )
                                1 -> SettingsActionRow(
                                    title = stringResource(R.string.settings_help_reset_seen),
                                    summary = stringResource(R.string.settings_help_reset_seen_summary),
                                    onClick = onResetSeenVersions,
                                )
                                else -> {
                                    val introduction = introductions[index - 2]
                                    SettingsActionRow(
                                        title = introduction.title,
                                        summary = if ("${introduction.id}:${introduction.version}" in seenVersions)
                                            stringResource(R.string.settings_help_seen) else introduction.summary,
                                        onClick = { onIntroductionSelected(introduction.id) },
                                    )
                                }
                            } },
                        )
                    }
                }
            },
        )

        CenteredSettingsItems(
            modifier = Modifier.fillMaxSize(),
            itemCount = items.size,
            itemKey = { index -> items[index].key },
            itemContent = { index ->
                if (eInk) {
                    items[index].content()
                } else if (index == 0) {
                    StandardHelpContent(
                        query = query,
                        onQueryChange = { query = it.copy(text = it.text.take(100)) },
                        questions = questions,
                        expandedQuestion = expandedQuestion,
                        onQuestionClick = { question ->
                            expandedQuestion = if (expandedQuestion == question) null else question
                        },
                        introductionsEnabled = introductionsEnabled,
                        onIntroductionsEnabledChanged = onIntroductionsEnabledChanged,
                        seenVersions = seenVersions,
                        introductions = introductions,
                        onResetSeenVersions = onResetSeenVersions,
                        onIntroductionSelected = onIntroductionSelected,
                    )
                }
            },
        )
    }

    selectedIntroduction?.let { introduction ->
        FeatureIntroductionDialog(
            introduction = introduction,
            onAcknowledged = {
                onIntroductionSeen(introduction.id, introduction.version)
                onIntroductionSelected(null)
            },
            onDismiss = { onIntroductionSelected(null) },
        )
    }
}

@Composable
private fun StandardHelpContent(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    questions: List<Pair<String, String>>,
    expandedQuestion: String?,
    onQuestionClick: (String) -> Unit,
    introductionsEnabled: Boolean,
    onIntroductionsEnabledChanged: (Boolean) -> Unit,
    seenVersions: Set<String>,
    introductions: List<FeatureIntroductionDefinition>,
    onResetSeenVersions: () -> Unit,
    onIntroductionSelected: (String) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_help_search_section))
    TsuyomiTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.settings_help_search_label),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
    )
    SettingsGroup(modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)) {
        questions.forEach { (question, answer) ->
            val expanded = expandedQuestion == question
            SettingsActionRow(
                title = question,
                summary = if (expanded) answer else null,
                modifier = Modifier.semantics {
                    if (expanded) {
                        collapse { onQuestionClick(question); true }
                    } else {
                        expand { onQuestionClick(question); true }
                    }
                },
                onClick = { onQuestionClick(question) },
            )
        }
        if (questions.isEmpty()) {
            SettingsActionRow(
                title = stringResource(R.string.settings_help_no_results),
                onClick = { onQueryChange(TextFieldValue()) },
                summary = stringResource(R.string.settings_help_clear_search),
            )
        }
    }
    SettingsSectionHeader(stringResource(R.string.settings_help_introductions_section))
    SettingsGroup {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_help_introductions_enabled),
            summary = stringResource(R.string.settings_help_introductions_enabled_summary),
            checked = introductionsEnabled,
            onCheckedChange = onIntroductionsEnabledChanged,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_help_reset_seen),
            summary = stringResource(R.string.settings_help_reset_seen_summary),
            onClick = onResetSeenVersions,
        )
        introductions.forEach { introduction ->
            SettingsActionRow(
                title = introduction.title,
                summary = if ("${introduction.id}:${introduction.version}" in seenVersions) {
                    stringResource(R.string.settings_help_seen)
                } else {
                    introduction.summary
                },
                onClick = { onIntroductionSelected(introduction.id) },
            )
        }
    }
}
data class FeatureIntroductionDefinition(
    val id: String,
    val version: Int,
    val title: String,
    val summary: String,
    val points: List<String>,
)


@Composable
fun featureIntroductionDefinitions(): List<FeatureIntroductionDefinition> = listOf(
    FeatureIntroductionDefinition(
        "website-mirror",
        1,
        stringResource(R.string.settings_help_intro_mirror_title),
        stringResource(R.string.settings_help_intro_mirror_summary),
        listOf(
            stringResource(R.string.settings_help_intro_mirror_point_1),
            stringResource(R.string.settings_help_intro_mirror_point_2),
        ),
    ),
    FeatureIntroductionDefinition(
        "updates",
        1,
        stringResource(R.string.settings_help_intro_updates_title),
        stringResource(R.string.settings_help_intro_updates_summary),
        listOf(
            stringResource(R.string.settings_help_intro_updates_point_1),
            stringResource(R.string.settings_help_intro_updates_point_2),
        ),
    ),
    FeatureIntroductionDefinition(
        "smart-collection",
        1,
        stringResource(R.string.settings_help_intro_smart_title),
        stringResource(R.string.settings_help_intro_smart_summary),
        listOf(
            stringResource(R.string.settings_help_intro_smart_point_1),
            stringResource(R.string.settings_help_intro_smart_point_2),
        ),
    ),
    FeatureIntroductionDefinition(
        "website-writeback",
        1,
        stringResource(R.string.settings_help_intro_writeback_title),
        stringResource(R.string.settings_help_intro_writeback_summary),
        listOf(
            stringResource(R.string.settings_help_intro_writeback_point_1),
            stringResource(R.string.settings_help_intro_writeback_point_2),
        ),
    ),
    FeatureIntroductionDefinition(
        "data-transfer",
        1,
        stringResource(R.string.settings_help_intro_data_title),
        stringResource(R.string.settings_help_intro_data_summary),
        listOf(
            stringResource(R.string.settings_help_intro_data_point_1),
            stringResource(R.string.settings_help_intro_data_point_2),
        ),
    ),
)

@Composable
fun featureIntroductionDefinition(id: String): FeatureIntroductionDefinition? =
    featureIntroductionDefinitions().firstOrNull { it.id == id }

@Composable
fun FeatureIntroductionDialog(
    introduction: FeatureIntroductionDefinition,
    onAcknowledged: () -> Unit,
    onDismiss: () -> Unit,
) {
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = introduction.title,
        body = {
            Text(introduction.summary)
            introduction.points.forEach { Text("• $it", modifier = Modifier.padding(top = TsuyomiSpacing.Sm)) }
            Text(
                stringResource(R.string.settings_help_introduction_authority_notice),
                modifier = Modifier.padding(top = TsuyomiSpacing.Md),
            )
        },
        confirmLabel = stringResource(R.string.settings_help_acknowledge),
        onConfirm = onAcknowledged,
        dismissLabel = stringResource(R.string.settings_help_later),
    )
}

@Composable
private fun helpQuestions(): List<Pair<String, String>> = listOf(
    stringResource(R.string.settings_help_question_offline) to stringResource(R.string.settings_help_answer_offline),
    stringResource(R.string.settings_help_question_cache) to stringResource(R.string.settings_help_answer_cache),
    stringResource(R.string.settings_help_question_remote) to stringResource(R.string.settings_help_answer_remote),
    stringResource(R.string.settings_help_question_reset) to stringResource(R.string.settings_help_answer_reset),
)
