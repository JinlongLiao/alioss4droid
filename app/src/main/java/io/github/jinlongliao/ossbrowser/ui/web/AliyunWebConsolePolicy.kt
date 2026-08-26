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
import java.net.URI
import java.net.URLEncoder

/** 阿里云中国站 RAM 用户登录页，支持通过 `username` 查询参数预填完整登录名。 */
private const val ALIYUN_CHINA_SIGN_IN_URL = "https://signin.aliyun.com/login.htm"

/** 阿里云国际站 RAM 用户登录页，支持通过 `username` 查询参数预填完整登录名。 */
private const val ALIYUN_INTERNATIONAL_SIGN_IN_URL =
    "https://signin.alibabacloud.com/login.htm"

/** 阿里云中国站 OSS 网页控制台入口。 */
private const val ALIYUN_CHINA_OSS_CONSOLE_URL = "https://oss.console.aliyun.com/overview"

/** 阿里云国际站 OSS 网页控制台入口。 */
private const val ALIYUN_INTERNATIONAL_OSS_CONSOLE_URL =
    "https://oss.console.alibabacloud.com/overview"

/**
 * 返回用户所选站点的 OSS 控制台入口。
 *
 * @param site 中国站或国际站。
 * @return 对应站点的官方 HTTPS 控制台 URL。
 */
internal fun aliyunOssConsoleUrl(site: AliyunSiteType): String = when (site) {
    AliyunSiteType.CHINA -> ALIYUN_CHINA_OSS_CONSOLE_URL
    AliyunSiteType.INTERNATIONAL -> ALIYUN_INTERNATIONAL_OSS_CONSOLE_URL
}

/**
 * 构造阿里云网页登录入口。
 *
 * 主账号直接打开 OSS 控制台并由阿里云重定向至官方账号登录页；RAM 用户仅使用官方
 * `username` 参数，避免通过 JavaScript 读取或修改登录表单。密码和 MFA 始终由网页及
 * 系统自动填充服务处理。
 *
 * @param site 用户选择的中国站或国际站；登录域名与 OSS 控制台域名必须保持一致。
 * @param accountType 用户选择的主账号或 RAM 用户入口。
 * @param username RAM 完整登录名，例如 `user@example.onaliyun.com`；前后空白会被移除。
 * @return 可直接交给 WebView 加载的 HTTPS URL。
 * @see <a href="https://help.aliyun.com/en/ram/user-guide/log-on-to-the-alibaba-cloud-management-console-as-a-ram-user">RAM user logon</a>
 * @see <a href="https://www.alibabacloud.com/help/en/ram/user-guide/log-on-to-the-alibaba-cloud-management-console-as-a-ram-user">International RAM user logon</a>
 */
internal fun buildAliyunSignInUrl(
    site: AliyunSiteType,
    accountType: AliyunAccountType,
    username: String
): String {
    val normalizedUsername = username.trim()
    if (accountType == AliyunAccountType.MAIN) {
        return aliyunOssConsoleUrl(site)
    }
    val signInUrl = when (site) {
        AliyunSiteType.CHINA -> ALIYUN_CHINA_SIGN_IN_URL
        AliyunSiteType.INTERNATIONAL -> ALIYUN_INTERNATIONAL_SIGN_IN_URL
    }
    if (normalizedUsername.isEmpty()) {
        return signInUrl
    }
    val encodedUsername = URLEncoder.encode(normalizedUsername, Charsets.UTF_8.name())
        .replace("+", "%20")
    return "$signInUrl?username=$encodedUsername"
}

/**
 * 判断顶层导航 URL 是否属于允许在内置 WebView 中展示的阿里云 HTTPS 域名。
 *
 * 只允许 `aliyun.com` 与 `alibabacloud.com` 本身及其子域，防止相似后缀域名绕过。解析失败、
 * 用户信息 URL、非 HTTPS 或无 Host URL 一律拒绝；被拒绝的网页导航由界面交给系统浏览器。
 *
 * @param url 待检查的绝对 URL。
 * @return `true` 表示允许 WebView 继续导航。
 */
internal fun isTrustedAliyunWebUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) {
        return@runCatching false
    }
    val host = uri.host?.lowercase() ?: return@runCatching false
    TRUSTED_HOST_SUFFIXES.any { suffix -> host == suffix || host.endsWith(".$suffix") }
}.getOrDefault(false)

/** WebView 顶层导航允许的阿里云官方域名后缀。 */
private val TRUSTED_HOST_SUFFIXES = setOf("aliyun.com", "alibabacloud.com")
