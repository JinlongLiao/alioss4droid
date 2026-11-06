package io.github.jinlongliao.ossbrowser.upload

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 单消费者 IO 命令队列。关闭只拒绝新命令，已经接受的命令仍按顺序处理到落盘结束。
 * @param awaitHistory 仓库初始化门闩，初始化未完成不执行命令。
 * @param processCommand 处理单条命令；返回时持久化和调度已完成。
 * @param onFailure 命令失败后的诊断与用户反馈，不吞掉已确认的业务结果。
 * @param onProcessed 每条已接受命令完成时调用，服务在 Main 更新待处理计数。
 * @param dispatcher 消费者使用的 IO 调度器。
 */
internal class UploadCommandQueue(
    private val awaitHistory: suspend () -> Unit,
    private val processCommand: suspend (UploadCommand) -> Unit,
    private val onFailure: suspend (UploadCommand, Exception) -> Unit,
    private val onProcessed: suspend () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** 与上传 Job 域分离，服务销毁仍可将已接受任务记录为中断。 */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    /** 不丢弃用户已接受命令，不用 conflated 通道覆盖排队或取消请求。 */
    private val channel = Channel<UploadCommand>(Channel.UNLIMITED)
    /** 只存在一个消费者，保证初始化、入队及取消先后顺序。 */
    private val processor = scope.launch {
        try {
            for (command in channel) {
                try {
                    awaitHistory()
                    processCommand(command)
                } catch (exception: Exception) {
                    onFailure(command, exception)
                } finally {
                    onProcessed()
                }
            }
        } finally {
            scope.cancel()
        }
    }

    /** 返回 true 仅表示已接收，仓库只在持久化成功后发布排队状态。 */
    fun enqueue(command: UploadCommand): Boolean = channel.trySend(command).isSuccess

    /** 停止接受新命令并排空已接受命令，不中断正在提交的历史。 */
    fun close() {
        channel.close()
    }

    /** 等待关闭后的消费者退出，供生命周期及顺序回归核对收尾完成。 */
    suspend fun awaitClosed() {
        processor.join()
    }
}
