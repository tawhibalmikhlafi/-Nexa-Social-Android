package com.nexa.social.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexa.social.NexaApplication
import com.nexa.social.core.common.Resource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Runs durable Supabase outboxes even when the app is not in the foreground. */
class NexaOfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NexaApplication ?: return Result.failure()
        var shouldRetry = false
        try {
            app.container.messageRepository.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            shouldRetry = true
        }
        try {
            app.container.clipRepository.refresh()
        } catch (_: Exception) {
            shouldRetry = true
        }
        try {
            when (val groups = app.container.groupRepository.refreshMyGroups()) {
                is Resource.Failure -> if (groups.reason == com.nexa.social.core.common.FailureReason.NETWORK) {
                    shouldRetry = true
                }
                else -> Unit
            }
        } catch (_: Exception) {
            shouldRetry = true
        }
        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nexa-supabase-offline-outboxes"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<NexaOfflineSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
