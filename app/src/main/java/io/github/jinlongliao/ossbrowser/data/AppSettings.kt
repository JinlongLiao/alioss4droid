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
    /** 云雾蓝：中性白底与蓝色强调，不随系统壁纸变化。 */
    DEFAULT,

    /** Gemini 蓝、青、紫、红品牌配色；使用固定色值，不受系统动态取色覆盖。 */
    GEMINI,

    /** 晴空：本地蓝青配色，兼容旧枚举名，不再加载远程壁纸。 */
    SUMMER_SKY,

    /** 青瓷：灰白底与柔和青绿色，兼容明暗模式。 */
    CELADON,

    /** 暮紫：浅灰紫背景与低饱和紫色强调。 */
    DUSK,

    /** 暖砂：米白背景与暖棕色强调。 */
    SAND,

    /** 流光彩虹：多色渐变和活泼的圆角形态。 */
    PRISM,

    /** 极光：青绿、蓝紫光晕与清晰的实色正文。 */
    AURORA,

    /** 冰川玻璃：冷色背景和半透明磨砂内容层。 */
    GLACIER_GLASS,

    /** 夜幕玻璃：紫蓝背景与轻玻璃高光，兼容明暗模式。 */
    TWILIGHT_GLASS,

    /** 极简黑白：无彩色强调，高对比的黑白布局。 */
    MONOCHROME,

    /** 像素终端：等宽字体、硬朗圆角与绿色强调。 */
    TERMINAL,

    /** 赛博霓虹：青紫光晕、几何边角与科技感字形。 */
    CYBER,

    /** 纸感书页：暖白底、衬线文字与细线图标。 */
    PAPER,

    /** 樱雾：柔粉与淡紫的轻盈配色。 */
    SAKURA,

    /** 松林：深浅绿色层次与自然感。 */
    FOREST,

    /** 海盐：清透青蓝与柔和水色背景。 */
    SEA_SALT,

    /** 落日余晖：杏橙、粉紫与暖色光晕。 */
    SUNSET
}

/** OSS 对象列表中的文件排序字段。 */
enum class OssObjectSortField {
    /** 按文件名称排序，不区分字母大小写。 */
    NAME,

    /** 按 OSS 返回的最后修改时间排序。 */
    MODIFIED_TIME
}

/** OSS 对象列表排序方向。 */
enum class OssObjectSortDirection {
    /** 正序；名称从 A 到 Z，修改时间从旧到新。 */
    ASCENDING,

    /** 倒序；名称从 Z 到 A，修改时间从新到旧。 */
    DESCENDING
}

/**
 * 用户偏好设置。非敏感数据，走普通 SharedPreferences。
 */
data class AppSettings(
    /** 主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 配色主题；默认使用与云文件夹启动图标一致的固定品牌色。 */
    val themePalette: ThemePalette = ThemePalette.DEFAULT,
    /** 全局浏览布局；Bucket、文件夹与文件统一使用，跨启动保留，默认单列列表。 */
    val browserLayoutMode: BrowserLayoutMode = BrowserLayoutMode.LIST,
    /** 每页拉取对象数（OSS ListObjects maxKeys）。 */
    val pageSize: Int = 10,
    /** OSS 文件排序字段；目录始终置顶。 */
    val ossObjectSortField: OssObjectSortField = OssObjectSortField.NAME,
    /** OSS 文件排序方向；目录之间使用相同方向按名称排序。 */
    val ossObjectSortDirection: OssObjectSortDirection = OssObjectSortDirection.ASCENDING,
    /** 文本预览的最大字节数（超过则提示）。 */
    val textPreviewMaxBytes: Long = 2L * 1024 * 1024,
    /** 进入音视频预览时是否自动播放。 */
    val autoPlayMedia: Boolean = true,
    /** 对删除 / 移动 / 重命名等破坏性操作是否要求二次确认。 */
    val confirmDestructiveOps: Boolean = true,
    /** 用户是否已同意隐私协议。 */
    val privacyAccepted: Boolean = false,
    /** 同意时间戳（可选展示）。 */
    val privacyAcceptedAt: Long = 0L,
    /**
     * 语言 BCP-47 标签。空串表示"跟随系统"。
     * 支持集合：`en`、`zh-CN`、`zh-TW`。其余语种回退到 `en`。
     */
    val languageTag: String = ""
)
