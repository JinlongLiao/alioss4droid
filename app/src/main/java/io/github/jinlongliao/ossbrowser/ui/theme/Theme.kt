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
package io.github.jinlongliao.ossbrowser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import io.github.jinlongliao.ossbrowser.data.ThemePalette

/**
 * 默认云雾蓝配色：白色内容层与低饱和蓝色强调，避免大面积彩色背景干扰文件阅读。
 *
 * @param darkTheme 是否返回低亮度的深色方案。
 * @return 不依赖系统壁纸取色的 Material 3 配色。
 */
internal fun cloudColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFFA8C7FF),
        onPrimary = Color(0xFF17375F),
        primaryContainer = Color(0xFF263E63),
        onPrimaryContainer = Color(0xFFDCE8FF),
        secondary = Color(0xFFC9BEF4),
        onSecondary = Color(0xFF352D53),
        secondaryContainer = Color(0xFF3C3459),
        onSecondaryContainer = Color(0xFFEAE1FF),
        tertiary = Color(0xFF8DD4DE),
        onTertiary = Color(0xFF07373E),
        tertiaryContainer = Color(0xFF164650),
        onTertiaryContainer = Color(0xFFBDEEF4),
        background = Color(0xFF151A22),
        onBackground = Color(0xFFE5EBF6),
        surface = Color(0xFF202630),
        onSurface = Color(0xFFE5EBF6),
        surfaceVariant = Color(0xFF2D3A50),
        onSurfaceVariant = Color(0xFFBAC7DC),
        surfaceContainerLowest = Color(0xFF121C2D),
        surfaceContainerLow = Color(0xFF202630),
        surfaceContainer = Color(0xFF202D42),
        surfaceContainerHigh = Color(0xFF28344A),
        surfaceContainerHighest = Color(0xFF313E54),
        outline = Color(0xFF8795AC),
        outlineVariant = Color(0xFF3B4960)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF355FA2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8EFF8),
        onPrimaryContainer = Color(0xFF1B3C6B),
        secondary = Color(0xFF66758F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEF0F6),
        onSecondaryContainer = Color(0xFF443365),
        tertiary = Color(0xFF226A78),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCDF3F7),
        onTertiaryContainer = Color(0xFF164653),
        background = Color(0xFFF6F8FC),
        onBackground = Color(0xFF202D44),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF202D44),
        surfaceVariant = Color(0xFFE6ECF7),
        onSurfaceVariant = Color(0xFF526079),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F9FF),
        surfaceContainer = Color(0xFFEFF3FC),
        surfaceContainerHigh = Color(0xFFE8EEFA),
        surfaceContainerHighest = Color(0xFFE1E8F5),
        outline = Color(0xFF74829B),
        outlineVariant = Color(0xFFE5E9F0)
    )
}

