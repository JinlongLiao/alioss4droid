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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染器（仅覆盖项目内隐私政策 / 开源声明所用的语法子集）。
 *
 * 支持：
 *  - 标题 `#`, `##`, `###`
 *  - 无序列表 `- ` / `* `
 *  - 有序列表 `1. `
 *  - 引用块 `> `
 *  - 围栏代码块 ``` ``` ```
 *  - 水平分割线 `---` / `***` / `___`
 *  - 行内：**粗体** / *斜体* / `代码` / [链接](https://...) / 行内 URL 自动识别
 *
 * 刻意保持小而稳：不依赖第三方库，~250 行，满足应用内合规文档展示即可。
 *
 * @author  jinlongliao (https://github.com/JinlongLiao/)
 * @since   2026-04-24
 */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(source) { parseBlocks(source) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val codeBg = colors.surfaceVariant
    val codeFg = colors.onSurfaceVariant
    val linkColor = colors.primary

    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> typography.headlineSmall
                        2 -> typography.titleLarge
                        else -> typography.titleMedium
                    }
                    if (idx != 0) Spacer(Modifier.height(14.dp))
                    Text(
                        text = inline(block.text, linkColor, codeBg, codeFg),
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = inline(block.text, linkColor, codeBg, codeFg),
                        style = typography.bodyMedium,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.BulletItem -> ListItemRow(
                    leading = "•",
                    leadingColor = linkColor,
                    content = inline(block.text, linkColor, codeBg, codeFg),
                    textColor = colors.onSurface
                )

                is MdBlock.NumberedItem -> ListItemRow(
                    leading = "${block.index}.",
                    leadingColor = linkColor,
                    content = inline(block.text, linkColor, codeBg, codeFg),
                    textColor = colors.onSurface
                )

                is MdBlock.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(colors.primary.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inline(block.text, linkColor, codeBg, codeFg),
                            style = typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.CodeBlock -> {
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

                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ListItemRow(
    leading: String,
    leadingColor: Color,
    content: AnnotatedString,
    textColor: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = leading,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = leadingColor,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------- block parsing ----------

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class BulletItem(val text: String) : MdBlock()
    data class NumberedItem(val index: String, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class CodeBlock(val text: String) : MdBlock()
    object Rule : MdBlock()
}

private val numberedPrefix = Regex("""^(\d+)\.\s+(.*)$""")

private fun parseBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val out = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            out += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i].trimEnd()
        when {
            raw.isBlank() -> {
                flushParagraph(); i++
            }
            raw == "---" || raw == "***" || raw == "___" -> {
                flushParagraph(); out += MdBlock.Rule; i++
            }
            raw.startsWith("```") -> {
                flushParagraph()
                val buf = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                    buf.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++
                out += MdBlock.CodeBlock(buf.toString().trimEnd('\n'))
            }
            raw.startsWith("### ") -> {
                flushParagraph(); out += MdBlock.Heading(3, raw.substring(4).trim()); i++
            }
            raw.startsWith("## ") -> {
                flushParagraph(); out += MdBlock.Heading(2, raw.substring(3).trim()); i++
            }
            raw.startsWith("# ") -> {
                flushParagraph(); out += MdBlock.Heading(1, raw.substring(2).trim()); i++
            }
            raw.startsWith("> ") -> {
                flushParagraph(); out += MdBlock.Quote(raw.substring(2).trim()); i++
            }
            raw.startsWith("- ") || raw.startsWith("* ") -> {
                flushParagraph()
                val buf = StringBuilder(raw.substring(2).trim())
                i++
                // 续行：以 2~4 空格缩进的非空行视为同一条目的续行
                while (i < lines.size) {
                    val next = lines[i]
                    if (next.isBlank()) break
                    val nextTrim = next.trimEnd()
                    val leading = next.takeWhile { it == ' ' }.length
                    val nextStart = nextTrim.trimStart()
                    if (leading in 1..6 &&
                        !nextStart.startsWith("- ") && !nextStart.startsWith("* ") &&
                        !numberedPrefix.containsMatchIn(nextStart)
                    ) {
                        buf.append(' ').append(nextStart)
                        i++
                    } else break
                }
                out += MdBlock.BulletItem(buf.toString())
            }
            numberedPrefix.containsMatchIn(raw) -> {
                flushParagraph()
                val m = numberedPrefix.find(raw)!!
                out += MdBlock.NumberedItem(m.groupValues[1], m.groupValues[2].trim())
                i++
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(raw.trim())
                i++
            }
        }
    }
    flushParagraph()
    return out
}

// ---------- inline parsing ----------

private val autoUrl = Regex("""https?://[^\s)]+""")

private fun inline(
    text: String,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(text.substring(i + 2, end), linkColor, codeBg, codeFg)
                    }
                    i = end + 2
                } else {
                    append(ch); i++
                }
            }
            (ch == '*' || ch == '_') && i + 1 < text.length && text[i + 1] != ch -> {
                val end = text.indexOf(ch, i + 1)
                if (end > 0 && end != i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(i + 1, end), linkColor, codeBg, codeFg)
                    }
                    i = end + 1
                } else {
                    append(ch); i++
                }
            }
            ch == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            color = codeFg
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(ch); i++
                }
            }
            ch == '[' -> {
                val close = text.indexOf(']', i + 1)
                if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val rp = text.indexOf(')', close + 2)
                    if (rp > 0) {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, rp)
                        withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                            appendInline(label, linkColor, codeBg, codeFg)
                        }
                        i = rp + 1
                        continue
                    }
                }
                append(ch); i++
            }
            ch == 'h' && text.startsWith("http", i) -> {
                val m = autoUrl.find(text, i)
                if (m != null && m.range.first == i) {
                    val url = m.value
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyle)) {
                        append(url)
                    }
                    i = m.range.last + 1
                } else {
                    append(ch); i++
                }
            }
            else -> {
                append(ch); i++
            }
        }
    }
}

/** 在已有 SpanStyle 作用域内继续递归解析内联标记。 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    text: String,
    linkColor: Color,
    codeBg: Color,
    codeFg: Color
) {
    append(inline(text, linkColor, codeBg, codeFg))
}
