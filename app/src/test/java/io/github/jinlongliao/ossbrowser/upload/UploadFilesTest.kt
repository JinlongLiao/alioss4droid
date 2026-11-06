package io.github.jinlongliao.ossbrowser.upload

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 使用真实临时目录验证快照不可变、失败/取消清理和任务间隔离。 */
class UploadFilesTest {
    /** 每个测试独立目录，JUnit 在结束后清理。 */
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** 重试直接读取已发布快照，即使源已变更或无权限也不会混用新旧内容。 */
    @Test
    fun retryUsesIdenticalSnapshotWithoutReopeningSource() = runBlocking {
        val files = UploadFiles(temporaryFolder.root)
        val first = files.prepareUploadFile("task-a") { "original".byteInputStream() }
        val second = files.prepareUploadFile("task-a") { error("不应重新读取源文档") }
        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals("original", second.readText())
    }

    /** 复制异常和取消都不能将半成品当作可上传快照。 */
    @Test
    fun failedOrCancelledCopyDoesNotPublishSnapshot() = runBlocking {
        val files = UploadFiles(temporaryFolder.root)
        listOf(IOException("read failure"), CancellationException("cancel")).forEachIndexed { index, failure ->
            val id = "task-$index"
            try {
                files.prepareUploadFile(id) {
                    object : InputStream() {
                        /** 模拟文档流中断；异常必须向调用方传播。 */
                        override fun read(): Int = throw failure
                    }
                }
                error("应当传播读取失败或取消")
            } catch (exception: Exception) {
                assertEquals(failure, exception)
            }
            assertFalse(File(files.taskDirectory(id), "source").exists())
            assertFalse(File(files.taskDirectory(id), "source.partial").exists())
            assertEquals("retry", files.prepareUploadFile(id) { "retry".byteInputStream() }.readText())
        }
    }

    /** 删除历史文件时仅触及指定任务，断点和半成品都可清理。 */
    @Test
    fun cleanupIsIsolatedAndIdempotent() = runBlocking {
        val files = UploadFiles(temporaryFolder.root)
        files.prepareUploadFile("first") { "first".byteInputStream() }
        val second = files.prepareUploadFile("second") { "second".byteInputStream() }
        File(files.taskDirectory("first"), "records").mkdirs()
        File(files.taskDirectory("first"), "records/checkpoint").writeText("checkpoint")
        files.deleteUploadFiles("first")
        files.deleteUploadFiles("first")
        assertFalse(files.taskDirectory("first").exists())
        assertTrue(second.exists())
        assertEquals("second", second.readText())
    }

    /** 使用真实协程取消复制，验证缓冲块检查会停止读取并清理半成品。 */
    @Test
    fun coroutineCancellationStopsCopyAndRemovesPartialFile() = runBlocking {
        val files = UploadFiles(temporaryFolder.root)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            files.prepareUploadFile("cancel-copy") {
                object : InputStream() {
                    /** 等待测试发出取消，下一块读取前必须检查协程状态。 */
                    override fun read(): Int = error("使用缓冲区读取")

                    /** 模拟阻塞中的文档提供方，仅返回一个数据块。 */
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        started.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        buffer[offset] = 1
                        return 1
                    }
                }
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            job.cancel()
            release.countDown()
            job.join()
        }
        assertTrue(job.isCancelled)
        assertFalse(File(files.taskDirectory("cancel-copy"), "source").exists())
        assertFalse(File(files.taskDirectory("cancel-copy"), "source.partial").exists())
    }

    /** 完整快照丢失时不得把旧断点用于新源文档。 */
    @Test
    fun missingSnapshotDiscardsOldCheckpointsBeforeCopy() = runBlocking {
        val files = UploadFiles(temporaryFolder.root)
        val records = File(files.taskDirectory("lost-snapshot"), "records").apply { mkdirs() }
        File(records, "old-checkpoint").writeText("old")
        files.prepareUploadFile("lost-snapshot") { "new source".byteInputStream() }
        assertFalse(records.exists())
    }

    /** 非法历史 ID 不能逃出上传目录删除其他文件。 */
    @Test(expected = IllegalArgumentException::class)
    fun cleanupRejectsPathTraversal() {
        UploadFiles(temporaryFolder.root).deleteUploadFiles("../outside")
    }
}
