package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.ttsReadAiSummaryOnly
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalTtsReadAiSummaryOnly = compositionLocalOf { TtsReadAiSummaryOnlyPreference.default }

object TtsReadAiSummaryOnlyPreference {
    const val default = false

    fun put(context: Context, scope: CoroutineScope, value: Boolean) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.ttsReadAiSummaryOnly, value)
        }
    }

    fun fromPreferences(preferences: Preferences): Boolean =
        preferences[DataStoreKey.keys[ttsReadAiSummaryOnly]?.key as Preferences.Key<Boolean>] ?: default
}
