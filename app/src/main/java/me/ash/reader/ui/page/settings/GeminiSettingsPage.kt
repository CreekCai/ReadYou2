package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.domain.service.RagflowBackfillWorker
import me.ash.reader.domain.service.RagflowRepository

@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val ragflow: RagflowRepository, private val workManager: WorkManager) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null); val status = _status.asStateFlow()
    fun test() = viewModelScope.launch { _status.value = "正在连接…"; _status.value = ragflow.test().fold({ "连接成功" }, { it.message ?: "连接失败" }) }
    fun sync() { RagflowBackfillWorker.enqueue(workManager); _status.value = "已开始同步现有星标文章" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSettingsPage(onBack: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val settings = LocalSettings.current
    val status by viewModel.status.collectAsState()
    var providerExpanded by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("大模型与知识库") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("摘要与翻译", style = MaterialTheme.typography.titleMedium) }
            item { ExposedDropdownMenuBox(providerExpanded, { providerExpanded = !providerExpanded }) { OutlinedTextField(settings.aiProvider.title, {}, readOnly = true, label = { Text("AI 服务商") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(providerExpanded, { providerExpanded = false }) { AiProviderPreference.values.forEach { provider -> DropdownMenuItem({ Text(provider.title) }, { provider.put(context, scope); providerExpanded = false }) } } } }
            if (settings.aiProvider == AiProviderPreference.OpenAI) {
                item { OutlinedTextField(settings.openAiBaseUrl, { OpenAiBaseUrlPreference.put(context, scope, it) }, label = { Text("OpenAI Base URL") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(settings.codexApiKey, { CodexApiKeyPreference.put(context, scope, it) }, label = { Text("OpenAI API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(settings.codexModel, { CodexModelPreference.put(context, scope, it) }, label = { Text("摘要模型") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(settings.codexTranslationModel, { CodexTranslationModelPreference.put(context, scope, it) }, label = { Text("翻译模型") }, modifier = Modifier.fillMaxWidth()) }
            } else {
                item { OutlinedTextField(settings.geminiApiKey, { GeminiApiKeyPreference.put(context, scope, it) }, label = { Text("Gemini API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(settings.geminiModel, { GeminiModelPreference.put(context, scope, it) }, label = { Text("摘要模型") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(settings.geminiTranslationModel, { GeminiTranslationModelPreference.put(context, scope, it) }, label = { Text("翻译模型") }, modifier = Modifier.fillMaxWidth()) }
            }
            item { HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("RAGFlow 星标知识库", style = MaterialTheme.typography.titleMedium); Text("向量化、关联检索与上下文管理均由 RAGFlow 服务端完成。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(settings.ragflowBaseUrl, { RagflowBaseUrlPreference.put(context, scope, it) }, label = { Text("RAGFlow 地址") }, placeholder = { Text("https://rag.example.com") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(settings.ragflowApiKey, { RagflowApiKeyPreference.put(context, scope, it) }, label = { Text("API 密钥") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(settings.ragflowDatasetId, { RagflowDatasetIdPreference.put(context, scope, it) }, label = { Text("数据集 ID") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(settings.ragflowChatId, { RagflowChatIdPreference.put(context, scope, it) }, label = { Text("对话助手 ID") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = viewModel::test) { Text("测试连接") }; Button(onClick = viewModel::sync) { Text("同步现有星标文章") } }; status?.let { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}
