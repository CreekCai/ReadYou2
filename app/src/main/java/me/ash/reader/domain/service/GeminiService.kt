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

    suspend fun generateInsight(content: String): String = withContext(ioDispatcher) {
        val insightText =
            when (settingsProvider.settings.aiProvider) {
                AiProviderPreference.OpenAI ->
                    generateCodexContent(
                        modelName = settingsProvider.settings.codexInsightModel,
                        apiKey = settingsProvider.settings.codexApiKey,
                        baseUrl = settingsProvider.settings.openAiBaseUrl,
                        prompt = settingsProvider.settings.geminiInsightPrompt,
                        content = content,
                        fallback = "No insight generated.",
                    )
                else ->
                    generateGeminiContent(
                        modelName = settingsProvider.settings.geminiInsightModel,
                        apiKey = settingsProvider.settings.geminiApiKey,
                        prompt = settingsProvider.settings.geminiInsightPrompt,
                        content = content,
                        fallback = "No insight generated.",
                    )
            }
        val document = parser.parse(insightText)
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

        val requestJson =
            gson.toJson(
                mapOf(
                    "model" to modelName,
                    "input" to "$prompt\n\n$content",
                    "store" to false,
                )
            )
        val request =
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw Exception(parseOpenAiError(body) ?: "OpenAI request failed: HTTP ${response.code}")
            }
            return parseOpenAiText(body) ?: fallback
        }
    }

    private fun parseOpenAiError(body: String): String? =
        runCatching {
            JsonParser.parseString(body)
                .asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        }.getOrNull()

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
