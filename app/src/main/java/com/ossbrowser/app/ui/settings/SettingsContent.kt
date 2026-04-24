package com.ossbrowser.app.ui.settings

/**
 * 隐私协议与第三方开源许可的静态文案。保持简洁；实际合规建议由律师审阅。
 */
object SettingsContent {

    const val APP_NAME = "OSS Browser"
    const val APP_DESCRIPTION =
        "一个开源的阿里云 OSS 客户端，支持多密钥管理、Bucket 浏览、对象上传 / 下载 / 删除 / 移动 / 预览。"
    const val PROJECT_URL = "https://github.com/aliyun/oss-browser"

    val PRIVACY_POLICY = """
        # 隐私政策
        
        生效日期：2026-04-24
        
        ## 1. 我们是谁
        
        本应用（"OSS Browser"）是一款开源的阿里云对象存储客户端。应用源码遵循 Apache License 2.0 开源。
        
        ## 2. 我们如何处理你的信息
        
        本应用**完全不上传、不收集、不分析**用户的任何个人数据、使用行为或设备标识。
        
        - **密钥（AccessKey ID / Secret）**：通过 Android Jetpack Security（`EncryptedSharedPreferences`）仅保存在**本机**，AES-256 加密。不会上传到任何第三方服务器。
        - **网络请求**：所有 OSS 相关请求**直连阿里云 OSS endpoint**（如 `*.aliyuncs.com`），由阿里云 SDK 处理。请求内容与阿里云之间的隐私关系以阿里云《个人信息保护政策》为准。
        - **下载缓存**：图片预览使用 Coil 在本地临时缓存；可在"设置 → 清除缓存"处随时清理。
        - **崩溃 / 统计**：本应用**不集成任何第三方统计、崩溃上报、广告、埋点 SDK**。
        
        ## 3. 你对自己数据的控制
        
        - 所有密钥、偏好设置均保存于应用私有目录；**卸载应用**即会被系统一并移除。
        - 也可在"密钥"页逐项删除 OSS 配置。
        
        ## 4. 权限说明
        
        - **网络权限**：向阿里云 OSS 发起 HTTPS 请求
        - **读取 / 写入文件**：通过系统"存储访问框架（SAF）"选择文件进行上传 / 下载；应用本身不扫描你的文件系统
        
        ## 5. 变更
        
        任何变更都会在项目仓库中同步；你可在本页查看最新版本。
        
        ## 6. 联系
        
        如有疑问或合规诉求，请在项目仓库的 Issue 中反馈。
    """.trimIndent()

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

    val OPEN_SOURCE_NOTICE = """
        本应用为开源软件，遵循 Apache License 2.0。
        
        允许：
        - 商业使用、修改、分发、私有使用、专利授权。
        
        要求：
        - 保留版权声明；
        - 在再分发时附带本许可证；
        - 对做出的修改进行说明。
        
        不允许：
        - 使用贡献者的商标；
        - 对使用本软件产生的后果追究作者责任。
    """.trimIndent()
}
