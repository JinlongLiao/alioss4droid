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

import io.github.jinlongliao.ossbrowser.data.ThemePalette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自定义主题浅色/深色 Material 3 配色回归测试。 */
class ThemeTest {

    /** 全部配色与明暗模式的正文、操作按钮和内容容器均需保持普通字号的最低可读对比度。 */
    @Test
    fun allPalettes_textAndActionsRemainReadableInBothModes() {
        for (palette in ThemePalette.entries) {
            for (darkTheme in listOf(false, true)) {
                val colors = appColorScheme(palette, darkTheme)
                val pairs = listOf(
                    colors.onSurface to colors.surface,
                    colors.onSurfaceVariant to colors.surface,
                    colors.onPrimary to colors.primary,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    colors.onPrimaryContainer to colors.secondaryContainer,
                    colors.onSecondaryContainer to colors.secondaryContainer
                )
                for (backdrop in themeVisualStyle(palette, darkTheme).backgroundColors) {
                    for ((foreground, background) in pairs) {
                        val composedBackground = background.compositeOver(backdrop)
                        val lighter = maxOf(foreground.luminance(), composedBackground.luminance())
                        val darker = minOf(foreground.luminance(), composedBackground.luminance())
                        assertTrue(
                            "palette=$palette dark=$darkTheme foreground=$foreground background=$background",
                            (lighter + 0.05f) / (darker + 0.05f) >= 4.5f
                        )
                    }
                }
            }
        }
    }

    /** 多色导航需要在每个渐变停止点上都能承载清晰的白色图标。 */
    @Test
    fun gradientNavigation_keepsWhiteIconsReadable() {
        for (palette in ThemePalette.entries) {
            for (color in themeVisualStyle(palette, false).accentColors) {
                assertTrue("palette=$palette color=$color", 1.05f / (color.luminance() + 0.05f) >= 4.5f)
            }
        }
    }

    /** 玻璃、纸感、终端在材质和圆角上必须存在真实差异，不能仅修改主题名称。 */
    @Test
    fun themeFamilies_changeMaterialShapeAndIconTreatment() {
        val glass = themeVisualStyle(ThemePalette.GLACIER_GLASS, false)
        val paper = themeVisualStyle(ThemePalette.PAPER, false)
        val terminal = themeVisualStyle(ThemePalette.TERMINAL, false)

        assertTrue(appColorScheme(ThemePalette.GLACIER_GLASS, false).surface.alpha < 1f)
        assertEquals(ThemeIconStyle.GLASS, glass.iconStyle)
        assertEquals(ThemeIconStyle.LINE, paper.iconStyle)
        assertEquals(ThemeIconStyle.GEOMETRIC, terminal.iconStyle)
        assertTrue(terminal.cornerRadius < glass.cornerRadius)
        assertTrue(themeVisualStyle(ThemePalette.SUMMER_SKY, false).backgroundColors.size > 1)
    }

    /** 深色模式应真正降低内容层亮度，而不是继续使用浅色玻璃背景。 */
    @Test
    fun cloudColorScheme_darkModeUsesLowLuminanceSurfaces() {
        val light = cloudColorScheme(false)
        val dark = cloudColorScheme(true)

        assertTrue(light.surface.luminance() > 0.9f)
        assertTrue(dark.surface.luminance() < 0.05f)
        assertTrue(dark.onSurface.luminance() > 0.7f)
    }

    /** 深色方案应使用 CSS 指定的近黑背景和 Gemini 浅蓝主色。 */
    @Test
    fun geminiColorScheme_dark_usesFixedBrandPalette() {
        val colors = geminiColorScheme(darkTheme = true)

        assertEquals(Color(0xFF060606), colors.background)
        assertEquals(Color(0xFF8AB4F8), colors.primary)
    }

    /** 浅色方案应保持白色背景，并与深色方案拥有不同的主色明度。 */
    @Test
    fun geminiColorScheme_light_usesFixedBrandPalette() {
        val light = geminiColorScheme(darkTheme = false)
        val dark = geminiColorScheme(darkTheme = true)

        assertEquals(Color.White, light.background)
        assertEquals(Color(0xFF185ABC), light.primary)
        assertNotEquals(dark.primary, light.primary)
    }

    /** 晴空主题兼容旧枚举名，但正文与背景必须完全不透明，不再依赖远程壁纸。 */
    @Test
    fun summerSkyColorScheme_light_usesOpaqueLocalPalette() {
        val colors = summerSkyColorScheme(darkTheme = false)

        assertEquals(1f, colors.background.alpha)
        assertEquals(1f, colors.surface.alpha)
        assertEquals(ThemePalette.SUMMER_SKY, ThemePalette.valueOf("SUMMER_SKY"))
    }

    /** 晴空的深色方案使用低亮度实色背景，避免壁纸破坏文本对比。 */
    @Test
    fun summerSkyColorScheme_dark_keepsReadableForeground() {
        val colors = summerSkyColorScheme(darkTheme = true)

        assertTrue(colors.background.luminance() < 0.05f)
        assertTrue(colors.onBackground.luminance() > 0.7f)
        assertNotEquals(summerSkyColorScheme(darkTheme = false).surface, colors.surface)
    }
}
