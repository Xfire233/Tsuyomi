/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.RoomUpdateStore
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.library.UpdateCoordinator
import org.tsuyomi.shared.librarydomain.UpdateCadence
import org.tsuyomi.shared.librarydomain.UpdatePolicy

/** Schedules durable update checks; policy persistence remains owned by [UpdateCoordinator]. */
interface UpdateScheduler {
    fun enqueueManual()

    suspend fun setPolicy(policy: UpdatePolicy)

    suspend fun cancel()
}

internal class UpdateRuntime(
    context: Context,
    database: TsuyomiDatabase,
    libraryRepository: RoomLibraryRepository,
) {
    private val applicationContext = context.applicationContext
    private val sourceRuntime = UpdateSourceRuntime(applicationContext, libraryRepository)

    val coordinator = UpdateCoordinator(
        store = RoomUpdateStore(database),
        probe = UpdateSourceProbe(applicationContext, sourceRuntime),
        candidates = sourceRuntime::candidates,
    )
    val scheduler = WorkManagerUpdateScheduler(applicationContext, coordinator)

    suspend fun restore() = scheduler.restore()
}

internal class WorkManagerUpdateScheduler(
    context: Context,
    private val coordinator: UpdateCoordinator,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) : UpdateScheduler {
    private val schedulingLock = Mutex()

    override fun enqueueManual() {
        workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInputData(workDataOf(UpdateWorker.TriggerKey to UpdateWorker.TriggerManual))
                .addTag(MANUAL_WORK_NAME)
                .build(),
        )
    }

    override suspend fun setPolicy(policy: UpdatePolicy) = schedulingLock.withLock {
        coordinator.setPolicy(policy)
        schedule(policy, ExistingPeriodicWorkPolicy.UPDATE)
    }

    override suspend fun cancel() = schedulingLock.withLock {
        val policy = coordinator.snapshots.first().policy
        coordinator.cancel()
        workManager.cancelUniqueWork(MANUAL_WORK_NAME).await()
        if (policy.cadence == UpdateCadence.OFF) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
        } else {
            schedule(policy, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }
    }

    /** Repairs only a missing enabled job; KEEP preserves WorkManager's live cadence after process death. */
    suspend fun restore() = schedulingLock.withLock {
        val policy = coordinator.snapshots.first().policy
        if (policy.cadence == UpdateCadence.OFF) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
        } else if (!hasLivePeriodicWork()) {
            schedule(policy, ExistingPeriodicWorkPolicy.KEEP)
        }
    }

    private suspend fun hasLivePeriodicWork(): Boolean = withContext(Dispatchers.IO) {
        workManager.getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get().any { work ->
            work.state !in setOf(WorkInfo.State.CANCELLED, WorkInfo.State.FAILED, WorkInfo.State.SUCCEEDED)
        }
    }

    private suspend fun schedule(policy: UpdatePolicy, policyForExistingWork: ExistingPeriodicWorkPolicy) {
        if (policy.cadence == UpdateCadence.OFF) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
            return
        }
        val intervalHours = policy.cadence.intervalHours
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            policyForExistingWork,
            PeriodicWorkRequestBuilder<UpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setInitialDelay(intervalHours, TimeUnit.HOURS)
                .setConstraints(policy.constraints())
                .setInputData(workDataOf(UpdateWorker.TriggerKey to UpdateWorker.TriggerScheduled))
                .addTag(PERIODIC_WORK_NAME)
                .build(),
        ).await()
    }

    private suspend fun Operation.await() {
        withContext(Dispatchers.IO) { result.get() }
    }

    private fun UpdatePolicy.constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(requiresCharging)
        .setRequiresBatteryNotLow(batteryNotLow)
        .build()

    private val UpdateCadence.intervalHours: Long
        get() = when (this) {
            UpdateCadence.HOURS_12 -> 12L
            UpdateCadence.DAILY -> 24L
            UpdateCadence.DAYS_3 -> 72L
            UpdateCadence.WEEKLY -> 168L
            UpdateCadence.OFF -> error("Disabled policy must not create periodic work")
        }

    internal companion object {
        const val ManualWorkName = "tsuyomi-update-manual"
        const val PeriodicWorkName = "tsuyomi-update-periodic"
        private const val MANUAL_WORK_NAME = ManualWorkName
        private const val PERIODIC_WORK_NAME = PeriodicWorkName
    }
}
