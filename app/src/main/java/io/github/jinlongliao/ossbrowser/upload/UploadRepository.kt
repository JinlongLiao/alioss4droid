package io.github.jinlongliao.ossbrowser.upload

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 应用级上传记录。初始读盘在后台执行，所有命令等待 [awaitLoaded] 后才读取或修改历史。
 * 写入仍串行、原子且先提交后发布；UI 只读取 StateFlow，不获取持久化锁。
 */
class UploadRepository internal constructor(
    /** 持久化接口，在 IO 上保存完整历史并等待原子提交。 */
    private val uploadTaskStore: UploadTaskStore,
    /** 任务独立的快照与断点文件。 */
    private val uploadFiles: UploadFiles,
    /** 初始化、命令与历史清理使用的 IO 调度器。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** 创建仓库不在调用线程读盘，旧文件位置和格式保持不变。 */
    constructor(context: Context) : this(
        AtomicUploadTaskStore(File(context.noBackupFilesDir, "upload_tasks.json")),
        UploadFiles(File(context.noBackupFilesDir, "uploads"))
    )

    /** 正在清理的终态任务禁止重新入队，直到批量持久化提交结束才释放。 */
    private val cleaningIds = mutableSetOf<String>()
    /** 初始化期间为空；业务判断必须先等待门闩，界面结合 isLoaded 展示加载态。 */
    private val mutableTasks = MutableStateFlow<List<UploadTask>>(emptyList())
    /** 是否已完成初始历史读取，避免把暂时空值解释为历史丢失。 */
    private val mutableIsLoaded = MutableStateFlow(false)
    /** 仅初始化历史的成功 ID，后续极快新完成不能被误收入启动基线。 */
    private var initializedCompletedIds: Set<String> = emptySet()
    /** awaitLoaded 后读取初始化历史的成功集合，不扫描可能已包含新任务的实时状态。 */
    val initialCompletedUploadIds: Set<String> get() = initializedCompletedIds
    /** 页面只订阅状态，不触发磁盘读取或等待仓库锁。 */
    val tasks = mutableTasks.asStateFlow()
    /** 历史读取状态，供界面显示加载提示。 */
    val isLoaded = mutableIsLoaded.asStateFlow()
    /** 应用级初始化只执行一次，服务和页面共享同一个完成门闩。 */
    private val initialization = CoroutineScope(SupervisorJob() + ioDispatcher).async {
        val loaded = recoverUploadTasks(uploadTaskStore.loadUploadTasks())
        initializedCompletedIds = loaded.filter { it.status == UploadStatus.SUCCEEDED }.map { it.id }.toSet()
        mutableTasks.value = loaded
        mutableIsLoaded.value = true
    }

    /** 等待原历史加载完成；取消等待不会取消应用级读取，也不会使新命令覆盖尚未读取的旧历史。 */
    suspend fun awaitLoaded() {
        initialization.await()
    }

    /** 仅 IO 调用；防止遗漏门闩的写入在初始化期间覆盖旧文件。 */
    private fun checkUploadHistoryLoaded() {
        check(mutableIsLoaded.value) { "上传历史尚未加载完成" }
    }

    /** 按 ID 插入或替换一个任务；必须在 IO 线程且初始化完成后调用。 */
    @WorkerThread
    @Synchronized
    fun saveUploadTask(task: UploadTask) {
        checkUploadHistoryLoaded()
        persistUploadTasks(mergeUploadTask(mutableTasks.value, task))
    }

    /**
     * 更新已有同代次任务；迟到回调不能覆盖重试，也不能用 RUNNING 撤销已持久化的取消。
     * 已确认远端成功仍允许收尾，避免把实际成功误报为取消；调用方必须在 IO 线程。
     */
    @WorkerThread
    @Synchronized
    fun updateUploadTask(task: UploadTask) {
        checkUploadHistoryLoaded()
        val current = mutableTasks.value.firstOrNull { it.id == task.id } ?: return
        if (current.attemptId != task.attemptId) return
        if (current.status == UploadStatus.CANCELLING && task.status == UploadStatus.RUNNING) return
        saveUploadTask(task)
    }

    /** 原子开始同代次的排队任务；已取消或已收尾时不再创建执行者，返回值已完成持久化。 */
    @WorkerThread
    @Synchronized
    fun startUploadTask(task: UploadTask): UploadTask? {
        checkUploadHistoryLoaded()
        val current = mutableTasks.value.firstOrNull { it.id == task.id } ?: return null
        if (current.attemptId != task.attemptId || current.status != UploadStatus.QUEUED) return null
        return current.copy(status = UploadStatus.RUNNING, error = null).also { saveUploadTask(it) }
    }

    /**
     * 名称解析与取消可能交错：只更新同代次且仍运行的任务，落盘后才允许调用方构造远端 key。
     * 拒绝迟到解析结果时返回 null，服务结束准备阶段，不得使用提交时的占位名称上传。
     */
    @WorkerThread
    @Synchronized
    fun resolveUploadTaskDisplayName(task: UploadTask, name: String): UploadTask? {
        checkUploadHistoryLoaded()
        val current = mutableTasks.value.firstOrNull { it.id == task.id } ?: return null
        if (current.attemptId != task.attemptId || current.status != UploadStatus.RUNNING ||
            !current.resolveDisplayName
        ) return null
        return current.copy(name = name, resolveDisplayName = false).also { saveUploadTask(it) }
    }

    /** 一次删除仍处于终态的指定记录；新状态以提交时的最新历史为准。 */
    @WorkerThread
    @Synchronized
    fun removeUploadHistory(ids: Set<String>) {
        checkUploadHistoryLoaded()
        if (ids.isEmpty()) return
        persistUploadTasks(mutableTasks.value.filterNot { it.id in ids && !it.status.isUploadActive() })
    }

    /** 原子校验重试与清理互斥；保持断点进度，不重复排队活跃任务。 */
    @WorkerThread
    @Synchronized
    fun queueUploadTask(task: UploadTask): Boolean {
        checkUploadHistoryLoaded()
        val current = mutableTasks.value.firstOrNull { it.id == task.id }
        // 新选择的文件初态为 QUEUED；历史已清理后迟到的失败/取消重试命令不能重新创建旧记录。
        if (current == null && task.status != UploadStatus.QUEUED) return false
        if (task.id in cleaningIds || current?.status?.isUploadActive() == true) return false
        saveUploadTask((current ?: task).copy(
            status = UploadStatus.QUEUED, error = null, attemptId = UUID.randomUUID().toString()
        ))
        return true
    }

    /** 只有排队和正在上传能进入取消收尾，状态落盘后服务才请求取消底层任务。 */
    @WorkerThread
    @Synchronized
    fun requestUploadCancellation(id: String): Boolean {
        checkUploadHistoryLoaded()
        val task = mutableTasks.value.firstOrNull { it.id == id } ?: return false
        if (task.status !in setOf(UploadStatus.QUEUED, UploadStatus.RUNNING)) return false
        saveUploadTask(task.copy(status = UploadStatus.CANCELLING))
        return true
    }

    /** 取消命令无执行者时完成收尾；不能覆盖刚刚已确认的成功/失败。 */
    @WorkerThread
    @Synchronized
    fun finishIdleUploadCancellation(id: String) {
        checkUploadHistoryLoaded()
        val task = mutableTasks.value.firstOrNull { it.id == id } ?: return
        if (task.status == UploadStatus.CANCELLING) saveUploadTask(task.copy(status = UploadStatus.CANCELLED))
    }

    /** SDK 工作线程的进度不能撤销取消请求；保持现有每个百分点持久化语义。 */
    @WorkerThread
    @Synchronized
    fun updateUploadProgress(task: UploadTask) {
        checkUploadHistoryLoaded()
        val current = mutableTasks.value.firstOrNull { it.id == task.id } ?: return
        if (current.status == UploadStatus.RUNNING && current.attemptId == task.attemptId) saveUploadTask(task)
    }

    /**
     * 文件逐项清理，成功 ID 汇总后对最新历史只提交一次，避免 N 条历史触发 N 次全量 JSON/fsync。
     * cleaningIds 持有到批量提交结束；文件或提交失败时保留对应记录，允许再次清理。
     * @return 未能清理的任务数；不发送远端删除请求。
     */
    suspend fun clearUploadHistory(): Int = withContext(NonCancellable + ioDispatcher) {
        awaitLoaded()
        val candidates = synchronized(this@UploadRepository) {
            selectUploadHistory(mutableTasks.value).filter { it.id !in cleaningIds }
                .also { cleaningIds.addAll(it.map { task -> task.id }) }
        }
        val cleanedIds = HashSet<String>(candidates.size)
        var failures = 0
        try {
            candidates.forEach { task ->
                try {
                    uploadFiles.deleteUploadFiles(task.id)
                    cleanedIds.add(task.id)
                } catch (exception: Exception) {
                    failures++
                    Log.e("UploadRepository", "清理上传历史文件失败 task=${task.id}", exception)
                }
            }
            try {
                removeUploadHistory(cleanedIds)
            } catch (exception: Exception) {
                failures += cleanedIds.size
                Log.e("UploadRepository", "批量保存上传历史清理结果失败 taskIds=$cleanedIds", exception)
            }
        } finally {
            synchronized(this@UploadRepository) { cleaningIds.removeAll(candidates.map { it.id }.toSet()) }
        }
        failures
    }

    /** 保持锁内先原子提交再发布内存状态；磁盘失败不能提前显示成功或取消完成。 */
    @WorkerThread
    private fun persistUploadTasks(updated: List<UploadTask>) {
        uploadTaskStore.saveUploadTasks(updated)
        mutableTasks.value = updated
    }
}
