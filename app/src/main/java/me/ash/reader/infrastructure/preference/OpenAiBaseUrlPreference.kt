package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.openAiBaseUrl
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalOpenAiBaseUrl = compositionLocalOf { OpenAiBaseUrlPreference.default }

object OpenAiBaseUrlPreference {
    const val default = "https://api.openai.com/v1"

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.openAiBaseUrl, value.trim())
        }
    }

    fun reset(context: Context, scope: CoroutineScope) {
        put(context, scope, default)
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[openAiBaseUrl]?.key as Preferences.Key<String>] ?: default
}
