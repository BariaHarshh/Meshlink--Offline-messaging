package com.meshlink.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.domain.repository.PendingMessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic WorkManager job that purges expired store-and-forward entries from
 * [pending_messages] table.
 *
 * Schedule: every 6 hours (see [MeshLinkApp.scheduleCleanupWork]).
 * Trigger: [PendingMessageRepository.deleteExpired] deletes rows where expiresAt <= now.
 *
 * This keeps the database lean.  The 48-hour TTL on [PendingMessage] means at most
 * 2 cleanup runs will see any given entry before it disappears naturally.
 */
@HiltWorker
class MeshCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageRepository: PendingMessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        Timber.d("MeshCleanupWorker: deleting pending messages expired before $now")
        pendingMessageRepository.deleteExpired(now)
        Timber.i("MeshCleanupWorker: cleanup complete")
        return Result.success()
    }
}
