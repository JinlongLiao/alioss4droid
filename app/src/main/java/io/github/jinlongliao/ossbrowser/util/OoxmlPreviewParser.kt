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

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Excel 工作表的本地轻量预览数据。 */
data class SpreadsheetSheetPreview(
    /** 工作表显示名称。 */
    val name: String,
    /** 已解析的行与单元格文本；空单元格以空字符串占位。 */
    val rows: List<List<String>>,
)

/** PowerPoint 单页幻灯片的本地轻量预览数据。 */
data class PresentationSlidePreview(
    /** 从 1 开始的幻灯片序号。 */
    val number: Int,
    /** 按文档顺序提取的文本片段。 */
    val texts: List<String>,
)

/**
 * 本地解析 OOXML `.xlsx` 与 `.pptx` 的轻量预览器。
 *
 * 解析过程只读取 ZIP 内必要 XML，不执行宏、不解析外部关系，并为 ZIP 总展开量、条目数、工作表
 * 行列数设置上限。XML 解析显式禁用 DTD 与外部实体，避免预览不可信 OSS 文件时访问本地或网络资源。
 *
 * @see <a href="https://ecma-international.org/publications-and-standards/standards/ecma-376/">ECMA-376 Office Open XML</a>
 */
object OoxmlPreviewParser {

    /** 单份预览允许读取的 ZIP XML 总字节数。 */
    private const val MAX_TOTAL_XML_BYTES = 32 * 1024 * 1024

    /** 防止异常 ZIP 使用大量微小条目耗尽资源。 */
    private const val MAX_ZIP_ENTRIES = 512

    /** 单个工作表最多展示的行数。 */
    private const val MAX_SHEET_ROWS = 200

    /** 单行最多展示的列数。 */
    private const val MAX_SHEET_COLUMNS = 50

    /** 单个单元格或幻灯片文本片段的最大字符数。 */
    private const val MAX_TEXT_CHARS = 4_096

