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
