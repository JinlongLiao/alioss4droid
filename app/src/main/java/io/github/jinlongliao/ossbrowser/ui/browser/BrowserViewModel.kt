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
package io.github.jinlongliao.ossbrowser.ui.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.OssProfileRepository
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.oss.OssBucketInfo
import io.github.jinlongliao.ossbrowser.oss.OssListCache
import io.github.jinlongliao.ossbrowser.oss.OssObjectItem
import io.github.jinlongliao.ossbrowser.oss.OssPreviewKind
import io.github.jinlongliao.ossbrowser.oss.OssService
import io.github.jinlongliao.ossbrowser.oss.isMarkdownFile
import io.github.jinlongliao.ossbrowser.oss.previewKind
import io.github.jinlongliao.ossbrowser.util.DocxText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

class BrowserViewModel(
    private val repository: OssProfileRepository,
    private val service: OssService,
    private val settingsRepo: SettingsRepository,
    private val listCache: OssListCache
) : ViewModel() {

    private val pageSize: Int get() = settingsRepo.settings.value.pageSize
    private val textPreviewMax: Long get() = settingsRepo.settings.value.textPreviewMaxBytes

    /** 从全局 Application 上下文读取当前 Locale 下的资源字符串。 */
    private fun str(resId: Int, vararg args: Any?): String =
        if (args.isEmpty()) OssBrowserApp.instance.getString(resId)
        else OssBrowserApp.instance.getString(resId, *args)

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** 最近一次触发预览的 File；重试 / 超限后的「下载」「生成链接」按钮都依赖它。 */
    private var lastPreviewItem: OssObjectItem.File? = null

    /** 供 UI 读取当前预览对应的源文件，用于触发 SAF 下载 / 生成链接弹窗等复用流程。 */
    fun currentPreviewItem(): OssObjectItem.File? = lastPreviewItem

    /**
     * 每次"首屏刷新"自增，用于丢弃过期的请求结果。
     *
     * 设计取舍：我们 **不** 对正在飞行的 OSS SDK 请求调用 coroutine `cancel`。
     * 阿里云 OSS Android SDK 的同步 API 不响应协程取消，强行中断底层 OkHttp
     * 连接会抛 "Connections closed" / `ClientException`，导致看起来一直卡在加载中。
     * 因此：旧请求允许跑完，但通过比对 [refreshSeq] 决定是否应用结果到 UI。
     */
    private var refreshSeq: Long = 0L
    private var loadMoreSeq: Long = 0L

    init {
        viewModelScope.launch {
            repository.state.collect { s ->
                val active = s.active
                val current = _state.value
                if (active?.id != current.activeProfile?.id) {
                    // 切换 profile 时：若配置了默认 bucket，则直接定位；否则先展示该账号下所有 bucket。
                    // 默认 bucket 场景下 endpoint 未知，使用 profile.endpoint；
                    // 跨 region 的 bucket 会在用户从 bucket 列表点击时，使用 extranetEndpoint 路由到对应区域。
                    _state.value = current.copy(
                        activeProfile = active,
                        currentBucket = active?.bucket?.takeIf { it.isNotBlank() },
                        currentBucketEndpoint = null,
                        prefix = "",
                        items = emptyList(),
                        buckets = emptyList(),
                        errorMessage = null,
                        task = null
                    )
                    if (active != null) loadCurrent(forceNetwork = false)
                }
            }
        }
    }

    /**
     * 强制刷新当前视图：失效对应缓存，拉最新数据。
     *
     * 适用于：顶部刷新按钮、写操作（上传 / 删除 / 移动 / 新建）后的自动刷新。
     */
    fun refresh() {
        val s = _state.value
        val profile = s.activeProfile ?: return
        val bucket = s.currentBucket
        if (bucket == null) listCache.invalidateBuckets(profile.id)
        else listCache.invalidateList(profile.id, bucket, s.prefix)
        loadCurrent(forceNetwork = true)
    }

    /**
     * 内部统一加载入口：
     * - `forceNetwork = false`：优先读缓存（bucket 列表 / 对象列表），命中则秒开，不发网络；
     * - `forceNetwork = true`：跳过缓存，走网络并把结果回写缓存。
     *
     * 导航类（进入 bucket、进入子目录、返回上级、路径跳转、切 profile）都走 `false`，
     * 因此多数日常点击都能秒响应。"刷新按钮"与写操作后走 `true`。
     */
    private fun loadCurrent(forceNetwork: Boolean) {
        val s = _state.value
        val profile = s.activeProfile ?: return
        val mySeq = ++refreshSeq
        // loadMore 的旧 seq 也一并失效（快速切目录时，避免上一目录的分页结果污染当前目录）
        loadMoreSeq++

        val bucket = s.currentBucket
        val prefix = s.prefix
        val endpoint = s.currentBucketEndpoint

        // 1) 命中缓存 → 直接应用，体验上无"加载"闪烁
        if (!forceNetwork) {
            if (bucket == null) {
                listCache.readBuckets(profile.id)?.let { cached ->
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = null,
                        buckets = cached,
                        items = emptyList(),
                        nextMarker = null,
                        loadingMore = false
                    )
                    return
                }
            } else {
                listCache.readList(profile.id, bucket, prefix)?.let { cached ->
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = null,
                        items = cached.items,
                        nextMarker = cached.nextMarker,
                        loadingMore = false
                    )
                    return
                }
            }
        }

        // 2) 走网络：先切 loading 态
        _state.value = _state.value.copy(
            loading = true,
            errorMessage = null,
            items = emptyList(),
            nextMarker = null,
            loadingMore = false
        )

        loadJob = viewModelScope.launch {
            try {
                if (bucket == null) {
                    val list = service.listBuckets()
                    if (mySeq != refreshSeq) return@launch
                    listCache.writeBuckets(profile.id, list)
                    _state.value = _state.value.copy(loading = false, buckets = list)
                } else {
                    val page = service.list(
                        bucket = bucket,
                        prefix = prefix,
                        marker = null,
                        maxKeys = pageSize,
                        endpoint = endpoint
                    )
                    if (mySeq != refreshSeq) return@launch
                    listCache.writeList(profile.id, bucket, prefix, page.items, page.nextMarker)
                    _state.value = _state.value.copy(
                        loading = false,
                        items = page.items,
                        nextMarker = page.nextMarker
                    )
                }
            } catch (e: CancellationException) {
                // ViewModel 销毁场景 —— 正常传播
                throw e
            } catch (e: Throwable) {
                // 本次请求已过期（用户切到别的目录）则静默丢弃
                if (mySeq != refreshSeq) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = e.localizedMessage ?: e.toString()
                )
            }
        }
    }

    /** 追加加载下一页 object；仅在 [BrowserUiState.nextMarker] 不为空且未加载中时生效。 */
    fun loadMore() {
        val s = _state.value
        val profile = s.activeProfile ?: return
        val bucket = s.currentBucket ?: return
        val marker = s.nextMarker ?: return
        if (s.loadingMore || s.loading) return
        val mySeq = ++loadMoreSeq
        val parentSeq = refreshSeq
        _state.value = _state.value.copy(loadingMore = true)

        viewModelScope.launch {
            try {
                val page = service.list(
                    bucket = bucket,
                    prefix = s.prefix,
                    marker = marker,
                    maxKeys = pageSize,
                    endpoint = s.currentBucketEndpoint
                )
                // 用户若在期间做了首屏刷新 / 切目录，本次结果就不再适用
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch

                val existing = _state.value.items.map { it.key }.toHashSet()
                val merged = (_state.value.items + page.items.filter { it.key !in existing })
                    .sortedWith(OssService.FolderFirstByName)
                _state.value = _state.value.copy(
                    items = merged,
                    nextMarker = page.nextMarker,
                    loadingMore = false
                )
                // 把"已加载部分"写回缓存：下次进入该目录可直接看到这些项，并继续从当前 nextMarker 向后分页。
                listCache.writeList(profile.id, bucket, s.prefix, merged, page.nextMarker)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch
                _state.value = _state.value.copy(
                    loadingMore = false,
                    errorMessage = e.localizedMessage ?: e.toString()
                )
            }
        }
    }

    fun selectBucket(name: String, endpoint: String? = null) {
        _state.value = _state.value.copy(
            currentBucket = name,
            currentBucketEndpoint = endpoint,
            prefix = "",
            items = emptyList()
        )
        loadCurrent(forceNetwork = false)
    }

    /** 退出当前 bucket，回到 bucket 列表。 */
    fun exitBucket() {
        _state.value = _state.value.copy(
            currentBucket = null,
            currentBucketEndpoint = null,
            prefix = "",
            items = emptyList()
        )
        loadCurrent(forceNetwork = false)
    }

    fun enterFolder(folderKey: String) {
        _state.value = _state.value.copy(prefix = folderKey)
        loadCurrent(forceNetwork = false)
    }

    fun up() {
        val cur = _state.value.prefix
        if (cur.isEmpty()) return
        val parent = cur.trimEnd('/').substringBeforeLast('/', "")
        val newPrefix = if (parent.isEmpty()) "" else "$parent/"
        _state.value = _state.value.copy(prefix = newPrefix)
        loadCurrent(forceNetwork = false)
    }

    fun jumpTo(prefix: String) {
        _state.value = _state.value.copy(prefix = prefix)
        loadCurrent(forceNetwork = false)
    }

    /**
     * 统一的"返回上一级"行为：
     * - 有 prefix：退到父目录
     * - prefix 为空但已进入 bucket：回到 bucket 列表
     * - 否则返回 false，交由 UI 决定（如"再按一次退出应用"）。
     */
    fun goBack(): Boolean {
        val s = _state.value
        if (s.activeProfile == null) return false
        if (s.currentBucket == null) return false
        if (s.prefix.isNotEmpty()) {
            up()
            return true
        }
        exitBucket()
        return true
    }

    /**
     * 按用户输入的相对路径跳转：
     * - 以 `/` 结尾：作为目录 prefix
     * - 不以 `/` 结尾：headObject 若存在则跳到其父目录并高亮该文件，否则当作目录（补 `/`）
     * 空串则回到 bucket 根。
     */
    fun navigateToPath(raw: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        val input = raw.trim().trimStart('/')
        if (input.isEmpty()) {
            _state.value = _state.value.copy(highlightKey = null)
            jumpTo("")
            return
        }
        if (input.endsWith("/")) {
            _state.value = _state.value.copy(highlightKey = null)
            jumpTo(input)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
            val isFile = try {
                service.exists(bucket = bucket, key = input, endpoint = endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (isFile) {
                val slash = input.lastIndexOf('/')
                val parent = if (slash < 0) "" else input.substring(0, slash + 1)
                _state.value = _state.value.copy(
                    prefix = parent,
                    highlightKey = input
                )
                loadCurrent(forceNetwork = false)
            } else {
                _state.value = _state.value.copy(highlightKey = null)
                jumpTo("$input/")
            }
        }
    }

    fun clearHighlight() {
        if (_state.value.highlightKey != null) {
            _state.value = _state.value.copy(highlightKey = null)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /**
     * 主动取消首屏 / 刷新加载（例如网络慢到无法忍受时），立刻回到非 loading 态。
     *
     * 注意：这里 **不** 真正中断 OSS SDK 请求（它不响应协程 cancel），
     * 只是让"正在飞行的结果"被视为过期（seq 失效）并把 UI 置回可操作状态。
     */
    fun cancelLoading() {
        refreshSeq++
        loadMoreSeq++
        _state.value = _state.value.copy(loading = false, loadingMore = false)
    }

    fun upload(context: Context, uri: Uri) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_uploading), 0, 0))
            runCatching {
                service.upload(
                    context = context,
                    bucket = bucket,
                    prefix = _state.value.prefix,
                    uri = uri,
                    endpoint = endpoint
                ) { sent, total ->
                    _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_uploading), sent, total))
                }
            }.onSuccess {
                _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_upload_done, it)))
                refresh()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_upload_failed, e.localizedMessage ?: e.toString())))
            }
        }
    }

    fun download(key: String, output: OutputStream, displayName: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_downloading), 0, 0))
            runCatching {
                output.use { os ->
                    service.download(bucket, key, os, endpoint = endpoint) { read, total ->
                        _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_downloading), read, total))
                    }
                }
            }.onSuccess {
                _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_download_done, displayName)))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_download_failed, e.localizedMessage ?: e.toString())))
            }
        }
    }

    fun delete(key: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_deleting), 0, 0))
            runCatching { service.delete(bucket, key, endpoint = endpoint) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_delete_done, key)))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_delete_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    fun mkdir(name: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { service.mkdir(bucket, _state.value.prefix, name, endpoint = endpoint) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_mkdir_done, it)))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_mkdir_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    /**
     * 打开文件预览：
     * - TEXT / DOCX：下载到内存（大小上限由用户设置）→ 字符串 / 段落
     * - IMAGE：生成 1 小时预签名 URL，由 UI 通过 Coil 加载
     * - AUDIO / VIDEO：生成 1 小时预签名 URL，内嵌 ExoPlayer 播放
     * - PDF：下载到 cacheDir，UI 用 PdfRenderer 渲染（带实时进度）
     * - UNSUPPORTED：提示不支持
     *
     * 失败或超限时保留弹窗并呈现友好界面（TooLarge / Error），而不是把消息闪给
     * snackbar 后丢失上下文。
     */
    fun preview(item: OssObjectItem.File) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        val kind = item.previewKind()
        lastPreviewItem = item
        if (kind == OssPreviewKind.UNSUPPORTED) {
            _state.value = _state.value.copy(
                task = TaskState.Failed(str(R.string.task_preview_unsupported))
            )
            return
        }
        // 前置检查：TEXT / DOCX 走内存下载，若预计大小已超阈值就不尝试真正下载。
        if ((kind == OssPreviewKind.TEXT || kind == OssPreviewKind.DOCX) &&
            item.size > 0 && item.size > textPreviewMax
        ) {
            _state.value = _state.value.copy(
                previewState = PreviewState.TooLarge(
                    key = item.key,
                    name = item.displayName,
                    kind = kind,
                    sizeBytes = item.size,
                    limitBytes = textPreviewMax
                )
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                previewState = PreviewState.Loading(
                    key = item.key,
                    name = item.displayName,
                    kind = kind,
                    totalBytes = item.size
                )
            )
            runCatching {
                when (kind) {
                    OssPreviewKind.TEXT -> {
                        val bytes = service.downloadBytes(
                            bucket = bucket,
                            key = item.key,
                            maxBytes = textPreviewMax,
                            endpoint = endpoint
                        )
                        PreviewState.Text(
                            key = item.key,
                            name = item.displayName,
                            content = bytes.toString(Charsets.UTF_8),
                            isMarkdown = item.isMarkdownFile()
                        )
                    }
                    OssPreviewKind.DOCX -> {
                        // docx 是 ZIP；XML 解析后只需保留纯文本段落，所以复用文本预览上限即可。
                        val bytes = service.downloadBytes(
                            bucket = bucket,
                            key = item.key,
                            maxBytes = textPreviewMax,
                            endpoint = endpoint
                        )
                        val paragraphs = DocxText.extractParagraphs(bytes)
                            ?: throw IllegalStateException(str(R.string.preview_docx_failed))
                        PreviewState.Docx(
                            key = item.key,
                            name = item.displayName,
                            paragraphs = paragraphs
                        )
                    }
                    OssPreviewKind.IMAGE,
                    OssPreviewKind.AUDIO,
                    OssPreviewKind.VIDEO -> {
                        val url = service.presignUrl(
                            bucket = bucket,
                            key = item.key,
                            expireSeconds = 3600L,
                            endpoint = endpoint
                        )
                        when (kind) {
                            OssPreviewKind.IMAGE ->
                                PreviewState.Image(item.key, item.displayName, url)
                            OssPreviewKind.AUDIO ->
                                PreviewState.Media(item.key, item.displayName, url, video = false)
                            else ->
                                PreviewState.Media(item.key, item.displayName, url, video = true)
                        }
                    }
                    OssPreviewKind.PDF -> {
                        val app = OssBrowserApp.instance
                        // 缓存文件名用 key 的 hash 避免冲突 / 非法字符
                        val cacheName = "preview_${item.key.hashCode().toUInt()}_${item.displayName}"
                        val target = java.io.File(app.cacheDir, "pdf/$cacheName")
                        target.parentFile?.mkdirs()
                        if (!target.exists() || target.length() == 0L) {
                            service.downloadToFile(
                                bucket = bucket,
                                key = item.key,
                                target = target,
                                endpoint = endpoint,
                                onProgress = { read, total ->
                                    // 只在当前预览仍指向该文件时回写进度，避免用户快速切换时错乱
                                    val cur = _state.value.previewState
                                    if (cur is PreviewState.Loading && cur.key == item.key) {
                                        _state.value = _state.value.copy(
                                            previewState = cur.copy(
                                                totalBytes = if (total > 0) total else cur.totalBytes,
                                                downloadedBytes = read
                                            )
                                        )
                                    }
                                }
                            )
                        }
                        PreviewState.Pdf(
                            key = item.key,
                            name = item.displayName,
                            filePath = target.absolutePath
                        )
                    }
                    else -> null
                }
            }.onSuccess { st ->
                if (st != null) _state.value = _state.value.copy(previewState = st)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(
                    previewState = PreviewState.Error(
                        key = item.key,
                        name = item.displayName,
                        kind = kind,
                        message = e.localizedMessage ?: e.toString()
                    )
                )
            }
        }
    }

    /** 用户在「预览失败」界面点击重试。 */
    fun retryPreview() {
        lastPreviewItem?.let { preview(it) }
    }

    fun closePreview() {
        _state.value = _state.value.copy(previewState = null)
        lastPreviewItem = null
    }

    /** 生成带签名的临时下载链接；成功后通过 [presignResult] 推出，UI 可展示 / 复制 / 分享。 */
    fun presignUrl(key: String, expireHours: Int) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        if (expireHours <= 0) {
            _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_presign_invalid_hours)))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_presigning), 0, 0))
            runCatching {
                service.presignUrl(
                    bucket = bucket,
                    key = key,
                    expireSeconds = expireHours.toLong() * 3600L,
                    endpoint = endpoint
                )
            }.onSuccess { url ->
                _state.value = _state.value.copy(
                    task = null,
                    presignResult = PresignResult(key = key, url = url, expireHours = expireHours)
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(
                    task = TaskState.Failed(str(R.string.task_presign_failed, e.localizedMessage ?: e.toString()))
                )
            }
        }
    }

    fun clearPresignResult() {
        _state.value = _state.value.copy(presignResult = null)
    }

    fun move(sourceKey: String, destPrefix: String, newName: String?) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            val isDir = sourceKey.endsWith("/")
            val baseName = if (isDir) sourceKey.trimEnd('/').substringAfterLast('/')
            else sourceKey.substringAfterLast('/', sourceKey)
            val targetName = (newName?.takeIf { it.isNotBlank() } ?: baseName).trim('/')
            val normalizedDest = when {
                destPrefix.isEmpty() -> ""
                destPrefix.endsWith("/") -> destPrefix
                else -> "$destPrefix/"
            }
            val destKey = normalizedDest + targetName + if (isDir) "/" else ""
            if (destKey == sourceKey) {
                _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_move_same_path)))
                return@launch
            }
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_moving), 0, 0))
            runCatching { service.move(bucket, sourceKey, destKey, endpoint = endpoint) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_move_done, destKey)))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_move_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    fun clearTask() {
        _state.value = _state.value.copy(task = null)
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = OssBrowserApp.instance
                val service = OssService(app.ossClientProvider)
                return BrowserViewModel(
                    repository = app.profileRepository,
                    service = service,
                    settingsRepo = app.settingsRepository,
                    listCache = app.ossListCache
                ) as T
            }
        }
    }
}

