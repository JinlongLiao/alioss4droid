package io.github.jinlongliao.ossbrowser.upload

import android.os.Looper
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 准备阶段用真实本地快照验证名称提交顺序、取消交错、旧任务和磁盘失败，不调用任何对象接口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UploadTaskPreparationTest {
    /** 每项测试独立历史文件。 */
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** 与 VM 新提交相同的占位任务，无凭据。 */
    private fun task(resolveName: Boolean = true) = UploadTask(
        "one", "test-profile", "test-bucket", "folder/", "content://test/one", "unnamed_123", null,
        resolveDisplayName = resolveName
    )

    /** 初始化并入队，保留真实磁盘以验证返回值确实来自已提交名称。 */
    private suspend fun queuedRepository(task: UploadTask = task()): UploadRepository {
        val repository = UploadRepository(
            AtomicUploadTaskStore(File(temporaryFolder.root, "upload_tasks.json")),
            UploadFiles(File(temporaryFolder.root, "uploads"))
        )
        repository.awaitLoaded()
        withContext(Dispatchers.IO) { assertTrue(repository.queueUploadTask(task)) }
        return repository
    }

    /** 名称读取在 IO；返回可用远端 key 前，真实名称和解析标记必须完整提交。 */
    @Test
    fun preparationPersistsResolvedUnicodeNameBeforeReturning(): Unit = runBlocking {
        val repository = queuedRepository()
        val prepared = prepareUploadTask(repository.tasks.value.single(), repository) {
            assertFalse(Looper.myLooper() == Looper.getMainLooper())
            assertEquals(UploadStatus.RUNNING, repository.tasks.value.single().status)
            "会议 记录.pdf"
        }
        assertEquals("folder/会议 记录.pdf", prepared.prefix + prepared.name)
        assertFalse(prepared.resolveDisplayName)
        val persisted = AtomicUploadTaskStore(File(temporaryFolder.root, "upload_tasks.json"))
            .loadUploadTasks().single()
        assertEquals(prepared, persisted)
    }

    /** 旧版本已保存名称的重试禁止重新查询云文档或改变对象 key。 */
    @Test
    fun legacyStoredNameDoesNotQueryProvider(): Unit = runBlocking {
        val repository = queuedRepository(task(false).copy(name = "original.txt"))
        val prepared = prepareUploadTask(repository.tasks.value.single(), repository) {
            error("旧任务不应重新解析名称")
        }
        assertEquals("original.txt", prepared.name)
    }

    /** 慢查询返回前取消已落盘时，迟到真实名称不能解除取消或返回占位 key。 */
    @Test
    fun cancellationDuringNameResolutionRejectsLateResult(): Unit = runBlocking {
        val repository = queuedRepository()
        try {
            prepareUploadTask(repository.tasks.value.single(), repository) {
                assertTrue(repository.requestUploadCancellation("one"))
                "late.txt"
            }
            error("取消后不能返回可上传快照")
        } catch (expected: CancellationException) {
            val current = repository.tasks.value.single()
            assertEquals(UploadStatus.CANCELLING, current.status)
            assertEquals("unnamed_123", current.name)
            assertTrue(current.resolveDisplayName)
        }
    }

    /** 尚未开始就取消的任务不会解析名称，也不会用 RUNNING 覆盖 CANCELLING。 */
    @Test
    fun cancellationBeforeStartNeverQueriesProvider(): Unit = runBlocking {
        val repository = queuedRepository()
        val queued = repository.tasks.value.single()
        withContext(Dispatchers.IO) { repository.requestUploadCancellation("one") }
        try {
            prepareUploadTask(queued, repository) { error("已取消任务不能读取文档") }
            error("取消后不能开始")
        } catch (expected: CancellationException) {
            assertEquals(UploadStatus.CANCELLING, repository.tasks.value.single().status)
        }
    }

    /** 提供方异常不清除解析标记；服务保存失败后，下一代重试仍会查询真实名称。 */
    @Test
    fun providerFailureKeepsNameResolvableOnRetry(): Unit = runBlocking {
        val repository = queuedRepository()
        try {
            prepareUploadTask(repository.tasks.value.single(), repository) { throw IOException("provider unavailable") }
            error("应传播提供方异常")
        } catch (expected: IOException) {
            assertEquals("provider unavailable", expected.message)
        }
        val failed = repository.tasks.value.single().copy(status = UploadStatus.FAILED)
        withContext(Dispatchers.IO) {
            repository.updateUploadTask(failed)
            assertTrue(repository.queueUploadTask(failed))
        }
        val prepared = prepareUploadTask(repository.tasks.value.single(), repository) { "recovered.txt" }
        assertEquals("recovered.txt", prepared.name)
        withContext(Dispatchers.IO) {
            assertNull(repository.resolveUploadTaskDisplayName(failed, "stale.txt"))
        }
        assertEquals(prepared, repository.tasks.value.single())
    }

    /** 真实名称快照提交失败，不能返回看似准备完成的任务或提前发布新名称。 */
    @Test
    fun failedNameCommitDoesNotReturnPreparedTask(): Unit = runBlocking {
        val repository = queuedRepository()
        try {
            prepareUploadTask(repository.tasks.value.single(), repository) {
                File(temporaryFolder.root, "upload_tasks.json.commit").mkdirs()
                "unsaved.txt"
            }
            error("名称保存失败不能继续上传")
        } catch (expected: IOException) {
            val current = repository.tasks.value.single()
            assertEquals("unnamed_123", current.name)
            assertTrue(current.resolveDisplayName)
        }
    }
}
