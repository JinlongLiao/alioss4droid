package io.github.jinlongliao.ossbrowser.upload

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 管理任务私有的不可变快照和 SDK 断点；重试复用同一路径，避免原始文档变化造成分片混合。
 * @param root 位于 noBackupFilesDir 下的上传目录，不参与系统备份。
 */
class UploadFiles(private val root: File) {
    /** 每任务独立目录；持久化 ID 必须是 UUID，拒绝路径穿越。
     * @param id 任务唯一 ID。
     * @return 任务文件目录，不隐式创建。
     */
    fun taskDirectory(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "非法上传任务 ID" }
        return File(root, id)
    }

    /**
     * 完整快照存在则直接复用；首次复制写入半成品，完成后重命名发布。
     * 每个缓冲块检查取消，取消或源读取失败时清理半成品，不生成可续传快照。
     * 调用方必须在 IO 线程运行，且同一任务不能并发准备或清理。
     * @param id 任务唯一 ID。
     * @param openSource 仅在没有完整快照时打开源文档，返回流由本方法关闭。
     * @return 已完整保存的快照。
     */
    suspend fun prepareUploadFile(id: String, openSource: () -> InputStream): File {
        val directory = taskDirectory(id).apply { check(mkdirs() || isDirectory) { "无法创建上传目录" } }
        val snapshot = File(directory, "source")
        if (snapshot.isFile) return snapshot
        // 完整快照不存在时，旧检查点不能与重新读取的文档混用。
        val records = File(directory, "records")
        check(!records.exists() || records.deleteRecursively()) { "无法清理失效的上传断点" }
        val partial = File(directory, "source.partial")
        try {
            openSource().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(snapshot)) { "无法保存上传文件快照" }
            return snapshot
        } finally {
            partial.delete()
        }
    }

    /** 删除本地快照与断点，不操作远端对象；删除失败时抛出异常，记录可保留供重试清理。
     * @param id 已停止执行的任务 ID。
     */
    fun deleteUploadFiles(id: String) {
        val directory = taskDirectory(id)
        check(!directory.exists() || directory.deleteRecursively()) { "无法清理上传文件: $id" }
    }
}
