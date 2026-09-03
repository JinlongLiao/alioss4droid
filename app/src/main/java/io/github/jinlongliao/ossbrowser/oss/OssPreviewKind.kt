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

/** 预览类型，按对象 key 的扩展名粗略判定。 */
enum class OssPreviewKind {
    TEXT,
    /** HTML 文档；默认以禁用脚本和外部网络的静态 WebView 渲染。 */
    HTML,
    IMAGE,
    AUDIO,
    VIDEO,
    PDF,
    /** Word 文档（.docx），应用内解析 XML 抽取纯文本预览。 */
    DOCX,
    /** Excel 工作簿（.xlsx），应用内解析工作表与缓存单元格值。 */
    XLSX,
    /** PowerPoint 演示文稿（.pptx），应用内按页提取文字内容。 */
    PPTX,
    /** CSV/TSV 分隔文本，应用内解析为可横向滚动的轻量表格。 */
    DELIMITED_TEXT,
    UNSUPPORTED;
}

/** Markdown 扩展名，用于在文本预览中启用「富文本/源码」切换。 */
private val MARKDOWN_EXTS: Set<String> = setOf("md", "markdown", "mdown", "mkd", "mkdn")

/** HTML 文档扩展名；从普通文本中单独分类，以便提供静态/完整网页预览。 */
private val HTML_EXTS: Set<String> = setOf("html", "htm", "xhtml")

/** 返回 true 表示文件名应以 Markdown 富文本形式渲染。 */
fun OssObjectItem.File.isMarkdownFile(): Boolean {
    val ext = displayName.lowercase().substringAfterLast('.', "")
    return ext in MARKDOWN_EXTS
}

/** 文本型扩展名（包含源码、配置、日志等；不断补充，覆盖主流场景）。 */
private val TEXT_EXTS: Set<String> = setOf(
    "txt", "log", "md", "markdown", "mdown", "mkd", "mkdn", "rst", "tex", "adoc",
    "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg", "properties", "env", "plist",
    "css", "scss", "sass", "less",
    "js", "ts", "jsx", "tsx", "mjs", "cjs", "vue", "svelte",
    "kt", "kts", "java", "gradle", "groovy", "scala", "clj", "cljs",
    "py", "ipynb", "rb", "php", "go", "rs", "swift", "lua", "dart", "erl", "ex", "exs",
    "c", "cc", "cpp", "cxx", "h", "hpp", "cs", "m", "mm", "r",
    "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "psm1",
    "sql", "graphql", "gql", "proto", "thrift",
    "dockerfile", "tf", "hcl",
    "gitignore", "gitattributes", "gitconfig", "editorconfig"
)

private val IMAGE_EXTS: Set<String> = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg"
)

private val AUDIO_EXTS: Set<String> = setOf(
    "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "amr", "mid", "midi"
)

private val VIDEO_EXTS: Set<String> = setOf(
    "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "wmv", "flv", "ts", "mpeg", "mpg"
)

private val PDF_EXTS: Set<String> = setOf("pdf")

private val DOCX_EXTS: Set<String> = setOf("docx")

/** 支持本地轻量预览的新格式 Excel 扩展名。 */
private val XLSX_EXTS: Set<String> = setOf("xlsx")

/** 支持本地轻量预览的新格式 PowerPoint 扩展名。 */
private val PPTX_EXTS: Set<String> = setOf("pptx")

/** 支持本地轻量表格预览的分隔文本扩展名。 */
private val DELIMITED_TEXT_EXTS: Set<String> = setOf("csv", "tsv")

fun OssObjectItem.File.previewKind(): OssPreviewKind {
    val name = displayName.lowercase()
    val ext = name.substringAfterLast('.', "")
    // 特例：无扩展名但属于约定俗成的文本文件
    if (ext.isEmpty()) {
        return when (name) {
            "readme", "license", "changelog", "dockerfile", "makefile",
            "cmakelists.txt", "notice", "authors", "contributors" -> OssPreviewKind.TEXT
            else -> OssPreviewKind.UNSUPPORTED
        }
    }
    return when (ext) {
        in PDF_EXTS -> OssPreviewKind.PDF
        in DOCX_EXTS -> OssPreviewKind.DOCX
        in XLSX_EXTS -> OssPreviewKind.XLSX
        in PPTX_EXTS -> OssPreviewKind.PPTX
        in DELIMITED_TEXT_EXTS -> OssPreviewKind.DELIMITED_TEXT
        in HTML_EXTS -> OssPreviewKind.HTML
        in TEXT_EXTS -> OssPreviewKind.TEXT
        in IMAGE_EXTS -> OssPreviewKind.IMAGE
        in AUDIO_EXTS -> OssPreviewKind.AUDIO
        in VIDEO_EXTS -> OssPreviewKind.VIDEO
        else -> OssPreviewKind.UNSUPPORTED
    }
}
