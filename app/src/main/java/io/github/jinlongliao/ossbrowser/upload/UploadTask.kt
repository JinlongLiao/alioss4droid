package io.github.jinlongliao.ossbrowser.upload

import kotlinx.serialization.Serializable
import io.github.jinlongliao.ossbrowser.data.StorageType

/** 单个文件的持久化上传快照；只保存配置 ID，不保存凭据。字节数以 SDK 回调为准。 */
@Serializable
data class UploadTask(
    /** 全局唯一任务 ID，进度只能更新该 ID 对应的记录。 */
    val id: String,
    /** 提交时的账号 ID，重试时禁止改用当前激活账号。 */
    val profileId: String,
    /** 提交时固定的目标 bucket。 */
    val bucket: String,
    /** 提交时固定的目录前缀。 */
    val prefix: String,
    /** 原始文档 URI；重试依赖文档提供方仍允许读取。 */
    val uri: String,
    /** 上传对象名称；新文件准备期先显示占位，真实名称落盘后固定，不随页面状态变化。 */
    val name: String,
    /** 提交时的区域 endpoint。 */
    val endpoint: String?,
    /** 等待、上传中及终态；进程重建后未完成任务标记为中断。 */
    val status: UploadStatus = UploadStatus.QUEUED,
    /** 已发送字节数，初始为零。 */
    val sent: Long = 0,
    /** 总字节数，零表示尚未知晓。 */
    val total: Long = 0,
    /** 失败原因，成功和等待时为空。 */
    val error: String? = null,
    /** 提交时的协议，禁止后续编辑配置让旧任务切换上传协议。 */
    val storageType: StorageType = StorageType.OSS,
    /** 提交时的 S3 签名区域，OSS 留空。 */
    val s3Region: String = "",
    /** 提交时的 S3 寻址方式。 */
    val s3ForcePathStyle: Boolean = false,
    /** 每次入队/重试的新代次，旧 journal 只允许覆盖同代次 RUNNING；旧记录默认为空。 */
    val attemptId: String = "",
    /** 新文件在服务 IO 准备阶段解析真实名称；旧任务默认 false，沿用已保存名称。 */
    val resolveDisplayName: Boolean = false
)

/** 上传生命周期；中断由用户确认后恢复分片上传，避免隐式覆盖远端对象。 */
@Serializable
enum class UploadStatus {
    /** 等待串行队列调度。 */
    QUEUED,
    /** 正在准备快照或传输文件。 */
    RUNNING,
    /** SDK 确认远端对象上传成功。 */
    SUCCEEDED,
    /** 准备或传输失败，保留本地快照和断点。 */
    FAILED,
    /** 进程或服务中断，可由用户恢复。 */
    INTERRUPTED,
    /** 已请求取消，底层请求尚未收尾，禁止清理和重试。 */
    CANCELLING,
    /** 请求已停止，保留断点供用户继续。 */
    CANCELLED
}

/** 按唯一 ID 合并 SDK 更新，其他任务保持原始快照与顺序。 */
fun mergeUploadTask(tasks: List<UploadTask>, updated: UploadTask): List<UploadTask> =
    if (tasks.any { it.id == updated.id }) {
        tasks.map { if (it.id == updated.id) updated else it }
    } else tasks + updated

/** 仅在进程初始化调用：无执行者的等待和运行任务转为中断，终态和字节进度保留。 */
fun recoverUploadTasks(tasks: List<UploadTask>): List<UploadTask> = tasks.map { task ->
    if (task.status in setOf(UploadStatus.QUEUED, UploadStatus.RUNNING, UploadStatus.CANCELLING)) {
        task.copy(status = UploadStatus.INTERRUPTED)
    } else task
}

/** 正在执行或等待收尾的任务不能重试和删除，否则旧回调可能覆盖新状态。 */
fun UploadStatus.isUploadActive(): Boolean =
    this in setOf(UploadStatus.QUEUED, UploadStatus.RUNNING, UploadStatus.CANCELLING)

/** 只选择终态记录供历史清理；上传、排队和取消收尾中的任务始终保留。 */
fun selectUploadHistory(tasks: List<UploadTask>): List<UploadTask> = tasks.filterNot { it.status.isUploadActive() }
