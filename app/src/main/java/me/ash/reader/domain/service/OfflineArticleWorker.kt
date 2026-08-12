package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.repository.ArticleDao

@HiltWorker
class OfflineArticleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleDao: ArticleDao,
    private val repository: OfflineArticleRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        return runCatching {
            if (inputData.getBoolean(KEY_REMOVE, false)) repository.remove(id)
            else repository.save(articleDao.queryById(id) ?: error("文章不存在"))
        }.fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
    }
    companion object {
        private const val KEY_ID = "articleId"
        private const val KEY_REMOVE = "remove"
        fun enqueue(manager: WorkManager, articleId: String, remove: Boolean) {
            val builder = OneTimeWorkRequestBuilder<OfflineArticleWorker>().setInputData(workDataOf(KEY_ID to articleId, KEY_REMOVE to remove))
            if (!remove) builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            manager.enqueueUniqueWork("OFFLINE_$articleId", ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}

@HiltWorker
class OfflineCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: OfflineArticleRepository,
    private val settingsProvider: me.ash.reader.infrastructure.preference.SettingsProvider,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        repository.cleanupAll(settingsProvider.settings.offlineRetentionDays)
    }.fold({ Result.success() }, { Result.retry() })
    companion object {
        fun schedule(manager: WorkManager) = manager.enqueueUniquePeriodicWork(
            "OFFLINE_CLEANUP", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<OfflineCleanupWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
