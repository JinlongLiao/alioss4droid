package io.github.jinlongliao.ossbrowser.upload

import android.util.AtomicFile
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 上传历史持久化边界，调用方在 IO 线程串行调用；返回即完成原子提交，保存失败必须抛出。 */
internal interface UploadTaskStore {
    /** 读取已提交的原始历史并合并同代次进度，进程中断转换由仓库随后完成。 */
    @WorkerThread
    fun loadUploadTasks(): List<UploadTask>

    /** 原子提交快照或进度，不能仅安排无等待的后台写入。 */
    @WorkerThread
    fun saveUploadTasks(tasks: List<UploadTask>)
}

/**
 * 保留旧 JSON 历史及 AtomicFile 备份读取；运行中仅字节变化时保存单任务 journal。
 * 队列、取消和终态仍完整提交历史后才清理已吸收的 journal；每次重试代次阻止旧进度复活。
 * @param historyFile 不参与系统备份的原上传历史文件。
 */
internal class AtomicUploadTaskStore(private val historyFile: File) : UploadTaskStore {
    /** 兼容旧版本 AtomicFile 的备份恢复，完整历史 JSON 格式保持不变。 */
    private val legacyFile = AtomicFile(historyFile)
    /** 每个运行任务的独立原子进度；本应用串行上传，不随终态历史条数放大写入量。 */
    internal val progressDirectory = File(historyFile.parentFile, "upload_progress")
    /** 新增可选字段不破坏旧历史。 */
    private val json = Json { ignoreUnknownKeys = true }
    /** 上次已提交的合并状态，用于只识别纯字节变化，不把状态/目标变化误写为进度。 */
    private var committedTasks: List<UploadTask>? = null
    /** 最近完整主快照的任务值，journal 摘要必须与该基线一致，而不是只比较运行代次。 */
    private var committedBaseTasks: List<UploadTask> = emptyList()

    /** 先合并同代次 RUNNING 进度，再交由仓库转换为进程中断，保留最后已提交字节数。 */
    override fun loadUploadTasks(): List<UploadTask> {
        val base = try {
            json.decodeFromString<List<UploadTask>>(legacyFile.openRead().bufferedReader().use { it.readText() })
        } catch (exception: Exception) {
            if (historyFile.exists() || exception !is FileNotFoundException) {
                Log.e("UploadRepository", "读取上传记录失败 path=$historyFile", exception)
            }
            emptyList()
        }
        val byId = HashMap<String, UploadTask>(base.size).apply { base.forEach { put(it.id, it) } }
        progressDirectory.listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
            try {
                val journal = json.decodeFromString<UploadProgressJournal>(file.readText())
                val progress = journal.task
                check(file.nameWithoutExtension == progress.id) { "上传进度记录 ID 与文件名不一致" }
                val original = byId[progress.id]
                if (original != null && canMergeUploadProgress(original, progress) &&
                    journal.baseFingerprint == uploadSnapshotFingerprint(original)) {
                    byId[progress.id] = progress
                }
            } catch (exception: Exception) {
                // 半写文件不以 .json 结尾；损坏的独立进度不影响原历史，保留完整异常供定位。
                Log.e("UploadRepository", "读取上传进度失败 path=$file", exception)
            }
        }
        committedBaseTasks = base
        return base.map { byId.getValue(it.id) }.also { committedTasks = it }
    }

    /** 百分比进度原子写单任务；其他变化完整提交历史，提交成功后才吸收旧 journal。 */
    override fun saveUploadTasks(tasks: List<UploadTask>) {
        val previous = committedTasks
        val progress = previous?.let { changedUploadProgress(it, tasks) }
        if (progress != null) {
            val baseline = committedBaseTasks.first { it.id == progress.id }
            val journal = UploadProgressJournal(uploadSnapshotFingerprint(baseline), progress)
            writeAtomicUploadFile(progressFile(progress.id), json.encodeToString(journal).toByteArray(Charsets.UTF_8))
        } else {
            writeAtomicUploadFile(historyFile, json.encodeToString(tasks).toByteArray(Charsets.UTF_8))
            committedBaseTasks = tasks
            // 完整快照已包含全部进度；删除失败也不能改变已确认的持久化结果。
            progressDirectory.listFiles().orEmpty().forEach { file ->
                if (file.isFile && !file.delete()) Log.w("UploadRepository", "清理已吸收上传进度失败 path=$file")
            }
        }
        committedTasks = tasks
    }

    /** 使用任务 ID 作为文件名，拒绝历史中非法 ID 导致目录穿越。 */
    internal fun progressFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "非法上传任务 ID" }
        return File(progressDirectory, "$id.json")
    }

    /** 只接纳同代次、同目标且不倒退的纯字节进度，不能撤销终态或套用到新重试。 */
    private fun canMergeUploadProgress(original: UploadTask, progress: UploadTask): Boolean =
        original.status == UploadStatus.RUNNING && progress.status == UploadStatus.RUNNING &&
            original.attemptId.isNotBlank() && original.attemptId == progress.attemptId &&
            progress.sent >= original.sent && progress.total >= progress.sent &&
            original.copy(sent = progress.sent, total = progress.total) == progress

    /** 同一主快照值具有稳定摘要；百分比写入只编码一个任务，不随历史规模增长。 */
    private fun uploadSnapshotFingerprint(task: UploadTask): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(task).toByteArray(Charsets.UTF_8))
        .let { Base64.getEncoder().encodeToString(it) }

    /** 同一列表仅一个 RUNNING 记录的字节数变化才可走 journal，状态和顺序变化必须完整提交。 */
    private fun changedUploadProgress(before: List<UploadTask>, after: List<UploadTask>): UploadTask? {
        if (before.size != after.size) return null
        var changed: UploadTask? = null
        before.indices.forEach { index ->
            if (before[index] != after[index]) {
                if (changed != null || !canMergeUploadProgress(before[index], after[index])) return null
                changed = after[index]
            }
        }
        return changed
    }
}

/**
 * 明确等待 fd.sync 后原子替换同目录文件；错误向上传播，禁止提前发布状态。
 * .commit 半成品永不参与恢复；旧完整文件在替换成功前保持可读。
 * Android 最低 API 31，已支持 Java NIO 的同目录原子移动，不用静默非原子回退。
 * @param file 目标历史或 journal 文件。
 * @param bytes 本次完整编码内容。
 * @see java.nio.file.Files.move
 */
internal fun writeAtomicUploadFile(file: File, bytes: ByteArray) {
    val parent = requireNotNull(file.parentFile)
    check(parent.mkdirs() || parent.isDirectory) { "无法创建上传记录目录: $parent" }
    val partial = File(parent, file.name + ".commit")
    try {
        partial.outputStream().use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(partial.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        if (partial.isFile) partial.delete()
    }
}
