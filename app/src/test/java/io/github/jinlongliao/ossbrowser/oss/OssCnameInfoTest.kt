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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** [buildCustomDomainUrl] 的 URL 保真和协议选择回归测试。 */
class OssCnameInfoTest {

    /** HTTPS 证书启用时应切换域名，并保留已编码 Object key 与查询参数。 */
    @Test
    fun buildCustomDomainUrl_preservesEncodedPathAndQueryForHttpsDomain() {
        val result = buildCustomDomainUrl(
            defaultUrl = "https://bucket.oss-cn-hangzhou.aliyuncs.com/a%20b/%E4%B8%AD.txt?x=1",
            cname = OssCnameInfo(domain = "static.example.com", httpsEnabled = true)
        )

        assertEquals("https://static.example.com/a%20b/%E4%B8%AD.txt?x=1", result)
    }

    /** 未启用 OSS 托管证书时应生成 HTTP 链接。 */
    @Test
    fun buildCustomDomainUrl_usesHttpWithoutEnabledCertificate() {
        val result = buildCustomDomainUrl(
            defaultUrl = "https://bucket.oss-cn-hangzhou.aliyuncs.com/file.txt",
            cname = OssCnameInfo(domain = "download.example.com", httpsEnabled = false)
        )

        assertEquals("http://download.example.com/file.txt", result)
    }

    /** 自定义域名必须是纯 authority，禁止意外把用户路径拼入分享 URL。 */
    @Test
    fun buildCustomDomainUrl_rejectsDomainContainingPath() {
        assertThrows(IllegalArgumentException::class.java) {
            buildCustomDomainUrl(
                defaultUrl = "https://bucket.oss-cn-hangzhou.aliyuncs.com/file.txt",
                cname = OssCnameInfo(domain = "example.com/path", httpsEnabled = true)
            )
        }
    }
}
