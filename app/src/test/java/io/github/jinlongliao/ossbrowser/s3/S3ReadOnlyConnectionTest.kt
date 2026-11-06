package io.github.jinlongliao.ossbrowser.s3

import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.ProfileBackupCodec
import io.github.jinlongliao.ossbrowser.data.StorageType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 用户显式授权的账号级验证。默认跳过，只调用 ListBuckets，不调用任何对象读写、列举或分片接口。
 * 凭据由仓库外私有文件提供，不输出到日志，不生成包含凭据的源码和资源。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class S3ReadOnlyConnectionTest {
    /** 连通性成功后导出应用既有格式的加密配置，供在设备中导入；不改变账号和 Bucket 配置。 */
    @Test
    fun validateAccountAndExportEncryptedProfileWithoutTouchingObjects(): Unit = runBlocking {
        val path = System.getProperty("alioss.s3.connectionConfig")
        assumeTrue("未显式指定真实账号配置，只运行本地协议测试", !path.isNullOrBlank())
        val config = Json.parseToJsonElement(File(requireNotNull(path)).readText()).jsonObject
        val profile = OssProfile(
            id = "aws-s3-authorized-test",
            name = "AWS S3 测试",
            endpoint = "",
            accessKeyId = config.getValue("accessKeyId").jsonPrimitive.content,
            accessKeySecret = config.getValue("secretAccessKey").jsonPrimitive.content,
            storageType = StorageType.AWS_S3,
            region = config.getValue("region").jsonPrimitive.content
        )
        val buckets = S3Storage(profile, null).listS3Buckets()
        println("S3 ListBuckets 成功；region=${profile.region}，bucketCount=${buckets.size}；未调用对象接口")
        val backup = File(File(path).parentFile, "AWS-S3测试配置.ossbak")
        backup.writeBytes(ProfileBackupCodec.encode(listOf(profile), null))
        backup.setReadable(false, false)
        backup.setReadable(true, true)
        backup.setWritable(false, false)
        backup.setWritable(true, true)
    }
}
