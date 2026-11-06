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
import android.os.CancellationSignal
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object FileUtils {

    /** 读取文档名称；同步旧 API 保持原有 URI fallback，调用方负责避免主线程慢查询。 */
    fun displayName(context: Context, uri: Uri): String? = queryDisplayName(context, uri, null)

    /**
     * 在 IO 解析云文档名称，协程取消立即通知提供方终止查询，不阻塞服务命令队列。
     * 提供方若忽略 CancellationSignal，读取线程仍需等待其退出，但 Main 和取消命令不受影响。
     */
    suspend fun resolveDisplayName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                continuation.resume(queryDisplayName(context, uri, cancellationSignal))
            } catch (exception: Exception) {
                continuation.resumeWithException(exception)
            }
        }
    }

    /** 与旧 API 使用相同名称/fallback 规则；CancellationSignal 仅供可取消后台准备阶段使用。 */
    private fun queryDisplayName(context: Context, uri: Uri, cancellationSignal: CancellationSignal?): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, cancellationSignal
        )
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
            }
        return uri.lastPathSegment
    }

    /** 创建独立临时副本；读取失败时删除半成品，避免失败上传占用缓存。 */
    fun copyUriToCache(context: Context, uri: Uri, name: String): File {
        val cacheDir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val out = File.createTempFile("upload_", "_${name.sanitizeFileName().takeLast(80)}", cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开所选文件: $uri" }
                out.outputStream().use { os -> input.copyTo(os) }
            }
            return out
        } catch (exception: Exception) {
            out.delete()
            throw exception
        }
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