/** 全局圆角体系：强化卡片、对话框与输入组件的层次，同时保持 Material 3 组件一致性。 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/** 标题层级采用中等字重，正文保留系统字体和缩放能力，兼容中英文及无障碍字号。 */
private val AppTypography = Typography().let { defaults ->
    defaults.copy(
        headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = defaults.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

/**
 * 构造 Gemini 固定配色，不读取系统壁纸颜色。
 *
 * CSS 中的灰阶用于背景、Surface 和轮廓，蓝/紫/红用于 primary、secondary、tertiary，
 * 使浅色和深色模式都保持 Gemini 的品牌辨识度。
 *
 * @param darkTheme 是否返回深色方案。
 * @return 完整的 Material 3 Gemini [androidx.compose.material3.ColorScheme]。
 */
internal fun geminiColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF8AB4F8),
        onPrimary = Color(0xFF002E69),
        primaryContainer = Color(0xFF174EA6),
        onPrimaryContainer = Color(0xFFD7E3FF),
        secondary = Color(0xFFD2BBFF),
        onSecondary = Color(0xFF3D2368),
        secondaryContainer = Color(0xFF563C80),
        onSecondaryContainer = Color(0xFFEADDFF),
        tertiary = Color(0xFFFFB3BB),
        onTertiary = Color(0xFF67001F),
        tertiaryContainer = Color(0xFF8E1735),
        onTertiaryContainer = Color(0xFFFFD9DD),
        background = Color(0xFF060606),
        onBackground = Color(0xFFE3E7F2),
        surface = Color(0xFF1E1F20),
        onSurface = Color(0xFFE3E7F2),
        surfaceVariant = Color(0xFF2F3543),
        onSurfaceVariant = Color(0xFFB7BECF),
        outline = Color(0xFF828FB1),
        outlineVariant = Color(0xFF3F4656),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF185ABC),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF001B3F),
        secondary = Color(0xFF72568F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEDCFF),
        onSecondaryContainer = Color(0xFF2A0E46),
        tertiary = Color(0xFFB3264A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E1),
        onTertiaryContainer = Color(0xFF3F0015),
        background = Color.White,
        onBackground = Color(0xFF202124),
        surface = Color(0xFFF0F4F9),
        onSurface = Color(0xFF202124),
        surfaceVariant = Color(0xFFE7EBF3),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C7CF),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

/**
 * 根据配色构造不透明的内容色板，背景只使用轻微色相，强调色用于操作与选中态。
 *
 * @param palette 用户选择并以枚举名持久化的主题。
 * @param darkTheme 是否使用对应的深色方案。
 * @return 完整色板；正文容器不依赖网络图片或背景透明度。
 */
