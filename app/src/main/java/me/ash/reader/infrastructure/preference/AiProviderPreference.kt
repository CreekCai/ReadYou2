package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiProvider
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAiProvider = compositionLocalOf<AiProviderPreference> { AiProviderPreference.default }

sealed class AiProviderPreference(val value: Int, val title: String) {
    data object Gemini : AiProviderPreference(0, "Gemini")
    data object OpenAI : AiProviderPreference(1, "OpenAI")

    fun put(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.aiProvider, value)
        }
    }

    companion object {
        val default = Gemini
        val values = listOf(Gemini, OpenAI)

        fun fromPreferences(preferences: Preferences): AiProviderPreference =
            when (preferences[DataStoreKey.keys[aiProvider]?.key as Preferences.Key<Int>]) {
                1 -> OpenAI
                else -> default
            }
    }
}
