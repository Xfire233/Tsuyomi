/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

data class FeatureIntroductionDefinition(
    val id: String,
    val version: Int,
    val title: String,
    val summary: String,
    val points: List<String>,
)

@Composable
fun HelpScreen(
    introductionsEnabled: Boolean,
    seenVersions: Set<String>,
    onIntroductionsEnabledChanged: (Boolean) -> Unit,
    onIntroductionSeen: (String, Int) -> Unit,
    onResetSeenVersions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIntroduction by remember { mutableStateOf<FeatureIntroductionDefinition?>(null) }
    val introductions = featureIntroductionDefinitions()
    val questions = helpQuestions().filter { (question, answer) ->
        query.isBlank() || question.contains(query, ignoreCase = true) || answer.contains(query, ignoreCase = true)
    }

    CenteredSettingsColumn(modifier) {
        SettingsSectionHeader(stringResource(R.string.settings_help_search_section))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            label = { Text(stringResource(R.string.settings_help_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
        )
        SettingsGroup(modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)) {
            questions.forEach { (question, answer) ->
                val expanded = expandedQuestion == question
                SettingsActionRow(
                    title = question,
                    summary = if (expanded) answer else null,
                    onClick = { expandedQuestion = if (expanded) null else question },
                )
            }
            if (questions.isEmpty()) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_help_no_results),
                    onClick = { query = "" },
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
                    onClick = { selectedIntroduction = introduction },
                )
            }
        }

    }

    selectedIntroduction?.let { introduction ->
        FeatureIntroductionDialog(
            introduction = introduction,
            onAcknowledged = {
                onIntroductionSeen(introduction.id, introduction.version)
                selectedIntroduction = null
            },
            onDismiss = { selectedIntroduction = null },
        )
    }
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(introduction.title) },
        text = {
            Column {
                Text(introduction.summary)
                introduction.points.forEach { Text("• $it", modifier = Modifier.padding(top = TsuyomiSpacing.Sm)) }
                Text(
                    stringResource(R.string.settings_help_introduction_authority_notice),
                    modifier = Modifier.padding(top = TsuyomiSpacing.Md),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledged) {
                Text(stringResource(R.string.settings_help_acknowledge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_help_later))
            }
        },
    )
}

@Composable
private fun helpQuestions(): List<Pair<String, String>> = listOf(
    stringResource(R.string.settings_help_question_offline) to stringResource(R.string.settings_help_answer_offline),
    stringResource(R.string.settings_help_question_cache) to stringResource(R.string.settings_help_answer_cache),
    stringResource(R.string.settings_help_question_remote) to stringResource(R.string.settings_help_answer_remote),
    stringResource(R.string.settings_help_question_reset) to stringResource(R.string.settings_help_answer_reset),
)
