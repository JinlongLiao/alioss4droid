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
package io.github.jinlongliao.ossbrowser.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 偏好设置仓库：使用普通 SharedPreferences（非敏感）。
 * 通过 [settings] StateFlow 对 UI 暴露当前设置，单线程写入。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        pageSize = prefs.getInt(KEY_PAGE_SIZE, 10),
        textPreviewMaxBytes = prefs.getLong(KEY_TEXT_MAX, 2L * 1024 * 1024),
        autoPlayMedia = prefs.getBoolean(KEY_AUTO_PLAY, true),
        confirmDestructiveOps = prefs.getBoolean(KEY_CONFIRM, true),
        privacyAccepted = prefs.getBoolean(KEY_PRIVACY, false),
        privacyAcceptedAt = prefs.getLong(KEY_PRIVACY_AT, 0L),
        languageTag = prefs.getString(KEY_LANGUAGE, "") ?: ""
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putInt(KEY_PAGE_SIZE, next.pageSize)
            .putLong(KEY_TEXT_MAX, next.textPreviewMaxBytes)
            .putBoolean(KEY_AUTO_PLAY, next.autoPlayMedia)
            .putBoolean(KEY_CONFIRM, next.confirmDestructiveOps)
            .putBoolean(KEY_PRIVACY, next.privacyAccepted)
            .putLong(KEY_PRIVACY_AT, next.privacyAcceptedAt)
            .putString(KEY_LANGUAGE, next.languageTag)
            .apply()
        _settings.value = next
    }

    fun acceptPrivacy() {
        update {
            it.copy(privacyAccepted = true, privacyAcceptedAt = System.currentTimeMillis())
        }
    }

    companion object {
        const val PREFS_NAME = "oss_browser_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_PAGE_SIZE = "page_size"
        const val KEY_TEXT_MAX = "text_preview_max"
        const val KEY_AUTO_PLAY = "auto_play_media"
        const val KEY_CONFIRM = "confirm_destructive"
        const val KEY_PRIVACY = "privacy_accepted"
        const val KEY_PRIVACY_AT = "privacy_accepted_at"
        const val KEY_LANGUAGE = "language_tag"

        /** 在 Application.attachBaseContext 中直接读取语言偏好（此时 repository 还未实例化）。 */
        fun readLanguageTag(context: Context): String {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return p.getString(KEY_LANGUAGE, "") ?: ""
        }
    }
}
