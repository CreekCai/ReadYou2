package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiTranslationPromptPreferenceTest {
    private val key = stringPreferencesKey("geminiTranslationPrompt")

    @Test
    fun `new installations translate to Chinese by default`() {
        assertEquals(
            GeminiTranslationPromptPreference.default,
            GeminiTranslationPromptPreference.fromPreferences(preferencesOf()),
        )
    }

    @Test
    fun `old ambiguous default is replaced with Chinese default`() {
        val oldPrompt = "Translate the following text to the system language, please just provide the translated text:"
        assertEquals(
            GeminiTranslationPromptPreference.default,
            GeminiTranslationPromptPreference.fromPreferences(preferencesOf(key to oldPrompt)),
        )
    }

    @Test
    fun `custom translation prompt is preserved`() {
        val customPrompt = "Translate into traditional Chinese:"
        assertEquals(
            customPrompt,
            GeminiTranslationPromptPreference.fromPreferences(preferencesOf(key to customPrompt)),
        )
    }
}
