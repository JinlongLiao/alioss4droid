package io.github.jinlongliao.ossbrowser.data

/** 文件浏览的全局布局偏好；与账号、目录、排序规则独立，首次安装默认使用列表。 */
enum class BrowserLayoutMode {
    /** 单列列表，保留较高的信息密度。 */
    LIST,

    /** 双列瀑布流，各卡片按内容高度排布。 */
    WATERFALL
}

/**
 * 从持久化名称恢复布局；旧版本缺少字段或未来版本值不可识别时安全回退为列表。
 * @param value 偏好文件中的枚举名，可以为空。
 * @return 可用于界面的布局方式。
 */
fun restoreBrowserLayoutMode(value: String?): BrowserLayoutMode =
    BrowserLayoutMode.entries.firstOrNull { it.name == value } ?: BrowserLayoutMode.LIST
