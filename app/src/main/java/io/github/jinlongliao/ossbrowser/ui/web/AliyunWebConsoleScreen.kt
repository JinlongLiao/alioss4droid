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
package io.github.jinlongliao.ossbrowser.ui.web

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.AliyunAccountType
import io.github.jinlongliao.ossbrowser.data.AliyunSiteType
import io.github.jinlongliao.ossbrowser.data.AppSettings

/**
 * 阿里云网页控制台页面。
 *
 * 此页面只承载阿里云官方网页，不会把 Cookie 转换为 OSS SDK 凭证。WebView 按系统默认行为持久化
 * Cookie，并允许系统自动填充服务识别登录表单；App 只保存账号类型和用户名。密码、MFA、网页
 * Cookie 内容均不会被业务代码读取或写入日志。
 *
 * @param settings 当前应用设置，用于恢复站点、账号类型和用户名。
 * @param onSaveAccount 保存不敏感的网页登录偏好，并立即打开对应登录页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliyunWebConsoleScreen(
    settings: AppSettings,
    onSaveAccount: (AliyunSiteType, AliyunAccountType, String) -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(aliyunOssConsoleUrl(settings.aliyunSiteType)) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showClearSessionDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(AliyunWebDiagnostics()) }
    var webViewGeneration by remember { mutableStateOf(0) }
    var rendererRecoveryCount by remember { mutableStateOf(0) }
    var rendererUnavailable by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aliyun_web_title)) },
                actions = {
                    IconButton(onClick = { showAccountDialog = true }) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = stringResource(R.string.aliyun_web_account)
                        )
                    }
                    IconButton(onClick = {
                        webView?.loadUrl(aliyunOssConsoleUrl(settings.aliyunSiteType))
                    }) {
                        Icon(
                            Icons.Outlined.Cloud,
                            contentDescription = stringResource(R.string.aliyun_web_open_oss)
                        )
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.common_more)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_diagnostics)) },
                            leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showDiagnosticsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_system_browser)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                openExternalUrl(context, currentUrl)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aliyun_web_clear_session)) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (rendererUnavailable) {
                WebViewUnavailablePanel(
                    onRetry = {
                        rendererUnavailable = false
                        rendererRecoveryCount = 0
                        webViewGeneration += 1
                    },
                    onOpenSystemBrowser = { openExternalUrl(context, currentUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                key(webViewGeneration) {
                    AliyunConsoleWebView(
                        // 优先恢复现有 Cookie 进入 OSS；需要切换账号或预填 RAM 用户名时使用账号按钮。
                        initialUrl = aliyunOssConsoleUrl(settings.aliyunSiteType),
                        onWebViewReady = { webView = it },
                        onPageStateChanged = { url, isLoading, hasHistory ->
                            currentUrl = url
                            loading = isLoading
                            canGoBack = hasHistory
                        },
                        onDiagnosticsChanged = { diagnostics = it },
                        onRendererGone = { didCrash ->
                            rendererRecoveryCount += 1
                            diagnostics = diagnostics.appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_renderer_gone,
                                    didCrash.toString()
                                )
                            )
                            webView = null
                            if (rendererRecoveryCount <= MAX_AUTOMATIC_RENDERER_RECOVERIES) {
                                webViewGeneration += 1
                            } else {
                                loading = false
                                rendererUnavailable = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
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
                webView?.loadUrl(buildAliyunSignInUrl(site, type, username))
            }
        )
    }

    if (showClearSessionDialog) {
        AlertDialog(
            onDismissRequest = { showClearSessionDialog = false },
            title = { Text(stringResource(R.string.aliyun_web_clear_session)) },
            text = { Text(stringResource(R.string.aliyun_web_clear_session_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearSessionDialog = false
                    clearWebSession(webView) {
                        webView?.loadUrl(
                            buildAliyunSignInUrl(
                                settings.aliyunSiteType,
                                settings.aliyunAccountType,
                                settings.aliyunUsername
                            )
                        )
                    }
                }) {
                    Text(stringResource(R.string.common_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
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
}

/**
 * 创建受限 WebView：阿里云 HTTPS 顶层导航留在应用内，其余 HTTPS 链接交给系统浏览器。
 *
 * JavaScript 与 DOM Storage 是阿里云控制台运行所需能力；文件/内容访问、混合内容、自动弹窗、
 * 自动媒体播放和 JavaScript 原生桥均保持关闭。用户主动点击产生的新窗口会经过同一官方域名
 * 白名单后复用当前页面或交给系统浏览器，避免 `target="_blank"` 链接无响应。证书错误直接取消，
 * 禁止用户绕过 TLS 校验。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AliyunConsoleWebView(
    initialUrl: String,
    onWebViewReady: (WebView) -> Unit,
    onPageStateChanged: (String, Boolean, Boolean) -> Unit,
    onDiagnosticsChanged: (AliyunWebDiagnostics) -> Unit,
    onRendererGone: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                var loadStartedAtMillis = 0L
                var diagnosticSnapshot = AliyunWebDiagnostics(
                    webViewVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WebView.getCurrentWebViewPackage()?.versionName ?: "-"
                    } else {
                        "-"
                    }
                )

                /** 发布一份不包含 Cookie 值或 URL 查询参数的诊断快照。 */
                fun publishDiagnostics(transform: (AliyunWebDiagnostics) -> AliyunWebDiagnostics) {
                    diagnosticSnapshot = transform(diagnosticSnapshot)
                    onDiagnosticsChanged(diagnosticSnapshot)
                }

                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                // 控制台部分入口使用 target="_blank"；允许接收新窗口请求，但下方仍拒绝非用户触发的弹窗。
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mediaPlaybackRequiresUserGesture = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, false)
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message
                    ): Boolean {
                        if (!isUserGesture) return false
                        val popupWebView = createPopupNavigationWebView(
                            context = context,
                            sourceWebView = view,
                            onPopupTarget = { target ->
                                publishDiagnostics { snapshot ->
                                    snapshot.appendEvent(
                                        context.getString(
                                            R.string.aliyun_web_diagnostic_popup,
                                            displayWebDiagnosticUrl(
                                                redactWebDiagnosticUrl(target),
                                                redactionEnabled = true
                                            )
                                        )
                                    )
                                }
                            }
                        )
                        val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                            popupWebView.destroy()
                            return false
                        }
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url.toString()
                        if (isTrustedAliyunWebUrl(target)) return false
                        if (request.isForMainFrame && request.url.scheme == "https") {
                            openExternalUrl(context, target)
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        loadStartedAtMillis = SystemClock.elapsedRealtime()
                        val safeUrl = redactWebDiagnosticUrl(url)
                        publishDiagnostics { snapshot ->
                            snapshot.copy(
                                currentUrl = safeUrl,
                                cookies = extractDiagnosticCookies(cookieManager.getCookie(url)),
                                cookiesAccepted = cookieManager.acceptCookie(),
                                thirdPartyCookiesAccepted =
                                    cookieManager.acceptThirdPartyCookies(view)
                            ).appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_started,
                                    displayWebDiagnosticUrl(safeUrl, redactionEnabled = true)
                                )
                            )
                        }
                        onPageStateChanged(url, true, view.canGoBack())
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val duration = (SystemClock.elapsedRealtime() - loadStartedAtMillis)
                            .coerceAtLeast(0L)
                        val safeUrl = redactWebDiagnosticUrl(url)
                        publishDiagnostics { snapshot ->
                            snapshot.copy(
                                currentUrl = safeUrl,
                                lastLoadDurationMillis = duration,
                                cookies = extractDiagnosticCookies(cookieManager.getCookie(url)),
                                cookiesAccepted = cookieManager.acceptCookie(),
                                thirdPartyCookiesAccepted =
                                    cookieManager.acceptThirdPartyCookies(view)
                            ).appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_finished,
                                    duration
                                )
                            )
                        }
                        onPageStateChanged(url, false, view.canGoBack())
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (!request.isForMainFrame) return
                        publishDiagnostics { snapshot ->
                            snapshot.appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_network_error,
                                    error.errorCode
                                )
                            )
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        if (!request.isForMainFrame) return
                        publishDiagnostics { snapshot ->
                            snapshot.appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_http_error,
                                    errorResponse.statusCode
                                )
                            )
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError
                    ) {
                        // 账号登录场景禁止继续访问证书异常页面，避免凭据被中间人截获。
                        publishDiagnostics { snapshot ->
                            snapshot.appendEvent(
                                context.getString(
                                    R.string.aliyun_web_diagnostic_tls_error,
                                    error.primaryError
                                )
                            )
                        }
                        handler.cancel()
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail
                    ): Boolean {
                        // 返回 true 表示由 App 清理失效实例；Compose 随后重建或展示恢复页。
                        view.destroy()
                        onRendererGone(detail.didCrash())
                        return true
                    }
                }
                setDownloadListener { url, _, _, _, _ -> openExternalUrl(context, url) }
                loadUrl(initialUrl)
                onWebViewReady(this)
            }
        },
        onRelease = { releasedWebView ->
            releasedWebView.stopLoading()
            releasedWebView.webChromeClient = null
            releasedWebView.webViewClient = WebViewClient()
            releasedWebView.destroy()
        }
    )
}

