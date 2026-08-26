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
package io.github.jinlongliao.ossbrowser.ui.web

import io.github.jinlongliao.ossbrowser.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 WebView 诊断 URL 脱敏与默认安全设置。 */
class AliyunWebDiagnosticsTest {

    /** URL 查询参数、片段和用户信息必须始终移除，关闭普通脱敏也不得恢复。 */
    @Test
    fun diagnosticUrlAlwaysRemovesAuthenticationParameters() {
        val sanitized = redactWebDiagnosticUrl(
            "https://user@OSS.CONSOLE.ALIYUN.COM/bucket/files?token=secret#access_key"
        )

        assertEquals("https://oss.console.aliyun.com/bucket/files", sanitized)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("access_key"))
    }

    /** 默认开启脱敏时只显示站点来源；关闭后最多展示无查询参数的路径。 */
    @Test
    fun displayUrlHonorsPathRedactionWithoutRestoringSecrets() {
        val sanitized = "https://oss.console.aliyun.com/bucket/files"

        assertEquals(
            "https://oss.console.aliyun.com",
            displayWebDiagnosticUrl(sanitized, redactionEnabled = true)
        )
        assertEquals(sanitized, displayWebDiagnosticUrl(sanitized, redactionEnabled = false))
    }

    /** 新安装和旧版本缺少设置键时均默认启用诊断脱敏。 */
    @Test
    fun diagnosticsRedactionDefaultsToEnabled() {
        assertTrue(AppSettings().webDiagnosticsRedactionEnabled)
    }

    /** Cookie 诊断只保留安全名称与固定长度脱敏值，不得残留任何完整原值。 */
    @Test
    fun diagnosticCookiesImmediatelyMaskValues() {
        val cookies = extractDiagnosticCookies(
            "session=abcdefghijklmnop; short=abc; unsafe name=value; session=duplicate"
        )

        assertEquals(
            listOf(
                AliyunWebDiagnosticCookie("session", "abcd********mnop"),
                AliyunWebDiagnosticCookie("short", "********"),
                AliyunWebDiagnosticCookie("unsafe_name", "v********e")
            ),
            cookies
        )
        assertFalse(cookies.toString().contains("abcdefghijklmnop"))
        assertFalse(cookies.toString().contains("duplicate"))
    }
}