data class BrowserUiState(
    val activeProfile: OssProfile? = null,
    /** null 表示未选中 bucket，此时展示 bucket 列表。 */
    val currentBucket: String? = null,
    /** 当前 bucket 的公网 endpoint（来自 ListBuckets 返回）；为空时回退到 profile.endpoint。 */
    val currentBucketEndpoint: String? = null,
    val prefix: String = "",
    val items: List<OssObjectItem> = emptyList(),
    val buckets: List<OssBucketInfo> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val task: TaskState? = null,
    val presignResult: PresignResult? = null,
    val previewState: PreviewState? = null,
    /** 路径跳转/定位时需要高亮并滚动到的对象 key；一次性消费。 */
    val highlightKey: String? = null,
    /** 下一页 marker；null 表示已无更多。 */
    val nextMarker: String? = null,
    /** 是否正在加载下一页（与 [loading] 首次加载区分开）。 */
    val loadingMore: Boolean = false
)

data class PresignResult(
    val key: String,
    val url: String,
    val expireHours: Int
)

sealed class TaskState {
    data class Progress(val label: String, val current: Long, val total: Long) : TaskState()
    data class Done(val message: String) : TaskState()
    data class Failed(val message: String) : TaskState()
}

/** 文件预览的 UI 状态。 */
sealed class PreviewState {
    abstract val key: String
    abstract val name: String

