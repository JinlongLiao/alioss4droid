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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.StorageType
import io.github.jinlongliao.ossbrowser.data.OssProfileRepository
import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField
import io.github.jinlongliao.ossbrowser.data.SettingsRepository
import io.github.jinlongliao.ossbrowser.oss.OssBucketInfo
import io.github.jinlongliao.ossbrowser.oss.OssCnameInfo
import io.github.jinlongliao.ossbrowser.oss.OssListCache
import io.github.jinlongliao.ossbrowser.oss.OssObjectItem
import io.github.jinlongliao.ossbrowser.oss.OssPreviewKind
import io.github.jinlongliao.ossbrowser.oss.OssService
import io.github.jinlongliao.ossbrowser.oss.isMarkdownFile
import io.github.jinlongliao.ossbrowser.oss.objectParentPrefix
import io.github.jinlongliao.ossbrowser.oss.previewKind
import io.github.jinlongliao.ossbrowser.oss.pdfObjectPreviewCacheName
import io.github.jinlongliao.ossbrowser.oss.canReusePdfObjectPreviewCache
import io.github.jinlongliao.ossbrowser.oss.sortOssObjectItems
import io.github.jinlongliao.ossbrowser.oss.loadSortedObjectDirectory
import io.github.jinlongliao.ossbrowser.oss.requiresCompleteObjectDirectory
import io.github.jinlongliao.ossbrowser.util.DocxText
import io.github.jinlongliao.ossbrowser.util.DelimitedTextPreviewParser
import io.github.jinlongliao.ossbrowser.util.OoxmlPreviewParser
import io.github.jinlongliao.ossbrowser.util.PresentationSlidePreview
import io.github.jinlongliao.ossbrowser.util.SpreadsheetSheetPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.util.UUID
import io.github.jinlongliao.ossbrowser.upload.UploadService
import io.github.jinlongliao.ossbrowser.upload.UploadTask
import io.github.jinlongliao.ossbrowser.upload.UploadRepository
import io.github.jinlongliao.ossbrowser.upload.UploadStatus
import io.github.jinlongliao.ossbrowser.util.FileUtils

