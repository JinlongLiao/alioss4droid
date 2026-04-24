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
package io.github.jinlongliao.ossbrowser.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 零依赖 DOCX 文本抽取：
 *  - `.docx` 本质是 ZIP，正文位于 `word/document.xml`（OOXML WordprocessingML 命名空间）。
 *  - 仅遍历 `<w:p>`（段落）/ `<w:t>`（文本 run）/ `<w:tab>` / `<w:br>` 几个核心元素，
 *    对格式、图片、表格、公式一律丢弃，保留段落切分即可满足「预览纯文本」诉求。
 *
 * 对非法 / 加密 / 扫描型 docx 返回 null，让上层回退到「不支持」状态。
 *
 * @author  jinlongliao (https://github.com/JinlongLiao/)
 * @since   2026-04-24
 */
object DocxText {

    private const val DOCUMENT_ENTRY = "word/document.xml"
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /**
     * 从 docx 字节抽取段落列表。
     *
     * - 每个 `<w:p>` 映射一个段落；内含的多个 `<w:t>` 按出现顺序拼接。
     * - `<w:tab/>` 转成 `\t`，`<w:br/>` 转成 `\n`。
     * - 连续空白段落合并为单行，避免生成大量空行。
     *
     * @return 段落列表；解析失败或找不到正文时返回 null。
     */
    fun extractParagraphs(bytes: ByteArray): List<String>? {
        val xml = readDocumentXml(bytes) ?: return null
        return runCatching { parseParagraphs(xml) }.getOrNull()
    }

    /**
     * 在 ZIP 中定位并读出 `word/document.xml` 的原始字节（最大 32MB，防止异常 docx 造成 OOM）。
     */
    private fun readDocumentXml(bytes: ByteArray): ByteArray? {
        val maxEntryBytes = 32L * 1024 * 1024
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals(DOCUMENT_ENTRY, ignoreCase = true)) {
                    val buf = ByteArray(64 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0L
                    while (true) {
                        val n = zin.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > maxEntryBytes) return null
                        out.write(buf, 0, n)
                    }
                    return out.toByteArray()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return null
    }

    private fun parseParagraphs(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var inParagraph = false
        var inText = false

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            val ns = parser.namespace
            val name = parser.name
            when (event) {
                XmlPullParser.START_TAG -> if (ns == W_NS) {
                    when (name) {
                        "p" -> {
                            inParagraph = true
                            current.setLength(0)
                        }
                        "t" -> inText = true
                        "tab" -> if (inParagraph) current.append('\t')
                        "br", "cr" -> if (inParagraph) current.append('\n')
                    }
                }

                XmlPullParser.TEXT -> if (inText) {
                    current.append(parser.text ?: "")
                }

                XmlPullParser.END_TAG -> if (ns == W_NS) {
                    when (name) {
                        "t" -> inText = false
                        "p" -> {
                            inParagraph = false
                            paragraphs += current.toString()
                            current.setLength(0)
                        }
                    }
                }
            }
        }
        // 合并连续空段落，最多保留单个空行间隔；开头/结尾的空段落去除
        val compact = mutableListOf<String>()
        var prevBlank = true
        for (p in paragraphs) {
            val blank = p.isBlank()
            if (blank && prevBlank) continue
            compact += p
            prevBlank = blank
        }
        while (compact.isNotEmpty() && compact.last().isBlank()) compact.removeAt(compact.lastIndex)
        return compact
    }
}
