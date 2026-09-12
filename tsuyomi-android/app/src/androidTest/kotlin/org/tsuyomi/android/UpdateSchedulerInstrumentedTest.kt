/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.UpdateCadence
import org.tsuyomi.shared.librarydomain.UpdatePolicy

@RunWith(AndroidJUnit4::class)
class UpdateSchedulerInstrumentedTest {
    @Test
    fun off_policy_removes_periodic_work_and_survives_runtime_restore() = runBlocking {
        withOriginalPolicy {
            application.updateScheduler.setPolicy(UpdatePolicy())
            awaitNoActivePeriodicWork()

            (application.updateScheduler as WorkManagerUpdateScheduler).restore()

            assertEquals(UpdatePolicy(), application.updateCoordinator.snapshots.first().policy)
            awaitNoActivePeriodicWork()
        }
    }

    @Test
    fun restore_repairs_enabled_policy_when_process_dies_before_enqueue() = runBlocking {
        withOriginalPolicy {
            val policy = UpdatePolicy(cadence = UpdateCadence.DAILY)
            application.updateCoordinator.setPolicy(policy)
            awaitNoActivePeriodicWork()

            (application.updateScheduler as WorkManagerUpdateScheduler).restore()

            assertEquals(policy, application.updateCoordinator.snapshots.first().policy)
            assertScheduledAtCadence(awaitPeriodicWork(), UpdateCadence.DAILY, System.currentTimeMillis())
        }
    }

