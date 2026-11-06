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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 原创折角文件图标与类别色；调用方以原色绘制，不用单色 tint 覆盖内部层次。 */
data class FileIconSpec(
    /** 可直接绘制的矢量图标。 */
    val icon: ImageVector,
    /** 文件分类色，不用于覆盖整张多层矢量图形。 */
    val tint: Color,
    /** 稳定的文件类型标识，与当前页面主题无关。 */
    val glyph: StorageGlyph = StorageGlyph.TEXT
)

/** TextIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val TextIcon = createFileIconSpec(Color(0xFF7F8DA2), StorageGlyph.TEXT)

/** TerminalIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val TerminalIcon = createFileIconSpec(Color(0xFF6E7E97), StorageGlyph.TERMINAL)

/** DefaultIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val DefaultIcon = createFileIconSpec(Color(0xFF7F8DA2), StorageGlyph.UNKNOWN)

/** AppIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val AppIcon = createFileIconSpec(Color(0xFF7C9670), StorageGlyph.APP)

/** ImageIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val ImageIcon = createFileIconSpec(Color(0xFF5D927C), StorageGlyph.IMAGE)

/** VideoIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val VideoIcon = createFileIconSpec(Color(0xFF8B78B2), StorageGlyph.VIDEO)

/** AudioIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val AudioIcon = createFileIconSpec(Color(0xFFB2845F), StorageGlyph.AUDIO)

/** PdfIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val PdfIcon = createFileIconSpec(Color(0xFFB66B72), StorageGlyph.PDF)

/** WordIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val WordIcon = createFileIconSpec(Color(0xFF5B82B2), StorageGlyph.WORD)

/** TableIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val TableIcon = createFileIconSpec(Color(0xFF5E8F7C), StorageGlyph.TABLE)

/** SlidesIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val SlidesIcon = createFileIconSpec(Color(0xFFB88260), StorageGlyph.SLIDES)

/** JavaIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val JavaIcon = createFileIconSpec(Color(0xFFB68A59), StorageGlyph.JAVA)

/** ArchiveIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val ArchiveIcon = createFileIconSpec(Color(0xFFAA895D), StorageGlyph.ARCHIVE)

/** JSON 花括号，区别于网页标签及普通源码。 */
private val JsonIcon = createFileIconSpec(Color(0xFFAB8751), StorageGlyph.JSON)

/** 网页窗口，区别于 JSON、XML 和通用代码。 */
private val WebIcon = createFileIconSpec(Color(0xFFBA795E), StorageGlyph.WEB)

/** Android 机器人，用于 APK 及相关 Android 交付物。 */
private val AndroidIcon = createFileIconSpec(Color(0xFF6A9565), StorageGlyph.ANDROID)

/** Windows 程序的四窗格，不与脚本终端混用。 */
private val WindowsIcon = createFileIconSpec(Color(0xFF578FB6), StorageGlyph.WINDOWS)

/** 磁盘镜像使用硬盘图形，不视为通用压缩包。 */
private val DiskIcon = createFileIconSpec(Color(0xFF7C8C9E), StorageGlyph.DISK)

/** 安装包使用封装箱，区别于可执行程序和磁盘镜像。 */
private val PackageIcon = createFileIconSpec(Color(0xFFAA8763), StorageGlyph.PACKAGE)

/** 通用配置采用滑杆图形，区别于 JSON 数据。 */
private val ConfigIcon = createFileIconSpec(Color(0xFF6C8E93), StorageGlyph.CONFIG)

/** 数据库文件与 SQL 脚本使用分层圆柱。 */
private val DatabaseIcon = createFileIconSpec(Color(0xFF7288B1), StorageGlyph.DATABASE)

/** Markdown 使用固定 M 与箭头，与普通文本区分。 */
private val MarkdownIcon = createFileIconSpec(Color(0xFF7589A4), StorageGlyph.MARKDOWN)

/** 样式表使用井号网格，与网页和脚本区分。 */
private val StyleIcon = createFileIconSpec(Color(0xFF8A7AB0), StorageGlyph.STYLE)

