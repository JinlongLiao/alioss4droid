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

/**
 * Bucket 上已启用的一个 OSS 自定义域名。
 *
 * @property domain 不包含协议和路径的域名。
 * @property httpsEnabled OSS 返回的证书状态是否为 `Enabled`；为 `false` 时公开链接使用 HTTP。
 *
 * @see <a href="https://help.aliyun.com/zh/oss/developer-reference/listcname">阿里云 OSS ListCname</a>
 */
data class OssCnameInfo(
    val domain: String,
    val httpsEnabled: Boolean
) {
    /** 依据 OSS 托管证书状态选择公开链接协议。 */
    val scheme: String get() = if (httpsEnabled) "https" else "http"
}

/**
 * 将 SDK 已正确编码 Object key 的默认公开 URL 切换到用户选择的自定义域名。
 *
 * @param defaultUrl SDK 生成的 OSS 默认公开 URL。
 * @param cname 用户选择的自定义域名及其证书状态。
 * @return 只替换协议与 authority、完整保留已编码路径和查询串的 URL。
 * @throws IllegalArgumentException 默认 URL 缺少协议或自定义域名包含路径时抛出。
 */
internal fun buildCustomDomainUrl(defaultUrl: String, cname: OssCnameInfo): String {
    require('/' !in cname.domain) { "自定义域名不能包含路径" }
    val schemeSeparator = defaultUrl.indexOf("://")
    require(schemeSeparator > 0) { "OSS 默认公开 URL 缺少协议" }
    val authorityStart = schemeSeparator + 3
    val suffixStart = listOf(
        defaultUrl.indexOf('/', authorityStart),
        defaultUrl.indexOf('?', authorityStart),
        defaultUrl.indexOf('#', authorityStart)
    ).filter { it >= 0 }.minOrNull() ?: defaultUrl.length
    return "${cname.scheme}://${cname.domain}${defaultUrl.substring(suffixStart)}"
}
