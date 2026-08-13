package me.ash.reader.domain.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.KnowledgeSuggestionCache
import me.ash.reader.domain.repository.KnowledgeSuggestionCacheDao
import me.ash.reader.domain.repository.RagflowDocumentDao
import me.ash.reader.domain.repository.RagflowKnowledgeRevision
import me.ash.reader.infrastructure.di.IODispatcher

@Singleton
class KnowledgeSuggestionService @Inject constructor(
    private val cacheDao: KnowledgeSuggestionCacheDao,
    private val documentDao: RagflowDocumentDao,
    private val ragflow: RagflowRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val refreshMutex = Mutex()

    suspend fun cachedQuestions(accountId: Int): List<String> =
        withContext(ioDispatcher) { decodeQuestions(cacheDao.get(accountId)?.questionsJson) }

    fun observeCachedQuestions(accountId: Int): Flow<List<String>> =
        cacheDao.observe(accountId)
            .map { decodeQuestions(it?.questionsJson) }
            .distinctUntilChanged()

    fun observeState(accountId: Int): Flow<KnowledgeSuggestionState> =
        cacheDao.observe(accountId)
            .map { it.toSuggestionState() }
            .distinctUntilChanged()

    suspend fun markSyncing(accountId: Int) = updateState(
        accountId,
        KnowledgeSuggestionCache.STATUS_SYNCING,
    )

    suspend fun markSyncFailure(accountId: Int, error: Throwable) = updateState(
        accountId,
        KnowledgeSuggestionCache.STATUS_FAILED,
        "星标文章同步失败：${error.userMessage()}",
    )

    suspend fun refreshIfNeeded(accountId: Int, nowMillis: Long = System.currentTimeMillis()): Boolean =
        withContext(ioDispatcher) {
            refreshMutex.withLock {
                if (!ragflow.isConfigured()) {
                    updateState(
                        accountId,
                        KnowledgeSuggestionCache.STATUS_CONFIGURATION_REQUIRED,
                        "请先完成 RAGFlow 数据集与对话助手配置",
                    )
                    return@withLock false
                }
                val snapshot = documentDao.syncedKnowledgeSnapshot(accountId)
                if (snapshot.isEmpty()) {
                    updateState(
                        accountId,
                        KnowledgeSuggestionCache.STATUS_INSUFFICIENT_CONTENT,
                        "尚无已完成解析的星标文章",
                    )
                    return@withLock false
                }
                val cached = cacheDao.get(accountId)
                if (!shouldRefreshKnowledgeSuggestions(cached, snapshot, nowMillis)) {
                    updateState(accountId, KnowledgeSuggestionCache.STATUS_READY)
                    return@withLock false
                }
                updateState(accountId, KnowledgeSuggestionCache.STATUS_GENERATING)
                runCatching { ragflow.suggestQuestions().getOrThrow() }
                    .fold(
                        onSuccess = { questions ->
                            if (questions.isEmpty()) {
                                updateState(
                                    accountId,
                                    KnowledgeSuggestionCache.STATUS_INSUFFICIENT_CONTENT,
                                    "RAGFlow 未返回符合要求的探索问题",
                                )
                                return@withLock false
                            }
                            cacheDao.upsert(
                                KnowledgeSuggestionCache(
                                    accountId = accountId,
                                    questionsJson = encodeQuestions(questions),
                                    snapshotJson = encodeSnapshot(snapshot),
                                    generatedAt = Date(nowMillis),
                                    status = KnowledgeSuggestionCache.STATUS_READY,
                                    updatedAt = Date(nowMillis),
                                )
                            )
                        },
                        onFailure = { error ->
                            updateState(
                                accountId,
                                KnowledgeSuggestionCache.STATUS_FAILED,
                                "探索问题生成失败：${error.userMessage()}",
                            )
                            throw error
                        },
                    )
                true
            }
        }

    private suspend fun updateState(accountId: Int, status: Int, errorMessage: String? = null) {
        val current = cacheDao.get(accountId)
        cacheDao.upsert(
            current?.copy(
                status = status,
                errorMessage = errorMessage,
                updatedAt = Date(),
            ) ?: KnowledgeSuggestionCache(
                accountId = accountId,
                questionsJson = "[]",
                snapshotJson = "{}",
                status = status,
                errorMessage = errorMessage,
            )
        )
    }
}

data class KnowledgeSuggestionState(
    val status: Int = KnowledgeSuggestionCache.STATUS_PREPARING,
    val errorMessage: String? = null,
)

private fun KnowledgeSuggestionCache?.toSuggestionState() = KnowledgeSuggestionState(
    status = this?.status ?: KnowledgeSuggestionCache.STATUS_PREPARING,
    errorMessage = this?.errorMessage,
)

private fun Throwable.userMessage(): String =
    message?.lineSequence()?.firstOrNull()?.take(240)?.takeIf(String::isNotBlank)
        ?: this::class.java.simpleName

internal const val KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS = 24 * 60 * 60 * 1_000L
internal const val KNOWLEDGE_SUGGESTION_MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1_000L

internal fun shouldRefreshKnowledgeSuggestions(
    cache: KnowledgeSuggestionCache?,
    snapshot: List<RagflowKnowledgeRevision>,
    nowMillis: Long,
): Boolean {
    if (snapshot.isEmpty()) return false
    if (cache == null || decodeQuestions(cache.questionsJson).isEmpty()) return true
    val age = (nowMillis - cache.generatedAt.time).coerceAtLeast(0L)
    if (age >= KNOWLEDGE_SUGGESTION_MAX_AGE_MILLIS) return true
    if (age < KNOWLEDGE_SUGGESTION_MIN_REFRESH_MILLIS) return false

    val previous = decodeSnapshot(cache.snapshotJson)
    val current = snapshot.associate { it.articleId to it.contentHash }
    val changed = (previous.keys + current.keys).count { previous[it] != current[it] }
    val total = maxOf(previous.size, current.size, 1)
    return changed >= 10 || changed.toDouble() / total >= 0.10
}

internal fun encodeQuestions(questions: List<String>): String =
    knowledgeSuggestionGson.toJson(questions.distinct())

internal fun decodeQuestions(value: String?): List<String> = runCatching {
    knowledgeSuggestionGson.fromJson<List<String>>(
        value ?: "[]",
        object : TypeToken<List<String>>() {}.type,
    ).map(String::trim).filter(String::isNotBlank).distinct()
}.getOrDefault(emptyList())

internal fun encodeSnapshot(snapshot: List<RagflowKnowledgeRevision>): String =
    knowledgeSuggestionGson.toJson(snapshot.associate { it.articleId to it.contentHash })

private fun decodeSnapshot(value: String): Map<String, String> = runCatching {
    knowledgeSuggestionGson.fromJson<Map<String, String>>(
        value,
        object : TypeToken<Map<String, String>>() {}.type,
    )
}.getOrDefault(emptyMap())

private val knowledgeSuggestionGson = Gson()
