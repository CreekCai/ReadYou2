package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.ttsSpeechRate
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalTtsSpeechRate = compositionLocalOf { TtsSpeechRatePreference.default }

object TtsSpeechRatePreference {
    const val default = 1.0F
    const val min = 0.5F
    const val max = 2.0F

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.ttsSpeechRate, value.coerceToRange())
        }
    }

    fun Float.coerceToRange(): Float = coerceIn(min, max)

    fun fromPreferences(preferences: Preferences): Float =
        preferences[DataStoreKey.keys[ttsSpeechRate]?.key as Preferences.Key<Float>] ?: default
}
