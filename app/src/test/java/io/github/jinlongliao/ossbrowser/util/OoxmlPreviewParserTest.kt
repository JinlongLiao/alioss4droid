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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** OOXML 本地轻量解析器的最小格式回归测试。 */
class OoxmlPreviewParserTest {

    /** XLSX 应解析工作表名称、共享字符串、数值与空列占位。 */
    @Test
    fun parseSpreadsheet_validWorkbook_returnsDisplayCells() {
        val bytes = zipOf(
            "xl/workbook.xml" to """
                <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Summary" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent(),
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent(),
            "xl/sharedStrings.xml" to "<sst><si><t>Name</t></si><si><t>Duckd</t></si></sst>",
            "xl/worksheets/sheet1.xml" to """
                <worksheet><sheetData><row>
                  <c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c>
                </row><row><c r="A2"><v>42</v></c></row></sheetData></worksheet>
            """.trimIndent()
        )

        val sheets = OoxmlPreviewParser.parseSpreadsheet(bytes)

        assertNotNull(sheets)
        assertEquals("Summary", sheets?.single()?.name)
        assertEquals(listOf(listOf("Name", "", "Duckd"), listOf("42")), sheets?.single()?.rows)
    }

    /** PPTX 应按页码排序并提取每页文本。 */
    @Test
    fun parsePresentation_validSlides_returnsOrderedTexts() {
        val bytes = zipOf(
            "ppt/slides/slide2.xml" to
                "<p:sld xmlns:p=\"p\" xmlns:a=\"a\"><a:t>Second</a:t></p:sld>",
            "ppt/slides/slide1.xml" to
                "<p:sld xmlns:p=\"p\" xmlns:a=\"a\"><a:t>Title</a:t><a:t>Body</a:t></p:sld>"
        )

        val slides = OoxmlPreviewParser.parsePresentation(bytes)

        assertEquals(listOf(1, 2), slides?.map { it.number })
        assertEquals(listOf("Title", "Body"), slides?.first()?.texts)
    }

    /** OOXML 条目包含 DTD 时必须在 DOM 解析前拒绝，避免外部实体访问。 */
    @Test
    fun parseSpreadsheet_doctypeEntry_rejectsUnsafeXml() {
        val bytes = zipOf(
            "xl/workbook.xml" to "<!DOCTYPE workbook><workbook/>"
        )

        assertThrows(IllegalArgumentException::class.java) {
            OoxmlPreviewParser.parseSpreadsheet(bytes)
        }
    }

    /** 将测试 XML 条目打包为内存 ZIP，模拟最小 OOXML 文件。 */
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
