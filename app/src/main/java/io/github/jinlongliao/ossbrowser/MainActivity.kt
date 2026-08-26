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
package io.github.jinlongliao.ossbrowser

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.data.ThemeMode
import io.github.jinlongliao.ossbrowser.ui.OssBrowserRoot
import io.github.jinlongliao.ossbrowser.ui.theme.OssBrowserTheme
import io.github.jinlongliao.ossbrowser.util.LocaleHelper

/**
 * 应用启动 Activity：负责初始化 Jetpack Compose UI 根节点 [OssBrowserRoot]，
 * 并根据用户设置组合配色主题（默认/Gemini）与显示模式（明/暗/跟随系统）。
 *
 * 通过 [attachBaseContext] 在 Activity 上下文层面套一层 [LocaleHelper] 语言包装，
 * 与 [OssBrowserApp.attachBaseContext] 保持一致，避免语言切换后 Activity 仍用旧 locale。
 *
 * @author  jinlongliao (https://github.com/JinlongLiao/)
 * @since   2026-04-24
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by OssBrowserApp.instance.settingsRepository.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            OssBrowserTheme(darkTheme = dark, palette = settings.themePalette) {
                OssBrowserRoot()
            }
        }
    }
}
