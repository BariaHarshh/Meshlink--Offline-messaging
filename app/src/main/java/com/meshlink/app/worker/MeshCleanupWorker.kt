package com.meshlink.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic WorkManager job that purges expired store-and-forward entries from
 * [pending_messages] table and marks expired or retry-exhausted unresolved messages as FAILED.
 *
 * Schedule: every 6 hours (see [MeshLinkApp.scheduleCleanupWork]).
 */
@HiltWorker
class MeshCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingMessageRepository: PendingMessageRepository,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        Timber.d("MeshCleanupWorker: deleting pending messages expired before $now")
        pendingMessageRepository.deleteExpired(now)
        val markedFailed = messageRepository.markFailedIfExpiredOrExhausted(now, Message.MAX_RETRY_COUNT)
        Timber.i("MeshCleanupWorker: cleanup complete, marked $markedFailed expired/exhausted messages as FAILED")
        return Result.success()
    }
}
