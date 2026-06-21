package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.model.article.ArticleAiContent
import me.ash.reader.domain.repository.ArticleAiContentDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.preference.AiProviderPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

@HiltWorker
class ArticleInsightWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val articleAiContentDao: ArticleAiContentDao,
    private val readerCacheHelper: ReaderCacheHelper,
    private val geminiService: GeminiService,
    private val settingsProvider: SettingsProvider,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val articleId = inputData.getString(KEY_ARTICLE_ID) ?: return Result.failure()
        val articleWithFeed = articleDao.queryById(articleId) ?: return Result.failure()
        val article = articleWithFeed.article
        val content =
            if (articleWithFeed.feed.isFullContent) {
                readerCacheHelper.readOrFetchFullContent(article).getOrNull()
            } else {
                article.rawDescription
            }.orEmpty()

        if (content.isBlank()) {
            articleAiContentDao.upsert(
                ArticleAiContent(
                    articleId = articleId,
                    type = ArticleAiContent.Type.INSIGHT,
                    status = ArticleAiContent.Status.FAILED,
                    errorMessage = "No content for insight",
                    updatedAt = Date(),
                )
            )
            return Result.failure()
        }

        return runCatching {
            val insight = geminiService.generateInsight(content)
            articleAiContentDao.upsert(
                ArticleAiContent(
                    articleId = articleId,
                    type = ArticleAiContent.Type.INSIGHT,
                    status = ArticleAiContent.Status.SUCCESS,
                    content = insight,
                    model = settingsProvider.settings.currentInsightModel(),
                    prompt = settingsProvider.settings.geminiInsightPrompt,
                    updatedAt = Date(),
                )
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { throwable ->
                articleAiContentDao.upsert(
                    ArticleAiContent(
                        articleId = articleId,
                        type = ArticleAiContent.Type.INSIGHT,
                        status = ArticleAiContent.Status.FAILED,
                        errorMessage = throwable.message ?: "Unknown error",
                        model = settingsProvider.settings.currentInsightModel(),
                        prompt = settingsProvider.settings.geminiInsightPrompt,
                        updatedAt = Date(),
                    )
                )
                Result.failure()
            },
        )
    }

    companion object {
        private const val KEY_ARTICLE_ID = "articleId"
        private const val WORK_NAME_PREFIX = "ARTICLE_INSIGHT_"

        fun enqueue(workManager: WorkManager, articleId: String) {
            val request =
                OneTimeWorkRequestBuilder<ArticleInsightWorker>()
                    .setInputData(workDataOf(KEY_ARTICLE_ID to articleId))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_PREFIX + articleId,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

private fun me.ash.reader.infrastructure.preference.Settings.currentInsightModel(): String =
    when (aiProvider) {
        AiProviderPreference.OpenAI -> codexInsightModel
        else -> geminiInsightModel
    }
