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

import android.util.Xml
import io.github.jinlongliao.ossbrowser.data.StorageType
import com.alibaba.sdk.android.oss.common.HttpMethod
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.common.auth.OSSStsTokenCredentialProvider
import com.alibaba.sdk.android.oss.common.utils.HttpUtil
import com.alibaba.sdk.android.oss.internal.RequestMessage
import com.alibaba.sdk.android.oss.signer.OSSSignerBase
import com.alibaba.sdk.android.oss.signer.OSSSignerParams
import com.alibaba.sdk.android.oss.signer.SignVersion
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ClientException
import com.alibaba.sdk.android.oss.ServiceException
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback
import com.alibaba.sdk.android.oss.model.PutObjectResult
import com.alibaba.sdk.android.oss.model.ResumableUploadRequest
import com.alibaba.sdk.android.oss.model.ResumableUploadResult
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask
import com.alibaba.sdk.android.oss.model.CopyObjectRequest
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.HeadObjectRequest
import com.alibaba.sdk.android.oss.model.ObjectMetadata
import com.alibaba.sdk.android.oss.model.ListBucketsRequest
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import io.github.jinlongliao.ossbrowser.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Date

/**
 * 对象存储操作入口，按配置协议分发到 AWS S3 或 OSS。OSS 业务封装：全部在 IO 线程执行，异常抛出给调用方。目录模型：以 `/` 结尾的 key 表示目录。
 *
 * Bucket / endpoint 都作为显式参数：同一个 profile（AK/SK）可用来访问该账号下任意 bucket；
 * 不同 region 的 bucket 通过传入各自的 `endpoint` 路由到正确区域。
 */
class OssService(private val provider: OssClientProvider) {

    private fun bucketClient(endpoint: String?, profileId: String? = null) =
        provider.current(endpoint, profileId)
            ?: throw IllegalStateException("未选择 OSS 配置，请先在「密钥」中新建并激活。")

    private fun serviceClient() =
        provider.service()
            ?: throw IllegalStateException("未选择 OSS 配置，请先在「密钥」中新建并激活。")

    /** 列出当前账号下所有 bucket。 */
    suspend fun listBuckets(): List<OssBucketInfo> = withContext(Dispatchers.IO) {
        provider.s3Storage()?.let { return@withContext it.listS3Buckets() }
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
        provider.s3Storage(endpoint)?.let { return@withContext it.listS3Objects(bucket, prefix, marker, maxKeys) }
        val (client, _) = bucketClient(endpoint)
        listOssObjects(client, bucket, prefix, marker, maxKeys)
    }

    /**
     * 一次目录扫描捕获同一账号客户端；S3 复用连接池，OSS 分页请求在 IO 线程运行。
     * 只提供当前层的元数据分页函数，不增加网络请求或改变任何文件内容。
     */
    suspend fun <T> withObjectDirectoryListing(
        bucket: String, prefix: String, endpoint: String?, profileId: String? = null,
        action: suspend (suspend (String?, Int) -> ListPage) -> T
    ): T = withContext(Dispatchers.IO) {
        // 分页追加也可能首次构造SDK客户端；捕获和释放都不能依赖调用方恰好已在后台线程。
        provider.s3Storage(endpoint, profileId)?.let {
            return@withContext it.withS3DirectoryListing(bucket, prefix, action)
        }
        val (client, _) = bucketClient(endpoint, profileId)
        action { marker, maxKeys ->
            withContext(Dispatchers.IO) { listOssObjects(client, bucket, prefix, marker, maxKeys) }
        }
    }

    /** 复用已捕获的 OSS 客户端读取单页，保持现有目录分组和元数据映射。 */
    private fun listOssObjects(
        client: OSSClient, bucket: String, prefix: String, marker: String?, maxKeys: Int
    ): ListPage {
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

        return ListPage(
            items = (folders + files).sortedWith(FolderFirstByName),
            nextMarker = if (result.isTruncated) result.nextMarker else null
        )
    }

