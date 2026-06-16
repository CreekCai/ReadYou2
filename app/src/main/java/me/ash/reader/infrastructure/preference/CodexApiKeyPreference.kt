package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.codexApiKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalCodexApiKey = compositionLocalOf { CodexApiKeyPreference.default }

object CodexApiKeyPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.codexApiKey, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[codexApiKey]?.key as Preferences.Key<String>] ?: default
}
