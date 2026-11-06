package io.github.jinlongliao.ossbrowser.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** 确保新 S3 字段兼容旧配置，且沿用既有加密备份格式，不额外存放凭据。 */
class S3ProfileTest {
    /** 旧数据没有类型或 Region 字段时仍按 OSS 读取。 */
    @Test
    fun legacyProfileDefaultsToOss() {
        val raw = """{"id":"old","name":"OSS","endpoint":"https://oss.example.com",
            "accessKeyId":"test-key","accessKeySecret":"test-secret"}"""
        val profile = Json.decodeFromString<OssProfile>(raw)
        assertEquals(StorageType.OSS, profile.storageType)
        assertEquals("", profile.region)
        assertEquals("", profile.sessionToken)
        assertEquals(false, profile.forcePathStyle)
    }

    /** S3 与 OSS 混合备份后恢复，区域、会话令牌和寻址方式均保留。 */
    @Test
    fun encryptedBackupRoundTripPreservesS3Configuration() {
        val oss = OssProfile.empty().copy(name = "OSS", accessKeyId = "test-key", accessKeySecret = "test-secret")
        val s3 = oss.copy(
            id = "s3", name = "S3", storageType = StorageType.AWS_S3,
            region = "eu-west-1", endpoint = "", sessionToken = "test-session", forcePathStyle = true
        )
        val profiles = listOf(oss, s3)
        val encrypted = ProfileBackupCodec.encode(profiles, "test-password")
        assertEquals(profiles, ProfileBackupCodec.decode(encrypted, "test-password"))
    }
}
