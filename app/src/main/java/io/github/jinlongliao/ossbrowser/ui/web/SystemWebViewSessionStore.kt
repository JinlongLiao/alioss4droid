/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.jinlongliao.ossbrowser.ui.web

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * 加密保存并恢复系统 WebView 的阿里云会话 Cookie。
 *
 * `CookieManager` 会自动持久化带有效期的 Cookie，但在进程退出后会丢弃会话 Cookie。
 * 本存储只记录受信任阿里云 HTTPS 页面实际可见的 Cookie 请求头，并在首次导航前恢复；
 * 数据由 Android Keystore 派生密钥加密，既不会写入日志，也不会提供导出接口。
 */
internal class SystemWebViewSessionStore(context: Context) {
    /** 加密首选项，仅在当前应用进程内持有引用。 */
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFERENCES_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 保存指定页面当前可见的 Cookie；非阿里云地址、空值和异常超长值会被忽略。
     *
     * @param url Cookie 所属页面 URL，用于在恢复时保持主机边界。
     * @param cookieHeader `CookieManager.getCookie` 返回的请求头内容。
     */
    fun persist(url: String, cookieHeader: String?) {
        val origin = trustedOrigin(url) ?: return
        val header = cookieHeader?.takeIf { it.isNotBlank() && it.length <= MAX_HEADER_LENGTH }
            ?: return
        val snapshots = readSnapshots()
        snapshots.put(origin, header)
        while (snapshots.length() > MAX_ORIGIN_COUNT) {
            snapshots.keys().asSequence().firstOrNull()?.let(snapshots::remove) ?: break
        }
        preferences.edit().putString(COOKIES_KEY, snapshots.toString()).apply()
    }

    /**
     * 在系统 WebView 首次导航前恢复全部加密 Cookie。
     *
     * Cookie 按原页面主机设置，所有异步写入结束后才回调，防止首个请求早于会话恢复。
     *
     * @param cookieManager 当前进程的系统 Cookie 管理器。
     * @param onRestored 恢复完成后的主线程回调；没有快照时立即执行。
     */
    fun restore(cookieManager: CookieManager, onRestored: () -> Unit) {
        val writes = buildList {
            val snapshots = readSnapshots()
            snapshots.keys().forEach { origin ->
                val header = snapshots.optString(origin)
                header.split(';').map(String::trim).filter(String::isNotEmpty).forEach { cookie ->
                    add(origin to cookie)
                }
            }
        }
        if (writes.isEmpty()) {
            onRestored()
            return
        }
        val remaining = AtomicInteger(writes.size)
        writes.forEach { (origin, cookie) ->
            cookieManager.setCookie(origin, cookie) {
                if (remaining.decrementAndGet() == 0) {
                    cookieManager.flush()
                    onRestored()
                }
            }
        }
    }

    /** 清除加密会话快照；由用户主动执行“清除网页会话”时调用。 */
    fun clear() {
        preferences.edit().remove(COOKIES_KEY).apply()
    }

    /** 读取加密 JSON；损坏数据按空快照降级，避免阻塞网页登录。 */
    private fun readSnapshots(): JSONObject = runCatching {
        JSONObject(preferences.getString(COOKIES_KEY, null) ?: "{}")
    }.getOrDefault(JSONObject())

    /**
     * 将受信任 URL 规范化为 `scheme://host`，防止查询参数或路径进入本地会话索引。
     *
     * @param url 待校验的页面地址。
     * @return 受信任 HTTPS 源；不符合策略时返回 `null`。
     */
    private fun trustedOrigin(url: String): String? {
        if (!isTrustedAliyunWebUrl(url)) return null
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return null
        return "https://$host"
    }

    private companion object {
        /** 加密首选项文件名。 */
        private const val PREFERENCES_NAME = "system_webview_session"

        /** Cookie 快照 JSON 的加密字段名。 */
        private const val COOKIES_KEY = "aliyun_session_cookies"

        /** 单个主机 Cookie 请求头上限，避免异常网页造成无界本地写入。 */
        private const val MAX_HEADER_LENGTH = 64 * 1024

        /** 最多记录的阿里云页面源数量。 */
        private const val MAX_ORIGIN_COUNT = 32
    }
}
