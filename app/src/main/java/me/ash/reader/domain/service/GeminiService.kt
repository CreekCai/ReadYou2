package me.ash.reader.domain.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.AiProviderPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class GeminiService @Inject constructor(
    private val settingsProvider: SettingsProvider,
    private val okHttpClient: OkHttpClient,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val aiHttpClient = okHttpClient.newBuilder()
        .readTimeout(AI_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(AI_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun testModel(
        provider: AiProviderPreference,
        modelName: String,
        apiKey: String,
        baseUrl: String,
    ): String = withContext(ioDispatcher) {
        require(modelName.isNotBlank()) { "模型名称为空" }
        when (provider) {
            AiProviderPreference.OpenAI -> generateCodexContent(
                modelName = modelName,
                apiKey = apiKey,
                baseUrl = baseUrl,
                prompt = "This is a connection test. Reply with OK only.",
                content = realisticConnectionTestContent(),
                fallback = "No response generated.",
            )
            else -> generateGeminiContent(
                modelName = modelName,
                apiKey = apiKey,
                prompt = "This is a connection test. Reply with OK only.",
                content = realisticConnectionTestContent(),
                fallback = "No response generated.",
            )
        }
    }

    suspend fun summarize(content: String): String = withContext(ioDispatcher) {
        when (settingsProvider.settings.aiProvider) {
            AiProviderPreference.OpenAI ->
                generateCodexContent(
                    modelName = settingsProvider.settings.codexModel,
                    apiKey = settingsProvider.settings.codexApiKey,
                    baseUrl = settingsProvider.settings.openAiBaseUrl,
                    prompt = settingsProvider.settings.geminiPrompt,
                    content = content,
                    fallback = "No summary generated.",
                )
            else ->
                generateGeminiContent(
                    modelName = settingsProvider.settings.geminiModel,
                    apiKey = settingsProvider.settings.geminiApiKey,
                    prompt = settingsProvider.settings.geminiPrompt,
                    content = content,
                    fallback = "No summary generated.",
                )
        }
    }

    suspend fun translate(content: String): String = withContext(ioDispatcher) {
        val translationText =
            when (settingsProvider.settings.aiProvider) {
                AiProviderPreference.OpenAI ->
                    generateCodexContent(
                        modelName = settingsProvider.settings.codexTranslationModel,
                        apiKey = settingsProvider.settings.codexApiKey,
                        baseUrl = settingsProvider.settings.openAiBaseUrl,
                        prompt = settingsProvider.settings.geminiTranslationPrompt,
                        content = content,
                        fallback = "No translation generated.",
                    )
                else ->
                    generateGeminiContent(
                        modelName = settingsProvider.settings.geminiTranslationModel,
                        apiKey = settingsProvider.settings.geminiApiKey,
                        prompt = settingsProvider.settings.geminiTranslationPrompt,
                        content = content,
                        fallback = "No translation generated.",
                    )
            }
        val document = parser.parse(translationText)
        renderer.render(document)
    }

    private suspend fun generateGeminiContent(
        modelName: String,
        apiKey: String,
        prompt: String,
        content: String,
        fallback: String,
    ): String {
        if (apiKey.isBlank()) {
            throw Exception("Gemini API Key is missing. Please configure it in Settings.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val response = generativeModel.generateContent("$prompt\n\n$content")
        return response.text ?: fallback
    }

    private fun generateCodexContent(
        modelName: String,
        apiKey: String,
        baseUrl: String,
        prompt: String,
        content: String,
        fallback: String,
    ): String {
        if (apiKey.isBlank()) {
            throw Exception("OpenAI API Key is missing. Please configure it in Settings.")
        }

        val normalizedBaseUrl = normalizeOpenAiBaseUrl(baseUrl)
        val input = "$prompt\n\n$content"

        val responsesJson =
            gson.toJson(
                mapOf(
                    "model" to modelName,
                    "input" to input,
                    "store" to false,
                    "max_output_tokens" to AI_MAX_OUTPUT_TOKENS,
                )
            )
        val responsesRequest =
            buildOpenAiRequest(
                url = "$normalizedBaseUrl/responses",
                apiKey = apiKey,
                requestJson = responsesJson,
            )

        val startedAt = System.nanoTime()
        try {
            aiHttpClient.newCall(responsesRequest).execute().use { response ->
                val body = response.body.string()

                if (response.isSuccessful) {
                    return parseOpenAiText(body) ?: fallback
                }

                if (response.code != 404 && response.code != 405) {
                    throw openAiRequestException("Responses API", responsesRequest.url.toString(), response.code, body)
                }
            }
        } catch (error: InterruptedIOException) {
            throw aiTimeoutException(modelName, responsesRequest.url.toString(), input.length, startedAt, error)
        }

        val chatJson = buildChatCompletionJson(
            modelName = modelName,
            input = input,
            siliconFlow = isSiliconFlowBaseUrl(normalizedBaseUrl),
        )
        val chatRequest =
            buildOpenAiRequest(
                url = "$normalizedBaseUrl/chat/completions",
                apiKey = apiKey,
                requestJson = chatJson,
            )

        val chatStartedAt = System.nanoTime()
        try {
            aiHttpClient.newCall(chatRequest).execute().use { response ->
                val body = response.body.string()

                if (!response.isSuccessful) {
                    throw openAiRequestException("Chat Completions API", chatRequest.url.toString(), response.code, body)
                }

                return parseChatCompletionText(body) ?: fallback
            }
        } catch (error: InterruptedIOException) {
            throw aiTimeoutException(modelName, chatRequest.url.toString(), input.length, chatStartedAt, error)
        }
    }

    private fun buildChatCompletionJson(
        modelName: String,
        input: String,
        siliconFlow: Boolean,
    ): String = gson.toJson(chatCompletionPayload(modelName, input, siliconFlow))

    private fun aiTimeoutException(
        modelName: String,
        url: String,
        inputLength: Int,
        startedAt: Long,
        cause: InterruptedIOException,
    ) = AiRequestException(
        userMessage = "AI 请求超时（诊断日志已复制）",
        diagnosticLog = buildString {
            appendLine("ReadYou AI request diagnostics")
            appendLine("Error: TIMEOUT")
            appendLine("Model: $modelName")
            appendLine("URL: $url")
            appendLine("Input characters: $inputLength")
            appendLine("Elapsed milliseconds: ${(System.nanoTime() - startedAt) / 1_000_000}")
            appendLine("Timeout seconds: $AI_REQUEST_TIMEOUT_SECONDS")
            append(cause.stackTraceToString())
        },
        cause = cause,
    )

    private fun buildOpenAiRequest(
        url: String,
        apiKey: String,
        requestJson: String,
    ): Request =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

    private fun parseChatCompletionText(body: String): String? =
        runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val choices = root.getAsJsonArray("choices")

            if (choices == null || choices.size() == 0) {
                return@runCatching null
            }

            choices[0]
                .asJsonObject
                .getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun parseOpenAiError(body: String): String? =
        runCatching {
            JsonParser.parseString(body)
                .asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        }.getOrNull()

    private fun openAiRequestException(
        api: String,
        url: String,
        status: Int,
        body: String,
    ): Exception {
        val serverMessage = parseOpenAiError(body)
        val responseExcerpt = body.trim().take(1_000).ifBlank { "<empty>" }
        val diagnostics = buildString {
            appendLine("ReadYou AI request diagnostics")
            appendLine("Error: HTTP_FAILURE")
            appendLine("API: $api")
            appendLine("URL: $url")
            appendLine("HTTP: $status")
            if (!serverMessage.isNullOrBlank()) appendLine("Message: $serverMessage")
            append("Response: $responseExcerpt")
        }
        return AiRequestException(
            userMessage = serverMessage ?: "$api 请求失败：HTTP $status（诊断日志已复制）",
            diagnosticLog = diagnostics,
        )
    }

    private fun parseOpenAiText(body: String): String? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        root.get("output_text")?.asString?.takeIf { it.isNotBlank() }?.let { return it }

        val output = root.getAsJsonArray("output") ?: return null
        val parts = buildList {
            output.forEach { item ->
                val content = item.asJsonObject.getAsJsonArray("content") ?: return@forEach
                content.forEach { contentItem ->
                    val contentObject = contentItem.asJsonObject
                    contentObject.get("text")?.asString?.takeIf { it.isNotBlank() }?.let(::add)
                    contentObject.get("output_text")?.asString?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun JsonObject.getAsJsonArray(name: String) =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.getAsJsonObject(name: String) =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}

class AiRequestException(
    val userMessage: String,
    val diagnosticLog: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

internal const val AI_REQUEST_TIMEOUT_SECONDS = 120L
internal const val AI_MAX_OUTPUT_TOKENS = 768

internal fun chatCompletionPayload(
    modelName: String,
    input: String,
    siliconFlow: Boolean,
): Map<String, Any> = mutableMapOf<String, Any>(
    "model" to modelName,
    "messages" to listOf(mapOf("role" to "user", "content" to input)),
    "stream" to false,
    "max_tokens" to AI_MAX_OUTPUT_TOKENS,
    "temperature" to 0.3,
).apply {
    if (siliconFlow && supportsSiliconFlowThinkingControl(modelName)) {
        put("enable_thinking", false)
    }
}

internal fun supportsSiliconFlowThinkingControl(modelName: String): Boolean {
    val normalizedModel = modelName.trim().lowercase()
    return normalizedModel.startsWith("qwen/qwen3")
}

internal fun isSiliconFlowBaseUrl(baseUrl: String): Boolean =
    baseUrl.toHttpUrlOrNull()?.host?.let { host ->
        host == "siliconflow.cn" || host.endsWith(".siliconflow.cn")
    } == true

internal fun realisticConnectionTestContent(): String = buildString {
    repeat(24) { index ->
        append("Paragraph ${index + 1}: This is representative article content used to verify sustained model response latency. ")
        append("Identify the main idea and return a concise result without showing reasoning. ")
    }
}

internal fun normalizeOpenAiBaseUrl(baseUrl: String): String {
    var normalized = sanitizeOpenAiBaseUrlInput(baseUrl).trimEnd('/')
    listOf("/chat/completions", "/responses").firstOrNull {
        normalized.endsWith(it, ignoreCase = true)
    }?.let { suffix ->
        normalized = normalized.dropLast(suffix.length).trimEnd('/')
    }

    val parsed = normalized.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("OpenAI Base URL is invalid. Please check it in Settings.")
    val apiRoot = if (parsed.encodedPath == "/") {
        parsed.newBuilder().addPathSegment("v1").build()
    } else {
        parsed
    }
    return apiRoot.toString().trimEnd('/')
}

internal fun sanitizeOpenAiBaseUrlInput(value: String): String =
    value.trim().takeWhile { !it.isWhitespace() }