    /**
     * 解析 `.xlsx` 工作表及单元格缓存值。
     *
     * @param bytes 完整 XLSX 文件内容。
     * @return 工作表列表；加密或关键结构缺失时返回 `null`。
     * @throws Exception ZIP 损坏、XML 不合法或超过安全限制时抛出，以便调用方记录真实根因。
     */
    fun parseSpreadsheet(bytes: ByteArray): List<SpreadsheetSheetPreview>? {
        val entries = readXmlEntries(bytes, "xl/")
        val workbook = parseXml(entries["xl/workbook.xml"] ?: return null)
        val relationships = entries["xl/_rels/workbook.xml.rels"]
            ?.let(::parseRelationships)
            .orEmpty()
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let(::parseSharedStrings)
            .orEmpty()
        return elements(workbook, "sheet").mapNotNull { sheet ->
            val name = sheet.getAttribute("name").ifBlank { "Sheet" }
            val relationId = sheet.getAttribute("r:id")
            val target = relationships[relationId] ?: return@mapNotNull null
            val normalizedTarget = if (target.startsWith("/")) {
                target.removePrefix("/")
            } else {
                "xl/${target.removePrefix("../")}"
            }
            val sheetXml = entries[normalizedTarget] ?: return@mapNotNull null
            SpreadsheetSheetPreview(name, parseSheetRows(sheetXml, sharedStrings))
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * 解析 `.pptx` 幻灯片中的文字内容。
     *
     * @param bytes 完整 PPTX 文件内容。
     * @return 按页码排列的幻灯片；加密或没有幻灯片时返回 `null`。
     * @throws Exception ZIP 损坏、XML 不合法或超过安全限制时抛出，以便调用方记录真实根因。
     */
    fun parsePresentation(bytes: ByteArray): List<PresentationSlidePreview>? {
        val entries = readXmlEntries(bytes, "ppt/slides/")
        return entries.entries
            .mapNotNull { (name, xml) ->
                val match = SLIDE_ENTRY_REGEX.matchEntire(name) ?: return@mapNotNull null
                val number = match.groupValues[1].toInt()
                val document = parseXml(xml)
                val texts = elements(document, "t")
                    .map { it.textContent.trim().take(MAX_TEXT_CHARS) }
                    .filter { it.isNotEmpty() }
                PresentationSlidePreview(number, texts)
            }
            .sortedBy { it.number }
            .takeIf { it.isNotEmpty() }
    }

    /** 读取目标目录下 XML 条目，并限制总展开量与条目数。 */
    private fun readXmlEntries(bytes: ByteArray, prefix: String): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>(32)
        var entryCount = 0
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                entryCount++
                require(entryCount <= MAX_ZIP_ENTRIES) { "OOXML ZIP entries exceed limit" }
                val isRequiredXml = entry.name.endsWith(".xml") || entry.name.endsWith(".rels")
                if (!entry.isDirectory && entry.name.startsWith(prefix) && isRequiredXml) {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        totalBytes += read
                        require(totalBytes <= MAX_TOTAL_XML_BYTES) { "OOXML XML content exceeds limit" }
                        output.write(buffer, 0, read)
                    }
                    entries[entry.name] = output.toByteArray()
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return entries
    }

    /**
     * 创建适配 Android 的安全 DOM 解析器。
     *
     * Android DOM 不支持桌面 JDK/Xerces 的外部访问与 `disallow-doctype-decl` feature，因此在创建
     * Document 前直接拒绝 XML 规范中大小写固定的 `<!DOCTYPE` 声明。没有 DTD 就无法声明外部实体，
     * 非验证模式的 DOM 也不会根据 OOXML 内容主动加载外部 Schema。
     */
    private fun parseXml(bytes: ByteArray): Document {
        require(!bytes.containsAsciiToken("<!DOCTYPE")) { "OOXML XML must not contain DOCTYPE" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    /** 在不解码整份 XML 的前提下查找 ASCII 安全标记。 */
    private fun ByteArray.containsAsciiToken(token: String): Boolean {
        val tokenBytes = token.toByteArray(Charsets.US_ASCII)
        if (size < tokenBytes.size) {
            return false
        }
        for (start in 0..size - tokenBytes.size) {
            var matches = true
            for (offset in tokenBytes.indices) {
                if (this[start + offset] != tokenBytes[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return true
            }
        }
        return false
    }

    /** 解析工作簿关系 ID 到内部文件路径的映射。 */
    private fun parseRelationships(bytes: ByteArray): Map<String, String> = elements(parseXml(bytes), "Relationship")
        .mapNotNull { element ->
            val id = element.getAttribute("Id")
            val target = element.getAttribute("Target")
            if (id.isBlank() || target.isBlank() || target.contains("://")) {
                null
            } else {
                id to target
            }
        }
        .toMap(HashMap())

    /** 解析共享字符串表，保留富文本单元格中多个文本 run 的顺序。 */
    private fun parseSharedStrings(bytes: ByteArray): List<String> = elements(parseXml(bytes), "si")
        .map { item -> descendants(item, "t").joinToString("") { it.textContent }.take(MAX_TEXT_CHARS) }

    /** 解析一个工作表的有限行列与缓存展示值。 */
    private fun parseSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        return elements(parseXml(bytes), "row").take(MAX_SHEET_ROWS).map { row ->
            val values = HashMap<Int, String>()
            descendants(row, "c").forEach { cell ->
                val column = columnIndex(cell.getAttribute("r"))
                if (column in 0 until MAX_SHEET_COLUMNS) {
                    val type = cell.getAttribute("t")
                    val raw = descendants(cell, if (type == "inlineStr") "t" else "v")
                        .joinToString("") { it.textContent }
                    values[column] = when (type) {
                        "s" -> raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                        "b" -> if (raw == "1") "TRUE" else "FALSE"
                        else -> raw
                    }.take(MAX_TEXT_CHARS)
                }
            }
            val lastColumn = values.keys.maxOrNull() ?: -1
            (0..lastColumn).map { values[it].orEmpty() }
        }
    }

    /** 将 `AB12` 形式的单元格引用转换为从 0 开始的列索引。 */
    private fun columnIndex(reference: String): Int {
        var result = 0
        val letters = reference.takeWhile(Char::isLetter)
        if (letters.isEmpty()) {
            return -1
        }
        letters.uppercase().forEach { result = result * 26 + (it - 'A' + 1) }
        return result - 1
    }

    /** 获取文档内指定 local name 的全部元素。 */
    private fun elements(document: Document, localName: String): List<Element> {
        val nodes = document.getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** 获取节点后代中指定 local name 的全部元素。 */
    private fun descendants(node: Node, localName: String): List<Element> {
        val elements = (node as Element).getElementsByTagNameNS("*", localName)
        return (0 until elements.length).mapNotNull { elements.item(it) as? Element }
    }

    /** PPTX 幻灯片条目及其页码。 */
    private val SLIDE_ENTRY_REGEX = Regex("ppt/slides/slide(\\d+)\\.xml")
}
