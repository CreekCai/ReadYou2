package me.ash.reader.ui.page.settings.tts

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
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
import me.ash.reader.infrastructure.preference.LocalTtsReadAiSummaryOnly
import me.ash.reader.infrastructure.preference.TtsConfigPreference
import me.ash.reader.infrastructure.preference.TtsReadAiSummaryOnlyPreference
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.settings.SettingItem
import org.json.JSONObject
import java.net.URLEncoder

@Composable
fun TtsSettingsPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ttsConfig = LocalTtsConfig.current
    val readAiSummaryOnly = LocalTtsReadAiSummaryOnly.current
    val parsedForm = remember(ttsConfig) { parseEdgeTtsForm(ttsConfig) }
    var apiUrl by remember(parsedForm.apiUrl) { mutableStateOf(parsedForm.apiUrl) }
    var token by remember(parsedForm.token) { mutableStateOf(parsedForm.token) }
    var voiceName by remember(parsedForm.voiceName) { mutableStateOf(parsedForm.voiceName) }
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

    LaunchedEffect(parsedForm) {
        apiUrl = parsedForm.apiUrl
        token = parsedForm.token
        voiceName = parsedForm.voiceName
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
                    DisplayText(
                        text = stringResource(R.string.tts_settings),
                        desc = stringResource(R.string.tts_settings_desc),
                    )
                }

                item {
                    SettingItem(
                        title = stringResource(R.string.tts_current_engine),
                        desc = configName,
                        onClick = {}
                    )
                }

                item {
                    SettingItem(
                        title = stringResource(R.string.tts_read_ai_summary_only),
                        desc = stringResource(R.string.tts_read_ai_summary_only_desc),
                        onClick = {
                            TtsReadAiSummaryOnlyPreference.put(
                                context,
                                scope,
                                !readAiSummaryOnly,
                            )
                        },
                        action = {
                            RYSwitch(
                                activated = readAiSummaryOnly,
                                onClick = {
                                    TtsReadAiSummaryOnlyPreference.put(
                                        context,
                                        scope,
                                        !readAiSummaryOnly,
                                    )
                                },
                            )
                        },
                    )
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text(stringResource(R.string.tts_api_url)) },
                            supportingText = { Text(stringResource(R.string.tts_api_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text(stringResource(R.string.tts_token)) },
                            supportingText = { Text(stringResource(R.string.tts_token_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = voiceName,
                            onValueChange = { voiceName = it },
                            label = { Text(stringResource(R.string.tts_voice_name)) },
                            supportingText = { Text(stringResource(R.string.tts_voice_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (apiUrl.isBlank() || voiceName.isBlank()) {
                                    context.showToast(context.getString(R.string.tts_missing_config))
                                    return@Button
                                }

                                val normalizedApiUrl = normalizeEdgeTtsApiUrl(apiUrl)
                                val json =
                                    JSONObject()
                                        .put("engine", "edge-read-aloud")
                                        .put("name", "Edge TTS - ${voiceName.trim()}")
                                        .put("apiUrl", normalizedApiUrl)
                                        .put("token", token.trim())
                                        .put("voiceName", voiceName.trim())
                                        .put(
                                            "url",
                                            buildEdgeTtsUrlTemplate(
                                                apiUrl = normalizedApiUrl,
                                                token = token,
                                                voiceName = voiceName,
                                            ),
                                        )
                                val config = json.toString()
                                TtsConfigPreference.put(context, scope, config)
                                configName = json.optString("name")
                                context.showToast(context.getString(R.string.tts_config_saved))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.tts_save_edge_config))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                                            context.showToast(context.getString(R.string.tts_config_saved))
                                        } else {
                                            context.showToast(context.getString(R.string.tts_invalid_config))
                                        }
                                    } catch (e: Exception) {
                                        context.showToast(context.getString(R.string.tts_invalid_config))
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.tts_import_clipboard))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                TtsConfigPreference.put(context, scope, "")
                                configName = context.getString(R.string.tts_default_local)
                                apiUrl = ""
                                token = ""
                                voiceName = DEFAULT_EDGE_TTS_VOICE_NAME
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.tts_reset_local))
                        }
                    }
                }
            }
        }
    )
}

private const val DEFAULT_EDGE_TTS_VOICE_NAME = "zh-CN-XiaoxiaoNeural"

private data class EdgeTtsForm(
    val apiUrl: String = "",
    val token: String = "",
    val voiceName: String = DEFAULT_EDGE_TTS_VOICE_NAME,
)

private fun parseEdgeTtsForm(ttsConfig: String): EdgeTtsForm {
    if (ttsConfig.isBlank()) return EdgeTtsForm()

    return try {
        val json = JSONObject(ttsConfig)
        EdgeTtsForm(
            apiUrl = json.optString("apiUrl", ""),
            token = json.optString("token", ""),
            voiceName =
                json.optString("voiceName", "")
                    .ifBlank { json.optString("name", DEFAULT_EDGE_TTS_VOICE_NAME) },
        )
    } catch (e: Exception) {
        EdgeTtsForm()
    }
}

private fun normalizeEdgeTtsApiUrl(rawUrl: String): String {
    var url = rawUrl.trim()
    if (url.isBlank()) return url
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }

    val uri = Uri.parse(url)
    val baseUrl =
        uri.buildUpon()
            .clearQuery()
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    val path = uri.path.orEmpty().trimEnd('/')

    return when {
        path.isBlank() -> "$baseUrl/api/synthesis"
        path == "/api" -> "$baseUrl/synthesis"
        else -> baseUrl
    }
}

private fun buildEdgeTtsUrlTemplate(
    apiUrl: String,
    token: String,
    voiceName: String,
): String {
    val query =
        buildList {
            add("voiceName=${voiceName.trim().urlEncode()}")
            if (token.isNotBlank()) {
                add("token=${token.trim().urlEncode()}")
            }
            add("rate={{speakSpeed/10}}")
            add("text={{speakText}}")
        }.joinToString("&")

    return "${apiUrl.trim()}?$query"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
