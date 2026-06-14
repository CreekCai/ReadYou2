package me.ash.reader.ui.page.settings.tts

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalTtsConfig
import me.ash.reader.infrastructure.preference.TtsConfigPreference
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.page.settings.SettingItem
import org.json.JSONObject

@Composable
fun TtsSettingsPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ttsConfig = LocalTtsConfig.current
    var configName by remember(ttsConfig) {
        mutableStateOf(
            try {
                if (ttsConfig.isNotBlank()) {
                    JSONObject(ttsConfig).optString("name", "Unknown Config")
                } else {
                    "Default Local TTS"
                }
            } catch (e: Exception) {
                "Invalid Config"
            }
        )
    }

    RYScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = "Text-to-Speech", desc = "Configure TTS engine")
                }

                item {
                    SettingItem(
                        title = "Current Engine",
                        desc = configName,
                        onClick = {}
                    )
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text.toString()
                                    try {
                                        // Validate JSON
                                        val json = JSONObject(text)
                                        if (json.has("url") && json.has("name")) {
                                            TtsConfigPreference.put(context, scope, text)
                                            configName = json.optString("name")
                                        }
                                    } catch (e: Exception) {
                                        // Invalid JSON
                                    }
                                }
                            }
                        ) {
                            Text("Import Config from Clipboard")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                TtsConfigPreference.put(context, scope, "")
                                configName = "Default Local TTS"
                            }
                        ) {
                            Text("Reset to Local TTS")
                        }
                    }
                }
            }
        }
    )
}
