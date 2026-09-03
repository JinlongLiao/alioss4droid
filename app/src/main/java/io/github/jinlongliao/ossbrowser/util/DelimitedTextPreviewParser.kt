/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.jinlongliao.ossbrowser.util

/**
 * CSV/TSV 本地轻量预览解析器。
 *
 * CSV 支持双引号字段、转义双引号和字段内换行；预览最多保留 200 行、每行 50 列，避免超大文本
 * 在 Compose 中创建过多节点。TSV 使用制表符分隔，并沿用相同的引号规则。
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4180">RFC 4180</a>
 */
object DelimitedTextPreviewParser {

    /** 单份预览最多展示的记录数。 */
    private const val MAX_ROWS = 200

    /** 单条记录最多展示的字段数。 */
    private const val MAX_COLUMNS = 50

    /** 单个字段最多展示的字符数。 */
    private const val MAX_CELL_CHARS = 4_096

    /**
     * 解析 UTF-8 CSV 或 TSV 内容。
     *
     * @param content 已解码的文本内容，可包含 UTF-8 BOM。
     * @param delimiter 字段分隔符，只允许逗号或制表符。
     * @return 可直接展示的有限行列；空文件返回空列表。
     * @throws IllegalArgumentException 分隔符不受支持时抛出。
     */
    fun parseDelimitedText(content: String, delimiter: Char): List<List<String>> {
        require(delimiter == ',' || delimiter == '\t') { "Unsupported delimiter" }
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val cell = StringBuilder()
        var index = if (content.startsWith('\uFEFF')) 1 else 0
        var quoted = false

        /** 将当前字段加入当前行，并执行展示长度限制。 */
        fun appendCell() {
            if (row.size < MAX_COLUMNS) {
                row.add(cell.toString().take(MAX_CELL_CHARS))
            }
            cell.clear()
        }

        /** 将当前记录加入结果；空白末行由调用方决定是否保留。 */
        fun appendRow() {
            appendCell()
            rows.add(row.toList())
            row.clear()
        }

        while (index < content.length && rows.size < MAX_ROWS) {
            val character = content[index]
            when {
                character == '"' && quoted && index + 1 < content.length && content[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == delimiter && !quoted -> appendCell()
                (character == '\n' || character == '\r') && !quoted -> {
                    appendRow()
                    if (character == '\r' && index + 1 < content.length && content[index + 1] == '\n') {
                        index++
                    }
                }
                cell.length < MAX_CELL_CHARS -> cell.append(character)
            }
            index++
        }
        if (rows.size < MAX_ROWS && (cell.isNotEmpty() || row.isNotEmpty())) {
            appendRow()
        }
        return rows
    }
}
