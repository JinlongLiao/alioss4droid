package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.ui.theme.ThemeIconStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 文件卡片内的识别符号，与现有扩展名分类对应，不参与文件预览类型判断。 */
enum class StorageGlyph {
    /** 通用文字文档。 */ TEXT,
    /** 图片山形与取景圆点。 */ IMAGE,
    /** 视频播放标识。 */ VIDEO,
    /** 音符。 */ AUDIO,
    /** 表格网格。 */ TABLE,
    /** 演示画板。 */ SLIDES,
    /** 压缩文件拉链。 */ ARCHIVE,
    /** 源码尖括号。 */ CODE,
    /** 字体字形。 */ FONT,
    /** 应用方块。 */ APP,
    /** Java 归档杯形。 */ JAVA,
    /** 终端提示符。 */ TERMINAL,
    /** 未知格式的问号，不伪装成文字文档。 */ UNKNOWN,
    /** Android 安装包的机器人轮廓。 */ ANDROID,
    /** JSON 的花括号与键值点。 */ JSON,
    /** HTML 网页的浏览器窗口与标签符号。 */ WEB,
    /** Windows 可执行程序的四窗格。 */ WINDOWS,
    /** DMG、ISO 等磁盘镜像的硬盘轮廓。 */ DISK,
    /** PKG、MSI 及 Linux 软件包的封装箱。 */ PACKAGE,
    /** 配置文件的三组调整滑杆。 */ CONFIG,
    /** SQL 与数据库文件的分层圆柱。 */ DATABASE,
    /** PDF 文档的固定字母标识。 */ PDF,
    /** 文字处理文档的 W 与文本基线。 */ WORD,
    /** Markdown 的 M 与向下箭头。 */ MARKDOWN,
    /** 样式表的井号网格。 */ STYLE,
    /** 动态库与二进制模块的芯片轮廓。 */ LIBRARY
}

