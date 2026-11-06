package io.github.jinlongliao.ossbrowser.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.jinlongliao.ossbrowser.MainActivity
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.oss.OssService
import io.github.jinlongliao.ossbrowser.util.FileUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户选择文件后启动的 dataSync 前台服务；串行执行队列，页面销毁不取消上传。
 * 不自动重启已中断请求：远端可能已收到对象，由用户主动恢复分片或重传小文件。
 * @see <a href="https://developer.android.com/develop/background-work/services/fgs/service-types">前台服务类型</a>
 */
class UploadService : Service() {
    /** 服务生命周期内的执行域；文档复制在 IO 线程运行，网络等待支持取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** 先前台启动，再由单消费者 IO 队列依次持久化和调度命令。 */
    private lateinit var uploadCommands: UploadCommandQueue
    /** 尚未完成持久化/调度的命令数，只在 Main 读写，防止空队列时过早停止服务。 */
    private var pendingCommands = 0
    /** 最近收到的系统启动代次，停止时不能误删系统中尚未投递的新命令。 */
    private var latestStartId = 0
    /** 服务停止后仍需完成已接收命令的中断记录；IO 消费者通过 volatile 观察。 */
    @Volatile
    private var destroyed = false
    /** 防止同一目标的多个文件同时写入，也控制临时副本的磁盘占用。 */
    private val queueMutex = Mutex()
    /** 已接收任务及取消句柄，阻止重复请求。仅主线程访问。 */
    private val uploadJobs = mutableMapOf<String, Job>()
    /** 私有持久化快照与 SDK 检查点，只在 SDK 退出后清理。 */
    private val uploadFiles by lazy { UploadFiles(File(noBackupFilesDir, "uploads")) }
    /** 上传期间保持 CPU 可用，避免熄屏后临时文件复制或网络发送被挂起；服务销毁时释放。 */
    private var uploadWakeLock: PowerManager.WakeLock? = null
    /** 全局状态源不依赖任一 Activity。 */
    private val repository get() = OssBrowserApp.instance.uploadRepository

    /** 立即建立通知渠道，满足 startForegroundService 的启动时限。 */
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("uploads", getString(R.string.upload_tasks), NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(1001, buildUploadNotification(null))
        uploadWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:uploads")
            .apply { acquire(6 * 60 * 60 * 1000L) }
        uploadCommands = UploadCommandQueue(
            awaitHistory = { repository.awaitLoaded() },
            processCommand = { processUploadCommand(it) },
            onFailure = { command, exception ->
                val task = (command as? UploadCommand.Enqueue)?.task
                val taskId = task?.id ?: (command as UploadCommand.Cancel).taskId
                Log.e(
                    "UploadService", "上传命令处理失败 task=$taskId bucket=${task?.bucket} key=${task?.prefix}${task?.name}",
                    exception
                )
                withContext(Dispatchers.Main.immediate) {
                    if (!destroyed) showUploadServiceFailure(this@UploadService, exception)
                }
            },
            onProcessed = {
                withContext(Dispatchers.Main.immediate) {
                    pendingCommands--
                    stopUploadServiceIfIdle()
                }
            }
        )
    }

    /** 先由系统进入前台服务，再将小型内存命令投递到 IO 队列；这里不读取或写入历史文件。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val command = try {
            if (intent?.action == "cancelUpload") {
                intent.getStringExtra("taskId")?.let { UploadCommand.Cancel(it) }
            } else {
                intent?.getStringExtra("task")?.let { UploadCommand.Enqueue(Json.decodeFromString<UploadTask>(it)) }
            }
        } catch (exception: Exception) {
            Log.e("UploadService", "解析上传命令失败 action=${intent?.action}", exception)
            showUploadServiceFailure(this, exception)
            null
        }
        if (command != null) {
            pendingCommands++
            if (!uploadCommands.enqueue(command)) {
                pendingCommands--
                showUploadServiceFailure(this, IllegalStateException("上传服务正在停止，请重新提交"))
            }
        }
        stopUploadServiceIfIdle()
        return START_NOT_STICKY
    }

    /** 串行 IO 命令处理，仓库状态先持久化，再由 Main 注册/取消 Job；Map 的线程所有权不变。 */
    private suspend fun processUploadCommand(command: UploadCommand) {
        when (command) {
            is UploadCommand.Enqueue -> {
                val task = command.task
                if (repository.tasks.value.firstOrNull { it.id == task.id }?.status?.isUploadActive() == true) return
                // 终态刚保存时旧 Job 可能仍在 Main 收尾；等其退出再重试，避免旧 finally 覆盖新 QUEUED。
                val previous = withContext(Dispatchers.Main.immediate) { uploadJobs[task.id] }
                previous?.join()
                if (!repository.queueUploadTask(task)) return
                val queued = repository.tasks.value.first { it.id == task.id }
                val started = withContext(Dispatchers.Main.immediate) {
                    if (destroyed) false else {
                        launchQueuedUpload(queued)
                        true
                    }
                }
                if (!started) repository.updateUploadTask(queued.copy(status = UploadStatus.INTERRUPTED))
            }
            is UploadCommand.Cancel -> {
                if (!repository.requestUploadCancellation(command.taskId)) return
                val cancelled = withContext(Dispatchers.Main.immediate) {
                    uploadJobs[command.taskId]?.also { it.cancel() } != null
                }
                if (!cancelled) repository.finishIdleUploadCancellation(command.taskId)
            }
        }
    }

