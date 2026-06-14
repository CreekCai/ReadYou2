package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiInsightPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiInsightPrompt = compositionLocalOf { GeminiInsightPromptPreference.default }

object GeminiInsightPromptPreference {
    const val default = "Provide an insight for this article."

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiInsightPrompt, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        return preferences[DataStoreKey.keys[geminiInsightPrompt]?.key as Preferences.Key<String>] ?: default
    }
}
