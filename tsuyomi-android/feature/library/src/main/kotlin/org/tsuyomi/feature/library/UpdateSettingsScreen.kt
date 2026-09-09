/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsInfoRow
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.librarydomain.UpdateCadence
import org.tsuyomi.shared.librarydomain.UpdatePolicy
import org.tsuyomi.shared.librarydomain.UpdateSnapshot
import org.tsuyomi.shared.model.BookIdentity

/** Settings and bounded durable scan report; it deliberately contains no update book list. */
@Composable
fun UpdateSettingsScreen(
    knownSourceIds: Set<String>,
    snapshot: UpdateSnapshot,
    bookLabels: Map<BookIdentity, String>,
    onSetPolicy: suspend (UpdatePolicy) -> Unit,
    onExcludeBook: suspend (BookIdentity, Boolean) -> Unit,
    onExcludeSource: suspend (String, Boolean) -> Unit,
    onLoadMoreSessionItems: suspend () -> Unit,
    notificationsUnavailable: Boolean,
    notificationPermissionRequestable: Boolean,
    onRequestNotifications: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val policy = snapshot.policy
    val manageableSourceIds = (knownSourceIds + snapshot.excludedSources).sorted()
    val automaticEnabled = policy.cadence != UpdateCadence.OFF
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("library-update-settings"),
        contentPadding = PaddingValues(bottom = TsuyomiSpacing.Lg),
    ) {
        snapshot.session?.let { session ->
            item {
                SettingsSectionHeader(stringResource(R.string.updates_recent_heading))
                SettingsGroup(Modifier.padding(horizontal = TsuyomiSpacing.Md)) {
                    Text(
                        stringResource(
                            R.string.updates_recent_summary,
                            updateSessionStateLabel(session.state),
                            session.completed,
                            session.total,
                            session.updated,
                            session.failed,
                        ),
                        modifier = Modifier.padding(TsuyomiSpacing.Md),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (snapshot.sessionItems.isNotEmpty()) {
                items(snapshot.sessionItems, key = { item -> "${item.identity.sourceId}:${item.identity.remoteBookId}" }) { item ->
                    SettingsInfoRow(
                        title = item.title,
                        summary = listOfNotNull(
                            updateItemStateLabel(item.state),
                            updateReasonLabel(item.reason),
                        ).distinct().joinToString(" · "),
                    )
                }
            }
            if (snapshot.hasMoreSessionItems) {
                item {
                    TsuyomiButton(
                        text = stringResource(
                            R.string.updates_load_more_reports,
                            snapshot.sessionItems.size,
                            snapshot.totalSessionItems,
                        ),
                        onClick = { scope.launch { onLoadMoreSessionItems() } },
                        modifier = Modifier.padding(TsuyomiSpacing.Md),
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
            }
        }
        item {
            SettingsSectionHeader(stringResource(R.string.updates_schedule_heading))
            SettingsGroup(Modifier.padding(horizontal = TsuyomiSpacing.Md)) {
                SegmentedSelector(
                    options = UpdateCadence.entries.map { cadence -> TsuyomiSegment(cadence, cadence.label()) },
                    selected = policy.cadence,
                    onSelect = { cadence -> scope.launch { onSetPolicy(policy.copy(cadence = cadence)) } },
                    label = stringResource(R.string.updates_cadence),
                    modifier = Modifier.padding(TsuyomiSpacing.Md).testTag("updates-cadence"),
                )
            }
        }
        item {
            SettingsSectionHeader(stringResource(R.string.updates_constraints_heading))
            SettingsGroup(Modifier.padding(horizontal = TsuyomiSpacing.Md)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.updates_unmetered),
                    summary = stringResource(R.string.updates_unmetered_summary),
                    checked = policy.unmeteredOnly,
                    enabled = automaticEnabled,
                    disabledReason = stringResource(R.string.updates_constraints_disabled),
                    onCheckedChange = { checked -> scope.launch { onSetPolicy(policy.copy(unmeteredOnly = checked)) } },
                    modifier = Modifier.testTag("updates-unmetered"),
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.updates_charging),
                    summary = stringResource(R.string.updates_charging_summary),
                    checked = policy.requiresCharging,
                    enabled = automaticEnabled,
                    disabledReason = stringResource(R.string.updates_constraints_disabled),
                    onCheckedChange = { checked -> scope.launch { onSetPolicy(policy.copy(requiresCharging = checked)) } },
                    modifier = Modifier.testTag("updates-charging"),
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.updates_battery_not_low),
                    summary = stringResource(R.string.updates_battery_not_low_summary),
                    checked = policy.batteryNotLow,
                    enabled = automaticEnabled,
                    disabledReason = stringResource(R.string.updates_constraints_disabled),
                    onCheckedChange = { checked -> scope.launch { onSetPolicy(policy.copy(batteryNotLow = checked)) } },
                    modifier = Modifier.testTag("updates-battery"),
                )
            }
        }
        if (notificationsUnavailable && onRequestNotifications != null) {
            item {
                SettingsSectionHeader(stringResource(R.string.updates_notifications_heading))
                SettingsActionRow(
                    title = stringResource(
                        if (notificationPermissionRequestable) {
                            R.string.updates_notifications_enable
                        } else {
                            R.string.updates_notifications_open_settings
                        },
                    ),
                    summary = stringResource(
                        if (notificationPermissionRequestable) {
                            R.string.updates_notifications_summary
                        } else {
                            R.string.updates_notifications_settings_summary
                        },
                    ),
                    onClick = onRequestNotifications,
                    modifier = Modifier.testTag("updates-notifications-enable"),
                )
            }
        }
        item {
            SettingsSectionHeader(stringResource(R.string.updates_exclusions_heading))
            Text(
                stringResource(R.string.updates_exclusions_summary),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.excludedBooks.isEmpty() && manageableSourceIds.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.updates_no_exclusions),
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (snapshot.excludedBooks.isNotEmpty()) {
            item { SettingsSectionHeader(stringResource(R.string.updates_excluded_books)) }
            items(snapshot.excludedBooks.toList().sortedWith(compareBy(BookIdentity::sourceId, BookIdentity::remoteBookId)), key = ::updateIdentityKey) { identity ->
                SettingsActionRow(
                    title = bookLabels[identity] ?: identity.remoteBookId,
                    summary = stringResource(R.string.updates_excluded_book_summary, identity.sourceId),
                    onClick = { scope.launch { onExcludeBook(identity, false) } },
                    modifier = Modifier.testTag("updates-restore-book-${updateIdentityKey(identity)}"),
                )
            }
        }
        if (manageableSourceIds.isNotEmpty()) {
            item { SettingsSectionHeader(stringResource(R.string.updates_excluded_sources)) }
            items(manageableSourceIds, key = { it }) { sourceId ->
                val excluded = sourceId in snapshot.excludedSources
                SettingsActionRow(
                    title = sourceId,
                    summary = stringResource(
                        if (excluded) R.string.updates_excluded_source_summary else R.string.updates_exclude_source_summary,
                    ),
                    onClick = { scope.launch { onExcludeSource(sourceId, !excluded) } },
                    modifier = Modifier.testTag("updates-source-${sourceId.hashCode()}"),
                )
            }
        }
    }
}

@Composable
private fun UpdateCadence.label(): String = stringResource(
    when (this) {
        UpdateCadence.OFF -> R.string.updates_cadence_off
        UpdateCadence.HOURS_12 -> R.string.updates_cadence_12_hours
        UpdateCadence.DAILY -> R.string.updates_cadence_daily
        UpdateCadence.DAYS_3 -> R.string.updates_cadence_3_days
        UpdateCadence.WEEKLY -> R.string.updates_cadence_weekly
    },
)

@Composable
private fun updateSessionStateLabel(state: String): String = stringResource(updateSessionStateLabelRes(state))

@StringRes
internal fun updateSessionStateLabelRes(state: String): Int = when (state) {
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.QUEUED -> R.string.updates_session_queued
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.RUNNING -> R.string.updates_session_running
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.COMPLETED -> R.string.updates_session_completed
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.PARTIAL -> R.string.updates_session_partial
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.FAILED -> R.string.updates_session_failed
    org.tsuyomi.shared.librarydomain.UpdateSessionStates.CANCELLED -> R.string.updates_session_cancelled
    else -> R.string.updates_session_failed
}

@Composable
private fun updateItemStateLabel(state: String): String = stringResource(updateItemStateLabelRes(state))

@StringRes
internal fun updateItemStateLabelRes(state: String): Int = when (state) {
    "PENDING" -> R.string.updates_state_waiting
    "UPDATED" -> R.string.updates_state_updated
    "UNCHANGED" -> R.string.updates_state_unchanged
    "UNAVAILABLE" -> R.string.updates_state_unavailable
    "FAILED" -> R.string.updates_state_failed
    "SKIPPED" -> R.string.updates_state_skipped
    "CANCELLED" -> R.string.updates_state_cancelled
    else -> R.string.updates_state_failed
}
private val UPDATE_DIAGNOSTIC_REASON = Regex("^[a-z][a-z0-9._-]{0,127}$")

@Composable
private fun updateReasonLabel(reason: String?): String? = reason?.let { reason ->
    val label = stringResource(updateReasonLabelRes(reason.substringBefore('.')))
    if (reason.startsWith("source-") && '.' in reason && UPDATE_DIAGNOSTIC_REASON.matches(reason)) {
        stringResource(R.string.updates_reason_diagnostic, label, reason)
    } else {
        label
    }
}

@StringRes
internal fun updateReasonLabelRes(code: String): Int = when {
    code == "excluded" || code == "excluded-or-ineligible" -> R.string.updates_reason_excluded
    code in setOf(
        "ineligible",
        "source-not-installed",
        "source-unavailable",
        "source-unverified",
        "source-credentials-unavailable",
        "source-lease-unavailable",
        "source-extension-cancelled",
    ) -> R.string.updates_reason_source_unavailable
    code.startsWith("source-network-") -> R.string.updates_reason_network
    code == "source-session-required" || code == "source-verification-required" ->
        R.string.updates_reason_verification
    code == "updates-not-supported" -> R.string.updates_reason_unsupported
    code == "stale-source-lease" || code == "source-evidence-mismatch" ->
        R.string.updates_reason_source_changed
    code == "prior-anchor-invalid" || code == "prior-anchor-not-prefix" ->
        R.string.updates_reason_directory_changed
    else -> R.string.updates_reason_failed
}

fun updateIdentityKey(identity: BookIdentity): String =
    "${identity.sourceId.length}:${identity.sourceId}${identity.remoteBookId.length}:${identity.remoteBookId}"
