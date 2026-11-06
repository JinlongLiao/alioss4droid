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

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import io.github.jinlongliao.ossbrowser.data.OssProfileRepository
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.oss.OssClientProvider
import io.github.jinlongliao.ossbrowser.oss.OssListCache
import io.github.jinlongliao.ossbrowser.util.LocaleHelper
import io.github.jinlongliao.ossbrowser.upload.UploadRepository

/**
 * 应用全局 [Application] 入口，负责各类仓储 / 单例的一次性初始化：
 *
 * - [settingsRepository]：应用设置（主题、语言、页大小、预览上限等）的读写；
 * - [profileRepository]：OSS 账号（AK/SK + endpoint 等）的持久化与激活态管理；
 * - [ossClientProvider]：按 profile + endpoint 惰性缓存的 Aliyun [com.alibaba.sdk.android.oss.OSSClient]；
 * - [ossListCache]：Bucket / 对象列表的本地缓存（内存 + 磁盘），用于秒开体验。
 *
 * 同时重写 [attachBaseContext] / [onConfigurationChanged] 来应用用户选定的 Locale，
 * 保证应用内语言切换不依赖系统设置。
 *
 * @author  jinlongliao (https://github.com/JinlongLiao/)
 * @since   2026-04-24
 */
class OssBrowserApp : Application() {

    lateinit var profileRepository: OssProfileRepository
        private set

    lateinit var ossClientProvider: OssClientProvider
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var ossListCache: OssListCache
        private set

    /** 跨页面共享的上传任务及持久化历史。 */
    val uploadRepository by lazy { UploadRepository(this) }

    override fun attachBaseContext(newBase: Context) {
        // 在此时 settingsRepository 尚未初始化，直接读 prefs。
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, tag))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 跟随系统时，系统语言变化要保持应用配置的 Locale 生效。
        val tag = settingsRepository.settings.value.languageTag
        LocaleHelper.wrap(this, tag)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        profileRepository = OssProfileRepository(this)
        ossClientProvider = OssClientProvider(this, profileRepository)
        ossListCache = OssListCache(this)
    }

    companion object {
        lateinit var instance: OssBrowserApp
            private set
    }
}