    @Test
    fun every_cadence_persists_constraints_and_next_schedule_in_real_periodic_work() = runBlocking {
        withOriginalPolicy {
            UpdateCadence.entries.filterNot { it == UpdateCadence.OFF }.forEach { cadence ->
                application.updateScheduler.setPolicy(UpdatePolicy())
                awaitNoActivePeriodicWork()
                val scheduledAt = System.currentTimeMillis()
                val policy = UpdatePolicy(
                    cadence = cadence,
                    unmeteredOnly = true,
                    requiresCharging = true,
                    batteryNotLow = true,
                )
                application.updateScheduler.setPolicy(policy)

                val scheduled = awaitPeriodicWork()
                assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
                assertEquals(NetworkType.UNMETERED, scheduled.constraints.requiredNetworkType)
                assertTrue(scheduled.constraints.requiresCharging())
                assertTrue(scheduled.constraints.requiresBatteryNotLow())
                assertScheduledAtCadence(scheduled, cadence, scheduledAt)
                assertEquals(policy, application.updateCoordinator.snapshots.first().policy)

                (application.updateScheduler as WorkManagerUpdateScheduler).restore()
                assertEquals(scheduled.id, awaitPeriodicWork().id)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun notification_denial_does_not_block_periodic_scheduling() = runBlocking {
        val originalAppOp = notificationAppOpMode()
        try {
            shell("appops set ${application.packageName} POST_NOTIFICATION ignore")
            assertFalse(notificationManager.areNotificationsEnabled())
            withOriginalPolicy {
                application.updateScheduler.setPolicy(UpdatePolicy(cadence = UpdateCadence.HOURS_12))

                assertEquals(WorkInfo.State.ENQUEUED, awaitPeriodicWork().state)
            }
        } finally {
            shell("appops set ${application.packageName} POST_NOTIFICATION $originalAppOp")
        }
    }

    @Test
    fun cancellation_replaces_periodic_work_at_the_next_opted_in_cadence() = runBlocking {
        withOriginalPolicy {
            application.updateScheduler.setPolicy(UpdatePolicy(cadence = UpdateCadence.DAILY))
            val beforeCancel = awaitPeriodicWork()
            val cancelledAt = System.currentTimeMillis()

            application.updateScheduler.cancel()

            val afterCancel = awaitPeriodicWork()
            assertNotEquals(beforeCancel.id, afterCancel.id)
            assertEquals(WorkInfo.State.ENQUEUED, afterCancel.state)
            assertScheduledAtCadence(afterCancel, UpdateCadence.DAILY, cancelledAt)
        }
    }

    @Test
    fun manual_request_during_owned_scan_finishes_without_a_follow_on_retry() = runBlocking {
        withOriginalPolicy {
            application.updateScheduler.cancel()
            val name = WorkManagerUpdateScheduler.ManualWorkName
            val previousIds = workManager.getWorkInfosForUniqueWork(name).get().map { it.id }.toSet()
            val entered = CompletableDeferred<Unit>()
            val running = async {
                application.updateCoordinator.runDetailed(onSessionStarted = {
                    entered.complete(Unit)
                    awaitCancellation()
                })
            }
            try {
                withTimeout(10_000) { entered.await() }
                application.updateScheduler.enqueueManual()
                awaitValue {
                    workManager.getWorkInfosForUniqueWork(name).get()
                        .firstOrNull { it.id !in previousIds && it.state == WorkInfo.State.SUCCEEDED }
                }
            } finally {
                application.updateCoordinator.cancel()
                running.cancelAndJoin()
            }
        }
    }

    private val application: TsuyomiApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TsuyomiApplication
    private val workManager: WorkManager
        get() = WorkManager.getInstance(application)
    private val notificationManager: NotificationManager
        get() = application.getSystemService(NotificationManager::class.java)

    private suspend fun withOriginalPolicy(block: suspend () -> Unit) {
        val original = application.updateCoordinator.snapshots.first().policy
        try {
            application.updateScheduler.setPolicy(UpdatePolicy())
            awaitNoActivePeriodicWork()
            block()
        } finally {
            application.updateScheduler.setPolicy(UpdatePolicy())
            awaitNoActivePeriodicWork()
            application.updateScheduler.setPolicy(original)
        }
    }

    private fun assertScheduledAtCadence(work: WorkInfo, cadence: UpdateCadence, scheduledAt: Long) {
        val expected = cadence.intervalMillis
        val lowerBound = scheduledAt + expected - SchedulingToleranceMillis
        val upperBound = scheduledAt + expected + SchedulingToleranceMillis
        assertTrue(
            "Expected ${cadence.name} next schedule in [$lowerBound, $upperBound], was ${work.nextScheduleTimeMillis}",
            work.nextScheduleTimeMillis in lowerBound..upperBound,
        )
    }

    private fun awaitPeriodicWork(): WorkInfo = awaitValue {
        activeWork(WorkManagerUpdateScheduler.PeriodicWorkName).singleOrNull()
    }

    private fun awaitNoActivePeriodicWork() {
        awaitValue {
            activeWork(WorkManagerUpdateScheduler.PeriodicWorkName)
                .takeIf { it.isEmpty() }
        }
    }

    private fun activeWork(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name)
        .get()
        .filter { it.state !in setOf(WorkInfo.State.CANCELLED, WorkInfo.State.FAILED, WorkInfo.State.SUCCEEDED) }

    private fun notificationAppOpMode(): String = Regex("POST_NOTIFICATION:\\s+(allow|ignore|default)")
        .find(shell("appops get ${application.packageName} POST_NOTIFICATION"))
        ?.groupValues
        ?.get(1)
        ?: "default"

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command)
        .let(ParcelFileDescriptor::AutoCloseInputStream)
        .bufferedReader()
        .use { it.readText() }

    private fun <T> awaitValue(value: () -> T?): T {
        repeat(100) {
            value()?.let { return it }
            Thread.sleep(25)
        }
        error("WorkManager did not reach the expected state")
    }

    private val UpdateCadence.intervalMillis: Long
        get() = when (this) {
            UpdateCadence.HOURS_12 -> 12L * 60 * 60 * 1000
            UpdateCadence.DAILY -> 24L * 60 * 60 * 1000
            UpdateCadence.DAYS_3 -> 3L * 24 * 60 * 60 * 1000
            UpdateCadence.WEEKLY -> 7L * 24 * 60 * 60 * 1000
            UpdateCadence.OFF -> error("Disabled policy has no cadence")
        }

    private companion object {
        const val SchedulingToleranceMillis = 2L * 60 * 1000
    }
}
