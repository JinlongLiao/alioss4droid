package io.github.jinlongliao.ossbrowser.upload

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 每任务 IO 准备阶段：先原子开始，再解析并保存真实名称，最后返回可用于构造远端 key 的快照。
 * 不运行于串行命令消费者；慢文档提供方不会阻塞其他入队/取消命令。
 * 解析或保存失败由服务保留可重试终态；任一边界发现取消均不得返回占位名用于上传。
 *
 * @param resolveDisplayName 文档提供方名称读取，需响应协程取消；旧任务不调用。
 * @return 已持久化的 RUNNING 快照，名称解析标记已清除。
 */
internal suspend fun prepareUploadTask(
    task: UploadTask,
    repository: UploadRepository,
    resolveDisplayName: suspend () -> String?
): UploadTask = withContext(Dispatchers.IO) {
    var current = withContext(NonCancellable) { repository.startUploadTask(task) }
        ?: throw CancellationException("上传任务已取消或不再排队: ${task.id}")
    currentCoroutineContext().ensureActive()
    if (current.resolveDisplayName) {
        val name = resolveDisplayName() ?: current.name
        currentCoroutineContext().ensureActive()
        current = withContext(NonCancellable) { repository.resolveUploadTaskDisplayName(current, name) }
            ?: throw CancellationException("上传名称解析完成前任务已取消: ${task.id}")
    }
    currentCoroutineContext().ensureActive()
    current
}
