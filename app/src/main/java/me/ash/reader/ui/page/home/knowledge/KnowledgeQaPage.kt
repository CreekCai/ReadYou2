package me.ash.reader.ui.page.home.knowledge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.ui.ext.collectAsStateValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeQaPage(onBack: () -> Unit, viewModel: KnowledgeQaViewModel = hiltViewModel()) {
    val messages = viewModel.messages.collectAsStateValue()
    val loading = viewModel.loading.collectAsStateValue()
    val count = viewModel.starredCount.collectAsStateValue()
    var input by remember { mutableStateOf("") }
    val suggestions = listOf("总结我最近关注的主题", "这些文章有哪些共同观点？", "帮我梳理关键结论")

    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("星标知识库"); Text("基于 $count 篇星标文章", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.navigationBarsPadding().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("向星标文章提问…") }, maxLines = 4)
                    IconButton(enabled = input.isNotBlank() && !loading, onClick = { val q = input; input = ""; viewModel.ask(q) }) { Icon(Icons.Rounded.Send, "发送") }
                }
            }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(22.dp).size(38.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(24.dp)); Text("从你的星标文章中寻找答案", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Text("RAGFlow 会检索最相关的文章片段，并结合上下文回答。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp)); suggestions.forEach { suggestion -> AssistChip(onClick = { viewModel.ask(suggestion) }, label = { Text(suggestion) }, modifier = Modifier.fillMaxWidth()) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(messages) { item ->
                    Column {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.End)) { Text(item.question, Modifier.padding(14.dp)) }
                        Spacer(Modifier.height(12.dp))
                        when { item.error != null -> Text(item.error, color = MaterialTheme.colorScheme.error); item.answer != null -> { Text(item.answer, style = MaterialTheme.typography.bodyLarge); if (item.sources.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text("参考来源", fontWeight = FontWeight.SemiBold); item.sources.forEach { Text("• ${it.title}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) } } }; else -> LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}
