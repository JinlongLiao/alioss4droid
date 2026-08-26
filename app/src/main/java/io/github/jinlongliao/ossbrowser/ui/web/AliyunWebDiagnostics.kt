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

import java.net.URI

/**
 * WebView 最近一次可安全展示的诊断快照。
 *
 * 所有 URL 在进入此模型前必须移除查询参数、片段和用户信息；Cookie 只保留名称和不可逆的
 * 脱敏值，禁止把完整值、登录令牌、STS 或 AccessKey 放入任何字段。
 *
 * @property currentUrl 已脱敏的当前顶层页面 URL。
 * @property webViewVersion Android System WebView 实现版本。
 * @property lastLoadDurationMillis 最近一次完整页面加载耗时，未完成时为 `null`。
 * @property cookies 当前站点 Cookie 的安全诊断结果；仅包含名称和即时生成的脱敏值。
 * @property cookiesAccepted WebView 是否允许第一方 Cookie。
 * @property thirdPartyCookiesAccepted 当前 WebView 是否允许第三方 Cookie。
 * @property recentEvents 最近发生的加载、导航或错误事件，最多保留固定数量。
 */
internal data class AliyunWebDiagnostics(
    val currentUrl: String = "-",
    val webViewVersion: String = "-",
    val lastLoadDurationMillis: Long? = null,
    val cookies: List<AliyunWebDiagnosticCookie> = emptyList(),
    val cookiesAccepted: Boolean = false,
    val thirdPartyCookiesAccepted: Boolean = false,
    val recentEvents: List<String> = emptyList()
) {
    /**
     * 追加一条不含凭证的诊断事件，并限制历史长度，避免长时间使用造成内存持续增长。
     *
     * @param event 已完成脱敏的简短事件描述。
     * @return 包含新事件的不可变快照。
     */
    fun appendEvent(event: String): AliyunWebDiagnostics = copy(
        recentEvents = (listOf(event) + recentEvents).take(MAX_RECENT_EVENTS)
    )

    private companion object {
        /** 单个 WebView 会话保留的最大诊断事件数量。 */
        const val MAX_RECENT_EVENTS = 12
    }
}

/**
 * 单个 Cookie 的安全诊断表示。
 *
 * @property name Cookie 名称；控制字符和非标准字符已替换，防止污染复制报告。
 * @property maskedValue 仅保留少量首尾字符的脱敏值，永远不是原始 Cookie 值。
 */
internal data class AliyunWebDiagnosticCookie(
    val name: String,
    val maskedValue: String
)

/**
 * 移除 URL 中可能携带授权码、Token、账号名或对象参数的部分。
 *
 * @param url WebView 提供的绝对 URL。
 * @return 仅包含 scheme、host、可选端口和 path 的 URL；解析失败时返回固定占位符。
 */
internal fun redactWebDiagnosticUrl(url: String): String = runCatching {
    val uri = URI(url)
    val scheme = uri.scheme?.lowercase() ?: return@runCatching "-"
    val host = uri.host?.lowercase() ?: return@runCatching "$scheme:"
    URI(scheme, null, host, uri.port, uri.path.orEmpty().ifEmpty { "/" }, null, null).toString()
}.getOrDefault("-")

/**
 * 根据用户设置决定是否进一步隐藏已安全移除查询参数的 URL 路径。
 *
 * @param sanitizedUrl [redactWebDiagnosticUrl] 产生的安全 URL。
 * @param redactionEnabled `true` 时仅显示 scheme、host 和可选端口。
 * @return 可展示和复制的诊断 URL，认证查询参数在两种模式下都不会恢复。
 */
internal fun displayWebDiagnosticUrl(
    sanitizedUrl: String,
    redactionEnabled: Boolean
): String {
    if (!redactionEnabled) return sanitizedUrl
    return runCatching {
        val uri = URI(sanitizedUrl)
        URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString()
    }.getOrDefault("-")
}

/**
 * 将 Cookie 请求头立即转换为只含名称和脱敏值的诊断列表。
 *
 * 原始请求头不得离开调用栈、不得写日志或保存在诊断模型中；重复名称仅保留第一项，防止报告
 * 无限制增长。Cookie 值只保留少量首尾字符，中间长度固定为星号，避免泄露完整值和实际长度。
 *
 * @param cookieHeader [android.webkit.CookieManager.getCookie] 返回的请求头，可为 `null`。
 * @return 最多 [MAX_DIAGNOSTIC_COOKIES] 项、按名称排序的安全诊断 Cookie。
 */
internal fun extractDiagnosticCookies(cookieHeader: String?): List<AliyunWebDiagnosticCookie> =
    cookieHeader
        ?.split(';')
        ?.mapNotNull { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val safeName = item.substring(0, separator)
                .trim()
                .map { character ->
                    if (character.isLetterOrDigit() || character in COOKIE_NAME_SYMBOLS) {
                        character
                    } else {
                        '_'
                    }
                }
                .joinToString("")
                .take(MAX_COOKIE_NAME_LENGTH)
                .takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            AliyunWebDiagnosticCookie(
                name = safeName,
                maskedValue = maskCookieValue(item.substring(separator + 1))
            )
        }
        ?.distinctBy(AliyunWebDiagnosticCookie::name)
        ?.sortedBy(AliyunWebDiagnosticCookie::name)
        ?.take(MAX_DIAGNOSTIC_COOKIES)
        .orEmpty()

/**
 * 对 Cookie 值执行不可逆的中间星号脱敏。
 *
 * @param value 原始 Cookie 值，仅在当前调用栈短暂存在。
 * @return 短值完全隐藏；较长值最多保留首尾各四个字符，中间使用固定星号。
 */
private fun maskCookieValue(value: String): String = when {
    value.length <= 4 -> "********"
    value.length <= 8 -> "${value.first()}********${value.last()}"
    else -> "${value.take(4)}********${value.takeLast(4)}"
}

/** Cookie 名称允许保留的 RFC token 常见符号，不含控制字符和分隔符。 */
private val COOKIE_NAME_SYMBOLS = setOf('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~')

/** 单次诊断最多展示的 Cookie 数量，防止异常网页构造超大报告。 */
private const val MAX_DIAGNOSTIC_COOKIES = 50

/** 单个 Cookie 名称最多保留的字符数。 */
private const val MAX_COOKIE_NAME_LENGTH = 128
