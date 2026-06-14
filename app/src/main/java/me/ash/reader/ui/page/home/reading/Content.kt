package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.util.Date
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.webview.RYWebView
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.roundClick
import me.ash.reader.ui.page.adaptive.SummarizationState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Content(
    modifier: Modifier = Modifier,
    content: String,
    feedName: String,
    title: String,
    author: String? = null,
    link: String? = null,
    publishedDate: Date,
    scrollState: ScrollState,
    listState: LazyListState,
    isLoading: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    summarizationState: SummarizationState = SummarizationState.Idle,
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val renderer = LocalReadingRenderer.current

    val textContentWidth = LocalTextContentWidth.current
    val maxWidthModifier = Modifier.widthIn(max = textContentWidth)
    val uriHandler = LocalUriHandler.current

    val headline =
        @Composable {
            Column(modifier = Modifier.then(maxWidthModifier).padding(horizontal = 12.dp)) {
                DisableSelection {
                    Metadata(
                        feedName = feedName,
                        title = title,
                        author = author,
                        publishedDate = publishedDate,
                        modifier = Modifier.roundClick { link?.let { uriHandler.openUri(it) } },
                    )
                }
            }
        }
        
    // A simplified summary composable for non-LazyListScope (WebView mode)
    val simpleSummary = 
        @Composable {
            AnimatedVisibility(visible = summarizationState !is SummarizationState.Idle) {
                Surface(
                    modifier = Modifier
                        .then(maxWidthModifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Summary",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        when (summarizationState) {
                            is SummarizationState.Loading -> LoadingIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                            is SummarizationState.Success -> {
                                Text(
                                    text = summarizationState.summary, // Raw text (HTML) for now in WebView mode
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is SummarizationState.Error -> Text(
                                text = summarizationState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> {}
                        }
                    }
                }
            }
        }

    if (isLoading) {
        Column { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    } else {

        when (renderer) {
            ReadingRendererPreference.WebView -> {
                Column(
                    modifier =
                        modifier
                            .padding(top = contentPadding.calculateTopPadding())
                            .fillMaxSize()
                            .drawVerticalScrollIndicator(scrollState)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(modifier = Modifier.then(maxWidthModifier)) {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            headline()
                            
                            simpleSummary()

                            RYWebView(
                                modifier = Modifier.fillMaxSize(),
                                content = content,
                                refererDomain = link.extractDomain(),
                                onImageClick = onImageClick,
                            )
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }

            ReadingRendererPreference.NativeComponent -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = modifier.fillMaxSize().drawVerticalScrollIndicator(listState),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                            headline()
                        }
                        
                        // Summary Item
                        if (summarizationState !is SummarizationState.Idle) {
                             item {
                                Surface(
                                    modifier = Modifier
                                        .then(maxWidthModifier)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "AI Summary",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        when (summarizationState) {
                                            is SummarizationState.Loading -> LoadingIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                                            is SummarizationState.Error -> Text(
                                                text = (summarizationState as SummarizationState.Error).message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            is SummarizationState.Success -> {
                                                 // The actual content is rendered below via Reader to support HTML/Markdown.
                                                 // This block is kept empty for success state as content is rendered separately.
                                                 // Ideally we would want to render it here, but Reader requires LazyListScope.
                                            }
                                            else -> {} 
                                        }
                                    }
                                }
                            }

                            if (summarizationState is SummarizationState.Success) {
                                Reader(
                                    context = context,
                                    subheadUpperCase = false,
                                    link = "",
                                    content = (summarizationState as SummarizationState.Success).summary,
                                    onImageClick = null,
                                    onLinkClick = { uriHandler.openUri(it) }
                                )
                                
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        Reader(
                            context = context,
                            subheadUpperCase = subheadUpperCase.value,
                            link = link ?: "",
                            content = content,
                            onImageClick = onImageClick,
                            onLinkClick = { uriHandler.openUri(it) },
                        )

                        item {
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }
        }
    }
}
