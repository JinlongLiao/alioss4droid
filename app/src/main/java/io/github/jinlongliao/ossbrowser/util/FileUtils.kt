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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

object FileUtils {

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
            }
        return uri.lastPathSegment
    }

    fun copyUriToCache(context: Context, uri: Uri, name: String): File {
        val cacheDir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val out = File(cacheDir, "${System.currentTimeMillis()}_${name.sanitizeFileName()}")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开所选文件: $uri" }
            out.outputStream().use { os -> input.copyTo(os) }
        }
        return out
    }

    private fun String.sanitizeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_")

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble() / 1024.0
        var i = 0
        while (size >= 1024 && i < units.lastIndex) {
            size /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.2f %s", size, units[i])
    }
}
