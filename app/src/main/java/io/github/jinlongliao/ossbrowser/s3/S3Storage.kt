package io.github.jinlongliao.ossbrowser.s3

import android.util.Log
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CopyObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.ListBucketsRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.oss.OssBucketInfo
import io.github.jinlongliao.ossbrowser.oss.OssObjectItem
import io.github.jinlongliao.ossbrowser.oss.OssService
import io.github.jinlongliao.ossbrowser.oss.validateObjectMovePaths
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Date
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 将 AWS S3 标准操作转换为现有文件模块模型，调用者继续使用同一文件界面。
 * 每次操作持有自己的客户端并在结束时关闭，账号切换不会改变飞行中的请求。
 * @param profile 提交操作时的配置快照，凭据只在内存使用。
 * @param endpoint 当前 Bucket 的区域端点；未提供时使用配置值或 SDK 区域默认值。
 * @see <a href="https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/use-services-s3.html">AWS S3</a>
 */
class S3Storage internal constructor(
    private val profile: OssProfile,
    private val endpoint: String?,
    /** 可注入的客户端工厂，用于本地协议测试；生产使用显式配置创建 SDK 客户端。 */
    private val s3ClientFactory: (() -> S3Client)? = null
) {
    /** 固定本次签名区域，Bucket 列表的标准区域端点优先于配置默认区域。 */
    private val region = resolveS3Region(profile, endpoint)

    /** 仅使用显式配置凭据，不读取主机默认 AWS 账号或环境变量。 */
    internal fun createS3Client(): S3Client {
        s3ClientFactory?.let { return it() }
        require(isS3ConnectionValid(region, endpoint ?: profile.endpoint)) { "请检查 AWS Region 和 HTTPS Endpoint" }
        return S3Client {
            this.region = this@S3Storage.region
            credentialsProvider = StaticCredentialsProvider(Credentials(
                accessKeyId = profile.accessKeyId,
                secretAccessKey = profile.accessKeySecret,
                sessionToken = profile.sessionToken.takeIf { it.isNotBlank() }
            ))
            forcePathStyle = profile.forcePathStyle
            val configured = endpoint?.takeIf { it.isNotBlank() } ?: profile.endpoint.takeIf { it.isNotBlank() }
            configured?.let {
                require(it.startsWith("https://")) { "S3 Endpoint 必须使用 HTTPS" }
                endpointUrl = Url.parse(it)
            }
        }
    }

    /** 统一记录失败阶段、目标、供应方异常及堆栈；取消按生命周期向上传播，不输出凭据。 */
    private suspend fun <T> withS3Client(
        stage: String, bucket: String? = null, key: String? = null, action: suspend (S3Client) -> T
    ): T = try {
        createS3Client().use { action(it) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Log.e("S3Storage", "S3 operation=$stage bucket=$bucket key=$key region=$region failed", exception)
        throw exception
    }

    /** 分页列出账号 Bucket；配置默认 Bucket 时界面沿用原流程直接进入，无需此权限。 */
    suspend fun listS3Buckets(): List<OssBucketInfo> = withS3Client("listBuckets") { client ->
        val buckets = mutableListOf<OssBucketInfo>()
        var token: String? = null
        do {
            val response = client.listBuckets(ListBucketsRequest {
                maxBuckets = 1000
                continuationToken = token
            })
            buckets += response.buckets.orEmpty().map { bucket ->
                val bucketRegion = bucket.bucketRegion ?: region
                OssBucketInfo(
                    name = bucket.name.orEmpty(), location = bucketRegion,
                    createDate = bucket.creationDate?.let { Date(it.epochSeconds * 1000) },
                    extranetEndpoint = profile.endpoint.takeIf { it.isNotBlank() } ?: awsS3Endpoint(bucketRegion),
                    storageClass = null
                )
            }
            val next = response.continuationToken
            check(next == null || next != token) { "S3 Bucket 列表返回了重复分页令牌" }
            token = next
        } while (token != null)
        buckets
    }

    /** 目录与文件映射到原列表；marker 是不透明的 V2 continuationToken。 */
    suspend fun listS3Objects(bucket: String, prefix: String, marker: String?, maxKeys: Int): OssService.ListPage =
        withS3Client("listObjects", bucket, prefix) { client ->
            listS3ObjectsWithClient(client, bucket, prefix, marker, maxKeys)
        }

    /** 一次完整目录扫描共享同一个客户端/连接池，结束后仍按原生命周期关闭。 */
    internal suspend fun <T> withS3DirectoryListing(
        bucket: String, prefix: String,
        action: suspend (suspend (String?, Int) -> OssService.ListPage) -> T
    ): T = withS3Client("listDirectory", bucket, prefix) { client ->
        action { marker, maxKeys -> listS3ObjectsWithClient(client, bucket, prefix, marker, maxKeys) }
    }

    /** 使用本次扫描捕获的客户端读取一页，避免每页重新建池和重复 TLS 连接。 */
    private suspend fun listS3ObjectsWithClient(
        client: S3Client, bucket: String, prefix: String, marker: String?, maxKeys: Int
    ): OssService.ListPage {
        val response = client.listObjectsV2(ListObjectsV2Request {
            this.bucket = bucket
            this.prefix = prefix
            delimiter = "/"
            continuationToken = marker
            this.maxKeys = maxKeys
        })
        val folders = response.commonPrefixes.orEmpty().mapNotNull { it.prefix }
            .filter { it != prefix }.map { OssObjectItem.Folder(it) }
        val files = response.contents.orEmpty().filter { it.key != prefix }.map { item ->
            OssObjectItem.File(
                item.key.orEmpty(), item.size ?: 0,
                item.lastModified?.let { Date(it.epochSeconds * 1000) }, item.eTag
            )
        }
        return OssService.ListPage(
            (folders + files).sortedWith(OssService.FolderFirstByName),
            response.nextContinuationToken.takeIf { response.isTruncated == true }
        )
    }

    /** 创建以斜杠结尾的零字节目录对象，与 OSS 的目录语义一致。 */
    suspend fun createS3Directory(
        bucket: String, prefix: String, name: String
    ): String = withS3Client("mkdir", bucket, prefix) { client ->
        val key = prefix + name.trim('/') + "/"
        client.putObject(PutObjectRequest {
            this.bucket = bucket
            this.key = key
            body = ByteStream.fromBytes(byteArrayOf())
        })
        key
    }

    /** 流式下载并检查取消，不将文件全部加载到内存；不关闭调用方拥有的输出流。 */
    suspend fun downloadS3Object(
        bucket: String, key: String, output: OutputStream, maxBytes: Long = Long.MAX_VALUE,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Long = withS3Client("download", bucket, key) { client ->
        client.getObject(GetObjectRequest {
            this.bucket = bucket
            this.key = key
        }) { response ->
            val length = response.contentLength ?: -1L
            check(length <= maxBytes) { "文件超过预览大小上限" }
            var read = 0L
            requireNotNull(response.body) { "S3 未返回文件内容" }.toInputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count <= maxBytes - read) { "文件超过预览大小上限" }
                    output.write(buffer, 0, count)
                    read += count
                    onProgress(read, length)
                }
            }
            output.flush()
            read
        }
    }

    /** 小文件预览仍使用原界面的上限，未知 Content-Length 时也在读取阶段限制字节数。 */
    suspend fun downloadS3Bytes(bucket: String, key: String, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        downloadS3Object(bucket, key, output, maxBytes)
        return output.toByteArray()
    }

    /** 下载失败不保留预览半成品。 */
    suspend fun downloadS3File(bucket: String, key: String, target: File, onProgress: (Long, Long) -> Unit): File {
        try {
            target.outputStream().use { downloadS3Object(bucket, key, it, onProgress = onProgress) }
            return target
        } catch (exception: Exception) {
            target.delete()
            throw exception
        }
    }

    /** 只将明确的不存在响应作为 false；鉴权和网络失败必须继续报告给调用方。 */
    suspend fun s3ObjectExists(
        bucket: String, key: String
    ): Boolean = withS3Client("headObject", bucket, key) { client ->
        if (key.isBlank()) return@withS3Client false
        try {
            client.headObject(HeadObjectRequest {
                this.bucket = bucket
                this.key = key
            })
            true
        } catch (exception: S3Exception) {
            if (exception.sdkErrorMetadata.errorCode in setOf("NotFound", "NoSuchKey", "404")) {
                false
            } else {
                throw exception
            }
        }
    }

    /** 有界分页获取递归操作的候选对象，不使用目录 delimiter。 */
    private suspend fun visitS3Objects(
        client: S3Client, bucket: String, prefix: String, action: suspend (String) -> Unit
    ) {
        var token: String? = null
        do {
            val page = client.listObjectsV2(ListObjectsV2Request {
                this.bucket = bucket
                this.prefix = prefix
                maxKeys = 500
                continuationToken = token
            })
            page.contents.orEmpty().forEach { item -> item.key?.let { action(it) } }
            val next = page.nextContinuationToken.takeIf { page.isTruncated == true }
            check(next == null || next != token) { "S3 对象列表返回了重复分页令牌" }
            token = next
        } while (token != null)
    }

    /** 删除对象或前缀内对象，版本化 Bucket 采用 S3 默认删除标记语义。 */
    suspend fun deleteS3Object(bucket: String, key: String) = withS3Client("delete", bucket, key) { client ->
        val deleteOne: suspend (String) -> Unit = { target ->
            client.deleteObject(DeleteObjectRequest {
                this.bucket = bucket
                this.key = target
            })
        }
        if (key.endsWith('/')) visitS3Objects(client, bucket, key, deleteOne) else deleteOne(key)
    }

    /** 移动沿用复制后删除；目标复制失败则保留源，不提供跨 Bucket 或原子重命名保证。 */
    suspend fun moveS3Object(
        bucket: String, sourceKey: String, destKey: String
    ) = withS3Client("move", bucket, sourceKey) { client ->
        if (sourceKey == destKey) return@withS3Client
        validateObjectMovePaths(sourceKey, destKey)
        val moveOne: suspend (String) -> Unit = { source ->
            val target = if (sourceKey.endsWith('/')) destKey + source.removePrefix(sourceKey) else destKey
            client.copyObject(CopyObjectRequest {
                this.bucket = bucket
                key = target
                copySource = encodeS3ObjectKey("$bucket/$source")
            })
            client.deleteObject(DeleteObjectRequest {
                this.bucket = bucket
                key = source
            })
        }
        if (sourceKey.endsWith('/')) visitS3Objects(client, bucket, sourceKey, moveOne) else moveOne(sourceKey)
    }

    /** 签名时效限制为 S3 SigV4 的七天；临时凭证过期会使链接提前失效。 */
    suspend fun presignS3Object(
        bucket: String, key: String, expireSeconds: Long
    ): String = withS3Client("presign", bucket, key) { client ->
        require(expireSeconds in 1..604800) { "S3 签名有效期须为 1 秒至 7 天" }
        client.presignGetObject(GetObjectRequest {
            this.bucket = bucket
            this.key = key
        }, expireSeconds.seconds)
            .url.toString()
    }

    /** 上传复用应用任务快照，S3 断点独立于 OSS；完成后由应用任务服务释放本地文件。 */
    suspend fun uploadS3File(
        bucket: String, key: String, source: File, records: File, onProgress: (Long, Long) -> Unit
    ): String = withS3Client("upload", bucket, key) { client ->
        val target = listOf(profile.id, region, endpoint.orEmpty(), profile.endpoint, bucket, key).joinToString("\n")
        uploadS3Multipart(client, bucket, key, source, records, target, onProgress)
    }

    /** 公共链接沿用现有分享弹窗；S3 未提供 OSS 的 ListCname 接口。 */
    fun publicS3ObjectUrl(bucket: String, key: String): String = s3PublicObjectUrl(profile, endpoint, bucket, key)
}
