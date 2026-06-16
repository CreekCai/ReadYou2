package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.typeChoEndpoint
import me.ash.reader.ui.ext.DataStoreKey.Companion.typeChoHomeUrl
import me.ash.reader.ui.ext.DataStoreKey.Companion.typeChoPassword
import me.ash.reader.ui.ext.DataStoreKey.Companion.typeChoUsername
import me.ash.reader.ui.ext.DataStoreKey.Companion.typeChoWorkerUrl
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalTypeChoEndpoint = compositionLocalOf { TypeChoEndpointPreference.default }
val LocalTypeChoHomeUrl = compositionLocalOf { TypeChoHomeUrlPreference.default }
val LocalTypeChoUsername = compositionLocalOf { TypeChoUsernamePreference.default }
val LocalTypeChoPassword = compositionLocalOf { TypeChoPasswordPreference.default }
val LocalTypeChoWorkerUrl = compositionLocalOf { TypeChoWorkerUrlPreference.default }

object TypeChoWorkerUrlPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.typeChoWorkerUrl, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[typeChoWorkerUrl]?.key as Preferences.Key<String>] ?: default
}

object TypeChoEndpointPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.typeChoEndpoint, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[typeChoEndpoint]?.key as Preferences.Key<String>] ?: default
}

object TypeChoHomeUrlPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.typeChoHomeUrl, value.trim())
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[typeChoHomeUrl]?.key as Preferences.Key<String>] ?: default
}

object TypeChoUsernamePreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.typeChoUsername, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[typeChoUsername]?.key as Preferences.Key<String>] ?: default
}

object TypeChoPasswordPreference {
    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(DataStoreKey.typeChoPassword, value)
        }
    }

    fun fromPreferences(preferences: Preferences): String =
        preferences[DataStoreKey.keys[typeChoPassword]?.key as Preferences.Key<String>] ?: default
}
