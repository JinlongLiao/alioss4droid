package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 地域预置映射与旧 Endpoint 兼容边界，避免将自定义部署误判成标准公网地址。 */
class OssRegionsTest {
    /** 地域和 Endpoint 均唯一，选中项始终使用 HTTPS 公网地址。 */
    @Test
    fun selectableRegionsHaveUniquePublicEndpoints() {
        assertEquals(ossRegionOptions.size, ossRegionOptions.map { it.code }.toSet().size)
        assertEquals(ossRegionOptions.size, ossRegionOptions.map { it.endpoint }.toSet().size)
        assertTrue(ossRegionOptions.all { it.endpoint.startsWith("https://oss-") })
        assertEquals(
            "https://oss-ap-southeast-1.aliyuncs.com",
            ossRegionOptions.first { it.code == "ap-southeast-1" }.endpoint
        )
    }

    /** 阿里云同名代码与 AWS 地理位置不同，必须使用 OSS 自己的名称资源，避免复用 AWS 选项。 */
    @Test
    fun ossRegionCodesRemainIndependentFromAwsRegions() {
        assertEquals(
            R.string.oss_region_ap_southeast_3,
            ossRegionOptions.first { it.endpoint == "https://oss-ap-southeast-3.aliyuncs.com" }.nameResource
        )
        assertEquals("ap-southeast-5", findOssRegionByEndpoint("https://oss-ap-southeast-5.aliyuncs.com")?.code)
    }

    /** 无协议、HTTP 和末尾斜杠属于旧地址的显示兼容，识别不改写原地址。 */
    @Test
    fun recognizesLegacyPublicEndpointSpellings() {
        listOf(
            "oss-cn-hangzhou.aliyuncs.com", "http://oss-cn-hangzhou.aliyuncs.com/",
            "https://oss-cn-hangzhou.aliyuncs.com", "HTTPS://OSS-CN-HANGZHOU.ALIYUNCS.COM/"
        ).forEach { assertEquals("cn-hangzhou", findOssRegionByEndpoint(it)?.code) }
    }

    /** 自定义路径、内网、鉴权/查询信息和桶域名一律保持自定义，不回填或截断地址。 */
    @Test
    fun leavesCustomEndpointsUnmatched() {
        listOf(
            "https://oss-cn-hangzhou.aliyuncs.com/context/", "https://oss-cn-hangzhou.aliyuncs.com:443",
            "https://oss-cn-hangzhou-internal.aliyuncs.com", "https://bucket.oss-cn-hangzhou.aliyuncs.com",
            "https://oss-cn-hangzhou.aliyuncs.com?x=1", "https://example.com/gateway", ""
        ).forEach { assertNull(it, findOssRegionByEndpoint(it)) }
    }
}
