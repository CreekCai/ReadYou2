package me.ash.reader.domain.service

import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.RagflowDocument
import me.ash.reader.domain.repository.RagflowDocumentDao
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class RagAnswer(val answer: String, val sessionId: String?, val sources: List<RagSource>)
data class RagSource(val title: String, val url: String?, val content: String?)
data class RagflowOption(val id: String, val name: String)
data class RagflowCatalog(
    val datasets: List<RagflowOption>,
    val chats: List<RagflowOption>,
)
data class RagflowRemoteDocument(val id: String, val name: String)

class RagflowRepository @Inject constructor(
    private val client: OkHttpClient,
    private val settingsProvider: SettingsProvider,
    private val mappingDao: RagflowDocumentDao,
    private val readerCache: ReaderCacheHelper,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val ragflowClient = client.newBuilder()
        .readTimeout(RAGFLOW_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun isConfigured() = settingsProvider.settings.run { ragflowBaseUrl.isNotBlank() && ragflowApiKey.isNotBlank() && ragflowDatasetId.isNotBlank() && ragflowChatId.isNotBlank() }

    suspend fun discover(baseUrl: String, apiKey: String): Result<RagflowCatalog> =
        withContext(ioDispatcher) {
            runCatching {
                require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                    "请输入有效的 RAGFlow 地址"
                }
                require(apiKey.isNotBlank()) { "请输入 API 密钥" }
                val headers = auth(apiKey)
                val datasetsRoot = JSONObject(
                    execute(
                        Request.Builder()
                            .url(url(baseUrl, "/api/v1/datasets?page=1&page_size=100"))
                            .headers(headers)
                            .get()
                            .build()
                    )
                )
                val chatsRoot = JSONObject(
                    execute(
                        Request.Builder()
                            .url(url(baseUrl, "/api/v1/chats?page=1&page_size=100"))
                            .headers(headers)
                            .get()
                            .build()
                    )
                )
                RagflowCatalog(
                    datasets = optionsFrom(datasetsRoot, "kbs"),
                    chats = optionsFrom(chatsRoot, "chats"),
                )
            }
        }

    suspend fun sync(
        article: ArticleWithFeed,
        force: Boolean = false,
        remoteDocuments: MutableList<RagflowRemoteDocument>? = null,
    ) = withContext(ioDispatcher) {
        if (!isConfigured()) return@withContext
        val a = article.article
        if (!a.isStarred) {
            delete(a.id)
            return@withContext
        }
        val html = readerCache.readOrFetchFullContent(a).getOrElse { a.rawDescription }
        val markdown = "# ${a.title}\n\n来源：${article.feed.name}\n原文：${a.link}\n发布日期：${a.date}\n\n${Jsoup.parse(html).text()}"
        val digest = sha256(markdown)
        val storedDigest = "$RAGFLOW_IDENTITY_VERSION$digest"
        val documentPrefix = ragflowDocumentPrefix(
            feedUrl = article.feed.url,
            articleLink = a.link,
            title = a.title,
            publishedAtMillis = a.date.time,
        )
        val documentName = "$documentPrefix${digest.take(RAGFLOW_CONTENT_HASH_LENGTH)}.md"
        val old = mappingDao.get(a.id)
        if (
            !force &&
            old?.contentHash == storedDigest &&
            old.status == RagflowDocument.STATUS_SYNCED
        ) {
            return@withContext
        }
        val knownRemoteDocuments = remoteDocuments ?: listManagedDocuments()
        val matchingRemoteDocuments = knownRemoteDocuments.filter { it.name.startsWith(documentPrefix) }
        val matchingContent = matchingRemoteDocuments.firstOrNull { it.name == documentName }
        if (
            !force &&
            matchingContent != null &&
            (old == null || old.status == RagflowDocument.STATUS_SYNCED)
        ) {
            matchingRemoteDocuments
                .filterNot { it.id == matchingContent.id }
                .forEach { staleDocument ->
                    runCatching { deleteRemote(staleDocument.id) }
                        .onFailure { error ->
                            if (!isMissingRagflowDocument(error)) throw error
                        }
                    knownRemoteDocuments.removeAll { it.id == staleDocument.id }
                }
            mappingDao.upsert(
                RagflowDocument(
                    articleId = a.id,
                    documentId = matchingContent.id,
                    contentHash = storedDigest,
                    status = RagflowDocument.STATUS_SYNCED,
                    syncedAt = Date(),
                )
            )
            return@withContext
        }
        (matchingRemoteDocuments.map { it.id } + listOfNotNull(old?.documentId))
            .distinct()
            .forEach { documentId ->
                runCatching { deleteRemote(documentId) }
                    .onFailure { error ->
                        // Reconciliation must still work after a document was deleted directly in
                        // RAGFlow or disappeared between listing and deletion.
                        if (!isMissingRagflowDocument(error)) throw error
                    }
                knownRemoteDocuments.removeAll { it.id == documentId }
            }
        mappingDao.upsert(RagflowDocument(a.id, contentHash = storedDigest, status = RagflowDocument.STATUS_PENDING))
        var uploadedId: String? = null
        runCatching {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", documentName, markdown.toRequestBody("text/markdown; charset=utf-8".toMediaType())).build()
            val response = execute(Request.Builder().url(url("/api/v1/datasets/${settingsProvider.settings.ragflowDatasetId}/documents")).headers(auth()).post(body).build())
            val data = JSONObject(response).optJSONArray("data") ?: error("RAGFlow 未返回文档")
            val id = data.optJSONObject(0)?.optString("id").orEmpty()
            require(id.isNotBlank()) { "RAGFlow 未返回文档 ID" }
            uploadedId = id
            knownRemoteDocuments += RagflowRemoteDocument(id, documentName)
            mappingDao.upsert(RagflowDocument(a.id, id, storedDigest, RagflowDocument.STATUS_PENDING))
            val parseBody = JSONObject().put("document_ids", JSONArray().put(id)).toString().toRequestBody(json)
            execute(Request.Builder().url(url("/api/v1/datasets/${settingsProvider.settings.ragflowDatasetId}/chunks")).headers(auth()).post(parseBody).build())
            mappingDao.upsert(RagflowDocument(a.id, id, storedDigest, RagflowDocument.STATUS_SYNCED, syncedAt = Date()))
        }.onFailure { mappingDao.upsert(RagflowDocument(a.id, uploadedId, storedDigest, RagflowDocument.STATUS_FAILED, errorMessage = it.message)) }.getOrThrow()
    }

    suspend fun listManagedDocuments(): MutableList<RagflowRemoteDocument> =
        withContext(ioDispatcher) {
            val documents = mutableListOf<RagflowRemoteDocument>()
            var page = 1
            var total = Int.MAX_VALUE
            while (documents.size < total) {
                val requestUrl = url(
                    "/api/v1/datasets/${settingsProvider.settings.ragflowDatasetId}/documents"
                ).toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("page_size", RAGFLOW_DOCUMENT_PAGE_SIZE.toString())
                    .addQueryParameter("keywords", RAGFLOW_DOCUMENT_PREFIX)
                    .build()
                val root = JSONObject(
                    execute(Request.Builder().url(requestUrl).headers(auth()).get().build())
                )
                val data = root.optJSONObject("data") ?: break
                val pageDocuments = data.optJSONArray("docs") ?: JSONArray()
                total = data.optInt("total", 0)
                for (index in 0 until pageDocuments.length()) {
                    val item = pageDocuments.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isNotBlank() && name.startsWith(RAGFLOW_DOCUMENT_PREFIX)) {
                        documents += RagflowRemoteDocument(id, name)
                    }
                }
                if (pageDocuments.length() < RAGFLOW_DOCUMENT_PAGE_SIZE) break
                page++
            }
            documents
        }

    suspend fun delete(articleId: String) = withContext(ioDispatcher) {
        mappingDao.get(articleId)?.documentId?.let { deleteRemote(it) }
        mappingDao.delete(articleId)
    }

    suspend fun ask(
        question: String,
        sessionId: String?,
        callTimeoutMillis: Long? = null,
        onUpdate: (RagAnswer) -> Unit = {},
    ): RagAnswer = withContext(ioDispatcher) {
        check(isConfigured()) { "请先在设置中完成 RAGFlow 配置" }
        val payload = JSONObject().put("question", question).put("stream", true).apply {
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
        }
        val request = Request.Builder()
            .url(url("/api/v1/chats/${settingsProvider.settings.ragflowChatId}/completions"))
            .headers(auth())
            .post(payload.toString().toRequestBody(json))
            .build()
        val call = ragflowClient.newCall(request)
        callTimeoutMillis?.let {
            call.timeout().timeout(it, TimeUnit.MILLISECONDS)
        }
        call.execute().use { response ->
            check(response.isSuccessful) { "RAGFlow HTTP ${response.code}" }
            val body = response.body
            var latest = RagAnswer("", sessionId, emptyList())
            var rawAnswer = ""
            var isThinking = false
            while (!body.source().exhausted()) {
                val line = body.source().readUtf8Line()?.trim().orEmpty()
                if (!line.startsWith("data:")) continue
                val event = line.removePrefix("data:").trim()
                if (event.isBlank()) continue
                if (isRagflowCompletionEvent(event)) break
                val root = runCatching { JSONObject(event) }.getOrNull() ?: continue
                val code = root.optInt("code")
                check(code == 0) { root.optString("message", "RAGFlow 请求失败") }
                val data = root.optJSONObject("data") ?: continue
                val answer = data.optString("answer")
                val sources = sourcesFrom(data)
                if (data.optBoolean("start_to_think")) isThinking = true
                val answerDelta = when {
                    answer.isBlank() -> ""
                    answer.startsWith(rawAnswer) -> answer.removePrefix(rawAnswer)
                    rawAnswer.startsWith(answer) -> ""
                    else -> answer
                }
                rawAnswer = when {
                    answer.isBlank() -> rawAnswer
                    answer.startsWith(rawAnswer) -> answer
                    rawAnswer.startsWith(answer) -> rawAnswer
                    else -> rawAnswer + answer
                }
                val visibleDelta = if (isThinking) "" else answerDelta
                if (data.optBoolean("end_to_think")) isThinking = false
                latest = RagAnswer(
                    answer = when {
                        visibleDelta.isBlank() -> latest.answer
                        else -> latest.answer + visibleDelta
                    },
                    sessionId = data.optString("session_id").takeIf(String::isNotBlank)
                        ?: latest.sessionId,
                    sources = if (sources.isNotEmpty()) sources else latest.sources,
                )
                val visibleAnswer = removeReasoning(latest.answer)
                if (visibleAnswer.isNotBlank()) {
                    onUpdate(latest.copy(answer = visibleAnswer))
                }
            }
            val finalAnswer = removeReasoning(latest.answer)
            check(finalAnswer.isNotBlank()) { "RAGFlow 未返回答案" }
            latest.copy(answer = finalAnswer)
        }
    }

    suspend fun suggestQuestions(): Result<List<String>> = runCatching {
        withTimeout(SUGGESTIONS_TIMEOUT_MILLIS) {
        val prompt = """
            请先检索并综合理解当前知识库中的全部星标文章。基于实际检索到的内容，洞察用户长期的
            关注点、实际需求、知识缺口、潜在决策，以及用户接下来最可能想探索的问题。

            生成 12 个互不重复、能够跨多篇文章检索回答的问题，每个问题尽量控制在 36 个汉字以内。问题应覆盖主题脉络、观点冲突、
            趋势变化、证据可靠性、知识缺口和可执行建议，避免只询问某一篇文章的摘要。
            每个问题必须包含知识库中实际出现的具体主题、概念、人物、项目或观点；禁止生成脱离
            知识库内容的通用问题。如果检索证据不足，就少生成，不要用泛化问题补足数量。
            只输出问题，每行一个，不要编号、解释、分类或展示思考过程。
        """.trimIndent()
        ask(
            question = prompt,
            sessionId = null,
            callTimeoutMillis = SUGGESTIONS_TIMEOUT_MILLIS,
        ).answer
            .lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .replace(Regex("^\\d+[.、)]\\s*"), "")
                    .trim()
            }
            .filter { it.length in 12..64 && (it.endsWith("？") || it.endsWith("?")) }
            .distinctBy { it.lowercase().filterNot(Char::isWhitespace) }
            .take(12)
            .toList()
        }
    }

    private fun removeReasoning(answer: String): String {
        var visible = answer
        val completedBlocks = listOf("think", "thinking", "reasoning", "retrieving")
        completedBlocks.forEach { tag ->
            visible = visible.replace(
                Regex("(?is)<$tag(?:\\s[^>]*)?>.*?</$tag>"),
                "",
            )
            visible = visible.replace(
                Regex("(?is)<$tag(?:\\s[^>]*)?>.*$"),
                "",
            )
        }
        return visible
            .replace(Regex("(?is)</?(?:think|thinking|reasoning|retrieving)(?:\\s[^>]*)?>"), "")
            .replace(
                Regex(
                    "(?is)^\\s*(?:#{1,6}\\s*)?(?:思考过程|思考|推理过程|推理|分析过程|Reasoning|Thought Process)\\s*[:：]?\\s*.*?(?=^\\s*(?:#{1,6}\\s*)?(?:最终答案|回答|Final Answer|Answer)\\s*[:：]?\\s*)",
                    setOf(RegexOption.MULTILINE),
                ),
                "",
            )
            .replace(
                Regex(
                    "(?im)^\\s*(?:#{1,6}\\s*)?(?:最终答案|Final Answer|Answer)\\s*[:：]?\\s*",
                ),
                "",
            )
            .trim()
    }

    private fun sourcesFrom(data: JSONObject): List<RagSource> {
        val sources = mutableListOf<RagSource>()
        val chunks = data.optJSONObject("reference")?.optJSONArray("chunks") ?: JSONArray()
        for (index in 0 until chunks.length()) {
            chunks.optJSONObject(index)?.let {
                sources += RagSource(
                    it.optString("document_name", "参考文章"),
                    it.optString("url").takeIf(String::isNotBlank),
                    it.optString("content").takeIf(String::isNotBlank),
                )
            }
        }
        return sources.distinctBy { it.title }
    }

    suspend fun test(): Result<Unit> = withContext(ioDispatcher) { runCatching {
        check(isConfigured()) { "请填写全部 RAGFlow 配置" }
        execute(Request.Builder().url(url("/api/v1/datasets?id=${settingsProvider.settings.ragflowDatasetId}")).headers(auth()).get().build())
        Unit
    } }

    suspend fun test(
        baseUrl: String,
        apiKey: String,
        datasetId: String,
    ): Result<Unit> = withContext(ioDispatcher) { runCatching {
        require(baseUrl.isNotBlank()) { "RAGFlow 地址为空" }
        require(apiKey.isNotBlank()) { "RAGFlow API Key 为空" }
        require(datasetId.isNotBlank()) { "RAGFlow 数据集未选择" }
        execute(
            Request.Builder()
                .url(url(baseUrl, "/api/v1/datasets?id=$datasetId"))
                .headers(auth(apiKey))
                .get()
                .build()
        )
        Unit
    } }

    suspend fun testChat(
        baseUrl: String,
        apiKey: String,
        chatId: String,
    ): Result<Unit> = withContext(ioDispatcher) { runCatching {
        require(baseUrl.isNotBlank()) { "RAGFlow 地址为空" }
        require(apiKey.isNotBlank()) { "RAGFlow API Key 为空" }
        require(chatId.isNotBlank()) { "RAGFlow 对话助手未选择" }
        val payload = JSONObject()
            .put("question", "Reply with OK only.")
            .put("stream", true)
            .toString()
            .toRequestBody(json)
        val request = Request.Builder()
            .url(url(baseUrl, "/api/v1/chats/$chatId/completions"))
            .headers(auth(apiKey))
            .post(payload)
            .build()
        try {
            val call = ragflowClient.newCall(request)
            call.timeout().timeout(RAGFLOW_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            call.execute().use { response ->
                check(response.isSuccessful) {
                    "RAGFlow HTTP ${response.code}: ${response.body.string().take(300)}"
                }
                val source = response.body.source()
                var receivedAnswer = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()?.trim().orEmpty()
                    if (!line.startsWith("data:")) continue
                    val event = line.removePrefix("data:").trim()
                    if (event.isBlank()) continue
                    if (isRagflowCompletionEvent(event)) break
                    val root = runCatching { JSONObject(event) }.getOrNull() ?: continue
                    val code = root.optInt("code")
                    check(code == 0) { root.optString("message", "RAGFlow 请求失败") }
                    if (hasRagflowAnswer(event)) {
                        receivedAnswer = true
                        break
                    }
                }
                check(receivedAnswer) { "RAGFlow 对话助手未返回回答" }
            }
        } catch (error: SocketTimeoutException) {
            throw SocketTimeoutException(
                "RAGFlow 对话助手在 ${RAGFLOW_REQUEST_TIMEOUT_SECONDS} 秒内未响应，请检查助手模型、上游 API 或服务器负载",
            ).apply { initCause(error) }
        }
        Unit
    } }

    private fun deleteRemote(id: String) {
        if (!isConfigured()) return
        val body = JSONObject().put("ids", JSONArray().put(id)).toString().toRequestBody(json)
        execute(Request.Builder().url(url("/api/v1/datasets/${settingsProvider.settings.ragflowDatasetId}/documents")).headers(auth()).delete(body).build())
    }
    private fun optionsFrom(root: JSONObject, nestedKey: String): List<RagflowOption> {
        val data = root.opt("data")
        val values = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray(nestedKey) ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) add(RagflowOption(id, item.optString("name").ifBlank { id }))
            }
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private fun url(path: String) = url(settingsProvider.settings.ragflowBaseUrl, path)
    private fun url(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/').removeSuffix("/api/v1")
        return base + path
    }
    private fun auth() = auth(settingsProvider.settings.ragflowApiKey)
    private fun auth(apiKey: String) = okhttp3.Headers.Builder().add("Authorization", "Bearer ${apiKey.trim()}").build()
    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        val value = response.body.string()
        check(response.isSuccessful) { "RAGFlow HTTP ${response.code}: ${value.take(300)}" }
        val code = runCatching { JSONObject(value).optInt("code") }.getOrDefault(0)
        check(code == 0) { runCatching { JSONObject(value).optString("message") }.getOrDefault("RAGFlow 请求失败") }
        value
    }
}

