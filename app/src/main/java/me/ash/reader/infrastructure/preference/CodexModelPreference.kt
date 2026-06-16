package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.codexInsightModel
import me.ash.reader.ui.ext.DataStoreKey.Companion.codexModel
import me.ash.reader.ui.ext.DataStoreKey.Companion.codexTranslationModel
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalCodexModel = compositionLocalOf { CodexModelPreference.default }
val LocalCodexTranslationModel = compositionLocalOf { CodexTranslationModelPreference.default }
val LocalCodexInsightModel = compositionLocalOf { CodexInsightModelPreference.default }

object CodexModelPreference {
    const val default = "gpt-4.1"

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.codexModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[codexModel]?.key as Preferences.Key<String>] ?: default
}

object CodexTranslationModelPreference {
    const val default = CodexModelPreference.default

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.codexTranslationModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[codexTranslationModel]?.key as Preferences.Key<String>] ?: default
}

object CodexInsightModelPreference {
    const val default = CodexModelPreference.default

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.codexInsightModel, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[codexInsightModel]?.key as Preferences.Key<String>] ?: default
}
