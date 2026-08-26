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

import io.github.jinlongliao.ossbrowser.data.AliyunAccountType
import io.github.jinlongliao.ossbrowser.data.AliyunSiteType
import io.github.jinlongliao.ossbrowser.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证阿里云登录预填 URL 与 WebView 顶层导航域名边界。 */
class AliyunWebConsolePolicyTest {

    /** RAM 用户名必须经过查询参数编码，主账号不得附带非官方预填参数。 */
    @Test
    fun buildSignInUrlOnlyPrefillsRamUsername() {
        assertEquals(
            "https://oss.console.aliyun.com/overview",
            buildAliyunSignInUrl(
                AliyunSiteType.CHINA,
                AliyunAccountType.MAIN,
                "owner@example.com"
            )
        )
        assertEquals(
            "https://signin.aliyun.com/login.htm?username=" +
                "user%2Btest%40example.onaliyun.com",
            buildAliyunSignInUrl(
                AliyunSiteType.CHINA,
                AliyunAccountType.RAM,
                " user+test@example.onaliyun.com "
            )
        )
    }

    /** 新安装和旧版升级缺少站点键时均以中国站作为稳定默认入口。 */
    @Test
    fun appSettingsDefaultsToChinaSite() {
        assertEquals(AliyunSiteType.CHINA, AppSettings().aliyunSiteType)
    }

    /** 国际站必须同时切换 RAM 登录域名和 OSS 控制台域名，避免跨站 Cookie 混用。 */
    @Test
    fun internationalSiteUsesAlibabaCloudDomains() {
        assertEquals(
            "https://oss.console.alibabacloud.com/overview",
            aliyunOssConsoleUrl(AliyunSiteType.INTERNATIONAL)
        )
        assertEquals(
            "https://signin.alibabacloud.com/login.htm?username=" +
                "user%40example.onaliyun.com",
            buildAliyunSignInUrl(
                AliyunSiteType.INTERNATIONAL,
                AliyunAccountType.RAM,
                "user@example.onaliyun.com"
            )
        )
    }

    /** 只允许阿里云 HTTPS 主域及子域，拒绝明文、相似后缀和带用户信息的 URL。 */
    @Test
    fun trustedUrlRejectsSpoofedOrUnsafeNavigation() {
        assertTrue(isTrustedAliyunWebUrl("https://signin.aliyun.com/login.htm"))
        assertTrue(isTrustedAliyunWebUrl("https://oss.console.alibabacloud.com/"))
        assertFalse(isTrustedAliyunWebUrl("http://signin.aliyun.com/login.htm"))
        assertFalse(isTrustedAliyunWebUrl("https://aliyun.com.evil.example/"))
        assertFalse(isTrustedAliyunWebUrl("https://user@signin.aliyun.com/"))
        assertFalse(isTrustedAliyunWebUrl("not a url"))
    }
}
