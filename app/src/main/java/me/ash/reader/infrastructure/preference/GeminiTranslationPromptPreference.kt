package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiTranslationPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiTranslationPrompt = compositionLocalOf { GeminiTranslationPromptPreference.default }

object GeminiTranslationPromptPreference {

    const val default = "Translate the following text to the system language, please just provide the translated text:"

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiTranslationPrompt, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[geminiTranslationPrompt]?.key as Preferences.Key<String>] ?: default
}
