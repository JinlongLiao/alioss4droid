/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jinlongliao.ossbrowser.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 应用内轻量 Markdown 渲染器（零 Markdown 库依赖，用于隐私政策 / 开源声明 /
 * `.md` 对象预览等场景）。
 *
 * 覆盖语法：
 *  - 标题：ATX `#`…`######`、Setext `====` / `----` 下划线式
 *  - 段落：换行合并为段落；行尾两个空格强制换行
 *  - 列表：`-` / `*` / `+` 无序、`1.` 有序，支持按缩进嵌套（2 空格一级）
 *  - 任务列表：`- [ ]` / `- [x]`
 *  - 引用块：连续 `>` 合并为多行引用
 *  - 围栏代码块：``` / ~~~，可附语言名（仅做标签保留）
 *  - 水平分隔：`---` / `***` / `___`
 *  - 图片：整段 `![alt](url)` 渲染为块级图片（Coil 异步加载）
 *  - 表格：GFM 管道语法 + 对齐行 `|:---|:---:|---:|`
 *  - 行内：`**B**` / `*I*` / `***BI***` / `~~S~~` / `` `code` `` /
 *    `[text](url)` / `![alt](url)` / `<http…>` / bare URL / `\X` 转义
 *  - 参考式链接：`[id]: url` 定义 + `[text][id]` / `[id]` 引用
 *
 * 刻意保持为单文件实现，不引入 markdown parser 依赖；在 CommonMark 的基础上
 * 做了务实裁剪（不处理嵌套引用、HTML 块、定义列表、脚注等冷门语法）。
 *
 * @author  jinlongliao (https://github.com/JinlongLiao/)
 * @since   2026-04-24
 */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier
) {
    val parsed = remember(source) { parseDocument(source) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val codeBg = colors.surfaceVariant
    val codeFg = colors.onSurfaceVariant
    val linkColor = colors.primary
    val ctx = InlineContext(parsed.refs, linkColor, codeBg, codeFg)

    Column(modifier = modifier) {
        parsed.blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Heading -> HeadingBlock(
                    first = idx == 0,
                    block = block,
                    ctx = ctx,
                    typography = typography,
                    color = colors.onSurface
                )

                is MdBlock.Paragraph -> {
                    Text(
                        text = inline(block.text, ctx),
                        style = typography.bodyMedium,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.ListItem -> ListItemBlock(
                    block = block,
                    ctx = ctx,
                    leadingColor = linkColor,
                    textColor = colors.onSurface
                )

                is MdBlock.Quote -> QuoteBlock(
                    block = block,
                    ctx = ctx,
                    typography = typography,
                    colors = colors
                )

                is MdBlock.Code -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(codeBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = block.text,
                            style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = codeFg,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.Table -> TableBlock(
                    block = block,
                    ctx = ctx,
                    typography = typography,
                    colors = colors
                )

                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

// ---------- rendering ----------

@Composable
private fun HeadingBlock(
    first: Boolean,
    block: MdBlock.Heading,
    ctx: InlineContext,
    typography: Typography,
    color: Color
) {
    val base = when (block.level) {
        1 -> typography.headlineSmall
        2 -> typography.titleLarge
        3 -> typography.titleMedium
        4 -> typography.titleSmall
        5 -> typography.bodyLarge
        else -> typography.bodyMedium
    }
    if (!first) Spacer(Modifier.height(14.dp))
    Text(
        text = inline(block.text, ctx),
        style = base.copy(fontWeight = FontWeight.SemiBold),
        color = color
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ListItemBlock(
    block: MdBlock.ListItem,
    ctx: InlineContext,
    leadingColor: Color,
    textColor: Color
) {
    val leading = when {
        block.checked == true -> "☑"
        block.checked == false -> "☐"
        block.ordered -> "${block.index ?: "1"}."
        else -> when (block.indent) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }
    }
    val startPad = (4 + 18 * block.indent).dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = startPad, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = leading,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = leadingColor,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = inline(block.text, ctx),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
private fun QuoteBlock(
    block: MdBlock.Quote,
    ctx: InlineContext,
    typography: Typography,
    colors: ColorScheme
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height((block.lines.size * 22).coerceAtLeast(20).dp)
                .background(colors.primary.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            block.lines.forEach { line ->
                Text(
                    text = inline(line, ctx),
                    style = typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TableBlock(
    block: MdBlock.Table,
    ctx: InlineContext,
    typography: Typography,
    colors: ColorScheme
) {
    val border = BorderStroke(1.dp, colors.outlineVariant)
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(border, shape)
            .clip(shape)
    ) {
        val colCount = block.headers.size.coerceAtLeast(1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
        ) {
            for (j in 0 until colCount) {
                TableCell(
                    text = block.headers.getOrElse(j) { "" },
                    align = block.aligns.getOrElse(j) { MdAlign.NONE },
                    ctx = ctx,
                    style = typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        block.rows.forEachIndexed { idx, row ->
            Row(Modifier.fillMaxWidth()) {
                for (j in 0 until colCount) {
                    TableCell(
                        text = row.getOrElse(j) { "" },
                        align = block.aligns.getOrElse(j) { MdAlign.NONE },
                        ctx = ctx,
                        style = typography.bodyMedium,
                        color = colors.onSurface
                    )
                }
            }
            if (idx != block.rows.lastIndex) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RowScope.TableCell(
    text: String,
    align: MdAlign,
    ctx: InlineContext,
    style: TextStyle,
    color: Color
) {
    val textAlign = when (align) {
        MdAlign.CENTER -> TextAlign.Center
        MdAlign.RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }
    Text(
        text = inline(text, ctx),
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

// ---------- data model ----------

internal enum class MdAlign { NONE, LEFT, CENTER, RIGHT }

internal sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(
        val ordered: Boolean,
        val index: String?,
        val indent: Int,
        val text: String,
        /** null 表示普通列表项；`true` / `false` 分别对应 `[x]` / `[ ]` 任务项。 */
        val checked: Boolean?
    ) : MdBlock()

    data class Quote(val lines: List<String>) : MdBlock()
    data class Code(val lang: String, val text: String) : MdBlock()
    data class Image(val alt: String, val url: String) : MdBlock()
    data class Table(
        val aligns: List<MdAlign>,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : MdBlock()

    object Rule : MdBlock()
}

internal data class ParsedMarkdown(
    val blocks: List<MdBlock>,
    val refs: Map<String, String>
)

internal data class InlineContext(
    val refs: Map<String, String>,
    val linkColor: Color,
    val codeBg: Color,
    val codeFg: Color
)

// ---------- parsing ----------

private val fenceStart = Regex("""^(\s*)(```+|~~~+)\s*([\w.+-]*)\s*$""")
private val atxHeading = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val horizontalRule = Regex("""^\s{0,3}([-*_])\s*(\1\s*){2,}$""")
private val listItemPattern = Regex("""^( {0,12})([-*+]|\d+\.)\s+(.*)$""")
private val taskBox = Regex("""^\[( |x|X)]\s+(.*)$""")
private val tableSeparator =
    Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val imageOnly = Regex("""^!\[([^\]]*)]\(\s*(\S+?)\s*(?:"[^"]*")?\s*\)$""")
