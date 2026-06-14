package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiTranslationModel
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiTranslationModel = compositionLocalOf { GeminiTranslationModelPreference.default }

object GeminiTranslationModelPreference {
    const val default = "gemini-2.5-flash"

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiTranslationModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        val stored = preferences[DataStoreKey.keys[geminiTranslationModel]?.key as Preferences.Key<String>] ?: default
        // We can add validation against availableModels if we want, but keeping it simple for now.
        return stored
    }
}
