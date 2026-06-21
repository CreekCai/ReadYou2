package me.ash.reader.ui.component.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanBeDisabledIconButton(
    modifier: Modifier = Modifier,
    disabled: Boolean,
    imageVector: ImageVector? = null,
    icon: @Composable () -> Unit = {},
    size: Dp = 24.dp,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    if (onLongClick != null) {
        Box(
            modifier =
                modifier
                    .alpha(if (disabled) 0.38f else 1f)
                    .combinedClickable(
                        enabled = !disabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageVector != null) {
                Icon(
                    modifier = Modifier.size(size),
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = if (disabled) MaterialTheme.colorScheme.outline else tint,
                )
            } else {
                icon()
            }
        }
        return
    }

    IconButton(
        modifier = modifier,
        enabled = !disabled,
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = MaterialTheme.colorScheme.outline
        )
    ) {
        if (imageVector != null) {
            Icon(
                modifier = Modifier.size(size),
                imageVector = imageVector,
                contentDescription = contentDescription,
            )
        } else {
            icon()
        }
    }
}
