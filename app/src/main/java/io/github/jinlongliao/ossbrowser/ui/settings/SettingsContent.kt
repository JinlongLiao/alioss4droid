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

import android.content.Context
import io.github.jinlongliao.ossbrowser.R

/**
 * 与合规 / 开源说明相关的静态文案入口。
 *
 * 长文（隐私政策、开源声明）实际内容放在 `res/raw[-zh-rCN|-zh-rTW]/` 下的 Markdown 文件，
 * 按当前应用 Locale 自动路由；此类只暴露读取函数与少量元信息常量。
 */
object SettingsContent {

    /** 用户可见的品牌名称；UI 上的"应用名"请直接用 R.string.app_name。 */
    const val APP_NAME = "OSS Browser for Android"

    /** 作者名称。 */
    const val AUTHOR_NAME = "jinlongliao"

    /** 作者 GitHub 主页。 */
    const val AUTHOR_URL = "https://github.com/JinlongLiao/"

    /** 项目主页。Settings 页"项目主页"一项直接打开此 URL。 */
    const val PROJECT_URL = "https://github.com/JinlongLiao/alioss4droid"

    /** Git 仓库地址（SSH），提供给开发者 clone 使用。 */
    const val PROJECT_GIT_URL = "git@github.com:JinlongLiao/alioss4droid.git"

    /** 读取随当前 Locale 的《隐私政策》Markdown 文本。 */
    fun privacyPolicy(context: Context): String = readRaw(context, R.raw.privacy_policy)

    /** 读取随当前 Locale 的开源声明文本。 */
    fun openSourceNotice(context: Context): String = readRaw(context, R.raw.open_source_notice)

    private fun readRaw(context: Context, resId: Int): String =
        runCatching {
            context.resources.openRawResource(resId).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")

    data class License(
        val name: String,
        val license: String,
        val url: String
    )

    val THIRD_PARTY_LICENSES: List<License> = listOf(
        License("Kotlin", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        License("kotlinx.coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        License("kotlinx.serialization", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        License("AndroidX / Jetpack", "Apache License 2.0", "https://developer.android.com/jetpack"),
        License("Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack/compose"),
        License("Material Components for Android", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
        License("Navigation Compose", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
        License("AndroidX Security Crypto", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/security"),
        License("Aliyun OSS Android SDK", "Apache License 2.0", "https://github.com/aliyun/aliyun-oss-android-sdk"),
        License("Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
        License("AndroidX Media3 (ExoPlayer)", "Apache License 2.0", "https://github.com/androidx/media")
    )
}
