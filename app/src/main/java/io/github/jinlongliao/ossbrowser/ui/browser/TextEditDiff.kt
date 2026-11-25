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
package io.github.jinlongliao.ossbrowser.ui.browser

import java.nio.charset.Charset

/**
 * 生成文本确认页使用的逐行差异。保留换行符，便于检查末尾换行和 CRLF 变化。
 * 先去掉公共首尾；大范围修改超过 LCS 预算时，以完整的删除及新增行显示中段。
 */
internal fun compareTextEditLines(original: String, draft: String): List<String> {
    val before = original.splitToSequence('\n').toList()
    val after = draft.splitToSequence('\n').toList()
    val commonSize = minOf(before.size, after.size)
    var start = 0
    while (start < commonSize && before[start] == after[start]) {
        start++
    }
    var end = 0
    while (end < commonSize - start && before[before.lastIndex - end] == after[after.lastIndex - end]) {
        end++
    }
    if (start == before.size && start == after.size) {
        return emptyList()
    }

    val removed = before.subList(start, before.size - end)
    val added = after.subList(start, after.size - end)
    val diff = mutableListOf<String>()
    before.subList(maxOf(0, start - 3), start).forEach { diff += "  ${it.visibleDiffLine()}" }
    if (removed.isNotEmpty() && added.isNotEmpty() && removed.size.toLong() * added.size <= 250_000) {
        val lengths = Array(removed.size + 1) { IntArray(added.size + 1) }
        for (oldIndex in removed.indices.reversed()) {
            for (newIndex in added.indices.reversed()) {
                lengths[oldIndex][newIndex] = if (removed[oldIndex] == added[newIndex]) {
                    1 + lengths[oldIndex + 1][newIndex + 1]
                } else {
                    maxOf(lengths[oldIndex + 1][newIndex], lengths[oldIndex][newIndex + 1])
                }
            }
        }
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < removed.size && newIndex < added.size) {
            when {
                removed[oldIndex] == added[newIndex] -> {
                    diff += "  ${removed[oldIndex++].visibleDiffLine()}"
                    newIndex++
                }
                lengths[oldIndex + 1][newIndex] >= lengths[oldIndex][newIndex + 1] ->
                    diff += "- ${removed[oldIndex++].visibleDiffLine()}"
                else -> diff += "+ ${added[newIndex++].visibleDiffLine()}"
            }
        }
        removed.drop(oldIndex).forEach { diff += "- ${it.visibleDiffLine()}" }
        added.drop(newIndex).forEach { diff += "+ ${it.visibleDiffLine()}" }
    } else {
        removed.forEach { diff += "- ${it.visibleDiffLine()}" }
        added.forEach { diff += "+ ${it.visibleDiffLine()}" }
    }
    before.subList(before.size - end, before.size - end + minOf(3, end))
        .forEach { diff += "  ${it.visibleDiffLine()}" }
    return diff
}

/** 用标记显示 CR，空行保留各自的差异前缀。 */
private fun String.visibleDiffLine(): String = replace("\r", "␍")

/** 可无损往返的文本编码。保存时保留原 BOM，并拒绝原字符集无法表示的新字符。 */
class TextEditEncoding internal constructor(
    private val charset: Charset,
    private val bom: ByteArray
) {
    /** 返回原编码的完整正文，出现无法表示的字符时返回空结果。 */
    fun encode(draft: String): ByteArray? {
        val body = draft.toByteArray(charset)
        return if (body.toString(charset) == draft) bom + body else null
    }

    /** 剥离 BOM 供编辑器显示，并验证解码结果能完整还原原始字节。 */
    fun decode(bytes: ByteArray): String? {
        val text = bytes.copyOfRange(bom.size, bytes.size).toString(charset)
        return text.takeIf { encode(it)?.contentEquals(bytes) == true }
    }

    companion object {
        /** 使用 BOM、XML 字节序或明确的 XML 声明识别编码，其它文本默认使用 UTF-8。 */
        fun detect(bytes: ByteArray, name: String): TextEditEncoding? {
            val utf8BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val littleBOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            val bigBOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            val noBOM = byteArrayOf()
            val xml = name.endsWith(".xml", ignoreCase = true)
            val detected = when {
                bytes.hasTextPrefix(utf8BOM) -> TextEditEncoding(Charsets.UTF_8, utf8BOM)
                bytes.hasTextPrefix(littleBOM) -> TextEditEncoding(Charsets.UTF_16LE, littleBOM)
                bytes.hasTextPrefix(bigBOM) -> TextEditEncoding(Charsets.UTF_16BE, bigBOM)
                xml && bytes.hasTextPrefix(byteArrayOf(60, 0, 63, 0)) -> TextEditEncoding(Charsets.UTF_16LE, noBOM)
                xml && bytes.hasTextPrefix(byteArrayOf(0, 60, 0, 63)) -> TextEditEncoding(Charsets.UTF_16BE, noBOM)
                xml -> {
                    val beginning = bytes.copyOfRange(0, minOf(bytes.size, 512)).toString(Charsets.US_ASCII)
                    val declared = Regex("""^<\?xml\s+[^?]*?\bencoding\s*=\s*['"]([a-zA-Z0-9._-]+)['"]""")
                        .find(beginning)?.groupValues?.get(1)
                    val charset = if (declared == null) Charsets.UTF_8 else {
                        runCatching { Charset.forName(declared) }.getOrNull() ?: return null
                    }
                    TextEditEncoding(charset, noBOM)
                }
                else -> TextEditEncoding(Charsets.UTF_8, noBOM)
            }
            return detected.takeIf { it.decode(bytes) != null }
        }
    }
}

/** 短字节标记比较不分配正文副本。 */
private fun ByteArray.hasTextPrefix(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
