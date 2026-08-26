/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.jinlongliao.ossbrowser.ui.web

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.AliyunAccountType
import io.github.jinlongliao.ossbrowser.data.AliyunSiteType
import io.github.jinlongliao.ossbrowser.data.AppSettings
import io.github.jinlongliao.ossbrowser.web.GeckoRuntimeProvider
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController

/**
 * 使用随应用分发的 Mozilla GeckoView 承载阿里云网页控制台。
 *
 * Gecko 会话的 Cookie 和站点数据由 Gecko profile 管理，业务代码不读取认证凭证。外部域名
 * 仍交给系统浏览器，防止网页导航越出阿里云官方站点边界。
 *
 * @param settings 当前站点、账号预填和诊断脱敏设置。
 * @param onSaveAccount 保存非敏感账号偏好并加载对应登录页。
 * @see <a href="https://mozilla.github.io/geckoview/consumer/docs/geckoview-quick-start">GeckoView Quick Start</a>
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeckoAliyunWebConsoleScreen(
    settings: AppSettings,
    onSaveAccount: (AliyunSiteType, AliyunAccountType, String) -> Unit
) {
    val context = LocalContext.current
    val session = remember { GeckoRuntimeProvider.getAliyunSession(context) }
    val runtime = GeckoRuntimeProvider.runtime
    var currentUrl by remember {
        mutableStateOf(
            GeckoRuntimeProvider.aliyunSessionUrl
                ?: aliyunOssConsoleUrl(settings.aliyunSiteType)
        )
    }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showClearSessionDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var loadStartedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var diagnostics by remember {
        mutableStateOf(
            AliyunWebDiagnostics(
                webViewVersion = "Mozilla GeckoView 154",
                cookiesAccepted = true,
                thirdPartyCookiesAccepted = true
            ).appendEvent("GeckoView session created")
        )
    }

    // 长期会话在底部页面切换后继续保留 Cookie、历史和页面状态，但必须标记为非活动，
    // 让 Gecko 释放不可见页面的渲染资源；重新进入时恢复活动状态而不重复下载网页。
    DisposableEffect(session) {
        session.setActive(true)
        onDispose {
            GeckoRuntimeProvider.requestCookieSnapshot()
            session.setFocused(false)
            session.setActive(false)
        }
    }

    session.navigationDelegate = remember(context) {
        object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val target = url ?: return
                currentUrl = target
                GeckoRuntimeProvider.updateAliyunSessionUrl(target)
                diagnostics = diagnostics.copy(currentUrl = redactWebDiagnosticUrl(target))
            }

            override fun onCanGoBack(session: GeckoSession, canGoBackValue: Boolean) {
                canGoBack = canGoBackValue
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val target = request.uri
                if (target == "about:blank" || isTrustedAliyunWebUrl(target)) {
                    // 本应用只展示一个 GeckoView。将 target=_blank/window.open 的受信页面
                    // 改在当前 Session 打开，否则 Gecko 默认请求不可见的新 Session，点击表现为无反应。
                    if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                        session.loadUri(target)
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                if (target.startsWith("https://", ignoreCase = true)) {
                    openExternalUrl(context, target)
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }
    }
    session.progressDelegate = remember {
        object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                loadStartedAt = SystemClock.elapsedRealtime()
                loading = true
                diagnostics = diagnostics
                    .copy(currentUrl = redactWebDiagnosticUrl(url))
                    .appendEvent("Started: ${redactWebDiagnosticUrl(url)}")
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val duration = SystemClock.elapsedRealtime() - loadStartedAt
                loading = false
                diagnostics = diagnostics
                    .copy(lastLoadDurationMillis = duration)
                    .appendEvent("Finished: success=$success, ${duration}ms")
                GeckoRuntimeProvider.requestCookieSnapshot()
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState
            ) {
                GeckoRuntimeProvider.persistAliyunSessionState(sessionState)
            }
        }
    }

    BackHandler(enabled = canGoBack) { session.goBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aliyun_web_title)) },
                actions = {
                    IconButton(onClick = { showAccountDialog = true }) {
                        Icon(Icons.Outlined.AccountCircle, stringResource(R.string.aliyun_web_account))
                    }
                    IconButton(onClick = {
                        session.loadUri(aliyunOssConsoleUrl(settings.aliyunSiteType))
                    }) {
                        Icon(Icons.Outlined.Cloud, stringResource(R.string.aliyun_web_open_oss))
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_system_browser)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                            onClick = {
                                showMoreMenu = false
                                openExternalUrl(context, currentUrl)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_diagnostics)) },
                            leadingIcon = { Icon(Icons.Outlined.BugReport, null) },
                            onClick = {
                                showMoreMenu = false
                                showDiagnosticsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_clear_session)) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                            onClick = {
                                showMoreMenu = false
                                showClearSessionDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { viewContext ->
                GeckoView(viewContext).apply {
                    setSession(session)
                    if (!GeckoRuntimeProvider.aliyunSessionLoaded) {
                        GeckoRuntimeProvider.markAliyunSessionLoaded()
                        session.loadUri(currentUrl)
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            onRelease = { it.releaseSession() }
        )
        if (loading) LinearProgressIndicator()
    }

    if (showAccountDialog) {
        AliyunAccountDialog(
            initialSite = settings.aliyunSiteType,
            initialType = settings.aliyunAccountType,
            initialUsername = settings.aliyunUsername,
            onDismiss = { showAccountDialog = false },
            onConfirm = { site, type, username ->
                onSaveAccount(site, type, username)
                showAccountDialog = false
                session.loadUri(buildAliyunSignInUrl(site, type, username))
            }
        )
    }
    if (showDiagnosticsDialog) {
        AliyunWebDiagnosticsDialog(
            diagnostics = diagnostics,
            redactionEnabled = settings.webDiagnosticsRedactionEnabled,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text(stringResource(R.string.aliyun_web_clear_session)) },
            text = { Text(stringResource(R.string.aliyun_web_clear_session_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    runtime.storageController.clearData(StorageController.ClearFlags.SITE_DATA)
                    GeckoRuntimeProvider.clearAliyunSessionState()
                    showClearSessionDialog = false
                    session.loadUri(
                        buildAliyunSignInUrl(
                            settings.aliyunSiteType,
                            settings.aliyunAccountType,
                            settings.aliyunUsername
                        )
                    )
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
