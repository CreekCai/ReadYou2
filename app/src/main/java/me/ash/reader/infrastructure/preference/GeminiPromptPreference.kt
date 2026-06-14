package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.geminiPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGeminiPrompt = compositionLocalOf { GeminiPromptPreference.default }

object GeminiPromptPreference {

    const val default = "请用中文总结以下内容，列出3条核心要点："

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiPrompt, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[geminiPrompt]?.key as Preferences.Key<String>] ?: default
}