private val refDefinition =
    Regex("""^\s{0,3}\[([^\]]+)]:\s*<?(\S+?)>?\s*(?:"[^"]*"|'[^']*'|\([^)]*\))?\s*$""")
private val autoUrl = Regex("""https?://[^\s)<>]+""")

private fun parseDocument(source: String): ParsedMarkdown {
    val rawLines = source.replace("\r\n", "\n").replace("\r", "\n").split("\n")

    // pass 1: extract reference-style link definitions, keep remaining lines
    val refs = HashMap<String, String>()
    val cleaned = ArrayList<String>(rawLines.size)
    for (line in rawLines) {
        val m = refDefinition.matchEntire(line)
        if (m != null) {
            refs[m.groupValues[1].trim().lowercase()] = m.groupValues[2]
        } else {
            cleaned.add(line)
        }
    }

    // pass 2: block-level parse
    val blocks = parseBlocks(cleaned)
    return ParsedMarkdown(blocks, refs)
}

private fun parseBlocks(lines: List<String>): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            val text = paragraph.toString().trim()
            val img = imageOnly.matchEntire(text)
            if (img != null) {
                out += MdBlock.Image(img.groupValues[1], img.groupValues[2])
            } else if (text.isNotEmpty()) {
                out += MdBlock.Paragraph(text)
            }
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        // blank line → paragraph boundary
        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // fenced code block
        val fence = fenceStart.matchEntire(raw)
        if (fence != null) {
            flushParagraph()
            val marker = fence.groupValues[2]
            val lang = fence.groupValues[3]
            val buf = StringBuilder()
            i++
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.startsWith(marker[0].toString().repeat(3)) &&
                    cur.all { it == marker[0] || it.isWhitespace() }
                ) {
                    i++
                    break
                }
                buf.append(lines[i]).append('\n')
                i++
            }
            out += MdBlock.Code(lang, buf.toString().trimEnd('\n'))
            continue
        }

        // ATX heading `#`…`######`
        val atx = atxHeading.matchEntire(trimmed)
        if (atx != null) {
            flushParagraph()
            out += MdBlock.Heading(atx.groupValues[1].length, atx.groupValues[2].trim())
            i++
            continue
        }

        // Setext heading: previous paragraph line + `===…` / `---…`
        if (paragraph.isNotEmpty() &&
            !paragraph.toString().contains('\n') &&
            trimmed.length >= 3 &&
            (trimmed.all { it == '=' } || trimmed.all { it == '-' })
        ) {
            val headingText = paragraph.toString().trim()
            paragraph.clear()
            val level = if (trimmed[0] == '=') 1 else 2
            out += MdBlock.Heading(level, headingText)
            i++
            continue
        }

        // horizontal rule (after setext check, since `---` is ambiguous)
        if (horizontalRule.matches(raw)) {
            flushParagraph()
            out += MdBlock.Rule
            i++
            continue
        }

        // block quote (consecutive `>` lines)
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val qLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                val content = lines[i].trim().removePrefix(">").let {
                    if (it.startsWith(" ")) it.substring(1) else it
                }
                qLines.add(content)
                i++
            }
            out += MdBlock.Quote(qLines)
            continue
        }

        // table (GFM): header row + separator row
        if (trimmed.contains('|') && i + 1 < lines.size &&
            tableSeparator.matches(lines[i + 1])
        ) {
            flushParagraph()
            val headers = splitTableRow(trimmed)
            val aligns = splitTableRow(lines[i + 1].trim()).map { cell ->
                val l = cell.startsWith(":")
                val r = cell.endsWith(":")
                when {
                    l && r -> MdAlign.CENTER
                    r -> MdAlign.RIGHT
                    l -> MdAlign.LEFT
                    else -> MdAlign.NONE
                }
            }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.isEmpty() || !cur.contains('|')) break
                rows += splitTableRow(cur)
                i++
            }
            out += MdBlock.Table(aligns, headers, rows)
            continue
        }

        // list item (bullet / ordered; possibly nested via leading spaces)
        val list = listItemPattern.matchEntire(raw)
        if (list != null) {
            flushParagraph()
            val leadingSpaces = list.groupValues[1].length
            val marker = list.groupValues[2]
            var rest = list.groupValues[3]
            val ordered = marker.endsWith(".")
            val index = if (ordered) marker.dropLast(1) else null
            val indent = (leadingSpaces / 2).coerceAtMost(5)
            var checked: Boolean? = null
            val task = taskBox.matchEntire(rest)
            if (task != null) {
                checked = task.groupValues[1].equals("x", ignoreCase = true)
                rest = task.groupValues[2]
            }
            // merge continuation lines indented beyond the marker column
            val buf = StringBuilder(rest.trim())
            i++
            while (i < lines.size) {
                val next = lines[i]
                if (next.isBlank()) break
                val nextLead = next.takeWhile { it == ' ' }.length
                val nextTrim = next.trim()
                val isNextListItem = listItemPattern.matchEntire(next) != null
                val isNextHeading = atxHeading.matches(nextTrim) || fenceStart.matchEntire(next) != null
                if (nextLead > leadingSpaces && !isNextListItem && !isNextHeading) {
                    buf.append(' ').append(nextTrim)
                    i++
                } else break
            }
            out += MdBlock.ListItem(ordered, index, indent, buf.toString(), checked)
            continue
        }

        // default: accumulate paragraph (preserve hard-break: trailing two spaces → \n)
        val hardBreak = raw.endsWith("  ")
        if (paragraph.isNotEmpty()) paragraph.append(if (hardBreak) '\n' else ' ')
        paragraph.append(trimmed)
        i++
    }
    flushParagraph()
    return out
}

