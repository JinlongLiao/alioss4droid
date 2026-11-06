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

    /**
     * 从本地偏好恢复完整设置。
     *
     * 新增枚举值读取失败或旧版本尚未写入对应键时均回退默认值，保证应用升级兼容。
     * 已退役网页控制台的旧偏好键不再读取，保留其他设置及账号数据。
     *
     * @return 可直接发布给界面使用的设置快照。
     */
    private fun load(): AppSettings = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        themePalette = runCatching {
            ThemePalette.valueOf(prefs.getString(KEY_THEME_PALETTE, null) ?: "")
        }.getOrDefault(ThemePalette.DEFAULT),
        browserLayoutMode = restoreBrowserLayoutMode(prefs.getString(KEY_BROWSER_LAYOUT_MODE, null)),
        pageSize = prefs.getInt(KEY_PAGE_SIZE, 10),
        ossObjectSortField = runCatching {
            OssObjectSortField.valueOf(prefs.getString(KEY_OSS_OBJECT_SORT_FIELD, null) ?: "")
        }.getOrDefault(OssObjectSortField.NAME),
        ossObjectSortDirection = runCatching {
            OssObjectSortDirection.valueOf(prefs.getString(KEY_OSS_OBJECT_SORT_DIRECTION, null) ?: "")
        }.getOrDefault(OssObjectSortDirection.ASCENDING),
        textPreviewMaxBytes = prefs.getLong(KEY_TEXT_MAX, 2L * 1024 * 1024),
        autoPlayMedia = prefs.getBoolean(KEY_AUTO_PLAY, true),
        confirmDestructiveOps = prefs.getBoolean(KEY_CONFIRM, true),
        privacyAccepted = prefs.getBoolean(KEY_PRIVACY, false),
        privacyAcceptedAt = prefs.getLong(KEY_PRIVACY_AT, 0L),
        languageTag = prefs.getString(KEY_LANGUAGE, "") ?: ""
    )

    /**
     * 原子计算并持久化下一份设置快照，然后同步通知观察者。
     *
     * @param transform 基于当前快照生成新快照的同步转换函数。
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putString(KEY_THEME_PALETTE, next.themePalette.name)
            .putString(KEY_BROWSER_LAYOUT_MODE, next.browserLayoutMode.name)
            .putInt(KEY_PAGE_SIZE, next.pageSize)
            .putString(KEY_OSS_OBJECT_SORT_FIELD, next.ossObjectSortField.name)
            .putString(KEY_OSS_OBJECT_SORT_DIRECTION, next.ossObjectSortDirection.name)
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
        /** 独立于明暗模式的配色主题持久化键。 */
        const val KEY_THEME_PALETTE = "theme_palette"
        /** 全局浏览布局的枚举名；不存在或无法识别时回退为单列列表。 */
        const val KEY_BROWSER_LAYOUT_MODE = "browser_layout_mode"
        const val KEY_PAGE_SIZE = "page_size"
        /** OSS 文件排序字段持久化键；旧版本不存在时默认按名称排序。 */
        const val KEY_OSS_OBJECT_SORT_FIELD = "oss_object_sort_field"
        /** OSS 文件排序方向持久化键；旧版本不存在时默认正序。 */
        const val KEY_OSS_OBJECT_SORT_DIRECTION = "oss_object_sort_direction"
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
