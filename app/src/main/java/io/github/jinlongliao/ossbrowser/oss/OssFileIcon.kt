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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 文件图标 + 建议色（未识别走默认）。颜色选值偏柔和，和 Material3 配色协调。 */
data class FileIconSpec(val icon: ImageVector, val tint: Color)

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "tif", "tiff", "raw")
private val VIDEO_EXT = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "wmv", "flv", "ts", "mpeg", "mpg")
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "amr", "mid", "midi")
private val PDF_EXT = setOf("pdf")
private val WORD_EXT = setOf("doc", "docx", "rtf", "odt", "pages")
private val EXCEL_EXT = setOf("xls", "xlsx", "csv", "tsv", "ods", "numbers")
private val PPT_EXT = setOf("ppt", "pptx", "odp", "key")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "apk", "jar", "war")
private val CODE_EXT = setOf(
    "kt", "kts", "java", "py", "rb", "php", "go", "rs", "swift", "lua",
    "c", "cc", "cpp", "cxx", "h", "hpp", "cs", "m", "mm",
    "js", "ts", "jsx", "tsx", "mjs", "cjs", "dart", "scala", "groovy",
    "html", "htm", "xhtml", "css", "scss", "sass", "less",
    "sql", "graphql", "proto", "sh", "bash", "zsh", "bat", "ps1"
)
private val DATA_EXT = setOf("json", "xml", "yaml", "yml", "toml", "ini", "conf", "properties", "env", "plist")
private val TEXT_EXT = setOf("txt", "md", "markdown", "log", "rst", "tex")
private val FONT_EXT = setOf("ttf", "otf", "woff", "woff2", "eot")
private val ANDROID_EXT = setOf("apk", "aab", "aar", "dex")

/** 文件名 → 图标 + 颜色。对未知类型返回通用文件图标 + 中性灰。 */
fun iconForFileName(name: String): FileIconSpec {
    val lower = name.lowercase()
    val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
    return when {
        ext.isEmpty() -> when (lower) {
            "readme", "license", "changelog" -> FileIconSpec(Icons.AutoMirrored.Outlined.Article, Color(0xFF6D4C41))
            "dockerfile", "makefile" -> FileIconSpec(Icons.Outlined.Terminal, Color(0xFF455A64))
            else -> FileIconSpec(Icons.Outlined.InsertDriveFile, Color(0xFF607D8B))
        }
        ext in ANDROID_EXT -> FileIconSpec(Icons.Outlined.Android, Color(0xFF689F38))
        ext in IMAGE_EXT -> FileIconSpec(Icons.Outlined.Image, Color(0xFF43A047))
        ext in VIDEO_EXT -> FileIconSpec(Icons.Outlined.Movie, Color(0xFF8E24AA))
        ext in AUDIO_EXT -> FileIconSpec(Icons.Outlined.AudioFile, Color(0xFFEF6C00))
        ext in PDF_EXT -> FileIconSpec(Icons.Outlined.PictureAsPdf, Color(0xFFD32F2F))
        ext in WORD_EXT -> FileIconSpec(Icons.Outlined.Description, Color(0xFF1976D2))
        ext in EXCEL_EXT -> FileIconSpec(Icons.Outlined.TableChart, Color(0xFF2E7D32))
        ext in PPT_EXT -> FileIconSpec(Icons.Outlined.Slideshow, Color(0xFFE65100))
        ext in ARCHIVE_EXT -> FileIconSpec(Icons.Outlined.FolderZip, Color(0xFF795548))
        ext in DATA_EXT -> FileIconSpec(Icons.Outlined.DataObject, Color(0xFF00838F))
        ext in CODE_EXT -> FileIconSpec(Icons.Outlined.Code, Color(0xFF00897B))
        ext in TEXT_EXT -> FileIconSpec(Icons.Outlined.TextSnippet, Color(0xFF5D4037))
        ext in FONT_EXT -> FileIconSpec(Icons.Outlined.TextFields, Color(0xFF5E35B1))
        else -> FileIconSpec(Icons.Outlined.InsertDriveFile, Color(0xFF607D8B))
    }
}
