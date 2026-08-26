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
package io.github.jinlongliao.ossbrowser.data

/** 主题模式。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 应用配色主题；与浅色/深色 [ThemeMode] 独立组合。 */
enum class ThemePalette {
    /** 默认 Material 3 配色；Android 12 及以上继续使用系统动态取色。 */
    DEFAULT,

    /** Gemini 蓝、青、紫、红品牌配色；使用固定色值，不受系统动态取色覆盖。 */
    GEMINI,

    /** 夏日晴空蓝绿配色；使用半透明 Surface 展示背景，并支持浅色、深色模式。 */
    SUMMER_SKY
}

/** 阿里云网页登录时用于选择登录入口的账号类型。 */
enum class AliyunAccountType {
    /** 阿里云主账号；登录页不附加用户名预填参数。 */
    MAIN,

    /** RAM 用户；登录页通过阿里云官方 `username` 参数预填完整登录名。 */
    RAM
}

/** 阿里云网页控制台站点；站点决定登录页和 OSS 控制台使用的官方域名。 */
enum class AliyunSiteType {
    /** 中国站，使用 `aliyun.com` 域名；作为新安装和旧版本升级后的默认值。 */
    CHINA,

    /** 国际站，使用 `alibabacloud.com` 域名。 */
    INTERNATIONAL
}

/** 阿里云网页控制台使用的内置浏览器引擎。 */
enum class WebBrowserEngine {
    /** 使用设备提供的 Android System WebView。 */
    SYSTEM_WEBVIEW,

    /** 使用随应用打包的 Mozilla GeckoView；不依赖设备 WebView 版本。 */
    GECKOVIEW
}

/**
 * 用户偏好设置。非敏感数据，走普通 SharedPreferences。
 */
data class AppSettings(
    /** 主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 配色主题；默认值保持升级前的应用外观和动态取色行为。 */
    val themePalette: ThemePalette = ThemePalette.DEFAULT,
    /** 每页拉取对象数（OSS ListObjects maxKeys）。 */
    val pageSize: Int = 10,
    /** 文本预览的最大字节数（超过则提示）。 */
    val textPreviewMaxBytes: Long = 2L * 1024 * 1024,
    /** 进入音视频预览时是否自动播放。 */
    val autoPlayMedia: Boolean = true,
    /** 对删除 / 移动 / 重命名等破坏性操作是否要求二次确认。 */
    val confirmDestructiveOps: Boolean = true,
    /** WebView 诊断是否隐藏 URL 路径和 Cookie 脱敏值；查询参数与完整凭证始终隐藏。 */
    val webDiagnosticsRedactionEnabled: Boolean = true,
    /** 网页控制台浏览器引擎；默认使用设备提供的系统 WebView。 */
    val webBrowserEngine: WebBrowserEngine = WebBrowserEngine.SYSTEM_WEBVIEW,
    /** 用户是否已同意隐私协议。 */
    val privacyAccepted: Boolean = false,
    /** 同意时间戳（可选展示）。 */
    val privacyAcceptedAt: Long = 0L,
    /** 阿里云网页控制台最近一次选择的账号类型；不参与 OSS SDK 鉴权。 */
    val aliyunAccountType: AliyunAccountType = AliyunAccountType.MAIN,
    /** 阿里云网页控制台站点；旧版本未保存此字段时默认进入中国站。 */
    val aliyunSiteType: AliyunSiteType = AliyunSiteType.CHINA,
    /** 阿里云网页控制台最近一次输入的账号名；密码和 MFA 永不写入应用设置。 */
    val aliyunUsername: String = "",
    /**
     * 语言 BCP-47 标签。空串表示"跟随系统"。
     * 支持集合：`en`、`zh-CN`、`zh-TW`。其余语种回退到 `en`。
     */
    val languageTag: String = ""
)
