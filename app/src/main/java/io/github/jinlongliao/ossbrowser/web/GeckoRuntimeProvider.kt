/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.jinlongliao.ossbrowser.web

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jinlongliao.ossbrowser.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

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

    /** 加密会话状态存储；内容可能包含浏览历史和表单状态，不得写入普通偏好或日志。 */
    private lateinit var sessionPreferences: SharedPreferences

    /** 与内置 Cookie 扩展保持连接的端口；仅在扩展后台环境中建立。 */
    private var sessionBridgePort: WebExtension.Port? = null

    /**
     * 使用 Application 上下文初始化 Runtime；重复调用没有副作用。
     *
     * @param context Application 或 Activity 上下文。
     */
    @Synchronized
    fun initialize(context: Context) {
        if (::runtime.isInitialized) return
        val applicationContext = context.applicationContext
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sessionPreferences = EncryptedSharedPreferences.create(
            applicationContext,
            SESSION_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val settings = GeckoRuntimeSettings.Builder()
            // 仅 Debug 包允许 Firefox about:debugging 通过 USB 连接；正式包不暴露调试端口。
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            // 控制台日志输出会降低重度网页性能；远程调试不依赖该日志开关。
            .debugLogging(false)
            .build()
        runtime = GeckoRuntime.create(applicationContext, settings)
        installSessionBridge()
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
            readPersistedSessionState()?.let { state ->
                session.restoreState(state)
                aliyunSessionLoaded = true
            }
            aliyunSession = session
        }
    }

    /**
     * 加密保存 Gecko 官方会话快照，供应用进程被系统终止后恢复页面历史和表单状态。
     *
     * Cookie、LocalStorage 与 IndexedDB 仍由 Gecko Profile 自行持久化；此处只保存
     * [GeckoSession.SessionState]，不解析或输出其中内容。
     *
     * @param state Gecko 在页面状态变化或会话转为后台时生成的完整快照。
     */
    fun persistAliyunSessionState(state: GeckoSession.SessionState) {
        if (!::sessionPreferences.isInitialized) return
        sessionPreferences.edit().putString(KEY_SESSION_STATE, state.toString()).apply()
        requestCookieSnapshot()
    }

    /** 删除进程内及加密存储中的页面恢复状态；由用户明确执行“清除网页会话”时调用。 */
    fun clearAliyunSessionState() {
        if (::sessionPreferences.isInitialized) {
            sessionPreferences.edit()
                .remove(KEY_SESSION_STATE)
                .remove(KEY_SESSION_COOKIES)
                .apply()
        }
        sessionBridgePort?.postMessage(JSONObject().put("type", "clear"))
        aliyunSessionLoaded = false
        aliyunSessionUrl = null
    }

    /**
     * 读取并解析加密会话快照；损坏或版本不兼容时安全回退为空会话。
     *
     * @return 可恢复的 Gecko 会话状态，不存在或解析失败时返回 `null`。
     */
    private fun readPersistedSessionState(): GeckoSession.SessionState? {
        if (!::sessionPreferences.isInitialized) return null
        val value = sessionPreferences.getString(KEY_SESSION_STATE, null) ?: return null
        return runCatching { GeckoSession.SessionState.fromString(value) }.getOrNull()
    }

    /** 请求特权扩展导出最新阿里云 Cookie；消息不包含在日志或诊断中。 */
    fun requestCookieSnapshot() {
        sessionBridgePort?.postMessage(JSONObject().put("type", "export"))
    }

    /**
     * 安装仅访问阿里云 Cookie 的内置扩展，并建立受信后台脚本到 App 的消息通道。
     *
     * 扩展没有 content script 权限，网页上下文不能调用此通道。所有入站消息仍按不可信数据
     * 校验，只有允许域名且大小受限的 Cookie 才会进入加密偏好。
     */
    private fun installSessionBridge() {
        runtime.webExtensionController
            .ensureBuiltIn(SESSION_BRIDGE_LOCATION, SESSION_BRIDGE_EXTENSION_ID)
            .accept { extension ->
                if (extension == null) {
                    return@accept
                }
                // GeckoResult 未声明回调线程；显式切到主线程以满足 GeckoView 消息代理契约。
                Handler(Looper.getMainLooper()).post {
                    extension.setMessageDelegate(
                        object : WebExtension.MessageDelegate {
                            override fun onConnect(port: WebExtension.Port) {
                                if (port.name != SESSION_BRIDGE_NATIVE_APP_ID ||
                                    port.sender.environmentType !=
                                    WebExtension.MessageSender.ENV_TYPE_EXTENSION
                                ) {
                                    port.disconnect()
                                    return
                                }
                                sessionBridgePort = port
                                port.setDelegate(object : WebExtension.PortDelegate {
                                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                        handleSessionBridgeMessage(message)
                                    }

                                    override fun onDisconnect(port: WebExtension.Port) {
                                        if (sessionBridgePort === port) {
                                            sessionBridgePort = null
                                        }
                                    }
                                })
                                restorePersistedCookies(port)
                            }
                        },
                        SESSION_BRIDGE_NATIVE_APP_ID
                    )
                }
            }
    }

    /** 验证扩展消息并将 Cookie 快照写入加密存储。 */
    private fun handleSessionBridgeMessage(message: Any) {
        val payload = message as? JSONObject ?: return
        if (payload.optString("type") != "cookies") return
        val cookies = sanitizeCookieArray(payload.optJSONArray("cookies"))
        sessionPreferences.edit().putString(KEY_SESSION_COOKIES, cookies.toString()).apply()
    }

    /** 将已加密保存的 Cookie 发送回特权扩展；无快照时也发送空数组完成握手。 */
    private fun restorePersistedCookies(port: WebExtension.Port) {
        val stored = sessionPreferences.getString(KEY_SESSION_COOKIES, null)
        val cookies = runCatching { JSONArray(stored ?: "[]") }.getOrDefault(JSONArray())
        port.postMessage(
            JSONObject()
                .put("type", "restore")
                .put("cookies", sanitizeCookieArray(cookies))
        )
    }

    /**
     * 对扩展提供的 Cookie 执行域名、数量和序列化大小约束。
     *
     * @param source 扩展提供的原始数组，可为 `null`。
     * @return 仅包含阿里云域名且不超过安全上限的新数组。
     */
    private fun sanitizeCookieArray(source: JSONArray?): JSONArray {
        val sanitized = JSONArray()
        if (source == null) return sanitized
        for (index in 0 until minOf(source.length(), MAX_SESSION_COOKIES)) {
            val cookie = source.optJSONObject(index) ?: continue
            val domain = cookie.optString("domain").removePrefix(".").lowercase()
            if (!isAllowedCookieDomain(domain)) continue
            val serialized = cookie.toString()
            if (serialized.length > MAX_SERIALIZED_COOKIE_LENGTH) continue
            sanitized.put(JSONObject(serialized))
        }
        return sanitized
    }

    /** 判断 Cookie 域名是否严格属于阿里云中国站或国际站。 */
    private fun isAllowedCookieDomain(domain: String): Boolean =
        ALLOWED_COOKIE_BASE_DOMAINS.any { base -> domain == base || domain.endsWith(".$base") }

    /** 标记首次导航已经开始，后续重新挂载不得再次主动加载首页。 */
    fun markAliyunSessionLoaded() {
        aliyunSessionLoaded = true
    }

    /** 更新内存中的当前地址，供离开页面后重新进入时恢复工具栏状态。 */
    fun updateAliyunSessionUrl(url: String) {
        aliyunSessionUrl = url
    }

    /** Gecko 会话状态加密偏好文件名，与普通应用设置隔离。 */
    private const val SESSION_PREFERENCES_NAME = "gecko_session_state"

    /** Gecko 官方序列化会话快照的加密存储键。 */
    private const val KEY_SESSION_STATE = "aliyun_session_state"

    /** 加密保存的阿里云 Cookie JSON 数组键。 */
    private const val KEY_SESSION_COOKIES = "aliyun_session_cookies"

    /** APK assets 中内置扩展的位置。 */
    private const val SESSION_BRIDGE_LOCATION =
        "resource://android/assets/aliyun_session_bridge/"

    /** 内置扩展固定 ID，必须与 manifest.json 完全一致。 */
    private const val SESSION_BRIDGE_EXTENSION_ID = "aliyun-session-bridge@ossbrowser.local"

    /** Native Messaging 通道名称，必须与扩展后台脚本一致。 */
    private const val SESSION_BRIDGE_NATIVE_APP_ID = "aliyun_session_bridge"

    /** 允许持久化的 Cookie 根域名。 */
    private val ALLOWED_COOKIE_BASE_DOMAINS = setOf("aliyun.com", "alibabacloud.com")

    /** 单次最多保存的 Cookie 数，防止扩展消息导致无界加密偏好增长。 */
    private const val MAX_SESSION_COOKIES = 256

    /** 单个 Cookie JSON 最大字符数。 */
    private const val MAX_SERIALIZED_COOKIE_LENGTH = 16 * 1024
}
