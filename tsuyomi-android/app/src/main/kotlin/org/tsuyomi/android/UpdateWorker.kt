/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import org.tsuyomi.shared.librarydomain.UpdateRunOutcome
import org.tsuyomi.shared.librarydomain.UpdateSessionLease

/** Intent action handled by the app shell to reveal Library with its persisted update filter. */
const val UpdateNotificationOpenAction = "org.tsuyomi.android.action.OPEN_LIBRARY_UPDATES"

internal class UpdateWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val foregroundFailure = AtomicReference<ForegroundPromotionFailure?>(null)
    private val notificationId = (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    override suspend fun doWork(): Result {
        val application = applicationContext as? TsuyomiApplication ?: return Result.failure()
        return try {
            setProgress(workDataOf(ProgressStateKey to ProgressStarting))
            val outcome = application.updateCoordinator.runDetailed(
                inputData.getString(TriggerKey).takeIf { it in setOf(TriggerManual, TriggerScheduled) }
                    ?: TriggerScheduled,
            ) { lease ->
                val failure = promoteForeground(lease)
                if (failure == null) {
                    true
                } else {
                    foregroundFailure.set(failure)
                    if (!failure.retryable) application.updateCoordinator.failPending(lease, failure.reason)
                    false
                }
            }
            foregroundFailure.get()?.let { failure ->
                setProgress(
                    workDataOf(
                        ProgressStateKey to ProgressDeferred,
                        ForegroundReasonKey to failure.reason,
                    ),
                )
                if (failure.retryable) Result.retry() else Result.failure()
            } ?: when (outcome) {
                UpdateRunOutcome.COMPLETED, UpdateRunOutcome.COALESCED -> {
                    setProgress(workDataOf(ProgressStateKey to ProgressCompleted))
                    Result.success()
                }
                UpdateRunOutcome.BUSY, UpdateRunOutcome.RELINQUISHED -> {
                    setProgress(
                        workDataOf(
                            ProgressStateKey to ProgressDeferred,
                            ForegroundReasonKey to outcome.name.lowercase(),
                        ),
                    )
                    Result.retry()
                }
            }
        } catch (error: CancellationException) {
            // The coordinator relinquishes this lease; explicit user cancellation was already durable.
            throw error
        } catch (_: Throwable) {
            setProgress(workDataOf(ProgressStateKey to ProgressFailed))
            Result.failure()
        } finally {
            applicationContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
    }

    private suspend fun promoteForeground(lease: UpdateSessionLease): ForegroundPromotionFailure? = try {
        setForeground(foregroundInfo(lease))
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        ForegroundPromotionFailure("foreground-security-blocked", retryable = false)
    } catch (_: IllegalArgumentException) {
        ForegroundPromotionFailure("foreground-manifest-invalid", retryable = false)
    } catch (_: RuntimeException) {
        ForegroundPromotionFailure("foreground-start-deferred", retryable = true)
    }

    private data class ForegroundPromotionFailure(
        val reason: String,
        val retryable: Boolean,
    )

    private fun foregroundInfo(lease: UpdateSessionLease): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                applicationContext.getString(R.string.updates_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(applicationContext, NotificationChannelId)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.updates_notification_running))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openUpdatesIntent())
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    applicationContext.getString(R.string.updates_notification_cancel),
                    cancelIntent(lease),
                ).build(),
            )
            .build()
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun openUpdatesIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        notificationId,
        Intent(applicationContext, MainActivity::class.java)
            .setAction(UpdateNotificationOpenAction)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelIntent(lease: UpdateSessionLease): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        notificationId,
        Intent(applicationContext, UpdateCancelReceiver::class.java)
            .setAction(UpdateCancelReceiver.ActionCancel)
            .setData("tsuyomi://updates/cancel/$id".toUri())
            .putExtra(UpdateCancelReceiver.SessionIdKey, lease.id)
            .putExtra(UpdateCancelReceiver.OwnerTokenKey, lease.ownerToken)
            .putExtra(UpdateCancelReceiver.LeaseExpiresAtKey, lease.expiresAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal companion object {
        const val TriggerKey = "update-trigger"
        const val TriggerManual = "manual"
        const val TriggerScheduled = "scheduled"
        const val ProgressStateKey = "update-progress-state"
        const val ProgressStarting = "starting"
        const val ProgressDeferred = "foreground-deferred"
        const val ProgressCompleted = "completed"
        const val ProgressFailed = "failed"
        const val ForegroundReasonKey = "foreground-reason"
        const val NotificationChannelId = "updates-running"
    }
}

/** Records cancellation for the notification's exact fenced session before stopping its exact worker. */
internal class UpdateCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionCancel) return
        val sessionId = intent.getStringExtra(SessionIdKey)?.takeIf { it.length in 1..128 } ?: return
        val ownerToken = intent.getStringExtra(OwnerTokenKey)?.takeIf { it.length in 1..128 } ?: return
        if (!intent.hasExtra(LeaseExpiresAtKey)) return
        val leaseExpiresAt = intent.getLongExtra(LeaseExpiresAtKey, -1L)
        val workId = intent.data?.lastPathSegment
            ?.let { encoded -> runCatching { UUID.fromString(encoded) }.getOrNull() }
            ?: return
        val pendingResult = goAsync()
        (context.applicationContext as? TsuyomiApplication)
            ?.cancelUpdatesFromNotification(sessionId, ownerToken, leaseExpiresAt, workId, pendingResult)
            ?: pendingResult.finish()
    }

    internal companion object {
        const val ActionCancel = "org.tsuyomi.android.action.CANCEL_UPDATES"
        const val SessionIdKey = "update-session-id"
        const val OwnerTokenKey = "update-session-owner"
        const val LeaseExpiresAtKey = "update-session-expires-at"
    }
}