internal const val SUGGESTIONS_TIMEOUT_MILLIS = 60_000L
internal const val RAGFLOW_REQUEST_TIMEOUT_SECONDS = 120L
internal const val RAGFLOW_DOCUMENT_PREFIX = "readyou-"
internal const val RAGFLOW_IDENTITY_VERSION = "v1:"
internal const val RAGFLOW_CONTENT_HASH_LENGTH = 16
internal const val RAGFLOW_DOCUMENT_PAGE_SIZE = 100

internal fun ragflowDocumentPrefix(
    feedUrl: String,
    articleLink: String,
    title: String,
    publishedAtMillis: Long,
): String {
    val stableSource = if (articleLink.isNotBlank()) {
        "link:${articleLink.trim()}"
    } else {
        "${feedUrl.trim()}\n${title.trim()}\n$publishedAtMillis"
    }
    return "$RAGFLOW_DOCUMENT_PREFIX${sha256(stableSource).take(32)}-"
}

internal fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun isRagflowCompletionEvent(event: String): Boolean =
    event.equals("true", ignoreCase = true) || event == "[DONE]"

internal fun hasRagflowAnswer(event: String): Boolean =
    runCatching {
        JSONObject(event)
            .optJSONObject("data")
            ?.optString("answer")
            ?.isNotBlank() == true
    }.getOrDefault(false)

internal fun isMissingRagflowDocument(error: Throwable): Boolean {
    val message = error.message.orEmpty().lowercase()
    return "http 404" in message ||
        "not found" in message ||
        "does not exist" in message ||
        "doesn't exist" in message ||
        "don't own the document" in message ||
        "doesn't own the document" in message ||
        "不存在" in message
}
