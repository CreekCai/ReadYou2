package me.ash.reader.ui.page.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.OfflineArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OfflineArticleRepository
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.OfflineRetentionDaysPreference
import me.ash.reader.ui.ext.collectAsStateValue

@HiltViewModel
class OfflineSettingsViewModel @Inject constructor(
    dao: OfflineArticleDao,
    accountService: AccountService,
    private val repository: OfflineArticleRepository,
) : ViewModel() {
    private val accountId = accountService.getCurrentAccountId()
    val articles = dao.observeArticles(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val entries = dao.observeEntries(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _isClearing = MutableStateFlow(false)
    val isClearing = _isClearing.asStateFlow()

    fun clear(onComplete: () -> Unit) = viewModelScope.launch {
        _isClearing.value = true
        try {
            repository.clear(accountId)
            onComplete()
        } finally {
            _isClearing.value = false
        }
    }

    fun remove(articleId: String) = viewModelScope.launch {
        repository.remove(articleId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSettingsPage(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: OfflineSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val articles = viewModel.articles.collectAsStateValue()
    val entries = viewModel.entries.collectAsStateValue()
    val isClearing = viewModel.isClearing.collectAsStateValue()
    val pageBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.background
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val options = remember {
        listOf(0 to "永不自动清理", 7 to "保存 7 天", 30 to "保存 30 天", 90 to "保存 90 天")
    }
    val totalBytes = entries.sumOf { it.sizeBytes }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearConfirmation = false },
            title = { Text("清空全部离线内容？") },
            text = { Text("将删除 ${articles.size} 篇文章及约 ${formatBytes(totalBytes)} 本地文件。此操作无法撤销。") },
            confirmButton = {
                Button(
                    enabled = !isClearing,
                    onClick = {
                        val count = articles.size
                        viewModel.clear {
                            showClearConfirmation = false
                            scope.launch { snackbarHostState.showSnackbar("已清理 $count 篇离线文章") }
                        }
                    },
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(enabled = !isClearing, onClick = { showClearConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线内容") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = pageBackground,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "已离线 ${articles.size} 篇",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "占用 ${formatBytes(totalBytes)} · 正文与图片保存在本机",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        value = options.firstOrNull { it.first == settings.offlineRetentionDays }?.second
                            ?: "保存 ${settings.offlineRetentionDays} 天",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("定期清理") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        options.forEach { (days, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    OfflineRetentionDaysPreference.put(context, scope, days)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showClearConfirmation = true },
                    enabled = articles.isNotEmpty() && !isClearing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("立即清理全部离线内容")
                }
            }
            item {
                HorizontalDivider()
                Text(
                    "已离线文章",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (articles.isEmpty()) {
                item {
                    Text(
                        "还没有离线文章。在文章列表中长按并选择“离线文章”。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(articles, key = { it.article.id }) { value ->
                ListItem(
                    headlineContent = { Text(value.article.title) },
                    supportingContent = { Text(value.feed.name) },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.remove(value.article.id)
                            scope.launch { snackbarHostState.showSnackbar("已移除离线文章") }
                        }) {
                            Icon(Icons.Rounded.DeleteOutline, "移除离线文章")
                        }
                    },
                    modifier = Modifier.clickable { onOpenArticle(value.article.id) },
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
