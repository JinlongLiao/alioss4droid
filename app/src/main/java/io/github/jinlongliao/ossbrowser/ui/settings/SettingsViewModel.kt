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
import io.github.jinlongliao.ossbrowser.data.AliyunAccountType
import io.github.jinlongliao.ossbrowser.data.AliyunSiteType
import io.github.jinlongliao.ossbrowser.data.AppSettings
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.data.ThemeMode
import io.github.jinlongliao.ossbrowser.data.ThemePalette
import io.github.jinlongliao.ossbrowser.data.WebBrowserEngine
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val state: StateFlow<AppSettings> = repo.settings

    fun setThemeMode(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    /** 更新配色主题，不改变用户选择的跟随系统/浅色/深色模式。 */
    fun setThemePalette(palette: ThemePalette) = repo.update { it.copy(themePalette = palette) }
    fun setPageSize(size: Int) = repo.update { it.copy(pageSize = size.coerceIn(1, 1000)) }
    fun setTextPreviewMax(bytes: Long) = repo.update { it.copy(textPreviewMaxBytes = bytes) }
    fun setAutoPlayMedia(enabled: Boolean) = repo.update { it.copy(autoPlayMedia = enabled) }
    fun setConfirmDestructive(enabled: Boolean) = repo.update { it.copy(confirmDestructiveOps = enabled) }
    /**
     * 设置 WebView 诊断 URL 路径和 Cookie 脱敏值的隐藏状态；完整认证凭证始终隐藏。
     *
     * @param enabled `true` 时隐藏 URL 路径。
     */
    fun setWebDiagnosticsRedaction(enabled: Boolean) = repo.update {
        it.copy(webDiagnosticsRedactionEnabled = enabled)
    }
    /**
     * 切换阿里云网页控制台使用的浏览器引擎。
     *
     * @param engine 系统 WebView 或随应用分发的 GeckoView。
     */
    fun setWebBrowserEngine(engine: WebBrowserEngine) = repo.update {
        it.copy(webBrowserEngine = engine)
    }
    /**
     * 保存网页登录站点和账号入口偏好；密码、MFA 和 Cookie 均不经过该设置仓库。
     *
     * @param site 网页控制台站点，决定后续加载中国站或国际站域名。
     * @param type 主账号或 RAM 用户入口。
     * @param username 用于后续预填的非敏感账号名；保存前移除首尾空白。
     */
    fun setAliyunWebAccount(site: AliyunSiteType, type: AliyunAccountType, username: String) =
        repo.update {
            it.copy(
                aliyunSiteType = site,
                aliyunAccountType = type,
                aliyunUsername = username.trim()
            )
        }
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
