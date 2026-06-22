package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.InsightState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleInsightPage(
    viewModel: ArticleListReaderViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val insightState = viewModel.insightState.collectAsStateValue()
    val maxWidthModifier = Modifier.widthIn(max = LocalTextContentWidth.current)

    LaunchedEffect(readerState.articleId) {
        if (readerState.articleId != null && insightState is InsightState.Idle) {
            viewModel.generateInsight()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "AI 洞察")
                        readerState.title?.let {
                            Text(
                                text = it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = context.getString(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = insightState !is InsightState.Loading,
                        onClick = { viewModel.generateInsight() },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "重新生成",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        ) {
            when (insightState) {
                InsightState.Idle -> {
                    item {
                        InsightEmptyState(
                            modifier = maxWidthModifier.fillMaxWidth(),
                            text = "还没有生成洞察",
                            actionText = "开始生成",
                            onClick = { viewModel.generateInsight() },
                        )
                    }
                }
                InsightState.Loading -> {
                    item {
                        InsightLoadingState(modifier = maxWidthModifier.fillMaxWidth())
                    }
                }
                is InsightState.Error -> {
                    item {
                        InsightEmptyState(
                            modifier = maxWidthModifier.fillMaxWidth(),
                            text = insightState.message,
                            actionText = "重试",
                            isError = true,
                            onClick = { viewModel.generateInsight() },
                        )
                    }
                }
                is InsightState.Success -> {
                    Reader(
                        context = context,
                        subheadUpperCase = false,
                        link = readerState.link.orEmpty(),
                        content = insightState.insight,
                        onImageClick = null,
                        onLinkClick = { uriHandler.openUri(it) },
                    )
                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun InsightLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "正在分析文章，请稍候...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightEmptyState(
    modifier: Modifier = Modifier,
    text: String,
    actionText: String,
    isError: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onClick) {
            Text(text = actionText)
        }
    }
}
