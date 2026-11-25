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

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ListCname V4 签名与错误脱敏的回归测试。 */
class OssListCnameSigningTest {

    /** V4 签名包含 cname 子资源、Bucket 地域，以及可选令牌。 */
    @Test
    fun createListCnameSignedUrl_usesV4AndTheBucketRegion() {
        val base = "https://examplebucket.oss-cn-hangzhou.aliyuncs.com/"
        val region = resolveListCnameRegion(base, "cn-shanghai")
        assertEquals("cn-hangzhou", region)
        assertEquals("cn-hangzhou", resolveListCnameRegion(
            "https://examplebucket.oss-cn-hangzhou-internal.aliyuncs.com/", ""
        ))
        val url = createListCnameSignedUrl(base, "examplebucket", region, "test-access-key", "test-secret", "token+/")
        val query = URI(url).query
        assertTrue(query.contains("cname="))
        assertTrue(query.contains("x-oss-signature-version=OSS4-HMAC-SHA256"))
        assertTrue(Regex("x-oss-credential=test-access-key/\\d{8}/cn-hangzhou/oss/aliyun_v4_request").containsMatchIn(query))
        assertTrue(query.contains("x-oss-security-token=token+/"))
        assertTrue(Regex("x-oss-signature=[0-9a-f]{64}").containsMatchIn(query))
    }

    /** 错误解析只返回 Code，不能把响应中的认证材料继续传给 UI 或日志消息。 */
    @Test
    fun extractOssErrorCode_doesNotExposeCredentialFields() {
        val response = """
            <Error>
              <Code>SignatureDoesNotMatch</Code>
              <OSSAccessKeyId>sensitive-ak</OSSAccessKeyId>
              <SignatureProvided>sensitive-signature</SignatureProvided>
            </Error>
        """.trimIndent()

        val code = extractOssErrorCode(response)

        assertEquals("SignatureDoesNotMatch", code)
        assertTrue(response.contains("sensitive-ak"))
        assertFalse(code.orEmpty().contains("sensitive"))
    }
}
