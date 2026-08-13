package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.repository.AccountDao

@HiltWorker
class KnowledgeSuggestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountDao: AccountDao,
    private val suggestionService: KnowledgeSuggestionService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        accountDao.queryAll().mapNotNull { it.id }.forEach { accountId ->
            runCatching { suggestionService.refreshIfNeeded(accountId) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(manager: WorkManager) = manager.enqueueUniquePeriodicWork(
            "KNOWLEDGE_SUGGESTION_REFRESH",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<KnowledgeSuggestionWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build(),
        )
    }
}
