package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.ash.reader.domain.model.article.RagflowDocument
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.RagflowDocumentDao
import java.util.concurrent.TimeUnit

@HiltWorker
class RagflowSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleDao: ArticleDao,
    private val repository: RagflowRepository,
    private val suggestionService: KnowledgeSuggestionService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        return runCatching {
            val article = articleDao.queryById(id)
            article?.article?.accountId?.let {
                suggestionService.markSyncing(it)
                try {
                    if (article.article.isStarred) repository.sync(article) else repository.delete(id)
                } catch (error: Throwable) {
                    suggestionService.markSyncFailure(it, error)
                    throw error
                }
                suggestionService.refreshIfNeeded(it)
            }
        }.fold({ Result.success() }, { if (runAttemptCount < 5) Result.retry() else Result.failure() })
    }
    companion object {
        private const val KEY_ID = "articleId"
        fun enqueue(manager: WorkManager, articleId: String) {
            val request = OneTimeWorkRequestBuilder<RagflowSyncWorker>()
                .setInputData(workDataOf(KEY_ID to articleId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            manager.enqueueUniqueWork("RAGFLOW_$articleId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

@HiltWorker
class RagflowBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleDao: ArticleDao,
    private val repository: RagflowRepository,
    private val mappingDao: RagflowDocumentDao,
    private val suggestionService: KnowledgeSuggestionService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val starred = articleDao.queryAllStarred()
        val accountIds = starred.map { it.article.accountId }.distinct()
        if (!repository.isConfigured()) {
            accountIds.forEach { suggestionService.refreshIfNeeded(it) }
            return@runCatching
        }
        val localMappings = mappingDao.all().associateBy { it.articleId }
        val needsRemoteReconciliation = force || starred.any {
            val mapping = localMappings[it.article.id]
            mapping == null ||
                mapping.status != RagflowDocument.STATUS_SYNCED ||
                !mapping.contentHash.startsWith(RAGFLOW_IDENTITY_VERSION)
        }
        val remoteDocuments = if (needsRemoteReconciliation) {
            repository.listManagedDocuments()
        } else {
            null
        }
        accountIds.forEach { accountId ->
            suggestionService.markSyncing(accountId)
            val accountArticles = starred.filter { it.article.accountId == accountId }
            val failures = accountArticles.mapNotNull { article ->
                runCatching {
                    repository.sync(
                        article = article,
                        force = force,
                        remoteDocuments = remoteDocuments,
                    )
                }.exceptionOrNull()
            }
            if (failures.isNotEmpty() && mappingDao.syncedKnowledgeSnapshot(accountId).isEmpty()) {
                suggestionService.markSyncFailure(accountId, failures.first())
                throw failures.first()
            }
            suggestionService.refreshIfNeeded(accountId)
        }
        val starredIds = starred.mapTo(mutableSetOf()) { it.article.id }
        mappingDao.all().filter { it.articleId !in starredIds }.forEach { repository.delete(it.articleId) }
    }.fold({ Result.success() }, { if (runAttemptCount < 5) Result.retry() else Result.failure() })
    companion object {
        private const val KEY_FORCE = "force"

        fun enqueue(manager: WorkManager, force: Boolean = false) = manager.enqueueUniqueWork(
            "RAGFLOW_BACKFILL", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RagflowBackfillWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        fun schedule(manager: WorkManager) = manager.enqueueUniquePeriodicWork(
            "RAGFLOW_RECONCILE",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RagflowBackfillWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
