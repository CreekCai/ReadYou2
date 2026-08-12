package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

private fun stringValue(preferences: Preferences, name: String): String =
    preferences[DataStoreKey.keys[name]?.key as Preferences.Key<String>] ?: ""

object RagflowBaseUrlPreference {
    const val default = ""
    fun put(context: Context, scope: CoroutineScope, value: String) = scope.launch(Dispatchers.IO) { context.dataStore.put(DataStoreKey.ragflowBaseUrl, value.trim().trimEnd('/')) }
    fun fromPreferences(value: Preferences) = stringValue(value, DataStoreKey.ragflowBaseUrl)
}
object RagflowApiKeyPreference {
    const val default = ""
    fun put(context: Context, scope: CoroutineScope, value: String) = scope.launch(Dispatchers.IO) { context.dataStore.put(DataStoreKey.ragflowApiKey, value.trim()) }
    fun fromPreferences(value: Preferences) = stringValue(value, DataStoreKey.ragflowApiKey)
}
object RagflowDatasetIdPreference {
    const val default = ""
    fun put(context: Context, scope: CoroutineScope, value: String) = scope.launch(Dispatchers.IO) { context.dataStore.put(DataStoreKey.ragflowDatasetId, value.trim()) }
    fun fromPreferences(value: Preferences) = stringValue(value, DataStoreKey.ragflowDatasetId)
}
object RagflowChatIdPreference {
    const val default = ""
    fun put(context: Context, scope: CoroutineScope, value: String) = scope.launch(Dispatchers.IO) { context.dataStore.put(DataStoreKey.ragflowChatId, value.trim()) }
    fun fromPreferences(value: Preferences) = stringValue(value, DataStoreKey.ragflowChatId)
}
object OfflineRetentionDaysPreference {
    const val default = 30
    fun put(context: Context, scope: CoroutineScope, value: Int) = scope.launch(Dispatchers.IO) { context.dataStore.put(DataStoreKey.offlineRetentionDays, value) }
    fun fromPreferences(value: Preferences) = value[DataStoreKey.keys[DataStoreKey.offlineRetentionDays]?.key as Preferences.Key<Int>] ?: default
}