internal fun appColorScheme(palette: ThemePalette, darkTheme: Boolean) = when (palette) {
    ThemePalette.DEFAULT -> cloudColorScheme(darkTheme)
    ThemePalette.GEMINI -> geminiColorScheme(darkTheme)
    ThemePalette.SUMMER_SKY -> summerSkyColorScheme(darkTheme)
    ThemePalette.CELADON -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFF9AD7BF else 0xFF286C5C),
        container = Color(if (darkTheme) 0xFF233E34 else 0xFFDEEFE6),
        background = Color(if (darkTheme) 0xFF151D1A else 0xFFF4F8F5)
    )
    ThemePalette.DUSK -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFCAB8E9 else 0xFF6B5793),
        container = Color(if (darkTheme) 0xFF3C3250 else 0xFFEEE6F7),
        background = Color(if (darkTheme) 0xFF1C1922 else 0xFFF8F6FA)
    )
    ThemePalette.SAND -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFDDC09A else 0xFF805F39),
        container = Color(if (darkTheme) 0xFF493B29 else 0xFFF2E8D8),
        background = Color(if (darkTheme) 0xFF201C17 else 0xFFFAF8F4)
    )
    ThemePalette.PRISM -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFB8C4FF else 0xFF4B60A8),
        container = Color(if (darkTheme) 0xFF313D63 else 0xFFE6EAFF),
        background = Color(if (darkTheme) 0xFF161B2D else 0xFFF8F7FF)
    )
    ThemePalette.AURORA -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFF8CDCCB else 0xFF246D68),
        container = Color(if (darkTheme) 0xFF214540 else 0xFFD8F4EE),
        background = Color(if (darkTheme) 0xFF101E26 else 0xFFF0FAF7)
    )
    ThemePalette.GLACIER_GLASS -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFA4D9F4 else 0xFF2B648B),
        container = Color(if (darkTheme) 0xFF24465C else 0xFFE1F2FA),
        background = Color(if (darkTheme) 0xFF142234 else 0xFFEAF5FD)
    )
    ThemePalette.TWILIGHT_GLASS -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFD1BCF8 else 0xFF705397),
        container = Color(if (darkTheme) 0xFF40325A else 0xFFEDE2FA),
        background = Color(if (darkTheme) 0xFF211A33 else 0xFFF2EDFA)
    )
    ThemePalette.MONOCHROME -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFF0F0F0 else 0xFF292929),
        container = Color(if (darkTheme) 0xFF363636 else 0xFFEBEBEB),
        background = Color(if (darkTheme) 0xFF000000 else 0xFFF7F7F7)
    )
    ThemePalette.TERMINAL -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFF8FE0A3 else 0xFF27623C),
        container = Color(if (darkTheme) 0xFF173D24 else 0xFFE0F1E3),
        background = Color(if (darkTheme) 0xFF0D1710 else 0xFFF1F7F1)
    )
    ThemePalette.CYBER -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFF70E7DE else 0xFF146C70),
        container = Color(if (darkTheme) 0xFF174246 else 0xFFD9F4F3),
        background = Color(if (darkTheme) 0xFF081620 else 0xFFF0F8FC)
    )
    ThemePalette.PAPER -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFE3C8A1 else 0xFF755B3C),
        container = Color(if (darkTheme) 0xFF493C2B else 0xFFF0E4D1),
        background = Color(if (darkTheme) 0xFF221F19 else 0xFFFAF6EC)
    )
    ThemePalette.SAKURA -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFF1BCD0 else 0xFF975A75),
        container = Color(if (darkTheme) 0xFF553246 else 0xFFF7E2EC),
        background = Color(if (darkTheme) 0xFF251C27 else 0xFFFCF4F8)
    )
    ThemePalette.FOREST -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFB8D5A3 else 0xFF516D3F),
        container = Color(if (darkTheme) 0xFF33452B else 0xFFE7EFDB),
        background = Color(if (darkTheme) 0xFF172117 else 0xFFF4F7EF)
    )
    ThemePalette.SEA_SALT -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFF96D6E4 else 0xFF246E7D),
        container = Color(if (darkTheme) 0xFF1B444C else 0xFFDEF2F5),
        background = Color(if (darkTheme) 0xFF112329 else 0xFFF0F9FA)
    )
    ThemePalette.SUNSET -> accentColorScheme(
        darkTheme,
        primary = Color(if (darkTheme) 0xFFFFC29E else 0xFF8E563B),
        container = Color(if (darkTheme) 0xFF553927 else 0xFFFFE9DA),
        background = Color(if (darkTheme) 0xFF291D24 else 0xFFFFF7F0)
    )
}.let { colors ->
    if (palette == ThemePalette.GLACIER_GLASS || palette == ThemePalette.TWILIGHT_GLASS) {
        colors.copy(
            surface = colors.surface.copy(alpha = 0.92f),
            surfaceContainerLow = colors.surfaceContainerLow.copy(alpha = 0.94f),
            surfaceContainer = colors.surfaceContainer.copy(alpha = 0.96f),
            outlineVariant = Color.White.copy(alpha = if (darkTheme) 0.24f else 0.8f)
        )
    } else if (palette == ThemePalette.MONOCHROME) {
        colors.copy(
            onPrimary = if (darkTheme) Color.Black else Color.White,
            onSecondary = if (darkTheme) Color.Black else Color.White,
            onTertiary = if (darkTheme) Color.Black else Color.White,
            onBackground = if (darkTheme) Color(0xFFF0F0F0) else Color(0xFF252525),
            onSurface = if (darkTheme) Color(0xFFF0F0F0) else Color(0xFF252525),
            onSurfaceVariant = if (darkTheme) Color(0xFFBEBEBE) else Color(0xFF606060),
            surface = if (darkTheme) Color(0xFF101010) else Color.White,
            outlineVariant = if (darkTheme) Color(0xFF373737) else Color(0xFFE2E2E2)
        )
    } else colors
}

/**
 * 旧晴空主题迁移为本地蓝青配色，保留持久化枚举名以兼容升级。
 *
 * @param darkTheme 是否返回深色方案。
 * @return 不含远程壁纸和透明正文容器的晴空色板。
 */
