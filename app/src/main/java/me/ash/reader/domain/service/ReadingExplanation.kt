package me.ash.reader.domain.service

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

data class ExplanationRequest(
    val selected: String,
    val paragraph: String,
    val question: String,
    val history: List<ExplanationTurn> = emptyList(),
    val originalContent: String = "",
)
data class ExplanationTurn(val question: String, val answer: String)
data class ExplanationSource(val title: String, val url: String)
data class ExplanationAnswer(
    val text: String,
    val sources: List<ExplanationSource> = emptyList(),
    val searchHtml: String = "",
    val searched: Boolean = false,
)

/** Never silently truncate the selected paragraph or claim a truncated feed is a full article. */
internal fun explanationContext(title: String, url: String, content: String, request: ExplanationRequest): String {
    require(request.selected.isNotBlank()) { "请先选中文字" }
    require(request.selected.length <= 12000) { "选择内容过长，请缩小选区" }
    require(request.question.length <= 4000) { "问题过长，请缩短后重试" }
    val body = Jsoup.parse(content).text()
    val original = Jsoup.parse(request.originalContent).text()
    require(body.length + original.length <= 240000) { "文章过长，暂不能完整解释；请选择较短文章" }
    return Gson().toJson(mapOf(
        "articleTitle" to title, "articleUrl" to url,
        "contextScope" to "当前阅读器已获取正文，可能为订阅摘要或译文；不可声称已读取未提供的原网页",
        "articleBody" to body,
        "originalBeforeTranslation" to original,
        "selection" to request.selected,
        "selectedParagraph" to request.paragraph,
        "conversation" to request.history.takeLast(8),
        "question" to request.question.ifBlank { "这里的选中内容是什么意思？" },
    ))
}

internal suspend fun okhttp3.Call.awaitExplanationResponse(): okhttp3.Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

internal fun parseGroundedExplanation(body: String): ExplanationAnswer {
    val root = JsonParser.parseString(body).asJsonObject
    val candidate = root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
        ?: error("模型没有返回答案，请检查模型设置或重试")
    var text = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
        ?.filter { it.asJsonObject.get("thought")?.asBoolean != true }
        ?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("\n").orEmpty()
    require(text.isNotBlank()) { "模型没有返回答案，可能受到内容限制" }
    val metadata = candidate.getAsJsonObject("groundingMetadata")
    val chunks = metadata?.getAsJsonArray("groundingChunks")
    val sources = chunks?.mapIndexedNotNull { index, item ->
        val web = item.asJsonObject.getAsJsonObject("web") ?: return@mapIndexedNotNull null
        val url = web.get("uri")?.asString.orEmpty()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapIndexedNotNull null
        ExplanationSource("[${index + 1}] ${web.get("title")?.asString ?: url}", url)
    }.orEmpty()
    // Google offsets are UTF-8 byte offsets; annotate segments by their returned exact text instead.
    metadata?.getAsJsonArray("groundingSupports")?.reversed()?.forEach { support ->
        val s = support.asJsonObject
        val segment = s.getAsJsonObject("segment")?.get("text")?.asString.orEmpty()
        val refs = s.getAsJsonArray("groundingChunkIndices")?.joinToString("") { "[${it.asInt + 1}]" }.orEmpty()
        val pos = if (segment.isNotEmpty()) text.lastIndexOf(segment) else -1
        if (pos >= 0) text = text.substring(0, pos + segment.length) + refs + text.substring(pos + segment.length)
    }
    return ExplanationAnswer(text, sources,
        metadata?.getAsJsonObject("searchEntryPoint")?.get("renderedContent")?.asString.orEmpty(),
        (metadata?.getAsJsonArray("webSearchQueries")?.size() ?: 0) > 0)
}
