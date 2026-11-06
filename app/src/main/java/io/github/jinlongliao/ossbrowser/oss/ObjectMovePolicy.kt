package io.github.jinlongliao.ossbrowser.oss

/**
 * 移动前验证目录边界，避免递归扫描把刚复制的目标再次纳入源，从而无限放大请求。
 * 相同路径由调用方作为不需要执行处理；文件路径不强制以斜杠结尾。
 */
fun validateObjectMovePaths(sourceKey: String, destinationKey: String) {
    if (sourceKey.endsWith('/')) {
        require(destinationKey.endsWith('/')) { "目标目录必须以 / 结尾" }
        require(sourceKey == destinationKey || !destinationKey.startsWith(sourceKey)) { "目录不能移动到自身子目录" }
    }
}

/** 返回文件或目录本身所在的父目录前缀；根目录对象返回空字符串。 */
fun objectParentPrefix(key: String): String {
    val parentSeparator = key.trimEnd('/').lastIndexOf('/')
    return if (parentSeparator < 0) "" else key.substring(0, parentSeparator + 1)
}
