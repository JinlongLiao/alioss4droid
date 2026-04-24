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
package io.github.jinlongliao.ossbrowser.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * 国际化辅助：把用户偏好的语言标签映射到本应用支持的 Locale，并生成带该 Locale 的 [Context] 包装。
 *
 * 支持集合：
 *  - `en`（英文；所有未识别语种都回退到此）
 *  - `zh-CN`（简体中文）
 *  - `zh-TW`（繁体中文）
 *
 * 空串表示"跟随系统"：按系统主 Locale 映射到支持集合。
 */
object LocaleHelper {

    /** 解析用户偏好 → 应用实际使用的 Locale。 */
    fun resolve(tag: String): Locale {
        return if (tag.isBlank()) systemLocale() else fromTag(tag)
    }

    /** 把系统 Locale 映射到我们支持的 Locale。 */
    private fun systemLocale(): Locale {
        val sys = Resources.getSystem().configuration.locales[0]
        return map(sys)
    }

    /** 把任意 BCP-47 / Locale 映射到支持集合内的 Locale。 */
    private fun map(src: Locale): Locale {
        val lang = src.language
        if (lang == "zh") {
            val script = src.script
            val country = src.country
            // 明确繁体标记
            if (script == "Hant" ||
                country == "TW" || country == "HK" || country == "MO"
            ) {
                return Locale.forLanguageTag("zh-TW")
            }
            // 默认简体
            return Locale.forLanguageTag("zh-CN")
        }
        // 其余全部回退到英文
        return Locale.ENGLISH
    }

    private fun fromTag(tag: String): Locale = when (tag.lowercase()) {
        "zh-cn", "zh_cn", "zh-hans", "zh-hans-cn" -> Locale.forLanguageTag("zh-CN")
        "zh-tw", "zh_tw", "zh-hant", "zh-hant-tw" -> Locale.forLanguageTag("zh-TW")
        "en", "en-us", "en-gb" -> Locale.ENGLISH
        else -> Locale.forLanguageTag(tag).let { map(it) }
    }

    /** 生成绑定了目标 Locale 的 [Context]，并同步更新 [Locale.setDefault]。 */
    fun wrap(base: Context, tag: String): Context {
        val locale = resolve(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
