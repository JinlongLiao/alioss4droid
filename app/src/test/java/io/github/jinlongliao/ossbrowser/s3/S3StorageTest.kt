package io.github.jinlongliao.ossbrowser.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest
import org.robolectric.RuntimeEnvironment
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.jinlongliao.ossbrowser.oss.loadSortedObjectDirectory
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField
import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** 用真实 AWS SDK 向本地模拟端点发请求，验证签名、对象操作、续传和取消；不调用真实 AWS。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class S3StorageTest {
    /** 每项测试结束清理本地快照及断点。 */
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** 全部使用无效测试凭据，禁止读取本机 AWS 配置。 */
    private val profile = OssProfile(
        name = "test", endpoint = "", accessKeyId = "test-key", accessKeySecret = "test-secret",
        storageType = StorageType.AWS_S3, region = "us-east-1", sessionToken = "test-token", forcePathStyle = true
    )

    /** 测试专用 HTTP 端点，生产仍强制 HTTPS；工厂保证每次操作得到独立客户端。 */
    private fun storage(server: MockWebServer, onClientCreated: () -> Unit = {}) = S3Storage(profile, null) {
        onClientCreated()
        S3Client {
            region = profile.region
            forcePathStyle = true
            endpointUrl = Url.parse(server.url("/").toString())
            credentialsProvider = StaticCredentialsProvider(Credentials("test-key", "test-secret", "test-token"))
        }
    }

    /** XML 响应遵循 S3 2006-03-01 命名空间。 */
    private fun xml(body: String) = MockResponse().setHeader("Content-Type", "application/xml").setBody(body)

    /** V2 分页令牌和中文对象名映射回原文件模型，真实请求包含 SigV4 和会话令牌。 */
    @Test
    fun listUsesSigV4SessionTokenAndPreservesContinuationToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(xml("""<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                <IsTruncated>true</IsTruncated><NextContinuationToken>next+token/=</NextContinuationToken>
                <CommonPrefixes><Prefix>folder/</Prefix></CommonPrefixes>
                <Contents><Key>中文.txt</Key><Size>3</Size><ETag>etag</ETag></Contents>
                </ListBucketResult>"""))
            val page = storage(server).listS3Objects("bucket", "", "input+token", 100)
            assertEquals("next+token/=", page.nextMarker)
            assertEquals(listOf("folder/", "中文.txt"), page.items.map { it.key })
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(request.getHeader("Authorization").orEmpty().startsWith("AWS4-HMAC-SHA256"))
            assertEquals("test-token", request.getHeader("x-amz-security-token"))
            assertEquals("input+token", request.requestUrl?.queryParameter("continuation-token"))
        }
    }

    /** 下载上限在 Content-Length 已知时提前拒绝，同时保留读取结果与输出流语义。 */
    @Test
    fun previewDownloadHonorsSizeLimit() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abcd"))
            val output = ByteArrayOutputStream()
            try {
                storage(server).downloadS3Object("bucket", "file", output, maxBytes = 3)
                error("应拒绝超限预览")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("上限"))
            }
            assertEquals(0, output.size())
            server.enqueue(MockResponse().setBody("abc"))
            assertEquals("abc", String(storage(server).downloadS3Bytes("bucket", "file", 3)))
        }
    }

    /** 零字节文件采用普通 PUT，不创建非法的空 multipart 会话。 */
    @Test
    fun emptyFileUsesOrdinaryPut() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("ETag", "empty"))
            val source = temporaryFolder.newFile("empty")
            storage(server).uploadS3File("bucket", "empty.txt", source, temporaryFolder.newFolder()) { _, _ -> }
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("PUT", request.method)
            assertEquals("/bucket/empty.txt", request.requestUrl?.encodedPath)
            assertEquals("PutObject", request.requestUrl?.queryParameter("x-id"))
            assertEquals(0L, request.bodySize)
        }
    }

    /** 第二片失败后，下一次只上传缺失分片；通过请求序列验证，不靠进度条推断。 */
    @Test
    fun resumeQueriesServerAndSkipsCompletedPart() = runBlocking {
        MockWebServer().use { server ->
            server.bodyLimit = 1024
            server.enqueue(xml("<InitiateMultipartUploadResult><UploadId>session-1</UploadId></InitiateMultipartUploadResult>"))
            server.enqueue(MockResponse().setHeader("ETag", "part-1").setHeader("x-amz-checksum-crc32", "AAAAAA==").setSocketPolicy(SocketPolicy.EXPECT_CONTINUE))
            server.enqueue(xml("<Error><Code>AccessDenied</Code><Message>denied</Message></Error>").setResponseCode(403).setSocketPolicy(SocketPolicy.EXPECT_CONTINUE))
            val source = temporaryFolder.newFile("large")
            RandomAccessFile(source, "rw").use { it.setLength(9L * 1024 * 1024) }
            val records = temporaryFolder.newFolder("records")
            try {
                storage(server).uploadS3File("bucket", "large.bin", source, records) { _, _ -> }
                error("应传播第二片错误")
            } catch (expected: S3Exception) {
                assertEquals("AccessDenied", expected.sdkErrorMetadata.errorCode)
            }
            assertTrue(File(records, "s3.json").exists())
            server.enqueue(xml("""<ListPartsResult><IsTruncated>false</IsTruncated>
                <Part><PartNumber>1</PartNumber><ETag>part-1</ETag><Size>8388608</Size><ChecksumCRC32>AAAAAA==</ChecksumCRC32></Part></ListPartsResult>"""))
            server.enqueue(MockResponse().setHeader("ETag", "part-2").setHeader("x-amz-checksum-crc32", "BBBBBB==").setSocketPolicy(SocketPolicy.EXPECT_CONTINUE))
            server.enqueue(xml("<CompleteMultipartUploadResult><ETag>complete</ETag></CompleteMultipartUploadResult>"))
            val progress = mutableListOf<Long>()
            storage(server).uploadS3File("bucket", "large.bin", source, records) { sent, _ -> progress += sent }
            val requests = (1..6).map { requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            assertEquals(1, requests.count { it.requestUrl?.queryParameter("partNumber") == "1" })
            assertEquals(2, requests.count { it.requestUrl?.queryParameter("partNumber") == "2" })
            assertTrue(requests[3].path.orEmpty().contains("uploadId=session-1"))
            val completion = requests.last().body.readUtf8()
            assertTrue(completion.contains("<ChecksumCRC32>AAAAAA==</ChecksumCRC32>"))
            assertTrue(completion.contains("<ChecksumCRC32>BBBBBB==</ChecksumCRC32>"))
            assertTrue(progress.contains(8L * 1024 * 1024))
            assertEquals(source.length(), progress.last())
            assertFalse(File(records, "s3.json").exists())
        }
    }

    /** 取消真实挂起 HTTP 请求后保留会话，任务不能错误地继续合并对象。 */
    @Test
    fun cancellationKeepsCheckpointWithoutCompletingUpload() = runBlocking {
        MockWebServer().use { server ->
            server.bodyLimit = 1024
            server.enqueue(xml("<InitiateMultipartUploadResult><UploadId>cancel-session</UploadId></InitiateMultipartUploadResult>"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.EXPECT_CONTINUE).setHeadersDelay(5, TimeUnit.SECONDS))
            val source = temporaryFolder.newFile("cancel")
            RandomAccessFile(source, "rw").use { it.setLength(8L * 1024 * 1024) }
            val records = temporaryFolder.newFolder("cancel-records")
            val job = launch(Dispatchers.IO) {
                storage(server).uploadS3File("bucket", "cancel.bin", source, records) { _, _ -> }
            }
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(server.takeRequest(10, TimeUnit.SECONDS))
                    requireNotNull(server.takeRequest(10, TimeUnit.SECONDS))
                }
            } finally {
                job.cancel()
                withTimeout(5000) { job.join() }
            }
            assertTrue(job.isCancelled)
            assertTrue(File(records, "s3.json").exists())
            assertEquals(2, server.requestCount)
        }
    }
    /** AWS SDK 升级共享 HTTP 依赖后，原 OSS SDK 的实际请求仍需保持可用。 */
    @Test
    fun ossSdkStillListsUploadsDownloadsAndDeletes() {
        MockWebServer().use { server ->
            val client = OSSClient(
                RuntimeEnvironment.getApplication(), server.url("/").toString(),
                OSSPlainTextAKSKCredentialProvider("test-key", "test-secret"),
                ClientConfiguration().apply { isHttpDnsEnable = false }
            )
            server.enqueue(xml("""<ListBucketResult><Name>bucket</Name><IsTruncated>false</IsTruncated>
                <Contents><Key>file.txt</Key><Size>3</Size><ETag>etag</ETag></Contents></ListBucketResult>"""))
            assertEquals("file.txt", client.listObjects(ListObjectsRequest("bucket")).objectSummaries.single().key)
            server.enqueue(MockResponse().setHeader("ETag", "etag"))
            client.putObject(PutObjectRequest("bucket", "file.txt", "abc".toByteArray()))
            server.enqueue(MockResponse().setBody("abc"))
            assertEquals("abc", client.getObject(GetObjectRequest("bucket", "file.txt")).objectContent.bufferedReader().use {
                it.readText()
            })
            server.enqueue(MockResponse().setResponseCode(204))
            client.deleteObject(DeleteObjectRequest("bucket", "file.txt"))
            val methods = (1..4).map { requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).method }
            assertEquals(listOf("GET", "PUT", "GET", "DELETE"), methods)
        }
    }
    /** 复制失败时禁止删除源文件；中文、空格和加号在复制源头中必须编码。 */
    @Test
    fun failedMoveDoesNotDeleteSource() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(xml("<Error><Code>AccessDenied</Code></Error>").setResponseCode(403))
            try {
                storage(server).moveS3Object("bucket", "中文 +.txt", "target.txt")
                error("复制失败必须传播")
            } catch (expected: S3Exception) {
                assertEquals("AccessDenied", expected.sdkErrorMetadata.errorCode)
            }
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("PUT", request.method)
            assertEquals("bucket/%E4%B8%AD%E6%96%87%20%2B.txt", request.getHeader("x-amz-copy-source"))
            assertEquals(1, server.requestCount)
        }
    }

    /** 前缀删除消费全部分页，只删除明确返回的对象。全部请求在本机模拟服务执行。 */
    @Test
    fun directoryDeletionUsesContinuationToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(xml("""<ListBucketResult><IsTruncated>true</IsTruncated>
                <NextContinuationToken>next-token</NextContinuationToken>
                <Contents><Key>folder/a</Key><Size>1</Size></Contents></ListBucketResult>"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(xml("""<ListBucketResult><IsTruncated>false</IsTruncated>
                <Contents><Key>folder/b</Key><Size>1</Size></Contents></ListBucketResult>"""))
            server.enqueue(MockResponse().setResponseCode(204))
            var clientsCreated = 0
            storage(server) { clientsCreated++ }.deleteS3Object("bucket", "folder/")
            assertEquals(1, clientsCreated)
            val requests = (1..4).map { requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            assertEquals(listOf("GET", "DELETE", "GET", "DELETE"), requests.map { it.method })
            assertEquals("next-token", requests[2].requestUrl?.queryParameter("continuation-token"))
            assertEquals("/bucket/folder/a", requests[1].requestUrl?.encodedPath)
            assertEquals("/bucket/folder/b", requests[3].requestUrl?.encodedPath)
        }
    }

    /** 签名链接包含会话令牌和有限有效期；生成链接本身不发送文件读取请求。 */
    @Test
    fun presignUsesConfiguredSessionWithoutReadingObject() = runBlocking {
        MockWebServer().use { server ->
            val url = storage(server).presignS3Object("bucket", "file.txt", 60).toHttpUrl()
            assertEquals("60", url.queryParameter("X-Amz-Expires"))
            assertEquals("test-token", url.queryParameter("X-Amz-Security-Token"))
            assertTrue(url.queryParameter("X-Amz-Signature").orEmpty().isNotBlank())
            assertEquals(0, server.requestCount)
        }
    }
    /** 快照被意外删除时必须失败，不能把 length=0 当空文件覆盖远端对象。 */
    @Test
    fun missingSnapshotDoesNotUploadEmptyObject() = runBlocking {
        MockWebServer().use { server ->
            try {
                storage(server).uploadS3File(
                    "bucket", "missing.txt", File(temporaryFolder.root, "missing"), temporaryFolder.newFolder()
                ) { _, _ -> }
                error("缺失快照必须失败")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("快照不存在"))
            }
            assertEquals(0, server.requestCount)
        }
    }
    /** 两页完整扫描只建立一个SDK客户端，不为每页重复创建和关闭连接池。 */
    @Test
    fun completeDirectoryScanReusesOneClientAcrossPages() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(xml("""<ListBucketResult><IsTruncated>true</IsTruncated>
                <NextContinuationToken>next</NextContinuationToken>
                <Contents><Key>a</Key><Size>1</Size></Contents></ListBucketResult>"""))
            server.enqueue(xml("""<ListBucketResult><IsTruncated>false</IsTruncated>
                <Contents><Key>z</Key><Size>1</Size></Contents></ListBucketResult>"""))
            var clientsCreated = 0
            val page = storage(server) { clientsCreated++ }.withS3DirectoryListing("bucket", "") { fetchPage ->
                loadSortedObjectDirectory(
                    OssObjectSortField.NAME,
                    OssObjectSortDirection.DESCENDING,
                    10, {}, fetchPage
                )
            }
            assertEquals(listOf("z", "a"), page.items.map { it.key })
            assertEquals(1, clientsCreated)
            assertEquals(2, server.requestCount)
        }
    }
}
