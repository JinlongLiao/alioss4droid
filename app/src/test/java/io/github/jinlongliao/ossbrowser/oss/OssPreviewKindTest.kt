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
package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.ui.browser.TextEditEncoding
import io.github.jinlongliao.ossbrowser.ui.browser.compareTextEditLines
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** 文件扩展名到预览类型的回归测试。 */
class OssPreviewKindTest {

    /** 编辑确认保留上下文，明确显示新增、删除及换行变化。 */
    @Test
    fun textEditDiffShowsReplacementsAndLineEndings() {
        assertEquals(
            listOf("  name:", "-   key: before", "+   key: after", "  unchanged: true"),
            compareTextEditLines("name:\n  key: before\nunchanged: true", "name:\n  key: after\nunchanged: true")
        )
        assertEquals(listOf("- a␍", "+ a", "  "), compareTextEditLines("a\r\n", "a\n"))
        assertEquals(listOf("  a", "+ "), compareTextEditLines("a", "a\n"))
        assertEquals(emptyList<String>(), compareTextEditLines("a", "a"))
        val xml = OssObjectItem.File(key = "folder/config.XML", size = 30, lastModified = null, etag = null)
        assertEquals(OssPreviewKind.TEXT, xml.previewKind())
        assertEquals(
            listOf("  <config>", "-   <port>80</port>", "+   <port>443</port>", "  </config>"),
            compareTextEditLines("<config>\n  <port>80</port>\n</config>", "<config>\n  <port>443</port>\n</config>")
        )
    }

    /** 编辑 XML 保留 BOM、字节序和显式的原字符集，拒绝无损转换失败的文件。 */
    @Test
    fun xmlTextEditPreservesOriginalEncoding() {
        val original = "<配置>旧值</配置>"
        val draft = "<配置>新值</配置>"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val source = bom + original.toByteArray(Charsets.UTF_16LE)
        val encoding = requireNotNull(TextEditEncoding.detect(source, "config.XML"))
        assertEquals(original, encoding.decode(source))
        assertArrayEquals(bom + draft.toByteArray(Charsets.UTF_16LE), encoding.encode(draft))

        val header = "<?xml version=\"1.0\" encoding=\"GBK\"?>"
        val gbk = charset("GBK")
        val declared = (header + original).toByteArray(gbk)
        val legacy = requireNotNull(TextEditEncoding.detect(declared, "config.xml"))
        assertArrayEquals((header + draft).toByteArray(gbk), legacy.encode(header + draft))
        assertNull(legacy.encode(header + "<配置>😀</配置>"))
        assertNull(TextEditEncoding.detect(byteArrayOf(0xC3.toByte()), "invalid.txt"))
    }

    /** 只有无存储编码的响应可以写回未压缩的文本字节。 */
    @Test
    fun ossIdentityEncodingAllowsUncompressedTextWriteback() {
        assertTrue(null.isIdentityOssEncoding())
        assertTrue(" Identity ".isIdentityOssEncoding())
        assertFalse("gzip".isIdentityOssEncoding())
    }

    /** HEAD 补到的版本必须用于第二次条件读取，条件失败时维持只读。 */
    @Test
    fun missingTextVersionUsesConditionalRead() = runBlocking {
        val initial = "old".toByteArray() to null
        val result = resolveTextSnapshotVersion(initial, readVersion = { "etag-1" }) { version ->
            assertEquals("\"etag-1\"", version)
            "verified".toByteArray() to null
        }
        assertEquals("verified", result.first.toString(Charsets.UTF_8))
        assertEquals("\"etag-1\"", result.second)
        val mismatch = resolveTextSnapshotVersion(initial, readVersion = { "etag-1" }) {
            "other".toByteArray() to "etag-2"
        }
        assertNull(mismatch.second)
        assertArrayEquals(initial.first, mismatch.first)
        val listed = resolveTextSnapshotVersion(initial, listedETag = "listed", readVersion = { null }) { version ->
            assertEquals(strongTextETag("listed"), version)
            "verified".toByteArray() to null
        }
        assertEquals(strongTextETag("listed"), listed.second)
        val stale = resolveTextSnapshotVersion(initial, listedETag = "stale", readVersion = { null }) {
            error("HTTP 412")
        }
        assertNull(stale.second)
        assertArrayEquals(initial.first, stale.first)
        assertNull(strongTextETag("*"))
        assertNull(strongTextETag("W/\"weak\""))
    }

    /** HTML 家族应进入网页渲染流程，不再作为普通源码文本预览。 */
    @Test
    fun previewKind_htmlExtensions_useHtmlPreview() {
        listOf("index.html", "page.HTM", "document.xhtml").forEach { name ->
            val item = OssObjectItem.File(
                key = "site/$name",
                size = 1L,
                lastModified = null,
                etag = null
            )

            assertEquals(OssPreviewKind.HTML, item.previewKind())
        }
    }

    /** CSS 和 JavaScript 仍按源码文本展示，避免脱离 HTML 上下文执行。 */
    @Test
    fun previewKind_webAssets_remainTextPreview() {
        listOf("theme.css", "app.js").forEach { name ->
            val item = OssObjectItem.File(
                key = "site/$name",
                size = 1L,
                lastModified = null,
                etag = null
            )

            assertEquals(OssPreviewKind.TEXT, item.previewKind())
        }
    }

    /** 新格式 Office 文档应进入各自的本地预览流程，旧格式继续保持不支持。 */
    @Test
    fun previewKind_modernOfficeFormats_useDedicatedLocalPreview() {
        val expected = mapOf(
            "document.docx" to OssPreviewKind.DOCX,
            "workbook.xlsx" to OssPreviewKind.XLSX,
            "slides.pptx" to OssPreviewKind.PPTX,
            "records.csv" to OssPreviewKind.DELIMITED_TEXT,
            "records.tsv" to OssPreviewKind.DELIMITED_TEXT
        )

        expected.forEach { (name, kind) ->
            val item = OssObjectItem.File(name, 1L, null, null)
            assertEquals(kind, item.previewKind())
        }
        listOf("legacy.doc", "legacy.xls", "legacy.ppt").forEach { name ->
            val item = OssObjectItem.File(name, 1L, null, null)
            assertEquals(OssPreviewKind.UNSUPPORTED, item.previewKind())
        }
    }
}