/** 原创 32 单位矢量图形；列表和预览共用，避免放大时模糊或出现额外的图标底框。 */
object StorageIcons {
    /**
     * 绘制带开口与浅层明暗的存储桶，取代含义模糊的服务器机架图标。
     * @param color 当前主题强调色。
     * @param style 图标材质，沿用同一语义轮廓切换描线、亮边或几何描边。
     * @return 可缓存并按任意密度绘制的存储桶矢量。
     */
    fun createBucketIcon(color: Color, style: ThemeIconStyle = ThemeIconStyle.SOFT): ImageVector = ImageVector.Builder(
        name = "CloudBucket", defaultWidth = 44.dp, defaultHeight = 44.dp,
        viewportWidth = 32f, viewportHeight = 32f
    ).apply {
        path(
            fill = if (style == ThemeIconStyle.LINE) null else Brush.linearGradient(
                listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.5f)),
                start = Offset(5f, 7f), end = Offset(27f, 28f)),
            stroke = SolidColor(color), strokeLineWidth = if (style == ThemeIconStyle.GEOMETRIC) 1.8f else 1.2f
        ) {
            moveTo(5f, 9f)
            lineTo(7f, 24f)
            curveTo(8f, 29f, 24f, 29f, 25f, 24f)
            lineTo(27f, 9f)
            close()
        }
        path(
            fill = SolidColor(color.copy(alpha = 0.14f)), stroke = SolidColor(color),
            strokeLineWidth = if (style == ThemeIconStyle.GEOMETRIC) 1.8f else 1.2f
        ) {
            moveTo(27f, 9f)
            curveTo(27f, 14f, 5f, 14f, 5f, 9f)
            curveTo(5f, 4f, 27f, 4f, 27f, 9f)
            close()
        }
        path(fill = null, stroke = SolidColor(color), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
            moveTo(11f, 19f)
            curveTo(14f, 21f, 18f, 21f, 21f, 19f)
        }
        if (style == ThemeIconStyle.GLASS) {
            path(fill = null, stroke = SolidColor(Color.White.copy(alpha = 0.8f)), strokeLineWidth = 0.9f) {
                moveTo(8f, 12f)
                lineTo(8f, 23f)
            }
        }
    }.build()

    /**
     * 绘制有后层和开口的文件夹；颜色由当前主题提供，保持导航与内容的一致性。
     * @param color 当前主题强调色。
     * @param style 图标材质，沿用同一语义轮廓切换描线、亮边或几何描边。
     * @return 文件夹矢量。
     */
    fun createFolderIcon(color: Color, style: ThemeIconStyle = ThemeIconStyle.SOFT): ImageVector = ImageVector.Builder(
        name = "CloudFolder", defaultWidth = 44.dp, defaultHeight = 44.dp,
        viewportWidth = 32f, viewportHeight = 32f
    ).apply {
        path(
            fill = if (style == ThemeIconStyle.LINE) null else SolidColor(color.copy(alpha = 0.18f)),
            stroke = SolidColor(color),
            strokeLineWidth = if (style == ThemeIconStyle.GEOMETRIC) 1.8f else 1.1f
        ) {
            moveTo(4f, 10f)
            quadTo(4f, 7f, 7f, 7f)
            lineTo(12f, 7f)
            lineTo(15f, 10f)
            lineTo(25f, 10f)
            quadTo(28f, 10f, 28f, 13f)
            lineTo(27f, 23f)
            quadTo(27f, 26f, 24f, 26f)
            lineTo(7f, 26f)
            quadTo(4f, 26f, 4f, 23f)
            close()
        }
        path(
            fill = if (style == ThemeIconStyle.LINE) null else Brush.linearGradient(
                listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.8f)),
                start = Offset(4f, 13f), end = Offset(28f, 27f)),
            stroke = SolidColor(color), strokeLineWidth = if (style == ThemeIconStyle.GEOMETRIC) 1.8f else 1.1f
        ) {
            moveTo(5f, 14f)
            lineTo(12f, 14f)
            lineTo(15f, 16f)
            lineTo(27f, 16f)
            quadTo(29f, 16f, 28f, 19f)
            lineTo(26f, 24f)
            quadTo(25f, 27f, 22f, 27f)
            lineTo(7f, 27f)
            quadTo(4f, 27f, 4f, 24f)
            lineTo(3f, 17f)
            quadTo(3f, 14f, 5f, 14f)
            close()
        }
        if (style == ThemeIconStyle.GLASS) {
            path(fill = null, stroke = SolidColor(Color.White.copy(alpha = 0.8f)), strokeLineWidth = 0.9f) {
                moveTo(8f, 12f)
                lineTo(8f, 23f)
            }
        }
    }.build()

    /**
     * 构造统一折角纸张及其分类符号；分类色只用于图形，不作为正文颜色。
     * 文件类型使用固定的语义图形与类别色，不随页面主题切换材质。
     * @param color 文件类别的柔和强调色。
     * @param glyph 纸张内部的分类符号。
     * @return 保留原生矢量颜色的文件图标，显示时不得再次施加单色 tint。
     */
    internal fun createStorageFileIcon(
        color: Color, glyph: StorageGlyph
    ): ImageVector = ImageVector.Builder(
        name = "CloudFile${glyph.name}", defaultWidth = 44.dp, defaultHeight = 44.dp,
        viewportWidth = 32f, viewportHeight = 32f
    ).apply {
        path(
            fill = Brush.linearGradient(
                listOf(color.copy(alpha = 0.08f), color.copy(alpha = 0.24f)),
                start = Offset(6f, 3f), end = Offset(26f, 29f)),
            stroke = SolidColor(color.copy(alpha = 0.65f)),
            strokeLineWidth = 1.1f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 3f)
            lineTo(19f, 3f)
            lineTo(26f, 10f)
            lineTo(26f, 26f)
            quadTo(26f, 29f, 23f, 29f)
            lineTo(9f, 29f)
            quadTo(6f, 29f, 6f, 26f)
            lineTo(6f, 6f)
            quadTo(6f, 3f, 9f, 3f)
            close()
        }
        path(fill = SolidColor(color.copy(alpha = 0.2f)), stroke = SolidColor(color), strokeLineWidth = 1f) {
            moveTo(19f, 3f)
            lineTo(19f, 8f)
            quadTo(19f, 10f, 21f, 10f)
            lineTo(26f, 10f)
        }
        path(
            fill = null, stroke = SolidColor(color), strokeLineWidth = 1.65f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            when (glyph) {
                StorageGlyph.UNKNOWN -> {
                    moveTo(13f, 16f)
                    curveTo(13f, 12f, 21f, 12f, 21f, 16f)
                    curveTo(21f, 19f, 17f, 18f, 17f, 21f)
                    moveTo(17f, 24f)
                    lineTo(17.1f, 24f)
                }
                StorageGlyph.ANDROID -> {
                    moveTo(11f, 18f)
                    lineTo(11f, 17f)
                    curveTo(11f, 12f, 21f, 12f, 21f, 17f)
                    lineTo(21f, 18f)
                    close()
                    moveTo(13f, 14f)
                    lineTo(12f, 12f)
                    moveTo(19f, 14f)
                    lineTo(20f, 12f)
                    moveTo(13.5f, 16.5f)
                    lineTo(13.6f, 16.5f)
                    moveTo(18.5f, 16.5f)
                    lineTo(18.6f, 16.5f)
                    moveTo(12f, 20.5f)
                    lineTo(20f, 20.5f)
                    lineTo(20f, 23f)
                    lineTo(12f, 23f)
                    close()
                    moveTo(14f, 23f)
                    lineTo(14f, 25f)
                    moveTo(18f, 23f)
                    lineTo(18f, 25f)
                    moveTo(9f, 20f)
                    lineTo(9f, 23f)
                    moveTo(23f, 20f)
                    lineTo(23f, 23f)
                }
                StorageGlyph.JSON -> {
                    moveTo(13f, 13f)
                    quadTo(11f, 13f, 11f, 15f)
                    lineTo(11f, 17f)
                    quadTo(11f, 19f, 9f, 19f)
                    quadTo(11f, 19f, 11f, 21f)
                    lineTo(11f, 23f)
                    quadTo(11f, 25f, 13f, 25f)
                    moveTo(19f, 13f)
                    quadTo(21f, 13f, 21f, 15f)
                    lineTo(21f, 17f)
                    quadTo(21f, 19f, 23f, 19f)
                    quadTo(21f, 19f, 21f, 21f)
                    lineTo(21f, 23f)
                    quadTo(21f, 25f, 19f, 25f)
                    moveTo(16f, 17f)
                    lineTo(16.1f, 17f)
                    moveTo(16f, 21f)
                    lineTo(16.1f, 21f)
                }
                StorageGlyph.WEB -> {
                    moveTo(10f, 13f)
                    lineTo(23f, 13f)
                    lineTo(23f, 24f)
                    lineTo(10f, 24f)
                    close()
                    moveTo(10f, 16f)
                    lineTo(23f, 16f)
                    moveTo(14f, 18.5f)
                    lineTo(12f, 20f)
                    lineTo(14f, 21.5f)
                    moveTo(19f, 18.5f)
                    lineTo(21f, 20f)
                    lineTo(19f, 21.5f)
                }
                StorageGlyph.WINDOWS -> {
                    moveTo(10f, 15f)
                    lineTo(15f, 14f)
                    lineTo(15f, 18f)
                    lineTo(10f, 18f)
                    close()
                    moveTo(18f, 13.5f)
                    lineTo(23f, 12.5f)
                    lineTo(23f, 18f)
                    lineTo(18f, 18f)
                    close()
                    moveTo(10f, 21f)
                    lineTo(15f, 21f)
                    lineTo(15f, 25f)
                    lineTo(10f, 24f)
                    close()
                    moveTo(18f, 21f)
                    lineTo(23f, 21f)
                    lineTo(23f, 26f)
                    lineTo(18f, 25f)
                    close()
                }
                StorageGlyph.DISK -> {
                    moveTo(10f, 18f)
                    lineTo(12f, 13f)
                    lineTo(20f, 13f)
                    lineTo(22f, 18f)
                    lineTo(22f, 24f)
                    lineTo(10f, 24f)
                    close()
                    moveTo(10f, 18f)
                    lineTo(22f, 18f)
                    moveTo(14f, 15.5f)
                    curveTo(14f, 14f, 18f, 14f, 18f, 15.5f)
                    curveTo(18f, 17f, 14f, 17f, 14f, 15.5f)
                    moveTo(13f, 21f)
                    lineTo(13.1f, 21f)
                    moveTo(17f, 21f)
                    lineTo(19f, 21f)
                }
                StorageGlyph.PACKAGE -> {
                    moveTo(10f, 16f)
                    lineTo(16f, 13f)
                    lineTo(22f, 16f)
                    lineTo(22f, 22f)
                    lineTo(16f, 25f)
                    lineTo(10f, 22f)
                    close()
                    moveTo(10f, 16f)
                    lineTo(16f, 19f)
                    lineTo(22f, 16f)
                    moveTo(16f, 19f)
                    lineTo(16f, 25f)
                    moveTo(13f, 14.5f)
                    lineTo(19f, 17.5f)
                    lineTo(19f, 20.5f)
                }
                StorageGlyph.CONFIG -> {
                    moveTo(10f, 14f)
                    lineTo(13f, 14f)
                    moveTo(16f, 14f)
                    lineTo(22f, 14f)
                    moveTo(13f, 12.5f)
                    lineTo(16f, 12.5f)
                    lineTo(16f, 15.5f)
                    lineTo(13f, 15.5f)
                    close()
                    moveTo(10f, 19f)
                    lineTo(18f, 19f)
                    moveTo(21f, 19f)
                    lineTo(22f, 19f)
                    moveTo(18f, 17.5f)
                    lineTo(21f, 17.5f)
                    lineTo(21f, 20.5f)
                    lineTo(18f, 20.5f)
                    close()
                    moveTo(10f, 24f)
                    lineTo(12f, 24f)
                    moveTo(15f, 24f)
                    lineTo(22f, 24f)
                    moveTo(12f, 22.5f)
                    lineTo(15f, 22.5f)
                    lineTo(15f, 25.5f)
                    lineTo(12f, 25.5f)
                    close()
                }
                StorageGlyph.DATABASE -> {
                    moveTo(11f, 15f)
                    curveTo(11f, 11f, 22f, 11f, 22f, 15f)
                    curveTo(22f, 19f, 11f, 19f, 11f, 15f)
                    lineTo(11f, 23f)
                    curveTo(11f, 27f, 22f, 27f, 22f, 23f)
                    lineTo(22f, 15f)
                    moveTo(11f, 19f)
                    curveTo(11f, 23f, 22f, 23f, 22f, 19f)
                }
                StorageGlyph.PDF -> {
                    moveTo(10f, 23f)
                    lineTo(10f, 16f)
                    lineTo(12.5f, 16f)
                    quadTo(15f, 18f, 12.5f, 20f)
                    lineTo(10f, 20f)
                    moveTo(16f, 23f)
                    lineTo(16f, 16f)
                    curveTo(21f, 16f, 21f, 23f, 16f, 23f)
                    moveTo(22f, 23f)
                    lineTo(22f, 16f)
                    lineTo(25f, 16f)
                    moveTo(22f, 19.5f)
                    lineTo(24.5f, 19.5f)
                }
                StorageGlyph.WORD -> {
                    moveTo(10f, 14f)
                    lineTo(12.5f, 22f)
                    lineTo(16f, 16.5f)
                    lineTo(19.5f, 22f)
                    lineTo(22f, 14f)
                    moveTo(11f, 25f)
                    lineTo(21f, 25f)
                }
                StorageGlyph.MARKDOWN -> {
                    moveTo(10f, 23f)
                    lineTo(10f, 15f)
                    lineTo(13.5f, 19f)
                    lineTo(17f, 15f)
                    lineTo(17f, 23f)
                    moveTo(22f, 15f)
                    lineTo(22f, 23f)
                    moveTo(19.5f, 20.5f)
                    lineTo(22f, 23f)
                    lineTo(24.5f, 20.5f)
                }
                StorageGlyph.STYLE -> {
                    moveTo(14f, 13f)
                    lineTo(12f, 25f)
                    moveTo(20f, 13f)
                    lineTo(18f, 25f)
                    moveTo(10f, 17f)
                    lineTo(23f, 17f)
                    moveTo(9f, 22f)
                    lineTo(22f, 22f)
                }
                StorageGlyph.LIBRARY -> {
                    moveTo(12f, 15f)
                    lineTo(21f, 15f)
                    lineTo(21f, 24f)
                    lineTo(12f, 24f)
                    close()
                    moveTo(9f, 17f)
                    lineTo(12f, 17f)
                    moveTo(9f, 22f)
                    lineTo(12f, 22f)
                    moveTo(21f, 17f)
                    lineTo(24f, 17f)
                    moveTo(21f, 22f)
                    lineTo(24f, 22f)
                    moveTo(14f, 12f)
                    lineTo(14f, 15f)
                    moveTo(19f, 12f)
                    lineTo(19f, 15f)
                    moveTo(14f, 24f)
                    lineTo(14f, 27f)
                    moveTo(19f, 24f)
                    lineTo(19f, 27f)
                }
                StorageGlyph.TEXT -> {
                    moveTo(11f, 15f)
                    lineTo(21f, 15f)
                    moveTo(11f, 19f)
                    lineTo(21f, 19f)
                    moveTo(11f, 23f)
                    lineTo(17f, 23f)
                }
                StorageGlyph.IMAGE -> {
                    moveTo(10f, 23f)
                    lineTo(15f, 16f)
                    lineTo(19f, 21f)
                    lineTo(22f, 18f)
                    moveTo(20f, 14f)
                    lineTo(20.1f, 14f)
                }
                StorageGlyph.VIDEO -> {
                    moveTo(13f, 14f)
                    lineTo(22f, 19f)
                    lineTo(13f, 24f)
                    close()
                }
                StorageGlyph.AUDIO -> {
                    moveTo(18f, 22f)
                    lineTo(18f, 14f)
                    lineTo(23f, 13f)
                    moveTo(18f, 22f)
                    curveTo(17f, 19f, 11f, 21f, 12f, 24f)
                    curveTo(13f, 26f, 18f, 25f, 18f, 22f)
                }
                StorageGlyph.TABLE -> {
                    moveTo(10f, 14f)
                    lineTo(22f, 14f)
                    lineTo(22f, 24f)
                    lineTo(10f, 24f)
                    close()
                    moveTo(10f, 18f)
                    lineTo(22f, 18f)
                    moveTo(16f, 14f)
                    lineTo(16f, 24f)
                }
                StorageGlyph.SLIDES -> {
                    moveTo(10f, 14f)
                    lineTo(22f, 14f)
                    lineTo(22f, 22f)
                    lineTo(10f, 22f)
                    close()
                    moveTo(16f, 22f)
                    lineTo(16f, 25f)
                    moveTo(13f, 25f)
                    lineTo(19f, 25f)
                    moveTo(13f, 19f)
                    lineTo(16f, 17f)
                    lineTo(19f, 19f)
                }
                StorageGlyph.ARCHIVE -> {
                    moveTo(15f, 13f)
                    lineTo(17f, 13f)
                    moveTo(17f, 16f)
                    lineTo(19f, 16f)
                    moveTo(15f, 19f)
                    lineTo(17f, 19f)
                    moveTo(16f, 22f)
                    lineTo(19f, 22f)
                    lineTo(19f, 25f)
                    lineTo(16f, 25f)
                    close()
                }
                StorageGlyph.CODE -> {
                    moveTo(13f, 15f)
                    lineTo(10f, 19f)
                    lineTo(13f, 23f)
                    moveTo(20f, 15f)
                    lineTo(23f, 19f)
                    lineTo(20f, 23f)
                    moveTo(18f, 14f)
                    lineTo(15f, 24f)
                }
                StorageGlyph.FONT -> {
                    moveTo(11f, 24f)
                    lineTo(16f, 13f)
                    lineTo(21f, 24f)
                    moveTo(13f, 20f)
                    lineTo(19f, 20f)
                }
                StorageGlyph.APP -> {
                    moveTo(10f, 14f)
                    lineTo(14f, 14f)
                    lineTo(14f, 18f)
                    lineTo(10f, 18f)
                    close()
                    moveTo(18f, 14f)
                    lineTo(22f, 14f)
                    lineTo(22f, 18f)
                    lineTo(18f, 18f)
                    close()
                    moveTo(10f, 22f)
                    lineTo(14f, 22f)
                    moveTo(18f, 22f)
                    lineTo(22f, 22f)
                }
                StorageGlyph.JAVA -> {
                    moveTo(11f, 17f)
                    lineTo(20f, 17f)
                    lineTo(20f, 22f)
                    quadTo(15f, 26f, 11f, 22f)
                    close()
                    moveTo(20f, 18f)
                    curveTo(25f, 16f, 25f, 23f, 20f, 22f)
                    moveTo(14f, 14f)
                    lineTo(15f, 12f)
                    moveTo(18f, 14f)
                    lineTo(19f, 12f)
                }
                StorageGlyph.TERMINAL -> {
                    moveTo(11f, 15f)
                    lineTo(15f, 19f)
                    lineTo(11f, 23f)
                    moveTo(18f, 23f)
                    lineTo(22f, 23f)
                }
            }
        }
    }.build()
}