/**
 * WebView 渲染进程连续异常后的可恢复页面，避免向用户留下不可操作的永久白屏。
 *
 * @param onRetry 用户主动重新创建 WebView 的回调。
 * @param onOpenSystemBrowser 使用系统浏览器继续访问当前安全 URL 的回调。
 * @param modifier 外部布局约束。
 */
@Composable
private fun WebViewUnavailablePanel(
    onRetry: () -> Unit,
    onOpenSystemBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.aliyun_web_renderer_unavailable),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.aliyun_web_renderer_unavailable_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(modifier = Modifier.padding(top = 16.dp)) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
            TextButton(onClick = onOpenSystemBrowser) {
                Text(stringResource(R.string.aliyun_web_system_browser))
            }
        }
    }
}

/** WebView 渲染进程崩溃后最多自动重建一次，防止设备持续崩溃形成刷新循环。 */
private const val MAX_AUTOMATIC_RENDERER_RECOVERIES = 1

/**
 * 展示可复制的 WebView 诊断信息。
 *
 * 开启脱敏时隐藏 URL 路径和 Cookie 值；关闭后可显示路径以及 Cookie 值的少量首尾字符，
 * 但查询参数、完整 Cookie 值和所有认证凭证始终不会进入诊断模型或剪贴板。
 *
 * @param diagnostics 当前只读诊断快照。
 * @param redactionEnabled 是否隐藏 URL 路径和 Cookie 脱敏值。
 * @param onDismiss 关闭诊断弹窗的回调。
 */
