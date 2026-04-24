package com.ossbrowser.app.ui.browser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ossbrowser.app.OssBrowserApp
import com.ossbrowser.app.data.OssProfile
import com.ossbrowser.app.data.OssProfileRepository
import com.ossbrowser.app.data.SettingsRepository
import com.ossbrowser.app.oss.OssBucketInfo
import com.ossbrowser.app.oss.OssObjectItem
import com.ossbrowser.app.oss.OssPreviewKind
import com.ossbrowser.app.oss.OssService
import com.ossbrowser.app.oss.previewKind
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
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val pageSize: Int get() = settingsRepo.settings.value.pageSize
    private val textPreviewMax: Long get() = settingsRepo.settings.value.textPreviewMaxBytes

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

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
                    if (active != null) refresh()
                }
            }
        }
    }

    /** 刷新当前视图：未选中 bucket 时拉 bucket 列表，否则拉 object 列表（首页）。 */
    fun refresh() {
        val s = _state.value
        if (s.activeProfile == null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = s.copy(
                loading = true,
                errorMessage = null,
                items = emptyList(),
                nextMarker = null,
                loadingMore = false
            )
            if (s.currentBucket == null) {
                try {
                    val list = service.listBuckets()
                    _state.value = _state.value.copy(loading = false, buckets = list)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = e.localizedMessage ?: e.toString()
                    )
                }
            } else {
                try {
                    val page = service.list(
                        bucket = s.currentBucket,
                        prefix = s.prefix,
                        marker = null,
                        maxKeys = pageSize,
                        endpoint = s.currentBucketEndpoint
                    )
                    _state.value = _state.value.copy(
                        loading = false,
                        items = page.items,
                        nextMarker = page.nextMarker
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = e.localizedMessage ?: e.toString()
                    )
                }
            }
        }
    }

    /** 追加加载下一页 object；仅在 [BrowserUiState.nextMarker] 不为空且未加载中时生效。 */
    fun loadMore() {
        val s = _state.value
        val bucket = s.currentBucket ?: return
        val marker = s.nextMarker ?: return
        if (s.loadingMore || s.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val page = service.list(
                    bucket = bucket,
                    prefix = s.prefix,
                    marker = marker,
                    maxKeys = pageSize,
                    endpoint = s.currentBucketEndpoint
                )
                // 极少数情况下 marker 与已有 key 重叠，这里做一次去重；
                // 并确保"文件夹优先 + 按名称排序"的全局顺序在分页叠加后仍然成立。
                val existing = _state.value.items.map { it.key }.toHashSet()
                val merged = (_state.value.items + page.items.filter { it.key !in existing })
                    .sortedWith(OssService.FolderFirstByName)
                _state.value = _state.value.copy(
                    items = merged,
                    nextMarker = page.nextMarker,
                    loadingMore = false
                )
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(loadingMore = false)
                throw e
            } catch (e: Throwable) {
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
        refresh()
    }

    /** 退出当前 bucket，回到 bucket 列表。 */
    fun exitBucket() {
        _state.value = _state.value.copy(
            currentBucket = null,
            currentBucketEndpoint = null,
            prefix = "",
            items = emptyList()
        )
        refresh()
    }

    fun enterFolder(folderKey: String) {
        _state.value = _state.value.copy(prefix = folderKey)
        refresh()
    }

    fun up() {
        val cur = _state.value.prefix
        if (cur.isEmpty()) return
        val parent = cur.trimEnd('/').substringBeforeLast('/', "")
        val newPrefix = if (parent.isEmpty()) "" else "$parent/"
        _state.value = _state.value.copy(prefix = newPrefix)
        refresh()
    }

    fun jumpTo(prefix: String) {
        _state.value = _state.value.copy(prefix = prefix)
        refresh()
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
                refresh()
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

    /** 主动取消首屏 / 刷新加载（例如网络慢到无法忍受时），立刻回到非 loading 态。 */
    fun cancelLoading() {
        loadJob?.cancel()
        loadJob = null
        _state.value = _state.value.copy(loading = false, loadingMore = false)
    }

    fun upload(context: Context, uri: Uri) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress("上传中", 0, 0))
            runCatching {
                service.upload(
                    context = context,
                    bucket = bucket,
                    prefix = _state.value.prefix,
                    uri = uri,
                    endpoint = endpoint
                ) { sent, total ->
                    _state.value = _state.value.copy(task = TaskState.Progress("上传中", sent, total))
                }
            }.onSuccess {
                _state.value = _state.value.copy(task = TaskState.Done("上传完成: $it"))
                refresh()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(task = TaskState.Failed("上传失败: ${e.localizedMessage ?: e}"))
            }
        }
    }

    fun download(key: String, output: OutputStream, displayName: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress("下载中", 0, 0))
            runCatching {
                output.use { os ->
                    service.download(bucket, key, os, endpoint = endpoint) { read, total ->
                        _state.value = _state.value.copy(task = TaskState.Progress("下载中", read, total))
                    }
                }
            }.onSuccess {
                _state.value = _state.value.copy(task = TaskState.Done("下载完成: $displayName"))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(task = TaskState.Failed("下载失败: ${e.localizedMessage ?: e}"))
            }
        }
    }

    fun delete(key: String) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress("删除中", 0, 0))
            runCatching { service.delete(bucket, key, endpoint = endpoint) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done("已删除: $key"))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed("删除失败: ${e.localizedMessage ?: e}"))
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
                    _state.value = _state.value.copy(task = TaskState.Done("目录已创建: $it"))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed("创建目录失败: ${e.localizedMessage ?: e}"))
                }
        }
    }

    /**
     * 打开文件预览：
     * - TEXT：下载到内存（大小上限由用户设置）→ 字符串
     * - IMAGE：生成 1 小时预签名 URL，由 UI 通过 Coil 加载
     * - AUDIO / VIDEO：生成 1 小时预签名 URL，内嵌 ExoPlayer 播放
     * - PDF：下载到 cacheDir，UI 用 PdfRenderer 渲染
     * - UNSUPPORTED：提示不支持
     */
    fun preview(item: OssObjectItem.File) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        val kind = item.previewKind()
        if (kind == OssPreviewKind.UNSUPPORTED) {
            _state.value = _state.value.copy(
                task = TaskState.Failed("暂不支持此类型的预览，可使用下载或「生成链接」。")
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                previewState = PreviewState.Loading(item.key, item.displayName, kind)
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
                            content = bytes.toString(Charsets.UTF_8)
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
                                endpoint = endpoint
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
                _state.value = _state.value.copy(previewState = st)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(
                    previewState = null,
                    task = TaskState.Failed("预览失败: ${e.localizedMessage ?: e}")
                )
            }
        }
    }

    fun closePreview() {
        _state.value = _state.value.copy(previewState = null)
    }

    /** 生成带签名的临时下载链接；成功后通过 [presignResult] 推出，UI 可展示 / 复制 / 分享。 */
    fun presignUrl(key: String, expireHours: Int) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        if (expireHours <= 0) {
            _state.value = _state.value.copy(task = TaskState.Failed("有效期必须大于 0 小时"))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress("生成链接中", 0, 0))
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
                    task = TaskState.Failed("生成链接失败: ${e.localizedMessage ?: e}")
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
                _state.value = _state.value.copy(task = TaskState.Failed("源路径与目标路径相同"))
                return@launch
            }
            _state.value = _state.value.copy(task = TaskState.Progress("移动中", 0, 0))
            runCatching { service.move(bucket, sourceKey, destKey, endpoint = endpoint) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done("已移动到: $destKey"))
                    refresh()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed("移动失败: ${e.localizedMessage ?: e}"))
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
                    settingsRepo = app.settingsRepository
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
        val kind: OssPreviewKind
    ) : PreviewState()

    data class Text(
        override val key: String,
        override val name: String,
        val content: String
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
}
