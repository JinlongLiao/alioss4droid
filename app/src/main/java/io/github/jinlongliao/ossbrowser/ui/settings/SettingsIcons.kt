package io.github.jinlongliao.ossbrowser.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 设置页原创语义图标；统一圆头线条，外层材质由当前主题决定。 */
object SettingsIcons {
    /** 账号连接：小钥匙与连接线。 */
    val Account = createSettingIcon("AccountConnection") {
        moveTo(10f, 9f)
        curveTo(10f, 5f, 4f, 5f, 4f, 9f)
        curveTo(4f, 13f, 10f, 13f, 10f, 9f)
        moveTo(10f, 9f)
        lineTo(20f, 9f)
        lineTo(20f, 13f)
        moveTo(16f, 9f)
        lineTo(16f, 12f)
        moveTo(5f, 18f)
        lineTo(19f, 18f)
    }

    /** 配色：三种相互呼应的色面轮廓。 */
    val Palette = createSettingIcon("ThemeSwatches") {
        moveTo(5f, 4f)
        lineTo(12f, 4f)
        lineTo(12f, 11f)
        lineTo(5f, 11f)
        close()
        moveTo(15f, 7f)
        lineTo(20f, 10f)
        lineTo(17f, 16f)
        lineTo(12f, 13f)
        close()
        moveTo(6f, 15f)
        lineTo(12f, 15f)
        lineTo(12f, 21f)
        lineTo(6f, 21f)
        close()
    }

    /** 明暗：月牙与太阳短光线。 */
    val Mode = createSettingIcon("DayNight") {
        moveTo(14f, 5f)
        curveTo(5f, 3f, 3f, 17f, 12f, 19f)
        curveTo(16f, 20f, 19f, 16f, 19f, 13f)
        curveTo(12f, 16f, 9f, 9f, 14f, 5f)
        moveTo(19f, 4f)
        lineTo(19f, 7f)
        moveTo(17.5f, 5.5f)
        lineTo(20.5f, 5.5f)
    }

    /** 语言：地球经纬线。 */
    val Language = createSettingIcon("LanguageGlobe") {
        moveTo(21f, 12f)
        curveTo(21f, 24f, 3f, 24f, 3f, 12f)
        curveTo(3f, 0f, 21f, 0f, 21f, 12f)
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        moveTo(12f, 3f)
        curveTo(6f, 8f, 6f, 16f, 12f, 21f)
        curveTo(18f, 16f, 18f, 8f, 12f, 3f)
    }

    /** 分页：两张有序内容卡片。 */
    val Pages = createSettingIcon("PagedCards") {
        moveTo(6f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 17f)
        moveTo(4f, 7f)
        lineTo(17f, 7f)
        lineTo(17f, 21f)
        lineTo(4f, 21f)
        close()
        moveTo(8f, 12f)
        lineTo(13f, 12f)
        moveTo(8f, 16f)
        lineTo(13f, 16f)
    }

    /** 文本预览：折角文档和两行内容。 */
    val TextPreview = createSettingIcon("TextPreview") {
        moveTo(6f, 3f)
        lineTo(14f, 3f)
        lineTo(19f, 8f)
        lineTo(19f, 21f)
        lineTo(6f, 21f)
        close()
        moveTo(14f, 3f)
        lineTo(14f, 8f)
        lineTo(19f, 8f)
        moveTo(10f, 13f)
        lineTo(15f, 13f)
        moveTo(10f, 17f)
        lineTo(15f, 17f)
    }

    /** 自动播放：圆角播放器内的播放标识。 */
    val Playback = createSettingIcon("MediaPlayback") {
        moveTo(6f, 5f)
        lineTo(18f, 5f)
        quadTo(21f, 5f, 21f, 8f)
        lineTo(21f, 17f)
        quadTo(21f, 20f, 18f, 20f)
        lineTo(6f, 20f)
        quadTo(3f, 20f, 3f, 17f)
        lineTo(3f, 8f)
        quadTo(3f, 5f, 6f, 5f)
        moveTo(10f, 9f)
        lineTo(16f, 12.5f)
        lineTo(10f, 16f)
        close()
    }

