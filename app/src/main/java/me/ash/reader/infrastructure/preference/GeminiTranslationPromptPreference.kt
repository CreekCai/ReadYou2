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

    private const val legacyDefault = "Translate the following text to the system language, please just provide the translated text:"
    const val default = "请将以下内容翻译成简体中文，只输出译文，不要添加解释："

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.geminiTranslationPrompt, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String {
        val saved = preferences[DataStoreKey.keys[geminiTranslationPrompt]?.key as Preferences.Key<String>]
        return if (saved.isNullOrBlank() || saved == legacyDefault) default else saved
    }
}
