package com.ossbrowser.app.oss

import android.content.Context
import android.net.Uri
import com.alibaba.sdk.android.oss.ClientException
import com.alibaba.sdk.android.oss.ServiceException
import com.alibaba.sdk.android.oss.model.CopyObjectRequest
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.ListBucketsRequest
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.ossbrowser.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Date

/**
 * OSS 业务封装：全部在 IO 线程执行，异常抛出给调用方。目录模型：以 `/` 结尾的 key 表示目录。
 *
 * Bucket / endpoint 都作为显式参数：同一个 profile（AK/SK）可用来访问该账号下任意 bucket；
 * 不同 region 的 bucket 通过传入各自的 `endpoint` 路由到正确区域。
 */
class OssService(private val provider: OssClientProvider) {

    private fun bucketClient(endpoint: String?) =
        provider.current(endpoint)
            ?: throw IllegalStateException("未选择 OSS 配置，请先在「密钥」中新建并激活。")

    private fun serviceClient() =
        provider.service()
            ?: throw IllegalStateException("未选择 OSS 配置，请先在「密钥」中新建并激活。")

    /** 列出当前账号下所有 bucket。 */
    suspend fun listBuckets(): List<OssBucketInfo> = withContext(Dispatchers.IO) {
        val (client, _) = serviceClient()
        val result = client.listBuckets(ListBucketsRequest())
        result.buckets.map { b ->
            OssBucketInfo(
                name = b.name ?: "",
                location = b.location,
                createDate = b.createDate?.let { Date(it.time) },
                extranetEndpoint = b.extranetEndpoint,
                storageClass = b.storageClass
            )
        }
    }

    /** 列出 prefix 下的对象和子目录（按 `/` 分隔）。 */
    suspend fun list(
        bucket: String,
        prefix: String,
        marker: String? = null,
        maxKeys: Int = 200,
        endpoint: String? = null
    ): ListPage = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val req = ListObjectsRequest(bucket).apply {
            this.prefix = prefix
            delimiter = "/"
            this.marker = marker
            this.maxKeys = maxKeys
        }
        val result = client.listObjects(req)

        val folders = result.commonPrefixes
            .filter { it != prefix }
            .map { OssObjectItem.Folder(it) }

        val files = result.objectSummaries
            .filter { it.key != prefix }
            .map {
                OssObjectItem.File(
                    key = it.key,
                    size = it.size,
                    lastModified = it.lastModified?.let { d -> Date(d.time) },
                    etag = it.eTag
                )
            }

