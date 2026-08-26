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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.data.ThemePalette

/** 默认浅色回退配色；Android 12 以下或关闭动态取色时使用。 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6BCB),
    onPrimary = Color.White,
    secondary = Color(0xFF3E8EDE),
    tertiary = Color(0xFF00897B)
)

/** 默认深色回退配色；Android 12 以下或关闭动态取色时使用。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2B4A),
    secondary = Color(0xFFA0C9FF),
    tertiary = Color(0xFF4DB6AC)
)

/** Gemini Logo 的蓝、青、紫、红横向渐变，用于选中导航等品牌强调区域。 */
private val GeminiBrandGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF217BFE),
        Color(0xFF078EFB),
        Color(0xFFAC87EB),
        Color(0xFFEE4D5D)
    )
)

/**
 * 夏日晴空主题在远程壁纸不可用时使用的本地渐变兜底。
 *
 * 渐变只承担背景氛围，不替代内容容器；所有文字仍由 Material 3 的前景色保证对比度。
 */
private val SummerSkyBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF82C8F4),
        Color(0xFFCDEBFA),
        Color(0xFFDDF4D3)
    )
)

/**
 * “夏日晴空（by Strivy）”原主题指定的远程壁纸地址。
 *
 * App 仅在用户主动选择该主题时请求，Coil 会写入应用私有磁盘缓存；地址本身不包含凭证。
 */
const val SUMMER_SKY_BACKGROUND_URL =
    "https://haowallpaper.com/link/common/file/previewFileImg/18325667651636608"

/** 全局圆角体系：强化卡片、对话框与输入组件的层次，同时保持 Material 3 组件一致性。 */
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

/**
 * 当前配色主题提供的品牌渐变；默认主题为 `null`，从而完整保留原有 Material 3 组件外观。
 */
val LocalBrandGradient = staticCompositionLocalOf<Brush?> { null }

/** 当前主题的全屏装饰背景；`null` 表示由 Material 3 背景色正常填充。 */
val LocalAppBackground = staticCompositionLocalOf<Brush?> { null }

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
 * 构造“夏日晴空（by Strivy）”固定配色。
 *
 * 原 CSS 的 primary、secondary 与前景色按 HSL 转换为固定 RGB；背景设为透明，
 * 由应用根节点绘制晴空背景。Surface 系列保留 75%—82% 不透明度，对应原主题的
 * 内容层透明度，同时确保列表、卡片和弹窗在复杂背景上仍可阅读。
 *
 * @param darkTheme 是否返回深色方案。
 * @return 支持透明内容层的 Material 3 配色。
 */
internal fun summerSkyColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF318CE8),
        onPrimary = Color.White,
        primaryContainer = Color(0xCC173B5C),
        onPrimaryContainer = Color(0xFFF0F2F5),
        secondary = Color(0xFF4F9630),
        onSecondary = Color.White,
        secondaryContainer = Color(0xCC29461E),
        onSecondaryContainer = Color(0xFFF0F2F5),
        background = Color.Transparent,
        onBackground = Color(0xFFF0F2F5),
        surface = Color(0xCC14212D),
        onSurface = Color(0xFFF0F2F5),
        surfaceVariant = Color(0xCC213543),
        onSurfaceVariant = Color(0xFFDDE5EB),
        surfaceContainerLowest = Color(0xBF101A24),
        surfaceContainerLow = Color(0xBF172632),
        surfaceContainer = Color(0xCC1B2C39),
        surfaceContainerHigh = Color(0xD1213544),
        surfaceContainerHighest = Color(0xD1273E4E),
        outline = Color(0x669FC4DD),
        outlineVariant = Color(0x405E8198)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF318CE8),
        onPrimary = Color.White,
        primaryContainer = Color(0xBFD6EDFF),
        onPrimaryContainer = Color(0xFF0D1A26),
        secondary = Color(0xFF5BA630),
        onSecondary = Color.White,
        secondaryContainer = Color(0xBFDDF1D2),
        onSecondaryContainer = Color(0xFF17300B),
        background = Color.Transparent,
        onBackground = Color(0xFF0D1A26),
        surface = Color(0xBFFFFFFF),
        onSurface = Color(0xFF0D1A26),
        surfaceVariant = Color(0xCCEAF5FC),
        onSurfaceVariant = Color(0xFF29475F),
        surfaceContainerLowest = Color(0xBFFFFFFF),
        surfaceContainerLow = Color(0xBFF7FCFF),
        surfaceContainer = Color(0xC7F0F8FD),
        surfaceContainerHigh = Color(0xCCE8F3F9),
        surfaceContainerHighest = Color(0xD1E0EEF6),
        outline = Color(0x66517086),
        outlineVariant = Color(0x40517086)
    )
}

/**
 * 应用 Material 3 主题。
 *
 * @param darkTheme 是否使用深色方案。
 * @param palette 用户选择的配色主题。
 * @param dynamicColor 默认主题是否允许 Android 12+ 系统动态取色；自定义主题始终使用固定色。
 * @param content 主题作用域内的 Compose 内容。
 */
@Composable
fun OssBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.DEFAULT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = when {
        palette == ThemePalette.GEMINI -> geminiColorScheme(darkTheme)
        palette == ThemePalette.SUMMER_SKY -> summerSkyColorScheme(darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val brandGradient = GeminiBrandGradient.takeIf { palette == ThemePalette.GEMINI }
    val appBackground = SummerSkyBackgroundGradient.takeIf { palette == ThemePalette.SUMMER_SKY }
    CompositionLocalProvider(
        LocalBrandGradient provides brandGradient,
        LocalAppBackground provides appBackground
    ) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
