package me.ash.reader.domain.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
                content = "OK",
                fallback = "No response generated.",
            )
            else -> generateGeminiContent(
                modelName = modelName,
                apiKey = apiKey,
                prompt = "This is a connection test. Reply with OK only.",
                content = "OK",
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
                )
            )
        val responsesRequest =
            buildOpenAiRequest(
                url = "$normalizedBaseUrl/responses",
                apiKey = apiKey,
                requestJson = responsesJson,
            )

        okHttpClient.newCall(responsesRequest).execute().use { response ->
            val body = response.body.string()

            if (response.isSuccessful) {
                return parseOpenAiText(body) ?: fallback
            }

            if (response.code != 404 && response.code != 405) {
                throw openAiRequestException("Responses API", responsesRequest.url.toString(), response.code, body)
            }
        }

        val chatJson =
            gson.toJson(
                mapOf(
                    "model" to modelName,
                    "messages" to
                        listOf(
                            mapOf(
                                "role" to "user",
                                "content" to input,
                            )
                        ),
                    "stream" to false,
                )
            )
        val chatRequest =
            buildOpenAiRequest(
                url = "$normalizedBaseUrl/chat/completions",
                apiKey = apiKey,
                requestJson = chatJson,
            )

        okHttpClient.newCall(chatRequest).execute().use { response ->
            val body = response.body.string()

            if (!response.isSuccessful) {
                throw openAiRequestException("Chat Completions API", chatRequest.url.toString(), response.code, body)
            }

            return parseChatCompletionText(body) ?: fallback
        }
    }

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
        return Exception(
            buildString {
                appendLine("$api request failed")
                appendLine("URL: $url")
                appendLine("HTTP: $status")
                if (!serverMessage.isNullOrBlank()) appendLine("Message: $serverMessage")
                append("Response: $responseExcerpt")
            }
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

internal fun normalizeOpenAiBaseUrl(baseUrl: String): String {
    var normalized = baseUrl.trim().trimEnd('/')
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
