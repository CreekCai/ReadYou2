package me.ash.reader.ui.page.settings

import androidx.datastore.preferences.core.edit
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.infrastructure.preference.ExplanationPreferences
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.ui.text.input.VisualTransformation
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.domain.service.GeminiService
import me.ash.reader.domain.service.AiRequestException
import me.ash.reader.domain.service.sanitizeOpenAiBaseUrlInput
import me.ash.reader.infrastructure.preference.AiProviderPreference
import me.ash.reader.infrastructure.preference.CodexApiKeyPreference
import me.ash.reader.infrastructure.preference.CodexModelPreference
import me.ash.reader.infrastructure.preference.CodexTranslationModelPreference
import me.ash.reader.infrastructure.preference.GeminiApiKeyPreference
import me.ash.reader.infrastructure.preference.GeminiModelPreference
import me.ash.reader.infrastructure.preference.GeminiPromptPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationModelPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationPromptPreference
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.OpenAiBaseUrlPreference

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val geminiService: GeminiService,
) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()
    private val _diagnosticLog = MutableStateFlow<String?>(null)
    val diagnosticLog = _diagnosticLog.asStateFlow()
    fun markSaved() { _status.value = "配置已保存" }

    fun test(config: AiConnectionTestConfig) {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            _diagnosticLog.value = null
            _status.value = "正在测试摘要、翻译与解释模型…"
            try {
                val results = buildList {
                    add(testAiModel("摘要模型", config.summaryModel, config))
                    add(testAiModel("翻译模型", config.translationModel, config))
                    add(testAiModel("解释模型", config.explanationModel, config))
                }
                val failures = results.filter { !it.success }
                _status.value = results.joinToString("\n") {
                    when {
                        it.skipped -> "○ ${it.name}：未配置，已跳过"
                        it.success -> "✓ ${it.name}：连接成功"
                        else -> "✕ ${it.name}：连接失败（诊断日志已复制）"
                    }
                }
                if (failures.isNotEmpty()) {
                    _diagnosticLog.value = buildDiagnosticLog(config, results)
                }
            } finally {
                _testing.value = false
            }
        }
    }

    private suspend fun testAiModel(
        name: String,
        model: String,
        config: AiConnectionTestConfig,
    ): ConnectionTestResult = runCatching {
        geminiService.testModel(
            provider = config.provider,
            modelName = model,
            apiKey = config.aiApiKey,
            baseUrl = config.openAiBaseUrl,
        )
    }.fold(
        { ConnectionTestResult(name, true) },
        { ConnectionTestResult(name, false, it) },
    )

}

data class AiConnectionTestConfig(
    val provider: AiProviderPreference,
    val openAiBaseUrl: String,
    val aiApiKey: String,
    val summaryModel: String,
    val translationModel: String,
    val explanationModel: String,
)

private data class ConnectionTestResult(
    val name: String,
    val success: Boolean,
    val error: Throwable? = null,
    val skipped: Boolean = false,
)