    /** Main 上注册执行句柄；排队等待及 SDK 退出都属于同一任务，终态落盘完成后才移除句柄。 */
    private fun launchQueuedUpload(task: UploadTask) {
        check(task.id !in uploadJobs) { "上传任务重复执行: ${task.id}" }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                queueMutex.withLock { performUploadTask(task) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // 持久化失败不能将 SupervisorJob 的未处理异常升级为整个应用崩溃。
                Log.e("UploadService", "上传执行或状态保存失败 task=${task.id}", exception)
                if (!destroyed) showUploadServiceFailure(this@UploadService, exception)
            } finally {
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) {
                            val latest = repository.tasks.value.firstOrNull { it.id == task.id }
                            if (latest?.attemptId == task.attemptId && latest.status.isUploadActive()) {
                                repository.updateUploadTask(latest.copy(
                                    status = if (latest.status == UploadStatus.CANCELLING) {
                                        UploadStatus.CANCELLED
                                    } else UploadStatus.INTERRUPTED
                                ))
                            }
                        }
                    } catch (exception: Exception) {
                        Log.e("UploadService", "上传任务收尾保存失败 task=${task.id}", exception)
                        if (!destroyed) showUploadServiceFailure(this@UploadService, exception)
                    } finally {
                        // 整个收尾均处于 NonCancellable，IO 返回取消或保存失败不能跳过 Main Map 清理。
                        uploadJobs.remove(task.id)
                        stopUploadServiceIfIdle()
                    }
                }
            }
        }
        uploadJobs[task.id] = job
        job.start()
    }

    /** 只在 Main 判断生命周期；命令仍在 IO 落盘或有任务正在收尾时不能停止服务。 */
    private fun stopUploadServiceIfIdle() {
        if (!destroyed && pendingCommands == 0 && uploadJobs.isEmpty() && stopSelfResult(latestStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /**
     * 每任务独立 IO 阶段解析并持久化真实名称，再准备不可变文件快照和远端 key。
     * 失败保留文件与断点；取消不会撤销已确认成功，迟到准备结果不能撤销取消。
     */
    private suspend fun performUploadTask(task: UploadTask) {
        val executionContext = currentCoroutineContext()
        var current = task
        try {
            current = prepareUploadTask(task, repository) {
                FileUtils.resolveDisplayName(this@UploadService, task.uri.toUri())
            }
            executionContext.ensureActive()
            getSystemService(NotificationManager::class.java).notify(1001, buildUploadNotification(current))
            val source = withContext(Dispatchers.IO) {
                uploadFiles.prepareUploadFile(task.id) {
                    requireNotNull(contentResolver.openInputStream(task.uri.toUri())) { "无法读取上传源文件" }
                }
            }
            var percent = -1
            OssService(OssBrowserApp.instance.ossClientProvider).upload(
                bucket = task.bucket,
                key = current.prefix + current.name,
                source = source,
                records = File(uploadFiles.taskDirectory(task.id), "records"),
                profileId = task.profileId,
                endpoint = task.endpoint,
                storageType = task.storageType,
                s3Region = task.s3Region,
                s3ForcePathStyle = task.s3ForcePathStyle
            ) { sent, total ->
                executionContext.ensureActive()
                val nextPercent = if (total > 0) (sent.toDouble() / total * 100).toInt() else 0
                if (nextPercent != percent) {
                    percent = nextPercent
                    current = current.copy(sent = sent, total = total)
                    repository.updateUploadProgress(current)
                    getSystemService(NotificationManager::class.java).notify(1001, buildUploadNotification(current))
                }
            }
            // 先失效真实上传目录的缓存，再发布成功事件；页面不活跃或已切目录也不能遗留旧列表。
            // SDK 已确认成功后即使收到取消也必须保存成功，不能在这里再次检查取消而误报取消。
            withContext(NonCancellable + Dispatchers.IO) {
                OssBrowserApp.instance.ossListCache.invalidateList(task.profileId, task.bucket, task.prefix)
                repository.updateUploadTask(current.copy(
                    status = UploadStatus.SUCCEEDED, sent = source.length(), total = source.length()
                ))
            }
            // 上传已成功，缓存清理失败不应将远端成功错误地显示为失败。
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    uploadFiles.deleteUploadFiles(task.id)
                } catch (exception: Exception) {
                    Log.e("UploadService", "上传完成但缓存清理失败 task=${task.id}", exception)
                }
            }
        } catch (exception: Exception) {
            val latest = repository.tasks.value.firstOrNull { it.id == task.id }
            // 清理成功对象的缓存时即使服务被取消，也不能将远端成功改成中断。
            if (latest?.status == UploadStatus.SUCCEEDED) return
            Log.e(
                "UploadService", "上传失败或取消 task=${task.id} bucket=${task.bucket} key=${current.prefix}${current.name}",
                exception
            )
            withContext(NonCancellable + Dispatchers.IO) {
                val currentState = repository.tasks.value.firstOrNull { it.id == task.id }
                // 解析名可能已保存，但 IO 返回 Main 时收到取消；以同代最新快照收尾，保留真实名称。
                val terminalBase = currentState?.takeIf { it.attemptId == task.attemptId } ?: current
                repository.updateUploadTask(terminalBase.copy(
                    status = when {
                        currentState?.status == UploadStatus.CANCELLING -> UploadStatus.CANCELLED
                        !executionContext.isActive -> UploadStatus.INTERRUPTED
                        else -> UploadStatus.FAILED
                    },
                    error = if (exception is CancellationException) null else exception.localizedMessage
                ))
            }
            if (exception is CancellationException) throw exception
        }
    }

    /** 点击通知回到应用；显示当前文件名与字节进度，准备阶段使用不确定进度。 */
    private fun buildUploadNotification(task: UploadTask?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val percent = if (task != null && task.total > 0) {
            (task.sent.toDouble() / task.total * 100).toInt()
        } else 0
        return Notification.Builder(this, "uploads")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.upload_tasks))
            .setContentText(task?.let { "${it.name} · $percent%" } ?: getString(R.string.upload_queued))
            .setProgress(100, percent, task == null || task.total <= 0)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** 系统超时后停止服务，保留未完成快照供下次启动识别为中断。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        beginUploadShutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 上传无需绑定，页面通过 StateFlow 观察任务。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 服务结束时释放协程域。 */
    override fun onDestroy() {
        beginUploadShutdown()
        uploadWakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    /** 先禁止新执行者，再关闭命令入口和取消已有上传；IO 消费者继续排空已接受命令的中断记录。 */
    private fun beginUploadShutdown() {
        destroyed = true
        uploadCommands.close()
        scope.cancel()
    }

    companion object {
        /** 用户动作中立即启动 FGS，取消记录和 Job 取消在服务的串行 IO 队列中依次发生。 */
        fun cancelUploadTask(context: Context, task: UploadTask) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, UploadService::class.java).setAction("cancelUpload").putExtra("taskId", task.id)
                )
            } catch (exception: Exception) {
                Log.e("UploadService", "取消上传服务启动失败 task=${task.id}", exception)
                showUploadServiceFailure(context, exception)
            }
        }

        /** 用户动作中先启动 FGS；任务参数不含凭据，历史在服务 IO 队列落盘后才显示为已排队。 */
        fun enqueueUploadTask(context: Context, task: UploadTask) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, UploadService::class.java).putExtra("task", Json.encodeToString(task))
                )
            } catch (exception: Exception) {
                Log.e("UploadService", "启动上传服务失败 task=${task.id}", exception)
                // 未接受的请求不假装排队成功，明确反馈用户重试；Main 不同步获取仓库持久化锁。
                showUploadServiceFailure(context, exception)
            }
        }

        /** 服务未能接受命令时显示明确失败，不在主线程尝试阻塞式持久化。 */
        private fun showUploadServiceFailure(context: Context, exception: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.task_upload_failed, exception.localizedMessage ?: exception.toString()),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
