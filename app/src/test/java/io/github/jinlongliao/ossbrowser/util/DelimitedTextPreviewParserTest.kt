/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.jinlongliao.ossbrowser.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** CSV/TSV 本地轻量预览解析器的格式回归测试。 */
class DelimitedTextPreviewParserTest {

    /** CSV 应支持 BOM、引号内逗号、转义引号及字段内换行。 */
    @Test
    fun parseDelimitedText_csvQuotedFields_returnsStructuredRows() {
        val content = "\uFEFFname,note\r\nDuckd,\"hello, \"\"OSS\"\"\"\r\nBird,\"line1\nline2\""

        val rows = DelimitedTextPreviewParser.parseDelimitedText(content, ',')

        assertEquals(
            listOf(
                listOf("name", "note"),
                listOf("Duckd", "hello, \"OSS\""),
                listOf("Bird", "line1\nline2")
            ),
            rows
        )
    }

    /** TSV 应使用制表符切分字段。 */
    @Test
    fun parseDelimitedText_tsv_returnsStructuredRows() {
        val rows = DelimitedTextPreviewParser.parseDelimitedText("name\tvalue\nA\t1", '\t')

        assertEquals(listOf(listOf("name", "value"), listOf("A", "1")), rows)
    }
}
