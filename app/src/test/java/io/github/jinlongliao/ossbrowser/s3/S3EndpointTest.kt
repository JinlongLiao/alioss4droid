package io.github.jinlongliao.ossbrowser.s3

import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖区域、中文对象路径、自定义 context-path 及超大文件分片数量边界。 */
class S3EndpointTest {
    /** URL 和区域计算不包含任何鉴权材料。 */
    private val profile = OssProfile.empty().copy(
        storageType = StorageType.AWS_S3, region = "us-east-1", endpoint = ""
    )

    /** 中国和商业分区使用各自域名；列表 Bucket 的区域覆盖默认区域用于签名。 */
    @Test
    fun standardEndpointsUseCorrectPartitionAndSigningRegion() {
        assertEquals("https://s3.cn-north-1.amazonaws.com.cn", awsS3Endpoint("cn-north-1"))
        assertEquals("https://s3.eu-west-1.amazonaws.com", awsS3Endpoint("eu-west-1"))
        assertEquals("eu-west-1", resolveS3Region(profile, "https://s3.eu-west-1.amazonaws.com"))
        assertEquals("us-east-1", resolveS3Region(profile, "https://storage.example.com/base"))
    }

    /** 自定义基础地址的路径不能被忽略，空格、加号、百分号和中文只编码一次。 */
    @Test
    fun publicUrlPreservesBasePathAndObjectKey() {
        val custom = profile.copy(endpoint = "https://storage.example.com/gateway/", forcePathStyle = true)
        assertEquals(
            "https://storage.example.com/gateway/bucket/folder/a%20b%2B%25%23.txt",
            s3PublicObjectUrl(custom, null, "bucket", "folder/a b+%#.txt")
        )
        assertEquals("%E4%B8%AD%E6%96%87//a%3Fb", encodeS3ObjectKey("中文//a?b"))
        assertEquals(
            "https://my.s3.us-east-1.amazonaws.com/file",
            s3PublicObjectUrl(profile, null, "my", "file")
        )
        assertEquals(
            "https://s3.us-east-1.amazonaws.com/my.bucket/file",
            s3PublicObjectUrl(profile, null, "my.bucket", "file")
        )
    }

    /** 最大文件仍不超过 10000 片，最小分片满足 S3 的 5 MiB 约束。 */
    @Test
    fun multipartSizesStayWithinServiceLimits() {
        listOf(0L, 8L * 1024 * 1024, 5L * 1024 * 1024 * 1024 * 1024).forEach { size ->
            val partSize = s3UploadPartSize(size)
            assertTrue(partSize >= 5L * 1024 * 1024)
            assertTrue((size + partSize - 1) / partSize <= 10000)
        }
    }
    /** 配置不允许把鉴权材料塞入 URL，也不能静默接受查询参数或 HTTP。 */
    @Test
    fun validatesRegionAndEndpointBeforeSaving() {
        assertTrue(isS3ConnectionValid("ap-northeast-1", ""))
        assertTrue(isS3ConnectionValid("ap-northeast-1", "https://example.com/context/"))
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com/?token=x").forEach {
            assertEquals(false, isS3ConnectionValid("ap-northeast-1", it))
        }
        assertEquals(false, isS3ConnectionValid("", ""))
    }
    /** 下拉代码唯一且可用于现有连接配置，东京当前配置不会因选择框新增而失效。 */
    @Test
    fun selectableRegionCodesAreUniqueAndValid() {
        val codes = s3RegionOptions.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.contains("ap-northeast-1"))
        assertTrue(codes.contains("cn-north-1"))
        assertTrue(codes.all { isS3ConnectionValid(it, "") })
    }
}
