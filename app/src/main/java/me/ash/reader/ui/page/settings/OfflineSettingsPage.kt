package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.OfflineArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OfflineArticleRepository
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.OfflineRetentionDaysPreference
import me.ash.reader.ui.ext.collectAsStateValue

@HiltViewModel
class OfflineSettingsViewModel @Inject constructor(dao: OfflineArticleDao, accountService: AccountService, private val repository: OfflineArticleRepository) : ViewModel() {
    private val accountId = accountService.getCurrentAccountId()
    val articles = dao.observeArticles(accountId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun clear() = viewModelScope.launch { repository.clearAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSettingsPage(onBack: () -> Unit, onOpenArticle: (String) -> Unit, viewModel: OfflineSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val settings = LocalSettings.current
    val articles = viewModel.articles.collectAsStateValue(); var expanded by remember { mutableStateOf(false) }
    val options = listOf(0 to "永不自动清理", 7 to "保存 7 天", 30 to "保存 30 天", 90 to "保存 90 天")
    Scaffold(topBar = { TopAppBar(title = { Text("离线内容") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("已离线 ${articles.size} 篇", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("正文与图片保存在本机，可在无网络时阅读。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(options.firstOrNull { it.first == settings.offlineRetentionDays }?.second ?: "保存 ${settings.offlineRetentionDays} 天", {}, readOnly = true, label = { Text("定期清理") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { (days, label) -> DropdownMenuItem({ Text(label) }, { OfflineRetentionDaysPreference.put(context, scope, days); expanded = false }) } }
                }
            }
            item { OutlinedButton(onClick = viewModel::clear, enabled = articles.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("立即清理全部离线内容") } }
            item { HorizontalDivider(); Text("已离线文章", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (articles.isEmpty()) item { Text("还没有离线文章。在文章列表中长按并选择“离线文章”。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 32.dp)) }
            items(articles, key = { it.article.id }) { value -> ListItem(headlineContent = { Text(value.article.title) }, supportingContent = { Text(value.feed.name) }, modifier = Modifier.clickable { onOpenArticle(value.article.id) }) }
        }
    }
}