@Composable
internal fun AliyunWebDiagnosticsDialog(
    diagnostics: AliyunWebDiagnostics,
    redactionEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val displayUrl = displayWebDiagnosticUrl(diagnostics.currentUrl, redactionEnabled)
    val cookiePolicy = context.getString(
        R.string.aliyun_web_diagnostic_cookie_policy_value,
        diagnostics.cookiesAccepted.toString(),
        diagnostics.thirdPartyCookiesAccepted.toString()
    )
    val cookieDetails = diagnostics.cookies.joinToString("\n") { cookie ->
        val displayValue = if (redactionEnabled) {
            context.getString(R.string.aliyun_web_diagnostic_cookie_value_hidden)
        } else {
            cookie.maskedValue
        }
        "${cookie.name}=$displayValue"
    }.ifEmpty {
        context.getString(R.string.aliyun_web_diagnostic_none)
    }
    val cookieSummary = "$cookiePolicy\n$cookieDetails"
    val duration = diagnostics.lastLoadDurationMillis?.let { value ->
        context.getString(R.string.aliyun_web_diagnostic_duration_value, value)
    } ?: context.getString(R.string.aliyun_web_diagnostic_none)
    val eventText = diagnostics.recentEvents.joinToString("\n").ifEmpty {
        context.getString(R.string.aliyun_web_diagnostic_no_events)
    }
    val report = buildString {
        appendLine(context.getString(R.string.aliyun_web_diagnostic_current_url, displayUrl))
        appendLine(
            context.getString(
                R.string.aliyun_web_diagnostic_webview_version,
                diagnostics.webViewVersion
            )
        )
        appendLine(context.getString(R.string.aliyun_web_diagnostic_load_duration, duration))
        appendLine(context.getString(R.string.aliyun_web_diagnostic_cookies, cookieSummary))
        appendLine(context.getString(R.string.aliyun_web_diagnostic_recent_events))
        append(eventText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.aliyun_web_diagnostics)) },
        text = {
            Text(
                text = report,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText(
                        context.getString(R.string.aliyun_web_diagnostics),
                        report
                    )
                )
                Toast.makeText(
                    context,
                    R.string.aliyun_web_diagnostic_copied,
                    Toast.LENGTH_SHORT
                ).show()
            }) {
                Text(stringResource(R.string.aliyun_web_diagnostic_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

/**
 * 创建只负责解析一次新窗口目标地址的临时 WebView。
 *
 * 阿里云官方 HTTPS 地址回到原 WebView，保持应用内操作体验；其他 HTTPS 地址交给系统浏览器。
 * 临时实例不会挂载到界面，完成第一次顶层导航后立即销毁，避免额外渲染器长期占用内存。
 *
 * @param context 用于创建临时 WebView 及启动系统浏览器的上下文。
 * @param sourceWebView 发起新窗口请求的主 WebView。
 * @return 已配置到新窗口传输对象、等待接收首个导航的临时 WebView。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createPopupNavigationWebView(
    context: Context,
    sourceWebView: WebView,
    onPopupTarget: (String) -> Unit
): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val target = request.url.toString()
                // window.open() 常先创建 about:blank，再由脚本写入真实地址；保留实例等待后续 HTTPS 导航。
                if (request.isForMainFrame && request.url.scheme == "about") return false
                onPopupTarget(target)
                when {
                    isTrustedAliyunWebUrl(target) -> sourceWebView.loadUrl(target)
                    request.isForMainFrame && request.url.scheme == "https" ->
                        openExternalUrl(context, target)
                }
                view.destroy()
                return true
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                view.destroy()
            }
        }
    }

/**
 * 账号入口配置弹窗；只处理站点、账号类型和非敏感账号名，不展示密码输入框。
 *
 * @param initialSite 上次保存的站点，首次使用时为中国站。
 * @param initialType 上次保存的账号类型。
 * @param initialUsername 上次保存的账号名，用于输入框预填。
 * @param onDismiss 用户取消配置时的回调。
 * @param onConfirm 用户确认站点、账号类型和账号名时的回调。
 */
@Composable
internal fun AliyunAccountDialog(
    initialSite: AliyunSiteType,
    initialType: AliyunAccountType,
    initialUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (AliyunSiteType, AliyunAccountType, String) -> Unit
) {
    var site by remember(initialSite) { mutableStateOf(initialSite) }
    var accountType by remember(initialType) { mutableStateOf(initialType) }
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    val canConfirm = accountType == AliyunAccountType.MAIN || username.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.aliyun_web_account)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.aliyun_web_site),
                    style = MaterialTheme.typography.labelLarge
                )
                SelectionOption(
                    selected = site == AliyunSiteType.CHINA,
                    label = stringResource(R.string.aliyun_web_site_china),
                    onClick = { site = AliyunSiteType.CHINA }
                )
                SelectionOption(
                    selected = site == AliyunSiteType.INTERNATIONAL,
                    label = stringResource(R.string.aliyun_web_site_international),
                    onClick = { site = AliyunSiteType.INTERNATIONAL }
                )
                Text(
                    text = stringResource(R.string.aliyun_web_account_type),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                SelectionOption(
                    selected = accountType == AliyunAccountType.MAIN,
                    label = stringResource(R.string.aliyun_web_account_main),
                    onClick = { accountType = AliyunAccountType.MAIN }
                )
                SelectionOption(
                    selected = accountType == AliyunAccountType.RAM,
                    label = stringResource(R.string.aliyun_web_account_ram),
                    onClick = { accountType = AliyunAccountType.RAM }
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.aliyun_web_username)) },
                    supportingText = {
                        Text(
                            if (accountType == AliyunAccountType.RAM) {
                                stringResource(R.string.aliyun_web_ram_username_hint)
                            } else {
                                stringResource(R.string.aliyun_web_main_username_hint)
                            }
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.aliyun_web_password_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(site, accountType, username.trim()) }
            ) {
                Text(stringResource(R.string.aliyun_web_save_and_login))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 单个站点或账号类型单选行。
 *
 * @param selected 当前选项是否选中。
 * @param label 展示给用户的选项名称。
 * @param onClick 用户选择此项时的回调。
 */
@Composable
private fun SelectionOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) {
            Text(label)
        }
    }
}

/** 清除 WebView Cookie、站点存储、历史和缓存；账号名设置保持不变，便于再次预填。 */
private fun clearWebSession(webView: WebView?, onCleared: () -> Unit) {
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView?.clearHistory()
        webView?.clearCache(true)
        onCleared()
    }
}

/** 将不受内置域名策略允许的 HTTPS 页面交给系统浏览器；失败时给出可恢复提示。 */
internal fun openExternalUrl(context: Context, url: String) {
    if (!url.startsWith("https://", ignoreCase = true)) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, R.string.aliyun_web_open_failed, Toast.LENGTH_SHORT).show()
    }
}
