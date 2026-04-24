package com.ossbrowser.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.ossbrowser.app.data.OssProfileRepository
import com.ossbrowser.app.data.SettingsRepository
import com.ossbrowser.app.oss.OssClientProvider
import com.ossbrowser.app.util.LocaleHelper

class OssBrowserApp : Application() {

    lateinit var profileRepository: OssProfileRepository
        private set

    lateinit var ossClientProvider: OssClientProvider
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

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
    }

    companion object {
        lateinit var instance: OssBrowserApp
            private set
    }
}
