package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.getNoteApiKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.getNoteClientId
import me.ash.reader.ui.ext.DataStoreKey.Companion.getNoteTopicId
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalGetNoteApiKey = compositionLocalOf { GetNoteApiKeyPreference.default }
val LocalGetNoteClientId = compositionLocalOf { GetNoteClientIdPreference.default }
val LocalGetNoteTopicId = compositionLocalOf { GetNoteTopicIdPreference.default }

object GetNoteApiKeyPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.getNoteApiKey, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[getNoteApiKey]?.key as Preferences.Key<String>] ?: default
}

object GetNoteClientIdPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.getNoteClientId, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[getNoteClientId]?.key as Preferences.Key<String>] ?: default
}

object GetNoteTopicIdPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.getNoteTopicId, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[getNoteTopicId]?.key as Preferences.Key<String>] ?: default
}
