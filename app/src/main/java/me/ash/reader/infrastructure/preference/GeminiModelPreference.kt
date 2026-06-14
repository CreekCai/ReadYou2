package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiModel
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiModel = compositionLocalOf { GeminiModelPreference.default }

object GeminiModelPreference {
    const val default = "gemini-2.5-flash"
    
    val availableModels = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-3-flash-preview",
        "gemini-3.1-pro-preview"
    )

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        val stored = preferences[DataStoreKey.keys[geminiModel]?.key as Preferences.Key<String>] ?: default
        // Ensure the stored value is one of the currently available models, if not fallback to default.
        // Or we can just return stored if we want to support older models still.
        // Given the requirement to change available models, it's safer to just return stored 
        // but the UI will only show the new list.
        return stored
    }
}
