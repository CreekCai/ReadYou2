package me.ash.reader.ui.page.settings

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
import me.ash.reader.domain.service.RagflowBackfillWorker
import me.ash.reader.domain.service.RagflowCatalog
import me.ash.reader.domain.service.RagflowRepository
import me.ash.reader.infrastructure.preference.AiProviderPreference
import me.ash.reader.infrastructure.preference.CodexApiKeyPreference
import me.ash.reader.infrastructure.preference.CodexModelPreference
import me.ash.reader.infrastructure.preference.CodexTranslationModelPreference
import me.ash.reader.infrastructure.preference.GeminiApiKeyPreference
import me.ash.reader.infrastructure.preference.GeminiModelPreference
import me.ash.reader.infrastructure.preference.GeminiTranslationModelPreference
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.OpenAiBaseUrlPreference
import me.ash.reader.infrastructure.preference.RagflowApiKeyPreference
import me.ash.reader.infrastructure.preference.RagflowBaseUrlPreference
import me.ash.reader.infrastructure.preference.RagflowChatIdPreference
import me.ash.reader.infrastructure.preference.RagflowDatasetIdPreference

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val ragflow: RagflowRepository,
    private val geminiService: GeminiService,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()
    private val _diagnosticLog = MutableStateFlow<String?>(null)
    val diagnosticLog = _diagnosticLog.asStateFlow()
    private val _catalog = MutableStateFlow<RagflowCatalog?>(null)
    val catalog = _catalog.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    val discovering = _discovering.asStateFlow()

    fun markSaved() {
        _status.value = "配置已保存"
    }

    fun test(config: AiConnectionTestConfig) {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            _diagnosticLog.value = null
            _status.value = "正在测试摘要、翻译与 RAGFlow…"
            try {
                val results = buildList {
                    add(testAiModel("摘要模型", config.summaryModel, config))
                    add(testAiModel("翻译模型", config.translationModel, config))
                    if (config.hasAnyRagflowValue) {
                        add(
                            runCatching {
                                ragflow.test(
                                    baseUrl = config.ragflowBaseUrl,
                                    apiKey = config.ragflowApiKey,
                                    datasetId = config.ragflowDatasetId,
                                ).getOrThrow()
                            }.fold(
                                { ConnectionTestResult("RAGFlow", true) },
                                { ConnectionTestResult("RAGFlow", false, it) },
                            )
                        )
                    } else {
                        add(ConnectionTestResult("RAGFlow", true, skipped = true))
                    }
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

    fun discover(baseUrl: String, apiKey: String) {
        if (_discovering.value) return
        viewModelScope.launch {
            _discovering.value = true
            _status.value = "正在获取数据集与助手…"
            ragflow.discover(baseUrl, apiKey).fold(
                onSuccess = {
                    _catalog.value = it
                    _status.value = when {
                        it.datasets.isEmpty() && it.chats.isEmpty() -> "连接成功，但没有找到数据集或对话助手"
                        it.datasets.isEmpty() -> "没有找到数据集，请先在 RAGFlow 中创建"
                        it.chats.isEmpty() -> "没有找到对话助手，请先在 RAGFlow 中创建"
                        else -> "已获取 ${it.datasets.size} 个数据集和 ${it.chats.size} 个对话助手"
                    }
                },
                onFailure = {
                    _catalog.value = null
                    _status.value = it.message ?: "获取失败"
                },
            )
            _discovering.value = false
        }
    }

    fun clearCatalog() {
        _catalog.value = null
    }

    fun sync() {
        RagflowBackfillWorker.enqueue(workManager)
        _status.value = "已加入后台同步队列"
    }
}

data class AiConnectionTestConfig(
    val provider: AiProviderPreference,
    val openAiBaseUrl: String,
    val aiApiKey: String,
    val summaryModel: String,
    val translationModel: String,
    val ragflowBaseUrl: String,
    val ragflowApiKey: String,
    val ragflowDatasetId: String,
) {
    val hasAnyRagflowValue = listOf(ragflowBaseUrl, ragflowApiKey, ragflowDatasetId).any(String::isNotBlank)
}

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
    val secrets = listOf(config.aiApiKey, config.ragflowApiKey).filter(String::isNotBlank)
    fun redact(value: String): String = secrets.fold(value) { text, secret -> text.replace(secret, "<REDACTED>") }
    return buildString {
        appendLine("ReadYou AI connection diagnostics")
        appendLine("Provider: ${config.provider.title}")
        appendLine("OpenAI Base URL: ${config.openAiBaseUrl.ifBlank { "<not set>" }}")
        appendLine("Summary model: ${config.summaryModel.ifBlank { "<not set>" }}")
        appendLine("Translation model: ${config.translationModel.ifBlank { "<not set>" }}")
        appendLine("RAGFlow Base URL: ${config.ragflowBaseUrl.ifBlank { "<not set>" }}")
        appendLine("RAGFlow dataset: ${config.ragflowDatasetId.ifBlank { "<not set>" }}")
        appendLine()
        results.forEach { result ->
            appendLine("[${result.name}] ${if (result.skipped) "SKIPPED" else if (result.success) "SUCCESS" else "FAILED"}")
            result.error?.let { error ->
                appendLine(redact(error.stackTraceToString()))
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
    val catalog by viewModel.catalog.collectAsState()
    val discovering by viewModel.discovering.collectAsState()
    val pageBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.background
    }

    var provider by remember { mutableStateOf(settings.aiProvider) }
    var providerExpanded by remember { mutableStateOf(false) }
    var openAiBaseUrl by remember { mutableStateOf(settings.openAiBaseUrl) }
    var codexApiKey by remember { mutableStateOf(settings.codexApiKey) }
    var codexModel by remember { mutableStateOf(settings.codexModel) }
    var codexTranslationModel by remember { mutableStateOf(settings.codexTranslationModel) }
    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var geminiTranslationModel by remember { mutableStateOf(settings.geminiTranslationModel) }
    var ragflowBaseUrl by remember { mutableStateOf(settings.ragflowBaseUrl) }
    var ragflowApiKey by remember { mutableStateOf(settings.ragflowApiKey) }
    var ragflowDatasetId by remember { mutableStateOf(settings.ragflowDatasetId) }
    var ragflowChatId by remember { mutableStateOf(settings.ragflowChatId) }
    var datasetExpanded by remember { mutableStateOf(false) }
    var chatExpanded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    LaunchedEffect(diagnosticLog) {
        diagnosticLog?.let { log ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("ReadYou AI diagnostics", log))
        }
    }

    val ragStarted = listOf(ragflowBaseUrl, ragflowApiKey, ragflowDatasetId, ragflowChatId).any { it.isNotBlank() }
    val ragUrlValid = ragflowBaseUrl.startsWith("https://") || ragflowBaseUrl.startsWith("http://")
    val credentialsComplete = ragUrlValid && ragflowApiKey.isNotBlank()
    val selectionStarted = ragflowDatasetId.isNotBlank() || ragflowChatId.isNotBlank()
    val selectionComplete = ragflowDatasetId.isNotBlank() && ragflowChatId.isNotBlank()
    val ragComplete = ragUrlValid && ragflowApiKey.isNotBlank() &&
        ragflowDatasetId.isNotBlank() && ragflowChatId.isNotBlank()
    val configurationValid = !ragStarted || (credentialsComplete && (!selectionStarted || selectionComplete))
    val onEdited: (String) -> Unit = { dirty = true }

    fun save() {
        showErrors = true
        if (!configurationValid) return
        provider.put(context, scope)
        OpenAiBaseUrlPreference.put(context, scope, openAiBaseUrl.trim())
        CodexApiKeyPreference.put(context, scope, codexApiKey.trim())
        CodexModelPreference.put(context, scope, codexModel.trim())
        CodexTranslationModelPreference.put(context, scope, codexTranslationModel.trim())
        GeminiApiKeyPreference.put(context, scope, geminiApiKey.trim())
        GeminiModelPreference.put(context, scope, geminiModel.trim())
        GeminiTranslationModelPreference.put(context, scope, geminiTranslationModel.trim())
        RagflowBaseUrlPreference.put(context, scope, ragflowBaseUrl.trimEnd('/').trim())
        RagflowApiKeyPreference.put(context, scope, ragflowApiKey.trim())
        RagflowDatasetIdPreference.put(context, scope, ragflowDatasetId.trim())
        RagflowChatIdPreference.put(context, scope, ragflowChatId.trim())
        dirty = false
        viewModel.markSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大模型与知识库") },
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
            item { Text("摘要与翻译", style = MaterialTheme.typography.titleMedium) }
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
                item { ConfigField(openAiBaseUrl, { openAiBaseUrl = it; onEdited(it) }, "OpenAI Base URL") }
                item { ConfigField(codexApiKey, { codexApiKey = it; onEdited(it) }, "OpenAI API Key", secret = true) }
                item { ConfigField(codexModel, { codexModel = it; onEdited(it) }, "摘要模型") }
                item { ConfigField(codexTranslationModel, { codexTranslationModel = it; onEdited(it) }, "翻译模型") }
            } else {
                item { ConfigField(geminiApiKey, { geminiApiKey = it; onEdited(it) }, "Gemini API Key", secret = true) }
                item { ConfigField(geminiModel, { geminiModel = it; onEdited(it) }, "摘要模型") }
                item { ConfigField(geminiTranslationModel, { geminiTranslationModel = it; onEdited(it) }, "翻译模型") }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                Text("RAGFlow 星标知识库", style = MaterialTheme.typography.titleMedium)
                Text(
                    "向量化、关联检索与上下文管理均由 RAGFlow 服务端完成。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ConfigField(
                    ragflowBaseUrl,
                    {
                        ragflowBaseUrl = it
                        ragflowDatasetId = ""
                        ragflowChatId = ""
                        viewModel.clearCatalog()
                        onEdited(it)
                    },
                    "RAGFlow 地址",
                    placeholder = "https://rag.example.com",
                    isError = showErrors && ragStarted && !ragUrlValid,
                    supporting = if (showErrors && ragStarted && !ragUrlValid) "请输入以 http:// 或 https:// 开头的地址" else null,
                )
            }
            item {
                ConfigField(
                    ragflowApiKey,
                    {
                        ragflowApiKey = it
                        ragflowDatasetId = ""
                        ragflowChatId = ""
                        viewModel.clearCatalog()
                        onEdited(it)
                    },
                    "API 密钥",
                    secret = true,
                    isError = showErrors && ragStarted && ragflowApiKey.isBlank(),
                )
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.discover(ragflowBaseUrl, ragflowApiKey) },
                    enabled = credentialsComplete && !discovering,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (discovering) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (discovering) "正在获取" else if (catalog == null) "获取数据集与助手" else "刷新数据集与助手")
                }
            }
            catalog?.let { values ->
                item {
                    RagflowOptionField(
                        label = "数据集",
                        selectedId = ragflowDatasetId,
                        options = values.datasets.map { it.id to it.name },
                        expanded = datasetExpanded,
                        onExpandedChange = { datasetExpanded = it },
                        onSelected = {
                            ragflowDatasetId = it
                            dirty = true
                        },
                        emptyText = "没有可用的数据集",
                        isError = showErrors && selectionStarted && ragflowDatasetId.isBlank(),
                    )
                }
                item {
                    RagflowOptionField(
                        label = "对话助手",
                        selectedId = ragflowChatId,
                        options = values.chats.map { it.id to it.name },
                        expanded = chatExpanded,
                        onExpandedChange = { chatExpanded = it },
                        onSelected = {
                            ragflowChatId = it
                            dirty = true
                        },
                        emptyText = "没有可用的对话助手",
                        isError = showErrors && selectionStarted && ragflowChatId.isBlank(),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::save, enabled = dirty || status == null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (dirty) "保存配置" else "配置已保存")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.test(
                                    AiConnectionTestConfig(
                                        provider = provider,
                                        openAiBaseUrl = openAiBaseUrl.trim(),
                                        aiApiKey = if (provider == AiProviderPreference.OpenAI) codexApiKey.trim() else geminiApiKey.trim(),
                                        summaryModel = if (provider == AiProviderPreference.OpenAI) codexModel.trim() else geminiModel.trim(),
                                        translationModel = if (provider == AiProviderPreference.OpenAI) codexTranslationModel.trim() else geminiTranslationModel.trim(),
                                        ragflowBaseUrl = ragflowBaseUrl.trim(),
                                        ragflowApiKey = ragflowApiKey.trim(),
                                        ragflowDatasetId = ragflowDatasetId.trim(),
                                    )
                                )
                            },
                            enabled = !testing,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (testing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (testing) "连接中" else "测试连接")
                        }
                        OutlinedButton(
                            onClick = viewModel::sync,
                            enabled = ragComplete && !dirty && !testing,
                            modifier = Modifier.weight(1f),
                        ) { Text("同步星标文章") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RagflowOptionField(
    label: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit,
    emptyText: String,
    isError: Boolean,
) {
    val selectedName = options.firstOrNull { it.first == selectedId }?.second
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) onExpandedChange(it) },
    ) {
        OutlinedTextField(
            value = selectedName ?: if (selectedId.isBlank()) "" else "已保存的选择",
            onValueChange = {},
            readOnly = true,
            enabled = options.isNotEmpty(),
            label = { Text(label) },
            placeholder = { Text(if (options.isEmpty()) emptyText else "请选择$label") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = if (selectedId.isNotBlank() && selectedName == null) {
                { Text("当前 ID：${selectedId.take(8)}…，可重新选择") }
            } else null,
            isError = isError,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { onExpandedChange(false) }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name)
                            Text(
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(id)
                        onExpandedChange(false)
                    },
                )
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
