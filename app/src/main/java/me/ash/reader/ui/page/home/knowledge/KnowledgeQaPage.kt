package me.ash.reader.ui.page.home.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.domain.service.RagSource
import me.ash.reader.ui.ext.collectAsStateValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeQaPage(
    onBack: () -> Unit,
    viewModel: KnowledgeQaViewModel = hiltViewModel(),
) {
    val messages = viewModel.messages.collectAsStateValue()
    val loading = viewModel.loading.collectAsStateValue()
    val suggestions = viewModel.suggestions.collectAsStateValue()
    val count = viewModel.starredCount.collectAsStateValue()
    val pageBackground = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.background
    }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val submit = {
        input.trim().takeIf(String::isNotEmpty)?.let {
            input = ""
            viewModel.ask(it)
        }
        Unit
    }

    LaunchedEffect(messages, loading) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("星标知识库")
                        Text(
                            "$count 篇文章已接入",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        bottomBar = {
            Surface(color = pageBackground, tonalElevation = 0.dp) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (messages.isEmpty()) "向星标文章提问" else "继续追问") },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (!loading) submit() }),
                    )
                    Spacer(Modifier.width(4.dp))
                    if (loading) {
                        IconButton(onClick = viewModel::cancel) {
                            Icon(Icons.Rounded.StopCircle, "停止生成")
                        }
                    } else {
                        IconButton(enabled = input.isNotBlank(), onClick = submit) {
                            Icon(Icons.Rounded.Send, "发送")
                        }
                    }
                }
            }
        },
        containerColor = pageBackground,
    ) { padding ->
        if (messages.isEmpty()) {
            KnowledgeEmptyState(
                count = count,
                suggestions = suggestions,
                onAsk = viewModel::ask,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(messages) { index, item ->
                    KnowledgeExchange(
                        item = item,
                        count = count,
                        onRetry = { viewModel.retry(index) },
                    )
                }
                item(key = "answer-end") { Spacer(Modifier.height(1.dp)) }
            }
        }
    }
}

@Composable
private fun KnowledgeEmptyState(
    count: Int,
    suggestions: List<String>,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(22.dp).size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "从你的星标文章中寻找答案",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (count == 0) "先给文章加上星标，知识库会自动建立。" else "RAGFlow 将检索相关片段，并结合上下文回答。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        if (suggestions.isNotEmpty()) {
            Text(
                "从最近上传的文章开始",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            suggestions.forEachIndexed { index, suggestion ->
                SuggestionRow(
                    number = index + 1,
                    text = suggestion,
                    onClick = { onAsk(suggestion) },
                )
                if (index != suggestions.lastIndex) HorizontalDivider()
            }
        } else if (count > 0) {
            Text(
                "最近的星标文章同步完成后，这里会出现相关问题。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    number: Int,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(30.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = "使用这个问题",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun KnowledgeExchange(
    item: QaMessage,
    count: Int,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.question,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .padding(vertical = 2.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {}
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "ReadYou 知识助手 · 基于 $count 篇文章",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "回答",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        when {
            item.error != null -> {
                Text(item.error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新提问")
                }
            }
            item.answer != null -> {
                MarkdownAnswer(item.answer, Modifier.fillMaxWidth())
                if (item.sources.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    KnowledgeSources(item.sources)
                }
            }
            else -> {
                Text("正在检索相关星标文章…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun KnowledgeSources(sources: List<RagSource>) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "查看 ${sources.size} 篇参考文章",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "收起参考文章" else "展开参考文章",
        )
    }
    if (expanded) {
        sources.forEachIndexed { index, source ->
            ListItem(
                headlineContent = { Text("${index + 1}. ${source.title}") },
                supportingContent = source.content?.let { content ->
                    { Text(content, maxLines = 2) }
                },
                trailingContent = source.url?.let { url ->
                    {
                        IconButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, "打开原文")
                        }
                    }
                },
                modifier = source.url?.let { url ->
                    Modifier.clickable { uriHandler.openUri(url) }
                } ?: Modifier,
            )
        }
    }
}
