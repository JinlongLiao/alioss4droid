package io.github.jinlongliao.ossbrowser.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.data.ThemePalette

/** 图标材质处理；改变图形层次，不改变其业务语义。 */
enum class ThemeIconStyle {
    /** 低饱和分层色块，作为常规主题的基础风格。 */ SOFT,
    /** 单色细描线，适合纸感和极简主题。 */ LINE,
    /** 透明色面与亮边，适合玻璃主题。 */ GLASS,
    /** 方正边角与更明确的描边，适合终端与赛博主题。 */ GEOMETRIC
}

/**
 * 配色之外的视觉令牌。使用少量明确字段控制背景、圆角和图标，避免十八套页面实现分叉。
 * @property palette 当前主题，供背景绘制可辨识的天空等装饰。
 * @property darkTheme 当前明暗状态。
 * @property backgroundColors 全屏背景色序列；单色主题仅有一个颜色。
 * @property accentColors 导航强调渐变；空列表表示使用标准单色选中态。
 * @property cornerRadius 内容卡片圆角，单位为 dp。
 * @property iconStyle 图标的材质表现。
 */
data class ThemeVisualStyle(
    val palette: ThemePalette,
    val darkTheme: Boolean,
    val backgroundColors: List<Color>,
    val accentColors: List<Color>,
    val cornerRadius: Dp,
    val iconStyle: ThemeIconStyle
)

/** 当前主题的完整视觉表现；默认值供独立预览组件使用。 */
val LocalThemeVisualStyle = staticCompositionLocalOf { themeVisualStyle(ThemePalette.DEFAULT, false) }

/**
 * 返回固定的本地视觉方案；不读取壁纸，不执行网络请求或动画任务。
 *
 * Gemini 的多色方向来自 Google 的视觉设计说明；玻璃只借鉴材质层级，不调用其他平台的材质 API。
 * @param palette 持久化主题。
 * @param darkTheme 是否返回低亮度背景。
 * @return 可用于背景、内容卡和图标绘制的不可变快照。
 * @see <a href="https://design.google/library/gemini-ai-visual-design">Google Gemini visual design</a>
 * @see <a
 * href="https://blog.google/company-news/inside-google/company-announcements/gradient-g-logo-design/"
 * >Google gradient direction</a>
 * @see <a href="https://fluent2.microsoft.design/material">Fluent 2 materials</a>
 * @see <a href="https://developer.apple.com/design/human-interface-guidelines/materials">Apple materials</a>
 */
internal fun themeVisualStyle(palette: ThemePalette, darkTheme: Boolean): ThemeVisualStyle {
    val colors = appColorScheme(palette, darkTheme)
    val background = when (palette) {
        ThemePalette.SUMMER_SKY -> if (darkTheme) listOf(0xFF102738, 0xFF18374B, 0xFF223C48)
            else listOf(0xFFCFEAFF, 0xFFE6F4FF, 0xFFF0F8FC)
        ThemePalette.GEMINI -> if (darkTheme) listOf(0xFF132138, 0xFF172A24, 0xFF302819, 0xFF302126)
            else listOf(0xFFEDF4FF, 0xFFEEF8F1, 0xFFFFF7E9, 0xFFFFF0F1)
        ThemePalette.PRISM -> if (darkTheme) listOf(0xFF182542, 0xFF32213B, 0xFF382B22)
            else listOf(0xFFDBF2FF, 0xFFF0E2FF, 0xFFFFE5ED, 0xFFFFF0D2)
        ThemePalette.AURORA -> if (darkTheme) listOf(0xFF082C31, 0xFF183144, 0xFF342548)
            else listOf(0xFFD8F8EE, 0xFFDCF0FF, 0xFFEFE2FF)
        ThemePalette.GLACIER_GLASS -> if (darkTheme) listOf(0xFF183449, 0xFF253756, 0xFF383452)
            else listOf(0xFFBCEEF5, 0xFFD4E4FF, 0xFFE7D6FA)
        ThemePalette.TWILIGHT_GLASS -> if (darkTheme) listOf(0xFF251A42, 0xFF35385C, 0xFF293F50)
            else listOf(0xFFE7D6F8, 0xFFDDE4FB, 0xFFD6EDF1)
        ThemePalette.CYBER -> if (darkTheme) listOf(0xFF08232C, 0xFF181B39, 0xFF291735)
            else listOf(0xFFDDF8F5, 0xFFE7EBFF, 0xFFF4E4FF)
        ThemePalette.SAKURA -> if (darkTheme) listOf(0xFF2C1C29, 0xFF302236)
            else listOf(0xFFFCE6EE, 0xFFF7EEFA)
        ThemePalette.SUNSET -> if (darkTheme) listOf(0xFF37232D, 0xFF382A30, 0xFF242B42)
            else listOf(0xFFFFE1C5, 0xFFFFE7E3, 0xFFECE3FA)
        else -> emptyList()
    }.map { Color(it) }.ifEmpty { listOf(colors.background) }
    val accent = when (palette) {
        ThemePalette.GEMINI -> listOf(0xFF326AC2, 0xFF2E7061, 0xFF8D6D28, 0xFFAD5D62)
        ThemePalette.PRISM -> listOf(0xFF316BAD, 0xFF6D55A1, 0xFFAC536C, 0xFF99602C)
        ThemePalette.AURORA -> listOf(0xFF216F68, 0xFF3D6494, 0xFF75528F)
        ThemePalette.CYBER -> listOf(0xFF196E78, 0xFF6250A1)
        ThemePalette.SUNSET -> listOf(0xFF9B6140, 0xFFA45B70, 0xFF785CA0)
        else -> emptyList()
    }.map { Color(it) }
    val radius = when (palette) {
        ThemePalette.TERMINAL, ThemePalette.CYBER -> 6.dp
        ThemePalette.PAPER -> 8.dp
        ThemePalette.MONOCHROME -> 10.dp
        ThemePalette.PRISM -> 24.dp
        ThemePalette.GLACIER_GLASS, ThemePalette.TWILIGHT_GLASS -> 22.dp
        else -> 16.dp
    }
    val iconStyle = when (palette) {
        ThemePalette.GLACIER_GLASS, ThemePalette.TWILIGHT_GLASS -> ThemeIconStyle.GLASS
        ThemePalette.PAPER, ThemePalette.MONOCHROME -> ThemeIconStyle.LINE
        ThemePalette.TERMINAL, ThemePalette.CYBER -> ThemeIconStyle.GEOMETRIC
        else -> ThemeIconStyle.SOFT
    }
    return ThemeVisualStyle(palette, darkTheme, background, accent, radius, iconStyle)
}