internal fun summerSkyColorScheme(darkTheme: Boolean) = accentColorScheme(
    darkTheme,
    primary = Color(if (darkTheme) 0xFFA7CFE8 else 0xFF2C6488),
    container = Color(if (darkTheme) 0xFF233E50 else 0xFFDFEDF6),
    background = Color(if (darkTheme) 0xFF111B23 else 0xFFF2F7FA)
)

/**
 * 为同一组内容层应用主题强调色，保证各主题的控件与弹窗仍使用一致的中性表面。
 *
 * @param darkTheme 是否使用深色正文和容器。
 * @param primary 按钮、图标和选中态的强调色。
 * @param container 低饱和强调容器色，正文使用单独的高对比文字色。
 * @param background 页面底色，必须完全不透明。
 * @return 已补齐各级内容层与前景色的主题色板。
 */
private fun accentColorScheme(darkTheme: Boolean, primary: Color, container: Color, background: Color) =
    cloudColorScheme(darkTheme).copy(
        primary = primary,
        onPrimary = if (darkTheme) Color(0xFF17262B) else Color.White,
        secondary = primary,
        onSecondary = if (darkTheme) Color(0xFF17262B) else Color.White,
        tertiary = primary,
        onTertiary = if (darkTheme) Color(0xFF17262B) else Color.White,
        primaryContainer = container,
        secondaryContainer = container,
        tertiaryContainer = container,
        onPrimaryContainer = if (darkTheme) Color(0xFFE7EAF0) else Color(0xFF28353D),
        onSecondaryContainer = if (darkTheme) Color(0xFFE7EAF0) else Color(0xFF28353D),
        onTertiaryContainer = if (darkTheme) Color(0xFFE7EAF0) else Color(0xFF28353D),
        background = background,
        surface = if (darkTheme) Color(0xFF24262B) else Color.White,
        surfaceContainerLow = if (darkTheme) Color(0xFF24262B) else Color(0xFFF9FAFB),
        surfaceContainer = if (darkTheme) Color(0xFF2A2C31) else Color(0xFFF3F4F6),
        surfaceContainerHigh = if (darkTheme) Color(0xFF303238) else Color(0xFFEEF0F3),
        surfaceContainerHighest = if (darkTheme) Color(0xFF37393F) else Color(0xFFE8EBEF),
        outlineVariant = if (darkTheme) Color(0xFF3E4249) else Color(0xFFE3E7EB)
    )

/**
 * 应用 Material 3 主题。
 *
 * @param darkTheme 是否使用深色方案。
 * @param palette 用户选择的配色主题。
 * @param dynamicColor 是否显式启用默认主题的系统取色；默认关闭以保持与启动图标一致。
 * @param content 主题作用域内的 Compose 内容。
 */
@Composable
fun OssBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (
        palette == ThemePalette.DEFAULT && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        appColorScheme(palette, darkTheme)
    }
    val visuals = themeVisualStyle(palette, darkTheme)
    val family = when (palette) {
        ThemePalette.PAPER -> FontFamily.Serif
        ThemePalette.TERMINAL, ThemePalette.CYBER -> FontFamily.Monospace
        else -> null
    }
    val typography = if (family == null) AppTypography else AppTypography.copy(
        titleLarge = AppTypography.titleLarge.copy(fontFamily = family),
        titleMedium = AppTypography.titleMedium.copy(fontFamily = family),
        bodyLarge = AppTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium = AppTypography.bodyMedium.copy(fontFamily = family),
        bodySmall = AppTypography.bodySmall.copy(fontFamily = family),
        labelLarge = AppTypography.labelLarge.copy(fontFamily = family)
    )
    CompositionLocalProvider(LocalThemeVisualStyle provides visuals) {
        val shapes = AppShapes.copy(
            medium = RoundedCornerShape(visuals.cornerRadius),
            small = RoundedCornerShape(if (visuals.cornerRadius < 12.dp) visuals.cornerRadius else 12.dp)
        )
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
    }
}
