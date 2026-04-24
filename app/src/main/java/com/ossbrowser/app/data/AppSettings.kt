package com.ossbrowser.app.data

/** 主题模式。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 用户偏好设置。非敏感数据，走普通 SharedPreferences。
 */
data class AppSettings(
    /** 主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
