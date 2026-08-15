package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.ui.component.base.Banner

private const val PRIMARY_DESTINATION_FRACTION = 0.8f

@Composable
internal fun StarredNavigationCarousel(
    filter: Filter,
    starredDescription: String,
    onStarredClick: () -> Unit,
    onSavedKnowledgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = maxWidth * PRIMARY_DESTINATION_FRACTION
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            item(key = "starred-articles") {
                FeedsBanner(
                    modifier = Modifier.width(cardWidth),
                    outerPadding = PaddingValues(0.dp),
                    filter = filter,
                    desc = starredDescription,
                    onClick = onStarredClick,
                )
            }
            item(key = "saved-knowledge-answers") {
                Banner(
                    modifier = Modifier.width(cardWidth),
                    outerPadding = PaddingValues(0.dp),
                    title = "已保存的知识库回答",
                    desc = "查看你保存的问题与答案",
                    icon = Icons.Rounded.Bookmarks,
                    action = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    onClick = onSavedKnowledgeClick,
                )
            }
        }
    }
}
