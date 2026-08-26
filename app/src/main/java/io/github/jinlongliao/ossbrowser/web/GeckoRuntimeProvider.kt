/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.jinlongliao.ossbrowser.web

import android.content.Context
import io.github.jinlongliao.ossbrowser.BuildConfig
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession

/**
 * 保存进程内唯一的 Gecko Runtime，避免多个页面重复初始化浏览器引擎。
 *
 * GeckoView 要求每个进程只创建一个 Runtime；初始化不联网下载内核，浏览器代码已随 APK 分发。
 *
 * @see <a href="https://mozilla.github.io/geckoview/consumer/docs/geckoview-quick-start">GeckoView Quick Start</a>
 */
object GeckoRuntimeProvider {
    /** 已初始化的 Runtime，仅在 [initialize] 完成后可访问。 */
    lateinit var runtime: GeckoRuntime
        private set

    /** 阿里云控制台长期会话；底部页面切换时保留页面、历史及已加载资源。 */
    private var aliyunSession: GeckoSession? = null

    /** 长期会话是否已经执行过首次导航，防止重新挂载 View 时重复下载网页。 */
    var aliyunSessionLoaded: Boolean = false
        private set

    /** 长期会话最近一次顶层 URL，仅保存于内存，不跨进程持久化。 */
    var aliyunSessionUrl: String? = null
        private set

    /**
     * 使用 Application 上下文初始化 Runtime；重复调用没有副作用。
     *
     * @param context Application 或 Activity 上下文。
     */
    @Synchronized
    fun initialize(context: Context) {
        if (::runtime.isInitialized) return
        val settings = GeckoRuntimeSettings.Builder()
            // 仅 Debug 包允许 Firefox about:debugging 通过 USB 连接；正式包不暴露调试端口。
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            // 控制台日志输出会降低重度网页性能；远程调试不依赖该日志开关。
            .debugLogging(false)
            .build()
        runtime = GeckoRuntime.create(context.applicationContext, settings)
    }

    /**
     * 获取进程内复用的阿里云 Gecko 会话；首次调用时创建并绑定唯一 Runtime。
     *
     * @return 可反复挂载到不同 [org.mozilla.geckoview.GeckoView] 的开放会话。
     */
    @Synchronized
    fun getAliyunSession(context: Context): GeckoSession {
        initialize(context)
        return aliyunSession ?: GeckoSession().also { session ->
            session.open(runtime)
            aliyunSession = session
        }
    }

    /** 标记首次导航已经开始，后续重新挂载不得再次主动加载首页。 */
    fun markAliyunSessionLoaded() {
        aliyunSessionLoaded = true
    }

    /** 更新内存中的当前地址，供离开页面后重新进入时恢复工具栏状态。 */
    fun updateAliyunSessionUrl(url: String) {
        aliyunSessionUrl = url
    }
}
