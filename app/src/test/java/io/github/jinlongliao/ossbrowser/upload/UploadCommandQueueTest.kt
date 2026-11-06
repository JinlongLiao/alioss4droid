package io.github.jinlongliao.ossbrowser.upload

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证服务实际使用的串行命令队列，不调用网络或任何真实对象接口。 */
class UploadCommandQueueTest {
    /** 初始化完成前不执行命令，关闭后排空已接受的命令且保留 enqueue/cancel 顺序。 */
    @Test
    fun waitsForHistoryAndDrainsCommandsInOrder(): Unit = runBlocking {
        val historyReady = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val command = UploadCommand.Cancel("test-task")
        val queue = UploadCommandQueue(
            awaitHistory = { historyReady.await() },
            processCommand = { events += "process:${(it as UploadCommand.Cancel).taskId}" },
            onFailure = { _, exception -> throw exception },
            onProcessed = { events += "committed" }
        )
        assertTrue(queue.enqueue(command))
        assertTrue(queue.enqueue(UploadCommand.Cancel("next-task")))
        queue.close()
        assertFalse(queue.enqueue(command))
        assertTrue(events.isEmpty())
        historyReady.complete(Unit)
        withTimeout(5000) { queue.awaitClosed() }
        assertEquals(listOf("process:test-task", "committed", "process:next-task", "committed"), events)
    }

    /** 一条命令失败也必须释放待处理计数，后续命令继续处理，不默默丢弃。 */
    @Test
    fun failedCommandDoesNotDiscardFollowingCommand(): Unit = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        val queue = UploadCommandQueue(
            awaitHistory = {},
            processCommand = {
                val id = (it as UploadCommand.Cancel).taskId
                if (id == "fail") throw IOException("test write failure")
                events += id
            },
            onFailure = { command, _ -> events += "failed:${(command as UploadCommand.Cancel).taskId}" },
            onProcessed = { events += "finished" }
        )
        queue.enqueue(UploadCommand.Cancel("fail"))
        queue.enqueue(UploadCommand.Cancel("next"))
        queue.close()
        withTimeout(5000) { queue.awaitClosed() }
        assertEquals(listOf("failed:fail", "finished", "next", "finished"), events)
    }
}
