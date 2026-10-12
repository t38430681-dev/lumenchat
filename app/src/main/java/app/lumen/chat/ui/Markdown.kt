package app.lumen.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Modelo e parser (Markdown leve, suficiente para respostas de chat)
// ---------------------------------------------------------------------------

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class ListBlock(val items: List<ListItem>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>, val hasHeader: Boolean) : MdBlock
    data object Rule : MdBlock
}

data class ListItem(val level: Int, val marker: String, val text: String)

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val ruleRe = Regex("^([-*_])(\\s*\\1){2,}$")
private val listRe = Regex("^(\\s*)([-*+]|\\d{1,3}[.)])\\s+(.*)$")
private val tableSepCell = Regex("^:?-{1,}:?$")

fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()

    fun flushParagraph() {
        if (para.isNotEmpty()) {
            out += MdBlock.Paragraph(para.toString().trimEnd())
            para.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append('\n')
                i++
            }
            out += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            i++ // fecha a cerca (ou passa do fim, se ainda estiver em streaming)
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        val heading = headingRe.matchEntire(trimmed)
        if (heading != null) {
            flushParagraph()
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        if (ruleRe.matches(trimmed)) {
            flushParagraph()
            out += MdBlock.Rule
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quote = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                if (quote.isNotEmpty()) quote.append('\n')
                quote.append(lines[i].trim().removePrefix(">").trim())
                i++
            }
            out += MdBlock.Quote(quote.toString())
            continue
        }

        if (trimmed.startsWith("|") && trimmed.length > 1) {
            flushParagraph()
            val rows = mutableListOf<List<String>>()
            var hasHeader = false
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                if (cells.all { tableSepCell.matches(it) }) {
                    hasHeader = rows.size == 1
                } else {
                    rows += cells
                }
                i++
            }
            if (rows.isNotEmpty()) out += MdBlock.Table(rows, hasHeader)
            continue
        }

        if (listRe.matches(line)) {
            flushParagraph()
            val items = mutableListOf<ListItem>()
            while (i < lines.size) {
                val current = lines[i]
                val match = listRe.matchEntire(current)
                if (match != null) {
                    val indent = match.groupValues[1].replace("\t", "    ").length
                    val rawMarker = match.groupValues[2]
                    val marker = when {
                        rawMarker.first().isDigit() -> rawMarker
                        indent >= 2 -> "◦"
                        else -> "•"
                    }
                    items += ListItem((indent / 2).coerceAtMost(3), marker, match.groupValues[3].trim())
                    i++
                } else if (
                    current.isNotBlank() && current.startsWith("  ") &&
                    items.isNotEmpty() && !current.trim().startsWith("```")
                ) {
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(text = last.text + "\n" + current.trim())
                    i++
                } else {
                    break
                }
            }
            out += MdBlock.ListBlock(items)
            continue
        }

        if (para.isNotEmpty()) para.append('\n')
        para.append(trimmed)
        i++
    }
    flushParagraph()
    return out
}

// ---------------------------------------------------------------------------
// Estilos inline: **negrito**, *itálico*, `código`, ~~riscado~~, [texto](url)
// ---------------------------------------------------------------------------

private fun AnnotatedString.Builder.appendInline(
    text: String,
    codeBackground: Color,
    linkStyle: SpanStyle,
) {
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]

        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 14.sp)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInline(text.substring(i + 2, end), codeBackground, linkStyle)
                }
                i = end + 2
                continue
            }
        }

        if (text.startsWith("~~", i)) {
            val end = text.indexOf("~~", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInline(text.substring(i + 2, end), codeBackground, linkStyle)
                }
                i = end + 2
                continue
            }
        }

        if (c == '*' && i + 1 < n && !text[i + 1].isWhitespace() && text[i + 1] != '*') {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1 && !text[end - 1].isWhitespace()) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInline(text.substring(i + 1, end), codeBackground, linkStyle)
                }
                i = end + 1
                continue
            }
        }

        if (c == '[') {
            val mid = text.indexOf("](", i + 1)
            if (mid > i) {
                val close = text.indexOf(')', mid + 2)
                if (close > mid) {
                    val label = text.substring(i + 1, mid)
                    val url = text.substring(mid + 2, close).trim()
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) {
                            append(label)
                        }
                        i = close + 1
                        continue
                    }
                }
            }
        }

        append(c)
        i++
    }
}

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val annotated = remember(text, scheme) {
        buildAnnotatedString {
            appendInline(
                text = text,
                codeBackground = scheme.surfaceContainerHighest,
                linkStyle = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline),
            )
        }
    }
    Text(text = annotated, style = style, modifier = modifier, color = color)
}

// ---------------------------------------------------------------------------
// Renderização
// ---------------------------------------------------------------------------

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                when (block) {
                    is MdBlock.Paragraph -> InlineText(block.text, type.bodyLarge)

                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> type.headlineSmall
                            2 -> type.titleLarge
                            3 -> type.titleMedium
                            else -> type.titleSmall
                        }
                        InlineText(block.text, style.copy(fontWeight = FontWeight.SemiBold))
                    }

                    is MdBlock.Code -> CodeBlock(block.lang, block.code)

                    is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(Modifier.padding(start = (item.level * 16).dp)) {
                                Text(
                                    text = item.marker,
                                    style = type.bodyLarge,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.width(if (item.marker.length > 2) 30.dp else 22.dp),
                                )
                                InlineText(item.text, type.bodyLarge, Modifier.weight(1f))
                            }
                        }
                    }

                    is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(scheme.outlineVariant, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        InlineText(block.text, type.bodyLarge, color = scheme.onSurfaceVariant)
                    }

                    is MdBlock.Table -> TableBlock(block)

                    MdBlock.Rule -> HorizontalDivider(color = scheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = lang.ifBlank { "código" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { copyToClipboard(context, code) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copiar código",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = code,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun TableBlock(table: MdBlock.Table) {
    val scheme = MaterialTheme.colorScheme
    val columns = table.rows.maxOf { it.size }
    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            val isHeader = table.hasHeader && rowIndex == 0
            Row(
                modifier = Modifier.background(if (isHeader) scheme.surfaceContainerHigh else Color.Transparent),
            ) {
                for (col in 0 until columns) {
                    InlineText(
                        text = row.getOrElse(col) { "" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        modifier = Modifier
                            .width(140.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (rowIndex < table.rows.lastIndex) HorizontalDivider(color = scheme.outlineVariant)
        }
    }
}
