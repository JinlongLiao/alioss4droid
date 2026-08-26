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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 自定义主题浅色/深色 Material 3 配色回归测试。 */
class ThemeTest {

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

    /** 夏日晴空浅色方案应使用晴空蓝主色与透明背景，让根节点壁纸可见。 */
    @Test
    fun summerSkyColorScheme_light_usesTransparentSkyPalette() {
        val colors = summerSkyColorScheme(darkTheme = false)

        assertEquals(Color(0xFF318CE8), colors.primary)
        assertEquals(Color.Transparent, colors.background)
        assertEquals(0xBF, colors.surface.alpha.times(255).toInt())
    }

    /** 夏日晴空深色方案应保留同一主色，并切换为明亮前景和深色半透明 Surface。 */
    @Test
    fun summerSkyColorScheme_dark_keepsReadableForeground() {
        val colors = summerSkyColorScheme(darkTheme = true)

        assertEquals(Color(0xFF318CE8), colors.primary)
        assertEquals(Color(0xFFF0F2F5), colors.onBackground)
        assertNotEquals(summerSkyColorScheme(darkTheme = false).surface, colors.surface)
    }
}
