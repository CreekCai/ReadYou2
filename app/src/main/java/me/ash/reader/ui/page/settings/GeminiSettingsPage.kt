package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import me.ash.reader.domain.service.RagflowBackfillWorker
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
    private val workManager: WorkManager,
) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()

    fun markSaved() {
        _status.value = "配置已保存"
    }

    fun test() {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            _status.value = "正在连接 RAGFlow…"
            _status.value = ragflow.test().fold({ "连接成功" }, { it.message ?: "连接失败" })
            _testing.value = false
        }
    }

    fun sync() {
        RagflowBackfillWorker.enqueue(workManager)
        _status.value = "已加入后台同步队列"
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
    var dirty by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val ragStarted = listOf(ragflowBaseUrl, ragflowApiKey, ragflowDatasetId, ragflowChatId)
        .any { it.isNotBlank() }
    val ragUrlValid = ragflowBaseUrl.startsWith("https://") || ragflowBaseUrl.startsWith("http://")
    val ragComplete = ragUrlValid && ragflowApiKey.isNotBlank() &&
        ragflowDatasetId.isNotBlank() && ragflowChatId.isNotBlank()
    val configurationValid = !ragStarted || ragComplete
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
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
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
                    { ragflowBaseUrl = it; onEdited(it) },
                    "RAGFlow 地址",
                    placeholder = "https://rag.example.com",
                    isError = showErrors && ragStarted && !ragUrlValid,
                    supporting = if (showErrors && ragStarted && !ragUrlValid) "请输入以 http:// 或 https:// 开头的地址" else null,
                )
            }
            item { ConfigField(ragflowApiKey, { ragflowApiKey = it; onEdited(it) }, "API 密钥", secret = true, isError = showErrors && ragStarted && ragflowApiKey.isBlank()) }
            item { ConfigField(ragflowDatasetId, { ragflowDatasetId = it; onEdited(it) }, "数据集 ID", isError = showErrors && ragStarted && ragflowDatasetId.isBlank()) }
            item { ConfigField(ragflowChatId, { ragflowChatId = it; onEdited(it) }, "对话助手 ID", isError = showErrors && ragStarted && ragflowChatId.isBlank()) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::save, enabled = dirty || status == null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (dirty) "保存配置" else "配置已保存")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::test,
                            enabled = ragComplete && !dirty && !testing,
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