/** 切分 GFM 表格的一行，处理 `\|` 转义与前后管道符。 */
private fun splitTableRow(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val buf = StringBuilder()
    var k = 0
    while (k < trimmed.length) {
        val ch = trimmed[k]
        if (ch == '\\' && k + 1 < trimmed.length && trimmed[k + 1] == '|') {
            buf.append('|')
            k += 2
        } else if (ch == '|') {
            cells += buf.toString().trim()
            buf.clear()
            k++
        } else {
            buf.append(ch)
            k++
        }
    }
    cells += buf.toString().trim()
    return cells
}

// ---------- inline parsing ----------

private fun inline(text: String, ctx: InlineContext): AnnotatedString = buildAnnotatedString {
    appendInline(text, ctx)
}

/** 在已有 Builder 作用域内继续递归解析内联标记，供 `**粗体里再加 *斜*` 这类嵌套使用。 */
private fun AnnotatedString.Builder.appendInline(text: String, ctx: InlineContext) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = ctx.linkColor, textDecoration = TextDecoration.Underline)
    )
    var i = 0
    while (i < text.length) {
        val ch = text[i]

        // `\X` → literal X (applies only to ASCII punctuation; keep simple)
        if (ch == '\\' && i + 1 < text.length && !text[i + 1].isLetterOrDigit()) {
            append(text[i + 1])
            i += 2
            continue
        }

        // hard line break: trailing two spaces already converted to '\n' by parser,
        // here just pass through newlines.
        if (ch == '\n') {
            append('\n')
            i++
            continue
        }

        // code span `…`
        if (ch == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > 0) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = ctx.codeBg,
                        color = ctx.codeFg
                    )
                ) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }

        // image ![alt](url)
        if (ch == '!' && i + 1 < text.length && text[i + 1] == '[') {
            val close = text.indexOf(']', i + 2)
            if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                val rp = text.indexOf(')', close + 2)
                if (rp > 0) {
                    val alt = text.substring(i + 2, close)
                    val url = text.substring(close + 2, rp).trim().trim('<', '>')
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                        append("\uD83D\uDDBC ")
                        append(alt.ifBlank { "image" })
                    }
                    i = rp + 1
                    continue
                }
            }
        }

        // link [text](url) or reference [text][id] / collapsed [id]
        if (ch == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > 0) {
                val label = text.substring(i + 1, close)
                // inline [text](url)
                if (close + 1 < text.length && text[close + 1] == '(') {
                    val rp = text.indexOf(')', close + 2)
                    if (rp > 0) {
                        val url = text.substring(close + 2, rp).trim().trim('<', '>')
                        withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                            appendInline(label, ctx)
                        }
                        i = rp + 1
                        continue
                    }
                }
                // reference [text][id]
                if (close + 1 < text.length && text[close + 1] == '[') {
                    val closeId = text.indexOf(']', close + 2)
                    if (closeId > 0) {
                        val id = text.substring(close + 2, closeId).ifBlank { label }
                        val url = ctx.refs[id.trim().lowercase()]
                        if (url != null) {
                            withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                                appendInline(label, ctx)
                            }
                            i = closeId + 1
                            continue
                        }
                    }
                }
                // collapsed / shortcut [id]
                val url = ctx.refs[label.trim().lowercase()]
                if (url != null) {
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                        appendInline(label, ctx)
                    }
                    i = close + 1
                    continue
                }
            }
        }

        // autolink <http…>
        if (ch == '<') {
            val rp = text.indexOf('>', i + 1)
            if (rp > 0) {
                val body = text.substring(i + 1, rp)
                if (body.startsWith("http://") || body.startsWith("https://")) {
                    withLink(LinkAnnotation.Url(url = body, styles = linkStyle)) { append(body) }
                    i = rp + 1
                    continue
                }
            }
        }

        // bold-italic ***…*** / ___…___
        if ((ch == '*' || ch == '_') && i + 2 < text.length &&
            text[i + 1] == ch && text[i + 2] == ch
        ) {
            val marker = "$ch$ch$ch"
            val end = text.indexOf(marker, i + 3)
            if (end > 0) {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                ) { appendInline(text.substring(i + 3, end), ctx) }
                i = end + 3
                continue
            }
        }

        // bold **…** / __…__
        if ((ch == '*' || ch == '_') && i + 1 < text.length && text[i + 1] == ch) {
            val marker = "$ch$ch"
            val end = text.indexOf(marker, i + 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInline(text.substring(i + 2, end), ctx)
                }
                i = end + 2
                continue
            }
        }

        // italic *…* / _…_  (reject empty / leading-space content to avoid false positives)
        if (ch == '*' || ch == '_') {
            val end = text.indexOf(ch, i + 1)
            if (end > i + 1) {
                val inner = text.substring(i + 1, end)
                val okBoundary = (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                    (end + 1 >= text.length || !text[end + 1].isLetterOrDigit() || ch == '*')
                if (inner.isNotBlank() && !inner.startsWith(' ') && !inner.endsWith(' ') && okBoundary) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(inner, ctx)
                    }
                    i = end + 1
                    continue
                }
            }
        }

        // strikethrough ~~…~~
        if (ch == '~' && i + 1 < text.length && text[i + 1] == '~') {
            val end = text.indexOf("~~", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInline(text.substring(i + 2, end), ctx)
                }
                i = end + 2
                continue
            }
        }

        // bare URL autolink
        if (ch == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
            val m = autoUrl.find(text, i)
            if (m != null && m.range.first == i) {
                val url = m.value.trimEnd('.', ',', ';', ':', '!', '?')
                withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) { append(url) }
                i += url.length
                continue
            }
        }

        append(ch)
        i++
    }
}
