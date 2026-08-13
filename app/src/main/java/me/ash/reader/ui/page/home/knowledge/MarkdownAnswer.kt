package me.ash.reader.ui.page.home.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension

@Composable
fun MarkdownAnswer(markdown: String, modifier: Modifier = Modifier) {
    val document = remember(markdown) {
        Parser.builder()
            .extensions(listOf(TablesExtension.create()))
            .build()
            .parse(markdown) as Document
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var node = document.firstChild
        while (node != null) {
            MarkdownBlock(node)
            node = node.next
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node, listIndex: Int? = null) {
    when (node) {
        is Heading -> MarkdownInline(
            node,
            style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
        )
        is Paragraph -> MarkdownInline(node, prefix = listIndex?.let { "$it. " })
        is FencedCodeBlock -> CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, null)
        is BlockQuote -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary)) {
                Spacer(Modifier.width(3.dp))
                Column(
                    Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RenderChildren(node)
                }
            }
        }
        is BulletList -> RenderList(node, ordered = false)
        is OrderedList -> RenderList(node, ordered = true, start = node.startNumber)
        is ThematicBreak -> HorizontalDivider()
        is TableBlock -> MarkdownTable(node)
        else -> RenderChildren(node)
    }
}

@Composable
private fun MarkdownTable(table: TableBlock) {
    val rows = buildList<TableRow> {
        var section = table.firstChild
        while (section != null) {
            var row = section.firstChild
            while (row != null) {
                if (row is TableRow) add(row)
                row = row.next
            }
            section = section.next
        }
    }
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.width((rows.maxOf { countCells(it) } * 150).dp)) {
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) {
                        Surface(
                            color = if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            MarkdownInline(
                                cell,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else null,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            )
                        }
                    }
                    cell = cell.next
                }
            }
        }
    }
}

private fun countCells(row: TableRow): Int {
    var count = 0
    var cell = row.firstChild
    while (cell != null) {
        if (cell is TableCell) count++
        cell = cell.next
    }
    return count
}

@Composable
private fun RenderList(node: Node, ordered: Boolean, start: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var child = node.firstChild
        var index = start
        while (child != null) {
            if (child is ListItem) {
                Row(Modifier.fillMaxWidth()) {
                    Text(if (ordered) "${index++}." else "•", modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RenderChildren(child)
                    }
                }
            }
            child = child.next
        }
    }
}

@Composable
private fun RenderChildren(parent: Node) {
    var child = parent.firstChild
    while (child != null) {
        MarkdownBlock(child)
        child = child.next
    }
}

@Composable
private fun MarkdownInline(
    node: Node,
    prefix: String? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(node, prefix, primary, codeBackground) {
        buildAnnotatedString {
            prefix?.let(::append)
            appendInline(node, primary, codeBackground)
        }
    }
    val uriHandler = LocalUriHandler.current
    ClickableText(
        modifier = modifier,
        text = annotated,
        style = style.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = fontWeight,
            lineHeight = style.lineHeight * 1.25f,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                runCatching { uriHandler.openUri(it.item) }
            }
        },
    )
}

private fun AnnotatedString.Builder.appendInline(node: Node, linkColor: Color, codeBackground: Color) {
    var child = node.firstChild
    while (child != null) {
        val current = child
        when (current) {
            is MarkdownText -> append(current.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(current, linkColor, codeBackground)
            }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInline(current, linkColor, codeBackground)
            }
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(current.literal)
            }
            is Link -> {
                pushStringAnnotation("URL", current.destination)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    appendInline(current, linkColor, codeBackground)
                }
                pop()
            }
            else -> appendInline(current, linkColor, codeBackground)
        }
        child = current.next
    }
}

@Composable
private fun CodeBlock(code: String, language: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth()) {
            language?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            Text(
                code.trimEnd(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
            )
        }
    }
}