        ListPage(
            items = (folders + files).sortedWith(FolderFirstByName),
            nextMarker = if (result.isTruncated) result.nextMarker else null
        )
    }

    companion object {
        /** 文件夹优先、名称升序（Locale-aware，不区分大小写）。 */
        val FolderFirstByName: Comparator<OssObjectItem> = Comparator { a, b ->
            val folderA = a is OssObjectItem.Folder
            val folderB = b is OssObjectItem.Folder
            when {
                folderA && !folderB -> -1
                !folderA && folderB -> 1
                else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
            }
        }
    }

    /** 以 `/` 结尾的空对象代表"目录"。 */
    suspend fun mkdir(
        bucket: String,
        prefix: String,
        name: String,
        endpoint: String? = null
    ) = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val key = buildString {
            append(prefix)
            append(name.trim('/'))
            append('/')
        }
        val req = PutObjectRequest(bucket, key, ByteArray(0))
        client.putObject(req)
        key
    }

    suspend fun upload(
        context: Context,
        bucket: String,
        prefix: String,
        uri: Uri,
        overrideName: String? = null,
        endpoint: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val fileName = overrideName?.takeIf { it.isNotBlank() }
            ?: FileUtils.displayName(context, uri)
            ?: "unnamed_${System.currentTimeMillis()}"

        val tempFile = FileUtils.copyUriToCache(context, uri, fileName)
        try {
            val key = prefix + fileName
            val req = PutObjectRequest(bucket, key, tempFile.absolutePath)
            req.setProgressCallback { _, sent, total -> onProgress(sent, total) }
            client.putObject(req)
            key
        } finally {
            tempFile.delete()
        }
    }

    suspend fun download(
        bucket: String,
        key: String,
        output: OutputStream,
        endpoint: String? = null,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val req = GetObjectRequest(bucket, key)
        val result = client.getObject(req)
        val contentLength = result.contentLength
        result.objectContent.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                total += n
                onProgress(total, contentLength)
            }
            output.flush()
            total
        }
    }

    /** 删除单个 object；若是"目录"前缀，则递归删除所有子项。 */
    suspend fun delete(
        bucket: String,
        key: String,
        endpoint: String? = null
    ) = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        if (key.endsWith("/")) {
            var marker: String? = null
            do {
                val req = ListObjectsRequest(bucket).apply {
                    prefix = key
                    this.marker = marker
                    maxKeys = 500
                }
                val res = client.listObjects(req)
                for (s in res.objectSummaries) {
                    client.deleteObject(DeleteObjectRequest(bucket, s.key))
                }
                marker = if (res.isTruncated) res.nextMarker else null
            } while (marker != null)
        } else {
            client.deleteObject(DeleteObjectRequest(bucket, key))
        }
    }

    /**
     * 移动 / 重命名：通过 copy + delete 实现。
     * - 对"目录" key（以 `/` 结尾），递归搬移其下所有对象。
     * - 跨 bucket 暂不支持，目标 bucket 与源 bucket 相同。
     */
    suspend fun move(
        bucket: String,
        sourceKey: String,
        destKey: String,
        endpoint: String? = null
    ) = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)

        if (sourceKey.endsWith("/")) {
            require(destKey.endsWith("/")) { "目标 key 需以 / 结尾" }
            var marker: String? = null
            do {
                val req = ListObjectsRequest(bucket).apply {
                    prefix = sourceKey
                    this.marker = marker
                    maxKeys = 500
                }
                val res = client.listObjects(req)
                for (s in res.objectSummaries) {
                    val rel = s.key.removePrefix(sourceKey)
                    val newKey = destKey + rel
                    copyOne(bucket, s.key, newKey, endpoint)
                    client.deleteObject(DeleteObjectRequest(bucket, s.key))
                }
                marker = if (res.isTruncated) res.nextMarker else null
            } while (marker != null)
        } else {
            copyOne(bucket, sourceKey, destKey, endpoint)
            client.deleteObject(DeleteObjectRequest(bucket, sourceKey))
        }
    }

    /** 判断对象 key 是否存在（只用于路径跳转时判断"文件 / 目录"）。 */
    suspend fun exists(
        bucket: String,
        key: String,
        endpoint: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext false
        val (client, _) = bucketClient(endpoint)
        runCatching { client.doesObjectExist(bucket, key) }.getOrDefault(false)
    }

    /**
     * 将对象下载到内存（byte 数组），用于文本/JSON/XML 等小文件预览。
     * 若对象大小超过 [maxBytes] 或未知但读取时超限，则抛 [IllegalStateException]。
     */
    suspend fun downloadBytes(
        bucket: String,
        key: String,
        maxBytes: Long,
        endpoint: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val result = client.getObject(GetObjectRequest(bucket, key))
        val contentLength = result.contentLength
        if (contentLength in 1..Long.MAX_VALUE && contentLength > maxBytes) {
            runCatching { result.objectContent.close() }
            throw IllegalStateException(
                "文件过大（${FileUtils.humanSize(contentLength)}），超过预览上限 ${FileUtils.humanSize(maxBytes)}。"
            )
        }
        result.objectContent.use { input ->
            val buf = ByteArray(64 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBytes) {
                    throw IllegalStateException(
                        "文件过大（已读取 ${FileUtils.humanSize(total)}），超过预览上限 ${FileUtils.humanSize(maxBytes)}。"
                    )
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    /**
     * 将对象下载到本地文件（用于 PDF 等需要随机读取的预览场景）。
     * 成功返回目标 [File]。失败时会清理不完整文件。
     */
    suspend fun downloadToFile(
        bucket: String,
        key: String,
        target: java.io.File,
        endpoint: String? = null,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }
    ): java.io.File = withContext(Dispatchers.IO) {
        val (client, _) = bucketClient(endpoint)
        val result = client.getObject(GetObjectRequest(bucket, key))
        val total = result.contentLength
        try {
            result.objectContent.use { input ->
                java.io.FileOutputStream(target).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            target
        } catch (e: Throwable) {
            runCatching { if (target.exists()) target.delete() }
            throw e
        }
    }

    /**
     * 生成带签名的预签名 URL，默认使用 `extranetEndpoint`。
     * @param expireSeconds 有效期（秒）。OSS 上限约 7 天（604800）。
     */
    suspend fun presignUrl(
        bucket: String,
        key: String,
        expireSeconds: Long,
        endpoint: String? = null
    ): String = withContext(Dispatchers.IO) {
        require(expireSeconds > 0) { "有效期必须为正数" }
        val (client, _) = bucketClient(endpoint)
        client.presignConstrainedObjectURL(bucket, key, expireSeconds)
    }

    @Throws(ClientException::class, ServiceException::class)
    private fun copyOne(bucket: String, src: String, dst: String, endpoint: String?) {
        if (src == dst) return
        val client = bucketClient(endpoint).first
        val req = CopyObjectRequest(bucket, src, bucket, dst)
        client.copyObject(req)
    }

    data class ListPage(
        val items: List<OssObjectItem>,
        val nextMarker: String?
    )
}
