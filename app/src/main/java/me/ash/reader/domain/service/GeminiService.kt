package me.ash.reader.domain.service

import com.google.ai.client.generativeai.GenerativeModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class GeminiService @Inject constructor(
    private val settingsProvider: SettingsProvider,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().build()

    suspend fun summarize(content: String): String = withContext(ioDispatcher) {
        val apiKey = settingsProvider.settings.geminiApiKey
        val modelName = settingsProvider.settings.geminiModel
        val userPrompt = settingsProvider.settings.geminiPrompt

        if (apiKey.isBlank()) {
            throw Exception("API Key is missing. Please configure it in Settings.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val prompt = "$userPrompt\n\n$content"
        val response = generativeModel.generateContent(prompt)
        response.text ?: "No summary generated."
    }

    suspend fun translate(content: String): String = withContext(ioDispatcher) {
        val apiKey = settingsProvider.settings.geminiApiKey
        val modelName = settingsProvider.settings.geminiTranslationModel
        val userPrompt = settingsProvider.settings.geminiTranslationPrompt

        if (apiKey.isBlank()) {
            throw Exception("API Key is missing. Please configure it in Settings.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val prompt = "$userPrompt\n\n$content"
        val response = generativeModel.generateContent(prompt)
        
        val translationText = response.text ?: "No translation generated."
        val document = parser.parse(translationText)
        renderer.render(document)
    }

    suspend fun generateInsight(content: String): String = withContext(ioDispatcher) {
        val apiKey = settingsProvider.settings.geminiApiKey
        val modelName = settingsProvider.settings.geminiInsightModel
        val userPrompt = settingsProvider.settings.geminiInsightPrompt

        if (apiKey.isBlank()) {
            throw Exception("API Key is missing. Please configure it in Settings.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val fullPrompt = "$userPrompt\n\n$content"
        val response = generativeModel.generateContent(fullPrompt)
        val insightText = response.text ?: "No insight generated."
        
        val document = parser.parse(insightText)
        renderer.render(document)
    }
}