class BrowserViewModel(
    private val repository: OssProfileRepository,
    private val service: OssService,
    private val settingsRepo: SettingsRepository,
    private val listCache: OssListCache,
    private val uploadRepository: UploadRepository
) : ViewModel() {

    private val pageSize: Int get() = settingsRepo.settings.value.pageSize
    private val textPreviewMax: Long get() = settingsRepo.settings.value.textPreviewMaxBytes

    /** 使用已持久化偏好排序对象，目录始终位于文件之前。 */
    private fun sortObjects(items: List<OssObjectItem>): List<OssObjectItem> {
        val settings = settingsRepo.settings.value
        return sortOssObjectItems(items, settings.ossObjectSortField, settings.ossObjectSortDirection)
    }

    /** 从全局 Application 上下文读取当前 Locale 下的资源字符串。 */
    private fun str(resId: Int, vararg args: Any?): String =
        if (args.isEmpty()) OssBrowserApp.instance.getString(resId)
        else OssBrowserApp.instance.getString(resId, *args)

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** 当前 ListCname 查询任务；关闭或重开分享弹窗时取消，防止旧结果覆盖新弹窗状态。 */
    private var customDomainsJob: Job? = null

    /** 最近一次触发预览的 File；重试 / 超限后的「下载」「生成链接」按钮都依赖它。 */
    private var lastPreviewItem: OssObjectItem.File? = null

    /** 关闭、重开或切换账号时废弃旧预览；同步 OSS 请求返回后也不能重新弹出旧内容。 */
    @Volatile
    private var previewGeneration = 0L

    /** 可取消的预览解析任务；耗时文档转换在 Default 调度器执行。 */
    private var previewJob: Job? = null

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
    @Volatile
    private var refreshSeq: Long = 0L
    private var loadMoreSeq: Long = 0L

    /** 固定短窗口合并连续上传的刷新，避免每个小文件完成都重新扫描完整目录。 */
    private var uploadRefreshJob: Job? = null

    /** 合并窗口中最近一次匹配的可见目录；导航后不能刷新用户已经离开的页面。 */
    private var uploadRefreshTarget: Triple<String, String, String>? = null

    /** 当前列表是否覆盖完整目录；部分缓存不能用于全局时间排序和名称倒序。 */
    private var currentDirectoryComplete = false

    /** 已应用到当前列表的排序，防止同步设置更新与 Flow 通知重复触发网络扫描。 */
    private var appliedObjectSort = settingsRepo.settings.value.let {
        it.ossObjectSortField to it.ossObjectSortDirection
    }

    /** 仅消费本 ViewModel 存活期间新完成的上传，避免历史成功记录导致每次进入页面刷新。 */
    private var completedUploadIds = emptySet<String>()

    init {
        viewModelScope.launch {
            repository.state.collect { s ->
                val active = s.active
                val current = _state.value
                if (active != current.activeProfile) {
                    previewGeneration++
                    previewJob?.cancel()
                    lastPreviewItem = null
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
                        previewState = null,
                        task = null
                    )
                    if (active != null) loadCurrent(forceNetwork = false)
                }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.map { it.ossObjectSortField to it.ossObjectSortDirection }
                .distinctUntilChanged().collect { (field, direction) ->
                    applyObjectSort(field, direction)
                }
        }
        viewModelScope.launch {
            uploadRepository.awaitLoaded()
            completedUploadIds = uploadRepository.initialCompletedUploadIds
            uploadRepository.tasks.map { tasks -> tasks.filter { it.status == UploadStatus.SUCCEEDED } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default).collect { completed ->
                    val newlyCompleted = completed.filter { it.id !in completedUploadIds }
                    completedUploadIds = completed.map { it.id }.toSet()
                    val current = _state.value
                    if (newlyCompleted.any {
                            it.profileId == current.activeProfile?.id && it.bucket == current.currentBucket &&
                                it.prefix == current.prefix
                        }) {
                        scheduleUploadedDirectoryRefresh()
                    }
                }
        }
    }

    /**
     * 在首个完成事件后固定等待 250ms，合并期间的后续完成事件不会延长等待。
     * 立即废弃正在读取的旧列表；窗口结束只刷新仍然可见的目标目录。
     */
    private fun scheduleUploadedDirectoryRefresh() {
        val current = _state.value
        val profileId = current.activeProfile?.id ?: return
        val bucket = current.currentBucket ?: return
        uploadRefreshTarget = Triple(profileId, bucket, current.prefix)
        refreshSeq++
        loadMoreSeq++
        if (uploadRefreshJob?.isActive == true) return
        uploadRefreshJob = viewModelScope.launch {
            delay(250)
            val target = uploadRefreshTarget
            uploadRefreshTarget = null
            val latest = _state.value
            if (target == Triple(latest.activeProfile?.id, latest.currentBucket, latest.prefix)) refresh()
        }
    }

    /**
     * 强制刷新当前视图：失效对应缓存，拉最新数据。
     *
     * 适用于：顶部刷新按钮、写操作（上传 / 删除 / 移动 / 新建）后的自动刷新。
     */
    fun refresh() {
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
        val snapshot = _state.value
        val profile = snapshot.activeProfile ?: return
        val mySeq = ++refreshSeq
        loadMoreSeq++
        val bucket = snapshot.currentBucket
        val prefix = snapshot.prefix
        val endpoint = snapshot.currentBucketEndpoint
        val sort = settingsRepo.settings.value
        val completeSort = requiresCompleteObjectDirectory(sort.ossObjectSortField, sort.ossObjectSortDirection)
        currentDirectoryComplete = false
        _state.value = snapshot.copy(
            loading = true,
            errorMessage = null,
            items = emptyList(),
            nextMarker = null,
            loadingMore = false
        )
        loadJob = viewModelScope.launch {
            try {
                // 主动刷新涉及磁盘删除/锁等待，同样放到IO；递增UI代次已先阻止旧响应发布。
                if (forceNetwork) withContext(Dispatchers.IO) {
                    if (bucket == null) listCache.invalidateBuckets(profile.id)
                    else listCache.invalidateList(profile.id, bucket, prefix)
                }
                if (mySeq != refreshSeq) return@launch
                if (bucket == null) {
                    // JSON 反序列化、磁盘读写和缓存 DTO 转换不能阻塞主线程。
                    val cached = if (forceNetwork) null else withContext(Dispatchers.IO) {
                        listCache.readBuckets(profile.id)
                    }
                    if (mySeq != refreshSeq) return@launch
                    if (cached != null) {
                        _state.value = _state.value.copy(loading = false, buckets = cached)
                        return@launch
                    }
                    val buckets = service.listBuckets()
                    if (mySeq != refreshSeq) return@launch
                    withContext(Dispatchers.IO) { listCache.writeBuckets(profile.id, buckets) }
                    if (mySeq != refreshSeq) return@launch
                    _state.value = _state.value.copy(loading = false, buckets = buckets)
                    return@launch
                }
                val generation = withContext(Dispatchers.IO) {
                    listCache.listGeneration(profile.id, bucket, prefix)
                }
                val cached = if (forceNetwork) null else withContext(Dispatchers.IO) {
                    listCache.readList(profile.id, bucket, prefix)?.takeIf { !completeSort || it.nextMarker == null }
                }
                if (mySeq != refreshSeq) return@launch
                if (cached != null) {
                    val sorted = withContext(Dispatchers.Default) {
                        sortOssObjectItems(cached.items, sort.ossObjectSortField, sort.ossObjectSortDirection)
                    }
                    if (mySeq != refreshSeq) return@launch
                    val currentGeneration = withContext(Dispatchers.IO) {
                        listCache.listGeneration(profile.id, bucket, prefix)
                    }
                    if (mySeq != refreshSeq) return@launch
                    if (generation != currentGeneration) {
                        loadCurrent(forceNetwork = true)
                        return@launch
                    }
                    currentDirectoryComplete = cached.nextMarker == null
                    _state.value = _state.value.copy(
                        loading = false, items = sorted, nextMarker = cached.nextMarker
                    )
                    return@launch
                }
                val page = withContext(Dispatchers.Default) {
                    service.withObjectDirectoryListing(bucket, prefix, endpoint, profile.id) { fetchPage ->
                        loadSortedObjectDirectory(
                            field = sort.ossObjectSortField,
                            direction = sort.ossObjectSortDirection,
                            pageSize = pageSize,
                            ensureCurrent = {
                                if (mySeq != refreshSeq) throw CancellationException("目录请求已过期")
                            },
                            fetchPage = fetchPage
                        )
                    }
                }
                if (mySeq != refreshSeq) return@launch
                val persisted = withContext(Dispatchers.IO) {
                    listCache.writeList(profile.id, bucket, prefix, page.items, page.nextMarker, generation)
                }
                if (mySeq != refreshSeq) return@launch
                if (!persisted) {
                    loadCurrent(forceNetwork = true)
                    return@launch
                }
                currentDirectoryComplete = page.nextMarker == null
                // 加载器已经完成排序，不在主线程把完整目录再排序一次。
                _state.value = _state.value.copy(
                    loading = false, items = page.items, nextMarker = page.nextMarker
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (mySeq != refreshSeq) return@launch
                Log.e(TAG, "目录加载失败 profile=${profile.id}, bucket=$bucket, prefix=$prefix", exception)
                _state.value = _state.value.copy(
                    loading = false, errorMessage = exception.localizedMessage ?: exception.toString()
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
                val generation = withContext(Dispatchers.IO) {
                    listCache.listGeneration(profile.id, bucket, s.prefix)
                }
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch
                val page = service.withObjectDirectoryListing(
                    bucket, s.prefix, s.currentBucketEndpoint, profile.id
                ) { fetchPage -> fetchPage(marker, pageSize) }
                // 用户若在期间做了首屏刷新 / 切目录，本次结果就不再适用
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch

                val currentItems = _state.value.items
                val merged = withContext(Dispatchers.Default) {
                    val existing = currentItems.map { it.key }.toHashSet()
                    sortObjects(currentItems + page.items.filter { it.key !in existing })
                }
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch
                val persisted = withContext(Dispatchers.IO) {
                    listCache.writeList(profile.id, bucket, s.prefix, merged, page.nextMarker, generation)
                }
                if (mySeq != loadMoreSeq || parentSeq != refreshSeq) return@launch
                if (!persisted) {
                    loadCurrent(forceNetwork = true)
                    return@launch
                }
                currentDirectoryComplete = page.nextMarker == null
                _state.value = _state.value.copy(
                    items = merged,
                    nextMarker = page.nextMarker,
                    loadingMore = false
                )
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

    /**
     * 同步保存排序偏好并立即应用。完整目录可直接重排；部分列表的时间/倒序排序必须补全目录。
     * @param field 文件排序字段。
     * @param direction 排序方向。
     */
    fun updateObjectSort(field: OssObjectSortField, direction: OssObjectSortDirection) {
        settingsRepo.update {
            it.copy(ossObjectSortField = field, ossObjectSortDirection = direction)
        }
        applyObjectSort(field, direction)
    }

    /** 应用设置页或当前页发起的排序切换；加载中的旧扫描通过刷新代次失效。 */
    private fun applyObjectSort(field: OssObjectSortField, direction: OssObjectSortDirection) {
        val requested = field to direction
        if (appliedObjectSort == requested) return
        appliedObjectSort = requested
        val current = _state.value
        if (current.currentBucket == null) return
        if (current.loading || current.errorMessage != null ||
            (requiresCompleteObjectDirectory(field, direction) && !currentDirectoryComplete)
        ) {
            loadCurrent(forceNetwork = false)
        } else {
            val generation = refreshSeq
            viewModelScope.launch {
                val sorted = withContext(Dispatchers.Default) {
                    sortOssObjectItems(current.items, field, direction)
                }
                if (generation != refreshSeq || appliedObjectSort != requested) return@launch
                _state.value = _state.value.copy(items = sorted)
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
        uploadRefreshJob?.cancel()
        uploadRefreshTarget = null
        refreshSeq++
        loadMoreSeq++
        _state.value = _state.value.copy(loading = false, loadingMore = false)
    }

    /**
     * 主线程只捕获账号、目录和URI，立即交给前台服务；真实名称由每任务IO准备阶段解析。
     * 避免云文档ContentProvider.query阻塞Main，也不把FGS启动推迟到用户退后台以后。
     */
    fun upload(context: Context, uri: Uri) {
        val current = _state.value
        val bucket = current.currentBucket ?: return
        val profile = current.activeProfile ?: return
        UploadService.enqueueUploadTask(context.applicationContext, UploadTask(
            id = UUID.randomUUID().toString(),
            profileId = profile.id,
            bucket = bucket,
            prefix = current.prefix,
            uri = uri.toString(),
            name = "unnamed_${System.currentTimeMillis()}",
            resolveDisplayName = true,
            endpoint = current.currentBucketEndpoint ?: profile.endpoint,
            storageType = profile.storageType,
            s3Region = profile.region,
            s3ForcePathStyle = profile.forcePathStyle
        ))
    }

    fun download(key: String, output: OutputStream, displayName: String) {
        val snapshot = _state.value
        val profileId = snapshot.activeProfile?.id ?: return
        val bucket = snapshot.currentBucket ?: return
        val endpoint = snapshot.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_downloading), 0, 0))
            runCatching {
                withContext(Dispatchers.IO) {
                    output.use { os ->
                        service.download(bucket, key, os, endpoint = endpoint, profileId = profileId) { read, total ->
                            _state.value = _state.value.copy(
                                task = TaskState.Progress(str(R.string.task_downloading), read, total)
                            )
                        }
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
        val snapshot = _state.value
        val profileId = snapshot.activeProfile?.id ?: return
        val bucket = snapshot.currentBucket ?: return
        val endpoint = snapshot.currentBucketEndpoint
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_deleting), 0, 0))
            runCatching { service.delete(bucket, key, endpoint = endpoint, profileId = profileId) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_delete_done, key)))
                    refreshChangedObjectDirectories(profileId, bucket, setOf(objectParentPrefix(key)), key.endsWith("/"))
                }
                .onFailure { e ->
                    refreshChangedObjectDirectories(profileId, bucket, setOf(objectParentPrefix(key)), key.endsWith("/"))
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_delete_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    fun mkdir(name: String) {
        val snapshot = _state.value
        val profileId = snapshot.activeProfile?.id ?: return
        val bucket = snapshot.currentBucket ?: return
        val endpoint = snapshot.currentBucketEndpoint
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { service.mkdir(bucket, snapshot.prefix, name, endpoint = endpoint, profileId = profileId) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_mkdir_done, it)))
                    refreshChangedObjectDirectories(profileId, bucket, setOf(snapshot.prefix))
                }
                .onFailure { e ->
                    refreshChangedObjectDirectories(profileId, bucket, setOf(snapshot.prefix))
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_mkdir_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    /**
     * 打开文件预览：
     * - TEXT / DOCX / XLSX / PPTX：受大小上限约束地下载到内存并本地解析
     * - IMAGE：生成 1 小时预签名 URL，由 UI 通过 Coil 加载
     * - AUDIO / VIDEO：生成 1 小时预签名 URL，内嵌 ExoPlayer 播放
     * - PDF：下载到 cacheDir，UI 用 PdfRenderer 渲染（带实时进度）
     * - UNSUPPORTED：提示不支持
     *
     * 失败或超限时保留弹窗并呈现友好界面（TooLarge / Error），而不是把消息闪给
     * snackbar 后丢失上下文。
     */
    fun preview(item: OssObjectItem.File) {
        val snapshot = _state.value
        val profile = snapshot.activeProfile ?: return
        val bucket = snapshot.currentBucket ?: return
        val endpoint = snapshot.currentBucketEndpoint
        val myGeneration = ++previewGeneration
        previewJob?.cancel()
        val kind = item.previewKind()
        lastPreviewItem = item
        if (kind == OssPreviewKind.UNSUPPORTED) {
            _state.value = _state.value.copy(
                task = TaskState.Failed(str(R.string.task_preview_unsupported)), previewState = null
            )
            return
        }
        // 前置检查：文本、HTML 与 OOXML 走内存下载，若预计大小已超阈值就不尝试真正下载。
        if ((kind == OssPreviewKind.TEXT || kind == OssPreviewKind.HTML ||
                kind == OssPreviewKind.DOCX || kind == OssPreviewKind.XLSX ||
                kind == OssPreviewKind.PPTX || kind == OssPreviewKind.DELIMITED_TEXT) &&
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
        previewJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                previewState = PreviewState.Loading(
                    key = item.key,
                    name = item.displayName,
                    kind = kind,
                    totalBytes = item.size
                )
            )
            runCatching {
                withContext(Dispatchers.Default) {
                    when (kind) {
                        OssPreviewKind.TEXT -> {
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            PreviewState.Text(
                                key = item.key,
                                name = item.displayName,
                                content = bytes.toString(Charsets.UTF_8),
                                isMarkdown = item.isMarkdownFile()
                            )
                        }
                        OssPreviewKind.HTML -> {
                            // HTML 首次只下载源码；是否允许脚本和外部资源由预览界面的显式开关控制。
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            PreviewState.Html(
                                key = item.key,
                                name = item.displayName,
                                content = bytes.toString(Charsets.UTF_8)
                            )
                        }
                        OssPreviewKind.DOCX -> {
                            // docx 是 ZIP；XML 解析后只需保留纯文本段落，所以复用文本预览上限即可。
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            val paragraphs = DocxText.extractParagraphs(bytes)
                                ?: throw IllegalStateException(str(R.string.preview_docx_failed))
                            PreviewState.Docx(
                                key = item.key,
                                name = item.displayName,
                                paragraphs = paragraphs
                            )
                        }
                        OssPreviewKind.XLSX -> {
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            val sheets = OoxmlPreviewParser.parseSpreadsheet(bytes)
                                ?: throw IllegalStateException(str(R.string.preview_xlsx_failed))
                            PreviewState.Spreadsheet(item.key, item.displayName, sheets)
                        }
                        OssPreviewKind.PPTX -> {
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            val slides = OoxmlPreviewParser.parsePresentation(bytes)
                                ?: throw IllegalStateException(str(R.string.preview_pptx_failed))
                            PreviewState.Presentation(item.key, item.displayName, slides)
                        }
                        OssPreviewKind.DELIMITED_TEXT -> {
                            val bytes = service.downloadBytes(
                                bucket = bucket,
                                key = item.key,
                                maxBytes = textPreviewMax,
                                endpoint = endpoint,
                                profileId = profile.id
                            )
                            val delimiter = if (item.displayName.endsWith(".tsv", ignoreCase = true)) '\t' else ','
                            val rows = DelimitedTextPreviewParser.parseDelimitedText(
                                bytes.toString(Charsets.UTF_8),
                                delimiter
                            )
                            PreviewState.Spreadsheet(
                                item.key,
                                item.displayName,
                                listOf(SpreadsheetSheetPreview(item.displayName, rows))
                            )
                        }
                        OssPreviewKind.IMAGE,
                        OssPreviewKind.AUDIO,
                        OssPreviewKind.VIDEO -> {
                            val url = service.presignUrl(
                                bucket = bucket,
                                key = item.key,
                                expireSeconds = 3600L,
                                endpoint = endpoint,
                                profileId = profile.id
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
                        OssPreviewKind.PDF -> withContext(Dispatchers.IO) {
                            val app = OssBrowserApp.instance
                            val cacheName = pdfObjectPreviewCacheName(profile.id, bucket, item)
                            val target = File(app.cacheDir, "pdf/$cacheName")
                            target.parentFile?.mkdirs()
                            if (!canReusePdfObjectPreviewCache(item, target.length())) {
                                // 每次预览使用独立半成品；旧请求即使晚返回也不能被新预览当成完整缓存。
                                val partial = File(target.parentFile, "$cacheName.$myGeneration.part")
                                try {
                                    service.downloadToFile(
                                        bucket = bucket,
                                        key = item.key,
                                        target = partial,
                                        endpoint = endpoint,
                                        profileId = profile.id,
                                        onProgress = { read, total ->
                                            _state.update { current ->
                                                val loading = current.previewState
                                                if (myGeneration == previewGeneration &&
                                                    loading is PreviewState.Loading && loading.key == item.key
                                                ) {
                                                    current.copy(previewState = loading.copy(
                                                        totalBytes = if (total > 0) total else loading.totalBytes,
                                                        downloadedBytes = read
                                                    ))
                                                } else current
                                            }
                                        }
                                    )
                                    currentCoroutineContext().ensureActive()
                                    if (myGeneration != previewGeneration) throw CancellationException("预览已过期")
                                    check(partial.renameTo(target)) { "无法保存完整 PDF 缓存" }
                                } finally {
                                    partial.delete()
                                }
                            }
                            PreviewState.Pdf(
                                key = item.key,
                                name = item.displayName,
                                filePath = target.absolutePath
                            )
                        }
                        else -> null
                    }
                }
            }.onSuccess { st ->
                if (myGeneration != previewGeneration) return@onSuccess
                if (st != null) _state.value = _state.value.copy(previewState = st)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (myGeneration != previewGeneration) return@onFailure
                Log.e(
                    TAG,
                    "Preview failed: stage=local-preview, bucket=$bucket, key=${item.key}, " +
                        "kind=$kind, objectSize=${item.size}, errorType=${e.javaClass.name}, " +
                        "message=${e.message}",
                    e
                )
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

    /** 关闭时先废弃代次，再取消下载/解析，旧结果不能重新打开弹窗。 */
    fun closePreview() {
        previewGeneration++
        previewJob?.cancel()
        lastPreviewItem = null
        _state.value = _state.value.copy(previewState = null)
    }

    /**
     * 按需查询当前 Bucket 的自定义域名。
     *
     * 只有用户选择“公开链接”时才调用，避免签名链接流程额外要求 `oss:ListCname` 权限。
     * 查询失败只写入可恢复的提示态，OSS 默认公网域名仍可继续生成公开链接。
     */
    fun loadCustomDomains() {
        val current = _state.value
        val bucket = current.currentBucket ?: return
        val endpoint = current.currentBucketEndpoint
        if (current.activeProfile?.storageType == StorageType.AWS_S3) {
            // S3 没有 OSS ListCname API；仍沿用同一分享弹窗和默认公开地址。
            _state.value = current.copy(customDomainLookup = CustomDomainLookupState(loaded = true))
            return
        }
        if (current.customDomainLookup.loading || current.customDomainLookup.loaded) return
        _state.value = current.copy(customDomainLookup = CustomDomainLookupState(loading = true))
        customDomainsJob = viewModelScope.launch {
            runCatching { service.listCnames(bucket = bucket, endpoint = endpoint) }
                .onSuccess { domains ->
                    if (_state.value.currentBucket != bucket) return@onSuccess
                    _state.value = _state.value.copy(
                        customDomainLookup = CustomDomainLookupState(
                            loaded = true,
                            domains = domains,
                            warning = if (domains.isEmpty()) str(R.string.share_cname_empty) else null
                        )
                    )
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (_state.value.currentBucket != bucket) return@onFailure
                    Log.e(
                        TAG,
                        "ListCname failed: bucket=$bucket, endpoint=${endpoint.orEmpty()}",
                        e
                    )
                    _state.value = _state.value.copy(
                        customDomainLookup = CustomDomainLookupState(
                            loaded = true,
                            // 详细异常只进入已脱敏日志；界面不展示 OSS 原始响应或签名字段。
                            warning = str(R.string.share_cname_failed)
                        )
                    )
                }
        }
    }

    /** 清除上一次分享弹窗的自定义域名查询结果，确保切换 Bucket 后不会复用旧域名。 */
    fun clearCustomDomains() {
        customDomainsJob?.cancel()
        customDomainsJob = null
        _state.value = _state.value.copy(customDomainLookup = CustomDomainLookupState())
    }

    /**
     * 生成用户选择的分享链接；成功后通过 [BrowserUiState.shareLinkResult] 交给 UI 复制或分享。
     *
     * 签名链接要求 [expireHours] 在 1~168 之间；公开链接没有过期时间，并可使用用户明确选择的
     * [customDomain]。本方法不检查 Object ACL，公开权限不足时链接访问方会收到 OSS 403。
     */
    fun generateShareUrl(
        key: String,
        mode: ShareLinkMode,
        expireHours: Int?,
        customDomain: OssCnameInfo?
    ) {
        val bucket = _state.value.currentBucket ?: return
        val endpoint = _state.value.currentBucketEndpoint
        if (mode == ShareLinkMode.SIGNED && (expireHours == null || expireHours !in 1..168)) {
            _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_presign_invalid_hours)))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(task = TaskState.Progress(str(R.string.task_presigning), 0, 0))
            runCatching {
                when (mode) {
                    ShareLinkMode.SIGNED -> service.presignUrl(
                        bucket = bucket,
                        key = key,
                        expireSeconds = requireNotNull(expireHours).toLong() * 3600L,
                        endpoint = endpoint
                    )
                    ShareLinkMode.PUBLIC -> service.publicObjectUrl(
                        bucket = bucket,
                        key = key,
                        endpoint = endpoint,
                        cname = customDomain
                    )
                }
            }.onSuccess { url ->
                _state.value = _state.value.copy(
                    task = null,
                    shareLinkResult = ShareLinkResult(
                        key = key,
                        url = url,
                        expireHours = expireHours.takeIf { mode == ShareLinkMode.SIGNED },
                        customDomain = customDomain?.domain
                    )
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Log.e(
                    TAG,
                    "Share link generation failed: bucket=$bucket, key=$key, mode=$mode, " +
                        "endpoint=${endpoint.orEmpty()}",
                    e
                )
                _state.value = _state.value.copy(
                    task = TaskState.Failed(str(R.string.task_presign_failed, e.localizedMessage ?: e.toString()))
                )
            }
        }
    }

    /** 关闭分享结果弹窗并清除其中的 URL。 */
    fun clearShareLinkResult() {
        _state.value = _state.value.copy(shareLinkResult = null)
    }

    fun move(sourceKey: String, destPrefix: String, newName: String?) {
        val snapshot = _state.value
        val profileId = snapshot.activeProfile?.id ?: return
        val bucket = snapshot.currentBucket ?: return
        val endpoint = snapshot.currentBucketEndpoint
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
            runCatching { service.move(bucket, sourceKey, destKey, endpoint = endpoint, profileId = profileId) }
                .onSuccess {
                    _state.value = _state.value.copy(task = TaskState.Done(str(R.string.task_move_done, destKey)))
                    refreshChangedObjectDirectories(
                        profileId, bucket, setOf(objectParentPrefix(sourceKey), objectParentPrefix(destKey)), isDir
                    )
                }
                .onFailure { e ->
                    refreshChangedObjectDirectories(
                        profileId, bucket, setOf(objectParentPrefix(sourceKey), objectParentPrefix(destKey)), isDir
                    )
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(task = TaskState.Failed(str(R.string.task_move_failed, e.localizedMessage ?: e.toString())))
                }
        }
    }

    /**
     * 普通写操作仅失效受影响父目录；递归目录变更还涉及后代缓存，在 IO 一次清理元数据缓存。
     * 请求完成时若用户已经切到其他账号或无关目录，不为了旧操作重复读取当前页面。
     */
    private suspend fun refreshChangedObjectDirectories(
        profileId: String, bucket: String, prefixes: Set<String>, recursive: Boolean = false
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            if (recursive) listCache.clear()
            else prefixes.forEach { prefix -> listCache.invalidateList(profileId, bucket, prefix) }
        }
        val current = _state.value
        if (current.activeProfile?.id == profileId && current.currentBucket == bucket &&
            (recursive || current.prefix in prefixes)
        ) loadCurrent(forceNetwork = true)
    }

    fun clearTask() {
        _state.value = _state.value.copy(task = null)
    }

    companion object {
        /** Android 日志标签；异常日志同时携带 Bucket、Object key、模式和 endpoint。 */
        private const val TAG = "BrowserViewModel"

        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = OssBrowserApp.instance
                val service = OssService(app.ossClientProvider)
                return BrowserViewModel(
                    repository = app.profileRepository,
                    service = service,
                    settingsRepo = app.settingsRepository,
                    listCache = app.ossListCache,
                    uploadRepository = app.uploadRepository
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
    /** 分享链接生成结果；非空时 UI 展示复制/分享弹窗。 */
    val shareLinkResult: ShareLinkResult? = null,
    /** 当前分享弹窗的自定义域名查询状态。 */
    val customDomainLookup: CustomDomainLookupState = CustomDomainLookupState(),
    val previewState: PreviewState? = null,
    /** 路径跳转/定位时需要高亮并滚动到的对象 key；一次性消费。 */
    val highlightKey: String? = null,
    /** 下一页 marker；null 表示已无更多。 */
    val nextMarker: String? = null,
    /** 是否正在加载下一页（与 [loading] 首次加载区分开）。 */
    val loadingMore: Boolean = false
)

/** 用户选择的分享链接类型。 */
enum class ShareLinkMode {
    /** 带有效期和 OSS 签名参数的临时链接。 */
    SIGNED,

    /** 不带签名或链接过期参数、依赖 Object 匿名读取权限的公开链接。 */
    PUBLIC
}

/**
 * 自定义域名查询状态。
 *
 * @property loading 是否正在请求 ListCname。
 * @property loaded 是否已经完成过请求，包括成功、无域名和失败。
 * @property domains OSS 返回的已启用自定义域名。
 * @property warning 查询失败或没有可用域名时的非阻断提示。
 */
data class CustomDomainLookupState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val domains: List<OssCnameInfo> = emptyList(),
    val warning: String? = null
)

/**
 * 已生成的分享链接。
 *
 * @property key Object key。
 * @property url 可复制或交给系统分享的完整 URL。
 * @property expireHours 签名链接的有效小时数；公开链接为 `null`。
 * @property customDomain 公开链接使用的自定义域名；默认 OSS 域名或签名链接为 `null`。
 */
data class ShareLinkResult(
    val key: String,
    val url: String,
    val expireHours: Int?,
    val customDomain: String?
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

    /** HTML 源码；UI 默认静态渲染，完整网页能力必须由用户在当前会话显式开启。 */
    data class Html(
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

    /** DOCX 预览：应用内解析得到的段落列表，UI 以排版方式直接渲染。 */
    data class Docx(
        override val key: String,
        override val name: String,
        val paragraphs: List<String>
    ) : PreviewState()

    /** XLSX 预览：按工作表保存有限行列的单元格展示值。 */
    data class Spreadsheet(
        override val key: String,
        override val name: String,
        val sheets: List<SpreadsheetSheetPreview>
    ) : PreviewState()

    /** PPTX 预览：按幻灯片保存本地提取的文字内容。 */
    data class Presentation(
        override val key: String,
        override val name: String,
        val slides: List<PresentationSlidePreview>
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