    data class Loading(
        override val key: String,
        override val name: String,
        val kind: OssPreviewKind,
        /** 对象总字节；0 表示未知。用于 Loading 界面展示预估大小与进度条。 */
        val totalBytes: Long = 0L,
        /** 已下载字节（仅 PDF 下载场景实时更新）。 */
        val downloadedBytes: Long = 0L
    ) : PreviewState()

    data class Text(
        override val key: String,
        override val name: String,
        val content: String,
        /** 若为 `true`，UI 可提供「源码 / 富文本」切换，默认以 Markdown 渲染。 */
        val isMarkdown: Boolean = false
    ) : PreviewState()

    data class Image(
        override val key: String,
        override val name: String,
        val url: String
    ) : PreviewState()

    /** 音视频预览：携带预签名 URL，由 UI 通过内嵌 ExoPlayer 播放。 */
    data class Media(
        override val key: String,
        override val name: String,
        val url: String,
        val video: Boolean
    ) : PreviewState()

    /** PDF 预览：携带本地已下载的文件路径，由 UI 用 PdfRenderer 分页渲染。 */
    data class Pdf(
        override val key: String,
        override val name: String,
        val filePath: String
    ) : PreviewState()

    /** DOCX 预览：应用内解析得到的段落列表，UI 以排版方式直接渲染。 */
    data class Docx(
        override val key: String,
        override val name: String,
        val paragraphs: List<String>
    ) : PreviewState()

    /**
     * 文件大小超过预览上限，UI 展示友好引导页：给出大小 / 上限、
     * 「下载到本地」「生成链接」「关闭」三枚动作。
     */
    data class TooLarge(
        override val key: String,
        override val name: String,
        val kind: OssPreviewKind,
        val sizeBytes: Long,
        val limitBytes: Long
    ) : PreviewState()

    /** 预览失败，UI 展示具体错误并提供「重试」。 */
    data class Error(
        override val key: String,
        override val name: String,
        val kind: OssPreviewKind,
        val message: String
    ) : PreviewState()
}
