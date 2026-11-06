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
package io.github.jinlongliao.ossbrowser.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.data.AppSettings
import io.github.jinlongliao.ossbrowser.data.BrowserLayoutMode
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.data.ThemeMode
import io.github.jinlongliao.ossbrowser.data.ThemePalette
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val state: StateFlow<AppSettings> = repo.settings

    fun setThemeMode(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    /** 更新配色主题，不改变用户选择的跟随系统/浅色/深色模式。 */
    fun setThemePalette(palette: ThemePalette) = repo.update { it.copy(themePalette = palette) }
    /** 保存全局布局选择；所有浏览页订阅同一仓库，切账号和重建页面也沿用该值。 */
    fun setBrowserLayoutMode(layoutMode: BrowserLayoutMode) = repo.update { it.copy(browserLayoutMode = layoutMode) }
    fun setPageSize(size: Int) = repo.update { it.copy(pageSize = size.coerceIn(1, 1000)) }
    fun setTextPreviewMax(bytes: Long) = repo.update { it.copy(textPreviewMaxBytes = bytes) }
    fun setAutoPlayMedia(enabled: Boolean) = repo.update { it.copy(autoPlayMedia = enabled) }
    fun setConfirmDestructive(enabled: Boolean) = repo.update { it.copy(confirmDestructiveOps = enabled) }
    fun setLanguageTag(tag: String) = repo.update { it.copy(languageTag = tag) }
    fun acceptPrivacy() = repo.acceptPrivacy()

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(OssBrowserApp.instance.settingsRepository) as T
        }
    }
}
