package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiInsightModel
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiInsightModel = compositionLocalOf { GeminiInsightModelPreference.default }

object GeminiInsightModelPreference {
    const val default = "gemini-2.5-flash"

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiInsightModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        val stored = preferences[DataStoreKey.keys[geminiInsightModel]?.key as Preferences.Key<String>] ?: default
        return stored
    }
}