    companion object {
        /** ListCname 错误响应的最大读取字符数，仅用于提取非敏感错误码。 */
        private const val MAX_ERROR_RESPONSE_LENGTH = 8_192

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

    /**
     * 以 `/` 结尾的空对象代表目录，IO线程完成请求后返回创建的key。
     * @param profileId 提交时捕获的账号ID，避免排队期间切换账号将请求发往其他身份。
     */
    suspend fun mkdir(
        bucket: String,
        prefix: String,
        name: String,
        endpoint: String? = null,
        profileId: String? = null
    ) = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.createS3Directory(bucket, prefix, name) }
        val (client, _) = bucketClient(endpoint, profileId)
        val key = buildString {
            append(prefix)
            append(name.trim('/'))
            append('/')
        }
        val req = PutObjectRequest(bucket, key, ByteArray(0))
        client.putObject(req)
        key
    }

    /**
     * 上传任务的固定文件快照；8 MiB 起使用分片断点续传，小文件及空文件使用普通 PUT。
     * 取消保留断点，等待 SDK 收尾后才返回，避免清理或重试与旧请求争用文件。
     * @param bucket 固定目标 bucket。
     * @param key 固定目标对象路径。
     * @param source 已完整复制的本地快照，上传期间不得修改。
     * @param records 此任务专属的 SDK 断点目录。
     * @param profileId 固定账号 ID。
     * @param endpoint 固定区域地址。
     * @param onProgress SDK 的已发送和总字节数回调，可能由工作线程调用。
     * @return 成功上传的对象路径。
     * @see <a href="https://www.alibabacloud.com/help/en/oss/developer-reference/resumable-upload-4">断点续传</a>
     */
    suspend fun upload(
        bucket: String,
        key: String,
        source: File,
        records: File,
        profileId: String,
        endpoint: String?,
        storageType: StorageType? = null,
        s3Region: String? = null,
        s3ForcePathStyle: Boolean? = null,
        onProgress: (sent: Long, total: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId, storageType, s3Region, s3ForcePathStyle)?.let {
            return@withContext it.uploadS3File(bucket, key, source, records, onProgress)
        }
        val (client, _) = provider.current(endpoint, profileId)
            ?: error("上传账号已删除，请重新选择账号和文件")
        var sdkTask: OSSAsyncTask<*>? = null
        try {
            suspendCancellableCoroutine { continuation ->
                if (source.length() >= 8L * 1024 * 1024) {
                    check(records.mkdirs() || records.isDirectory) { "无法创建上传断点目录" }
                    val request = ResumableUploadRequest(bucket, key, source.absolutePath, records.absolutePath)
                    request.setDeleteUploadOnCancelling(false)
                    request.partSize = maxOf(2L * 1024 * 1024, (source.length() + 9998) / 9999)
                    request.setProgressCallback { _, sent, total -> onProgress(sent, total) }
                    sdkTask = client.asyncResumableUpload(
                        request,
                        object : OSSCompletedCallback<ResumableUploadRequest, ResumableUploadResult> {
                            /** 远端合并成功后才报告完成。 */
                            override fun onSuccess(request: ResumableUploadRequest, result: ResumableUploadResult) {
                                continuation.resume(key)
                            }

                            /** 保留供应方错误，由服务记录完整上下文和堆栈。 */
                            override fun onFailure(
                                request: ResumableUploadRequest,
                                clientException: ClientException?,
                                serviceException: ServiceException?
                            ) {
                                continuation.resumeWithException(
                                    serviceException ?: clientException ?: IllegalStateException("OSS 上传失败")
                                )
                            }
                        }
                    )
                } else {
                    val request = PutObjectRequest(bucket, key, source.absolutePath)
                    request.setProgressCallback { _, sent, total -> onProgress(sent, total) }
                    sdkTask = client.asyncPutObject(
                        request,
                        object : OSSCompletedCallback<PutObjectRequest, PutObjectResult> {
                            /** 普通 PUT 成功后报告完成。 */
                            override fun onSuccess(request: PutObjectRequest, result: PutObjectResult) {
                                continuation.resume(key)
                            }

                            /** 小文件失败同样保留根因。 */
                            override fun onFailure(
                                request: PutObjectRequest,
                                clientException: ClientException?,
                                serviceException: ServiceException?
                            ) {
                                continuation.resumeWithException(
                                    serviceException ?: clientException ?: IllegalStateException("OSS 上传失败")
                                )
                            }
                        }
                    )
                }
                continuation.invokeOnCancellation { sdkTask?.cancel() }
            }
        } finally {
            // SDK cancel 仅请求取消，不代表工作线程已退出；等待退出后才允许重试或删除断点。
            withContext(NonCancellable + Dispatchers.IO) { sdkTask?.waitUntilFinished() }
        }
    }

    /**
     * 在IO线程流式复制对象到调用方输出流，不在内存持有完整文件；取消在下一次读取前生效。
     * @param profileId 提交时捕获的账号ID；为空时兼容使用当前激活账号。
     */
    suspend fun download(
        bucket: String,
        key: String,
        output: OutputStream,
        endpoint: String? = null,
        profileId: String? = null,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.downloadS3Object(bucket, key, output, onProgress = onProgress) }
        val (client, _) = bucketClient(endpoint, profileId)
        val req = GetObjectRequest(bucket, key)
        val result = client.getObject(req)
        val contentLength = result.contentLength
        result.objectContent.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
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

    /**
     * 删除单个object或递归目录；串行逐对象操作，部分失败不回滚已经删除的对象。
     * @param profileId 提交时捕获的账号ID；全部分页和删除沿用同一客户端身份。
     */
    suspend fun delete(
        bucket: String,
        key: String,
        endpoint: String? = null,
        profileId: String? = null
    ) = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.deleteS3Object(bucket, key) }
        val (client, _) = bucketClient(endpoint, profileId)
        if (key.endsWith("/")) {
            var marker: String? = null
            do {
                currentCoroutineContext().ensureActive()
                val req = ListObjectsRequest(bucket).apply {
                    prefix = key
                    this.marker = marker
                    maxKeys = 500
                }
                val res = client.listObjects(req)
                for (s in res.objectSummaries) {
                    currentCoroutineContext().ensureActive()
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
     * - 禁止把目录移入自身子目录；任一copy失败时不能删除该源对象，已完成对象不回滚。
     * @param profileId 提交时捕获的账号ID；全部复制和删除沿用同一客户端身份。
     */
    suspend fun move(
        bucket: String,
        sourceKey: String,
        destKey: String,
        endpoint: String? = null,
        profileId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (sourceKey == destKey) return@withContext
        validateObjectMovePaths(sourceKey, destKey)
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.moveS3Object(bucket, sourceKey, destKey) }
        val (client, _) = bucketClient(endpoint, profileId)

        if (sourceKey.endsWith("/")) {
            require(destKey.endsWith("/")) { "目标 key 需以 / 结尾" }
            var marker: String? = null
            do {
                currentCoroutineContext().ensureActive()
                val req = ListObjectsRequest(bucket).apply {
                    prefix = sourceKey
                    this.marker = marker
                    maxKeys = 500
                }
                val res = client.listObjects(req)
                for (s in res.objectSummaries) {
                    currentCoroutineContext().ensureActive()
                    val rel = s.key.removePrefix(sourceKey)
                    val newKey = destKey + rel
                    copyOne(client, bucket, s.key, newKey)
                    client.deleteObject(DeleteObjectRequest(bucket, s.key))
                }
                marker = if (res.isTruncated) res.nextMarker else null
            } while (marker != null)
        } else {
            copyOne(client, bucket, sourceKey, destKey)
            client.deleteObject(DeleteObjectRequest(bucket, sourceKey))
        }
    }

    /** 判断对象 key 是否存在（只用于路径跳转时判断"文件 / 目录"）。 */
    suspend fun exists(
        bucket: String,
        key: String,
        endpoint: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint)?.let { return@withContext it.s3ObjectExists(bucket, key) }
        if (key.isBlank()) return@withContext false
        val (client, _) = bucketClient(endpoint)
        runCatching { client.doesObjectExist(bucket, key) }.getOrDefault(false)
    }

    /**
     * 将对象下载到内存（byte 数组），用于文本/JSON/XML 等小文件预览。
     * 若对象大小超过 [maxBytes] 或未知但读取时超限，则抛 [IllegalStateException]。
     * @param profileId 预览开始时捕获的账号ID；为空兼容当前激活账号。
     */
    suspend fun downloadBytes(
        bucket: String,
        key: String,
        maxBytes: Long,
        endpoint: String? = null,
        profileId: String? = null
    ): ByteArray = downloadTextSnapshot(bucket, key, maxBytes, endpoint, profileId, recoverVersion = false).first

    /** 缺失 ETag 时补取元数据，再用条件 GET 绑定正文；普通预览无需补取。 */
    suspend fun downloadTextSnapshot(
        bucket: String,
        key: String,
        maxBytes: Long,
        endpoint: String? = null,
        profileId: String? = null,
        recoverVersion: Boolean = true,
        listedETag: String? = null
    ): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let {
            return@withContext it.downloadS3TextSnapshot(bucket, key, maxBytes, recoverVersion, listedETag)
        }
        val (client, _) = bucketClient(endpoint, profileId)
        val initial = readOssTextSnapshot(client, GetObjectRequest(bucket, key), maxBytes)
        if (!recoverVersion || initial.second != null) return@withContext initial
        resolveTextSnapshotVersion(initial, listedETag, readVersion = {
            val head = client.headObject(HeadObjectRequest(bucket, key))
            strongTextETag(head.metadata?.eTag)
                ?: head.responseHeader?.entries?.firstOrNull { it.key.equals("ETag", true) }?.value
        }) { version ->
            val request = GetObjectRequest(bucket, key).apply {
                requestHeaders = mapOf("If-Match" to version, "Accept-Encoding" to "identity")
            }
            readOssTextSnapshot(client, request, maxBytes)
        }
    }

    /** 读取单次 GET 的正文与同一响应的有效版本。 */
    private suspend fun readOssTextSnapshot(
        client: OSSClient, request: GetObjectRequest, maxBytes: Long
    ): Pair<ByteArray, String?> {
        val result = client.getObject(request)
        val contentLength = result.contentLength
        if (contentLength in 1..Long.MAX_VALUE && contentLength > maxBytes) {
            runCatching { result.objectContent.close() }
            throw IllegalStateException(
                "文件过大（${FileUtils.humanSize(contentLength)}），超过预览上限 ${FileUtils.humanSize(maxBytes)}。"
            )
        }
        return result.objectContent.use { input ->
            val buf = ByteArray(64 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
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
            val responseTag = result.responseHeader?.entries?.firstOrNull { it.key.equals("ETag", true) }?.value
            out.toByteArray() to (strongTextETag(result.metadata?.eTag) ?: strongTextETag(responseTag))
        }
    }

    /**
     * 仅供用户确认差异后写回。OSS 先复查 ETag，保留常用元数据后替换原 key。
     * @see <a href="https://www.alibabacloud.com/help/en/oss/user-guide/0017-00000245">OSS PutObject 条件限制</a>
     */
    suspend fun replaceTextObject(
        bucket: String, key: String, etag: String, content: ByteArray, endpoint: String?, profileId: String
    ): String? = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let {
            return@withContext it.replaceS3TextObject(bucket, key, etag, content)
        }
        val (client, _) = bucketClient(endpoint, profileId)
        val head = client.headObject(HeadObjectRequest(bucket, key))
        val previous = head.metadata
        val responseTag = head.responseHeader?.entries?.firstOrNull { it.key.equals("ETag", true) }?.value
        val currentVersion = strongTextETag(previous.eTag) ?: strongTextETag(responseTag)
        check(currentVersion == null || currentVersion == etag) { "远端文件已更新，请重新打开预览。" }
        var contentEncoding = previous.contentEncoding
        if (currentVersion == null || !contentEncoding.isIdentityOssEncoding()) {
            // OSS 可能按传输请求动态压缩，Android SDK 的 HEAD 无法指定 Accept-Encoding。
            // 用不接受动态压缩的条件 GET 核对实际对象编码，响应体不参与写回。
            val conditional = client.getObject(GetObjectRequest(bucket, key).apply {
                requestHeaders = mapOf("If-Match" to etag, "Accept-Encoding" to "identity")
            })
            conditional.objectContent.use {
                val returned = conditional.responseHeader?.entries
                    ?.firstOrNull { it.key.equals("ETag", true) }?.value
                val verifiedVersion = strongTextETag(conditional.metadata?.eTag) ?: strongTextETag(returned)
                check(verifiedVersion == null || verifiedVersion == etag) { "远端文件已更新，请重新打开预览。" }
                contentEncoding = conditional.metadata?.contentEncoding ?: conditional.responseHeader?.entries
                    ?.firstOrNull { it.key.equals("Content-Encoding", true) }?.value
            }
        }
        val headers = previous.rawMetadata.entries.associate { (name, value) -> name.lowercase() to value }
        check(contentEncoding.isIdentityOssEncoding() &&
            (headers["x-oss-tagging-count"]?.toString()?.toIntOrNull() ?: 0) == 0
        ) { "暂不支持更新带内容编码或对象标签的文本。" }
        val metadata = ObjectMetadata().apply {
            userMetadata = previous.userMetadata
            val retained = listOf(
                "Content-Type", "Cache-Control", "Content-Disposition", "Expires", "x-oss-storage-class",
                "x-oss-server-side-encryption", "x-oss-server-side-data-encryption",
                "x-oss-server-side-encryption-key-id"
            )
            retained.forEach { name -> headers[name.lowercase()]?.let { setHeader(name, it) } }
        }
        currentCoroutineContext().ensureActive()
        val updated = client.putObject(PutObjectRequest(bucket, key, content, metadata))
        val updatedHeader = updated.responseHeader?.entries?.firstOrNull { it.key.equals("ETag", true) }?.value
        strongTextETag(updated.eTag) ?: strongTextETag(updatedHeader)
    }

    /**
     * 将对象下载到本地文件（用于 PDF 等需要随机读取的预览场景）。
     * 成功返回目标 [File]。失败时会清理不完整文件。
     * @param profileId 预览开始时捕获的账号ID；为空兼容当前激活账号。
     */
    suspend fun downloadToFile(
        bucket: String,
        key: String,
        target: java.io.File,
        endpoint: String? = null,
        profileId: String? = null,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> }
    ): java.io.File = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.downloadS3File(bucket, key, target, onProgress) }
        val (client, _) = bucketClient(endpoint, profileId)
        val result = client.getObject(GetObjectRequest(bucket, key))
        val total = result.contentLength
        try {
            result.objectContent.use { input ->
                java.io.FileOutputStream(target).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
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
        endpoint: String? = null,
        profileId: String? = null
    ): String = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.presignS3Object(bucket, key, expireSeconds) }
        require(expireSeconds > 0) { "有效期必须为正数" }
        val (client, _) = bucketClient(endpoint, profileId)
        client.presignConstrainedObjectURL(bucket, key, expireSeconds)
    }

    /**
     * 生成无需签名参数的公开对象 URL。
     *
     * 默认地址由 SDK 按当前 Bucket endpoint 生成；指定 [cname] 时只替换协议和域名，保留 SDK
     * 对 Object key 的路径编码。调用方必须确保 Bucket/Object 允许匿名读取，否则访问仍会返回 403。
     *
     * @param bucket Object 所属 Bucket。
     * @param key Object key。
     * @param endpoint Bucket 所在地域的公网 endpoint；为空时使用当前 profile 的 endpoint。
     * @param cname 用户选择的已启用自定义域名；为空时使用 OSS 默认公网域名。
     * @return 不包含签名和过期参数的公开 URL。
     */
    suspend fun publicObjectUrl(
        bucket: String,
        key: String,
        endpoint: String? = null,
        cname: OssCnameInfo? = null,
        profileId: String? = null
    ): String = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext it.publicS3ObjectUrl(bucket, key) }
        val (client, _) = bucketClient(endpoint, profileId)
        val defaultUrl = client.presignPublicObjectURL(bucket, key)
        if (cname == null) {
            defaultUrl
        } else {
            buildCustomDomainUrl(defaultUrl, cname)
        }
    }

    /**
     * 查询 Bucket 已启用的自定义域名。
     *
     * Android SDK 2.9.x 未公开封装 ListCname，因此复用 SDK 的 V4 签名构造短时有效的
     * `GET /?cname` 请求，再直接读取官方 XML 响应。签名 URL 不会进入异常文本或日志。
     * RAM 用户需要 `oss:ListCname` 权限；未绑定域名时 OSS 返回 404，本方法按空列表处理。
     *
     * @param bucket 待查询的 Bucket。
     * @param endpoint Bucket 所在地域的公网 endpoint；为空时使用当前 profile 的 endpoint。
     * @return 状态为 `Enabled` 的自定义域名，保持 OSS 返回顺序。
     * @throws IllegalStateException 网络失败、权限不足或响应无法解析时抛出，消息包含状态码、
     * 请求 ID 和服务错误码，供界面提示和问题定位。
     * @see <a href="https://help.aliyun.com/zh/oss/developer-reference/listcname">阿里云 OSS ListCname</a>
     */
    suspend fun listCnames(
        bucket: String,
        endpoint: String? = null,
        profileId: String? = null
    ): List<OssCnameInfo> = withContext(Dispatchers.IO) {
        provider.s3Storage(endpoint, profileId)?.let { return@withContext emptyList() }
        val (client, profile) = bucketClient(endpoint, profileId)
        val baseUrl = client.presignPublicObjectURL(bucket, "")
        val region = resolveListCnameRegion(baseUrl, profile.region)
        val signedUrl = createListCnameSignedUrl(
            baseUrl = baseUrl,
            bucket = bucket,
            region = region,
            accessKeyId = profile.accessKeyId,
            accessKeySecret = profile.accessKeySecret,
            sessionToken = profile.sessionToken
        )
        val connection = URL(signedUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val requestId = connection.getHeaderField("x-oss-request-id").orEmpty()
                val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                    .take(MAX_ERROR_RESPONSE_LENGTH)
                val errorCode = extractOssErrorCode(responseBody)
                if (statusCode == HttpURLConnection.HTTP_NOT_FOUND && errorCode == "NoSuchCname") {
                    return@withContext emptyList()
                }
                // OSS 的 SignatureDoesNotMatch 响应含 AccessKeyId、签名和 StringToSign。
                // 异常只保留错误码与 RequestId，避免日志或 UI 再次暴露这些认证材料。
                throw IllegalStateException(
                    "ListCname 请求失败：bucket=$bucket, status=$statusCode, " +
                        "code=${errorCode ?: "Unknown"}, requestId=$requestId"
                )
            }
            connection.inputStream.use(::parseCnames)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 以流式方式解析 ListCname XML，避免加载与业务无关的响应字段。
     * Certificate/Status 只在 Certificate 节点内代表 HTTPS 证书状态，不能与 Cname/Status 混用。
     *
     * @param input OSS ListCname 成功响应流；方法不会关闭该流。
     * @return 响应中的全部 Cname 条目。
     */
    private fun parseCnames(input: InputStream): List<OssCnameInfo> {
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        val result = ArrayList<OssCnameInfo>()
        var inCname = false
        var inCertificate = false
        var domain = ""
        var status = ""
        var certificateStatus = ""
        var wildcard = false
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "Cname" -> {
                        inCname = true
                        domain = ""
                        status = ""
                        certificateStatus = ""
                        wildcard = false
                    }
                    "Certificate" -> if (inCname) inCertificate = true
                    "Domain" -> if (inCname) domain = parser.nextText().trim()
                    "IsWildCard" -> if (inCname) wildcard = parser.nextText().trim().equals("true", ignoreCase = true)
                    "Status" -> if (inCname) {
                        val value = parser.nextText().trim()
                        if (inCertificate) certificateStatus = value else status = value
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "Certificate" -> inCertificate = false
                    "Cname" -> {
                        if (!wildcard && !domain.startsWith("*.") && domain.isNotBlank() &&
                            status.equals("Enabled", ignoreCase = true)
                        ) {
                            result += OssCnameInfo(
                                domain = domain,
                                httpsEnabled = certificateStatus.equals("Enabled", ignoreCase = true)
                            )
                        }
                        inCname = false
                    }
                }
            }
            parser.next()
        }
        return result
    }

    /** 整个移动操作沿用提交时捕获的客户端，中途切账号也不能改变复制/删除的身份。 */
    @Throws(ClientException::class, ServiceException::class)
    private fun copyOne(client: OSSClient, bucket: String, src: String, dst: String) {
        if (src == dst) return
        val req = CopyObjectRequest(bucket, src, bucket, dst)
        client.copyObject(req)
    }

    data class ListPage(
        val items: List<OssObjectItem>,
        val nextMarker: String?
    )
}