private fun buildDiagnosticLog(
    config: AiConnectionTestConfig,
    results: List<ConnectionTestResult>,
): String {
    val secrets = listOf(config.aiApiKey).filter(String::isNotBlank)
    fun redact(value: String): String = secrets.fold(value) { text, secret -> text.replace(secret, "<REDACTED>") }
    return buildString {
        appendLine("ReadYou AI connection diagnostics")
        appendLine("Provider: ${config.provider.title}")
        appendLine("OpenAI Base URL: ${config.openAiBaseUrl.ifBlank { "<not set>" }}")
        appendLine("Summary model: ${config.summaryModel.ifBlank { "<not set>" }}")
        appendLine("Translation model: ${config.translationModel.ifBlank { "<not set>" }}")
        appendLine()
        results.forEach { result ->
            appendLine("[${result.name}] ${if (result.skipped) "SKIPPED" else if (result.success) "SUCCESS" else "FAILED"}")
            result.error?.let { error ->
                appendLine(
                    redact(
                        (error as? AiRequestException)?.diagnosticLog
                            ?: error.stackTraceToString()
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSettingsPage(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val status by viewModel.status.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val diagnosticLog by viewModel.diagnosticLog.collectAsState()
    val pageBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.background
    }

    var provider by remember { mutableStateOf(settings.aiProvider) }
    var providerExpanded by remember { mutableStateOf(false) }
    var openAiBaseUrl by remember {
        mutableStateOf(sanitizeOpenAiBaseUrlInput(settings.openAiBaseUrl))
    }
    var codexApiKey by remember { mutableStateOf(settings.codexApiKey) }
    var codexModel by remember { mutableStateOf(settings.codexModel) }
    var codexTranslationModel by remember { mutableStateOf(settings.codexTranslationModel) }
    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var geminiTranslationModel by remember { mutableStateOf(settings.geminiTranslationModel) }
    var geminiExplanationModel by remember { mutableStateOf(settings.geminiExplanationModel) }
    var codexExplanationModel by remember { mutableStateOf(settings.codexExplanationModel) }
    var explanationPrompt by remember { mutableStateOf(settings.explanationPrompt) }
    var explanationSearch by remember { mutableStateOf(settings.explanationSearch) }
    var summaryPrompt by remember { mutableStateOf(settings.geminiPrompt) }
    var translationPrompt by remember { mutableStateOf(settings.geminiTranslationPrompt) }
    var dirty by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    LaunchedEffect(diagnosticLog) {
        diagnosticLog?.let { log ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("ReadYou AI diagnostics", log))
        }
    }

    val onEdited: (String) -> Unit = { dirty = true }

    fun save() {
        showErrors = true
        provider.put(context, scope)
        val sanitizedOpenAiBaseUrl = sanitizeOpenAiBaseUrlInput(openAiBaseUrl)
        openAiBaseUrl = sanitizedOpenAiBaseUrl
        OpenAiBaseUrlPreference.put(context, scope, sanitizedOpenAiBaseUrl)
        CodexApiKeyPreference.put(context, scope, codexApiKey.trim())
        CodexModelPreference.put(context, scope, codexModel.trim())
        CodexTranslationModelPreference.put(context, scope, codexTranslationModel.trim())
        GeminiApiKeyPreference.put(context, scope, geminiApiKey.trim())
        GeminiModelPreference.put(context, scope, geminiModel.trim())
        GeminiTranslationModelPreference.put(context, scope, geminiTranslationModel.trim())
        GeminiPromptPreference.put(context, scope, summaryPrompt)
        GeminiTranslationPromptPreference.put(context, scope, translationPrompt)
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[ExplanationPreferences.geminiModel] = geminiExplanationModel.trim()
                prefs[ExplanationPreferences.openAiModel] = codexExplanationModel.trim()
                prefs[ExplanationPreferences.prompt] = explanationPrompt
                prefs[ExplanationPreferences.search] = explanationSearch
            }
        }
        dirty = false
        viewModel.markSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBackground,
                    scrolledContainerColor = pageBackground,
                ),
            )
        },
        containerColor = pageBackground,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("摘要、翻译与解释", style = MaterialTheme.typography.titleMedium) }
            item {
                ExposedDropdownMenuBox(providerExpanded, { providerExpanded = !providerExpanded }) {
                    OutlinedTextField(
                        value = provider.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI 服务商") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(providerExpanded, { providerExpanded = false }) {
                        AiProviderPreference.values.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.title) },
                                onClick = {
                                    provider = value
                                    providerExpanded = false
                                    dirty = true
                                },
                            )
                        }
                    }
                }
            }
            if (provider == AiProviderPreference.OpenAI) {
                item {
                    ConfigField(
                        openAiBaseUrl,
                        {
                            openAiBaseUrl = sanitizeOpenAiBaseUrlInput(it)
                            onEdited(it)
                        },
                        "OpenAI Base URL",
                    )
                }
                item { ConfigField(codexApiKey, { codexApiKey = it; onEdited(it) }, "OpenAI API Key", secret = true) }
                item { ConfigField(codexModel, { codexModel = it; onEdited(it) }, "摘要模型") }
                item { ConfigField(codexTranslationModel, { codexTranslationModel = it; onEdited(it) }, "翻译模型") }
            } else {
                item { ConfigField(geminiApiKey, { geminiApiKey = it; onEdited(it) }, "Gemini API Key", secret = true) }
                item { ConfigField(geminiModel, { geminiModel = it; onEdited(it) }, "摘要模型") }
                item { ConfigField(geminiTranslationModel, { geminiTranslationModel = it; onEdited(it) }, "翻译模型") }
            }
            item {
                ConfigField(
                    if (provider == AiProviderPreference.OpenAI) codexExplanationModel else geminiExplanationModel,
                    { if (provider == AiProviderPreference.OpenAI) codexExplanationModel = it else geminiExplanationModel = it; dirty = true },
                    "解释模型",
                )
            }
            item { ConfigField(explanationPrompt, { explanationPrompt = it; dirty = true }, "解释提示词", singleLine = false, minLines = 3, maxLines = 6) }
            if (provider == AiProviderPreference.Gemini) {
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(explanationSearch, { explanationSearch = it; dirty = true })
                        Text("允许解释时使用 Google 搜索")
                    }
                    Text("默认关闭。开启后模型按需搜索，搜索和模型用量可能计费。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                ConfigField(
                    value = summaryPrompt,
                    onValueChange = {
                        summaryPrompt = it
                        onEdited(it)
                    },
                    label = "摘要提示词",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            }
            item {
                ConfigField(
                    value = translationPrompt,
                    onValueChange = {
                        translationPrompt = it
                        onEdited(it)
                    },
                    label = "翻译提示词",
                    supporting = "默认翻译成简体中文；可修改目标语言或翻译要求",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::save, enabled = dirty || status == null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (dirty) "保存配置" else "配置已保存")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.test(
                                AiConnectionTestConfig(
                                    provider = provider,
                                    openAiBaseUrl = sanitizeOpenAiBaseUrlInput(openAiBaseUrl),
                                    aiApiKey = if (provider == AiProviderPreference.OpenAI) codexApiKey.trim() else geminiApiKey.trim(),
                                    summaryModel = if (provider == AiProviderPreference.OpenAI) codexModel.trim() else geminiModel.trim(),
                                    explanationModel = if (provider == AiProviderPreference.OpenAI) codexExplanationModel.trim() else geminiExplanationModel.trim(),
                                    translationModel = if (provider == AiProviderPreference.OpenAI) codexTranslationModel.trim() else geminiTranslationModel.trim(),
                                )
                            )
                        },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (testing) "连接中" else "测试连接")
                    }
                    status?.let {
                        Text(
                            it,
                            color = if (it.contains("失败") || it.contains("请")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (dirty) {
                        Text("有未保存的修改", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
    placeholder: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = if (secret) { {
            Row {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "隐藏 API Key" else "显示 API Key")
                }
                IconButton(onClick = {
                    val clip = ClipData.newPlainText("API Key", value)
                    if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                }, enabled = value.isNotBlank()) { Icon(Icons.Rounded.ContentCopy, "复制 API Key") }
            }
        } } else null,
        placeholder = placeholder?.let { { Text(it) } },
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