    /** 操作确认：盾牌和勾选。 */
    val Security = createSettingIcon("OperationShield") {
        moveTo(12f, 3f)
        lineTo(20f, 6f)
        lineTo(19f, 14f)
        quadTo(18f, 19f, 12f, 22f)
        quadTo(6f, 19f, 5f, 14f)
        lineTo(4f, 6f)
        close()
        moveTo(8f, 12f)
        lineTo(11f, 15f)
        lineTo(16f, 10f)
    }

    /** 缓存：清扫刷和地面。 */
    val Cache = createSettingIcon("CacheBrush") {
        moveTo(15f, 3f)
        lineTo(10f, 12f)
        moveTo(7f, 11f)
        lineTo(15f, 15f)
        lineTo(12f, 21f)
        lineTo(4f, 17f)
        close()
        moveTo(8f, 15f)
        lineTo(6f, 18f)
        moveTo(16f, 21f)
        lineTo(21f, 21f)
    }

    /** 配置导出：托盘及向上箭头。 */
    val Export = createSettingIcon("ExportProfiles") {
        moveTo(4f, 14f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 14f)
        moveTo(12f, 15f)
        lineTo(12f, 3f)
        moveTo(7f, 8f)
        lineTo(12f, 3f)
        lineTo(17f, 8f)
    }

    /** 配置导入：托盘及向下箭头。 */
    val Import = createSettingIcon("ImportProfiles") {
        moveTo(4f, 14f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 14f)
        moveTo(12f, 3f)
        lineTo(12f, 15f)
        moveTo(7f, 10f)
        lineTo(12f, 15f)
        lineTo(17f, 10f)
    }

    /** 版本信息：信息圆环。 */
    val Info = createSettingIcon("VersionInfo") {
        moveTo(21f, 12f)
        curveTo(21f, 24f, 3f, 24f, 3f, 12f)
        curveTo(3f, 0f, 21f, 0f, 21f, 12f)
        moveTo(12f, 8f)
        lineTo(12.1f, 8f)
        moveTo(12f, 12f)
        lineTo(12f, 17f)
    }

    /** 作者：头像与肩部轮廓。 */
    val Person = createSettingIcon("AuthorProfile") {
        moveTo(16f, 7f)
        curveTo(16f, 13f, 8f, 13f, 8f, 7f)
        curveTo(8f, 1f, 16f, 1f, 16f, 7f)
        moveTo(4f, 21f)
        curveTo(4f, 12f, 20f, 12f, 20f, 21f)
    }

    /** 项目：成对代码括号。 */
    val Project = createSettingIcon("ProjectCode") {
        moveTo(8f, 6f)
        lineTo(3f, 12f)
        lineTo(8f, 18f)
        moveTo(16f, 6f)
        lineTo(21f, 12f)
        lineTo(16f, 18f)
        moveTo(14f, 4f)
        lineTo(10f, 20f)
    }

    /** 协议：展开书页。 */
    val License = createSettingIcon("OpenLicense") {
        moveTo(12f, 6f)
        quadTo(8f, 3f, 3f, 5f)
        lineTo(3f, 20f)
        quadTo(8f, 18f, 12f, 21f)
        quadTo(16f, 18f, 21f, 20f)
        lineTo(21f, 5f)
        quadTo(16f, 3f, 12f, 6f)
        lineTo(12f, 21f)
    }

    /** 隐私：闭合锁体与锁环。 */
    val Privacy = createSettingIcon("PrivacyLock") {
        moveTo(7f, 10f)
        lineTo(7f, 7f)
        curveTo(7f, 0f, 17f, 0f, 17f, 7f)
        lineTo(17f, 10f)
        moveTo(5f, 10f)
        lineTo(19f, 10f)
        lineTo(19f, 21f)
        lineTo(5f, 21f)
        close()
        moveTo(12f, 14f)
        lineTo(12f, 17f)
    }

    /**
     * 构造可在列表中共享的不可变描线矢量。
     * @param name 图标内部标识。
     * @param drawGlyph 原创语义路径，不包含外部资源。
     * @return 24 单位圆头描线图标，交由语义图标容器着色。
     */
    private fun createSettingIcon(name: String, drawGlyph: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            ) { drawGlyph() }
        }.build()
}