/** 无 HTTP 内容编码或显式 identity 的对象可以原样写回文本字节。 */
internal fun String?.isIdentityOssEncoding(): Boolean = isNullOrBlank() || trim().equals("identity", ignoreCase = true)

/** 只接受可用作 If-Match 的单一强版本标记。 */
internal fun strongTextETag(raw: String?): String? {
    val value = raw?.trim()?.removeSurrounding("\"") ?: return null
    if (value.isEmpty() || value == "*" || value.startsWith("W/") || value.any { it !in '!'..'~' || it == '"' }) {
        return null
    }
    return "\"$value\""
}

/** HEAD 无版本时可用列表版本发起条件读取，只有服务端接受该条件才生成可写快照。 */
internal suspend fun resolveTextSnapshotVersion(
    initial: Pair<ByteArray, String?>,
    listedETag: String? = null,
    readVersion: suspend () -> String?,
    readMatching: suspend (String) -> Pair<ByteArray, String?>
): Pair<ByteArray, String?> {
    try {
        val headVersion = try {
            strongTextETag(readVersion())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val version = headVersion ?: strongTextETag(listedETag) ?: return initial
        val (data, returnedVersion) = readMatching(version)
        return if (returnedVersion == null || strongTextETag(returnedVersion) == version) data to version else initial
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // 元数据缺失或条件失败时保留原快照供用户阅读。
        return initial
    }
}

/** ListCname 预签名 URL 的有效期，单位为秒。 */
private const val LIST_CNAME_URL_TTL_SECONDS = 60L

/** 根据请求使用的公网或内网端点确定地域，跨地域 Bucket 不沿用旧账号地域。 */
internal fun resolveListCnameRegion(baseUrl: String, configuredRegion: String): String {
    val host = URI(baseUrl).host.orEmpty()
    return Regex("(?:^|\\.)oss-([a-z0-9-]+)\\.aliyuncs\\.com$", RegexOption.IGNORE_CASE)
        .find(host)?.groupValues?.get(1)?.lowercase()?.removeSuffix("-internal")
        ?: configuredRegion.takeIf { it.isNotBlank() }
        ?: error("ListCname 需要可识别的 OSS 地域，请使用 Bucket 的地域 Endpoint。")
}

/**
 * 使用 SDK 的 V4 签名器签入 cname 子资源，并保留 SDK 已解析的请求地址。
 * 返回值含短期签名凭据，仅用于发送请求。
 * @see <a href="https://www.alibabacloud.com/help/en/oss/developer-reference/add-signatures-to-urls">OSS V4 URL 签名</a>
 */
internal fun createListCnameSignedUrl(
    baseUrl: String,
    bucket: String,
    region: String,
    accessKeyId: String,
    accessKeySecret: String,
    sessionToken: String = ""
): String {
    val endpoint = URI(baseUrl)
    require(endpoint.scheme == "https" && endpoint.rawQuery == null && endpoint.rawFragment == null) {
        "ListCname requires an HTTPS bucket endpoint"
    }
    val credentials = if (sessionToken.isEmpty()) {
        OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret)
    } else {
        OSSStsTokenCredentialProvider(accessKeyId, accessKeySecret, sessionToken)
    }
    val context = OSSSignerParams("/$bucket/", credentials).apply {
        product = "oss"
        this.region = region
        expiration = LIST_CNAME_URL_TTL_SECONDS
    }
    val request = RequestMessage().apply {
        method = HttpMethod.GET
        addParameter("cname", "")
    }
    OSSSignerBase.createRequestPresigner(SignVersion.V4, context).presign(request)
    return baseUrl + "?" + HttpUtil.paramToQueryString(request.parameters, Charsets.UTF_8.name())
}

/**
 * 从 OSS XML 错误响应中提取短错误码。
 *
 * 只允许不含空白和 XML 标记的短值，避免把 AccessKeyId、Signature、StringToSign 等
 * 服务端诊断字段带入异常、日志或界面。
 *
 * @param responseBody 截断后的 OSS XML 错误响应。
 * @return 安全的错误码；格式不匹配时返回 `null`。
 */
internal fun extractOssErrorCode(responseBody: String): String? =
    Regex("<Code>\\s*([^<\\s]{1,128})\\s*</Code>", RegexOption.IGNORE_CASE)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
