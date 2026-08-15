package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.ui.component.base.Banner

@Composable
fun FeedsBanner(
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    filter: Filter,
    desc: String? = null,
    onClick: () -> Unit = {},
) {
    Banner(
        modifier = modifier,
        outerPadding = outerPadding,
        title = filter.toName(),
        desc = desc,
        icon = filter.iconOutline,
        action = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        onClick = onClick
    )
}
