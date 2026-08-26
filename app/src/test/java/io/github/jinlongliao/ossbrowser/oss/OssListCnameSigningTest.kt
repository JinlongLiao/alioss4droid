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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ListCname V1 签名与错误脱敏的回归测试。 */
class OssListCnameSigningTest {

    /** 签名必须覆盖 `?cname` 子资源，并对 Base64 特殊字符进行 URL 编码。 */
    @Test
    fun createListCnameSignedUrl_includesCnameInKnownSignatureVector() {
        val url = createListCnameSignedUrl(
            baseUrl = "https://examplebucket.oss-cn-hangzhou.aliyuncs.com/",
            bucket = "examplebucket",
            accessKeyId = "test-access-key",
            accessKeySecret = "test-secret",
            expiresAtSeconds = 1_700_000_000L
        )

        assertEquals(
            "https://examplebucket.oss-cn-hangzhou.aliyuncs.com/" +
                "?cname&OSSAccessKeyId=test-access-key&Expires=1700000000&" +
                "Signature=f5KSVCIv9Jls%2BY%2FvNDzQdCF5Z78%3D",
            url
        )
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