/** 二进制模块使用芯片轮廓。 */
private val LibraryIcon = createFileIconSpec(Color(0xFF8C7B9D), StorageGlyph.LIBRARY)

/** CodeIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val CodeIcon = createFileIconSpec(Color(0xFF5E8F88), StorageGlyph.CODE)

/** FontIcon 对应分类的矢量缓存，避免列表重组时重复构造路径。 */
private val FontIcon = createFileIconSpec(Color(0xFF8977A5), StorageGlyph.FONT)

/**
 * 创建保留类别色的文件图标；类别映射与预览判断相互独立。
 * @param color 低饱和类别色。
 * @param glyph 文档内部标识。
 * @return 可跨列表项共享的不可变图标规格。
 */
private fun createFileIconSpec(color: Color, glyph: StorageGlyph) =
    FileIconSpec(StorageIcons.createStorageFileIcon(color, glyph), color, glyph)

/** 常见静态与相机图片格式。 */
private val IMAGE_EXT = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff", "raw", "dng"
)
/** 常见视频格式；单独的 .ts 名称优先按 TypeScript 图标展示，实际预览规则独立判断。 */
private val VIDEO_EXT = setOf(
    "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "wmv", "flv", "mpeg", "mpg", "m2ts", "mts"
)
/** 音频与 MIDI 文件。 */
private val AUDIO_EXT = setOf(
    "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "amr", "mid", "midi", "aiff"
)
/** PDF 文档。 */
private val PDF_EXT = setOf("pdf")
/** 文字处理文档。 */
private val WORD_EXT = setOf("doc", "docx", "rtf", "odt", "pages")
/** 表格及分隔数据表。 */
private val EXCEL_EXT = setOf("xls", "xlsx", "csv", "tsv", "ods", "numbers")
/** 演示文档。 */
private val PPT_EXT = setOf("ppt", "pptx", "odp", "key")
/** 通用压缩包；APK、Java 归档和磁盘镜像由专属分组处理。 */
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "bzip2", "xz", "zst", "lz", "lz4", "cab")
/** Java 归档及 Java 源文件使用杯形。 */
private val JAVA_EXT = setOf("jar", "war", "ear", "java", "class")
/** Windows 可执行程序，区别于软件安装容器。 */
private val WINDOWS_EXT = setOf("exe", "com", "scr")
/** 独立应用容器与便携应用，不显示成命令行脚本。 */
private val APP_EXT = setOf("app", "appimage", "ipa")
/** 安装包、插件包与发行版软件包。 */
private val PACKAGE_EXT = setOf(
    "pkg", "mpkg", "msi", "msix", "msixbundle", "appx", "appxbundle", "deb", "rpm", "nupkg", "crx", "vsix"
)
/** 磁盘和虚拟磁盘镜像。 */
private val DISK_EXT = setOf("dmg", "iso", "img", "vhd", "vhdx", "vmdk", "qcow", "qcow2", "sparseimage", "sparsebundle")
/** 结构化 JSON 数据及常见变体。 */
private val JSON_EXT = setOf("json", "jsonc", "json5", "jsonl", "ndjson", "geojson", "ipynb")
/** 网页文档；XML 和普通源码继续使用代码符号。 */
private val WEB_EXT = setOf("html", "htm", "xhtml", "mhtml", "mht", "shtml")
/** 样式表。 */
private val STYLE_EXT = setOf("css", "scss", "sass", "less")
/** 可执行脚本，使用终端符号而非桌面应用图标。 */
private val SCRIPT_EXT = setOf("sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "psm1")
/** 普通源代码；不改变文件预览是否受支持的判断。 */
private val CODE_EXT = setOf(
    "kt", "kts", "py", "rb", "php", "go", "rs", "swift", "lua", "c", "cc", "cpp", "cxx", "h", "hpp", "cs", "m", "mm",
    "js", "ts", "jsx", "tsx", "mjs", "cjs", "dart", "scala", "groovy", "xml", "xsd", "xsl", "graphql", "proto",
    "vue", "svelte"
)
/** 配置与环境文件；完整隐藏文件名也在下面的名称分支处理。 */
private val CONFIG_EXT = setOf(
    "yaml", "yml", "toml", "ini", "conf", "cfg", "config", "properties", "env", "plist", "gradle"
)
/** 数据库及 SQL 文件。 */
private val DATABASE_EXT = setOf("db", "sqlite", "sqlite3", "db3", "sql", "mdb", "accdb")
/** 普通文本与日志。 */
private val TEXT_EXT = setOf("txt", "log", "rst", "tex", "text")
/** Markdown 文档。 */
private val MARKDOWN_EXT = setOf("md", "markdown", "mdown", "mdx")
/** 字体文件。 */
private val FONT_EXT = setOf("ttf", "otf", "woff", "woff2", "eot")
/** Android 安装包、拆分包与开发交付物。 */
private val ANDROID_EXT = setOf("apk", "apks", "apkm", "xapk", "aab", "aar", "dex")
/** 动态库和二进制模块。 */
private val LIBRARY_EXT = setOf("dll", "so", "dylib", "lib", "a", "o", "wasm", "bin")
/** 这些完整名称属于配置，不能按无扩展名文件或最后一段后缀误判。 */
private val CONFIG_NAMES = setOf(".env", ".gitignore", ".gitattributes", ".editorconfig", ".npmrc", ".prettierrc")

/** 无后缀的常见说明文档名称。 */
private val TEXT_NAMES = setOf("readme", "license", "licence", "changelog", "notice")

/** Linux 带数字版本后缀的共享库，例如 libdemo.so.1.2。 */
private val VERSIONED_LIBRARY_NAME = Regex(".*\\.so(?:\\.\\d+)+$")

/**
 * 文件名映射到图标和类别色；未识别类型回退为中性文件，不改变预览能力。
 * 图形和类别色固定，不读取主题；列表与预览共用缓存，主题切换不会重建或替换类型标识。
 * @param name 完整显示名称或含目录的对象 key。
 * @return 文件分类及对应的矢量图形。
 */
fun iconForFileName(name: String): FileIconSpec {
    // 只解析最后的文件名，目录中的点不会误作扩展名；兼容展示层传入对象 key 或本地路径。
    val lower = name.substringAfterLast('/').substringAfterLast('\\').lowercase()
    val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
    return when {
        lower in CONFIG_NAMES || lower.startsWith(".env.") -> ConfigIcon
        lower == "dockerfile" || lower.startsWith("dockerfile.") || lower == "makefile" -> TerminalIcon
        lower in TEXT_NAMES -> TextIcon
        ext in ANDROID_EXT -> AndroidIcon
        ext in WINDOWS_EXT -> WindowsIcon
        ext in APP_EXT -> AppIcon
        ext in PACKAGE_EXT -> PackageIcon
        ext in DISK_EXT -> DiskIcon
        ext in IMAGE_EXT -> ImageIcon
        ext in VIDEO_EXT -> VideoIcon
        ext in AUDIO_EXT -> AudioIcon
        ext in PDF_EXT -> PdfIcon
        ext in WORD_EXT -> WordIcon
        ext in EXCEL_EXT -> TableIcon
        ext in PPT_EXT -> SlidesIcon
        ext in JAVA_EXT -> JavaIcon
        ext in ARCHIVE_EXT -> ArchiveIcon
        ext in JSON_EXT -> JsonIcon
        ext in WEB_EXT -> WebIcon
        ext in STYLE_EXT -> StyleIcon
        ext in SCRIPT_EXT -> TerminalIcon
        ext in DATABASE_EXT -> DatabaseIcon
        ext in LIBRARY_EXT || VERSIONED_LIBRARY_NAME.matches(lower) -> LibraryIcon
        ext in CONFIG_EXT -> ConfigIcon
        ext in CODE_EXT -> CodeIcon
        ext in MARKDOWN_EXT -> MarkdownIcon
        ext in TEXT_EXT -> TextIcon
        ext in FONT_EXT -> FontIcon
        else -> DefaultIcon
    }
}
