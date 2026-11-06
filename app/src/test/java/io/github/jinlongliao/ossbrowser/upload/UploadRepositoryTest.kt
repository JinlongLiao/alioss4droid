package io.github.jinlongliao.ossbrowser.upload

import android.os.Looper
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证加载门闩、先提交后发布、批量清理与重试互斥；全部使用本地历史和文件。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UploadRepositoryTest {
    /** 每项测试独立私有模拟目录。 */
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** 构造无真实凭据、默认可恢复的任务记录。 */
    private fun task(id: String, status: UploadStatus = UploadStatus.FAILED) = UploadTask(
        id, "test-profile", "test-bucket", "", "content://test/$id", "$id.txt", null, status = status
    )

    /** 首次读盘放后台；新命令必须等待历史读取，不能覆盖尚未加载的旧任务。 */
    @Test
    fun initializationDoesNotBlockMainAndCommandsAwaitExistingHistory(): Unit = runBlocking {
        val gate = CountDownLatch(1)
        val store = RecordingUploadTaskStore(listOf(task("old", UploadStatus.RUNNING)), gate)
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        try {
            assertFalse(repository.isLoaded.value)
            val queued = async(Dispatchers.IO) {
                repository.awaitLoaded()
                repository.queueUploadTask(task("new", UploadStatus.QUEUED))
            }
            assertFalse(queued.isCompleted)
            gate.countDown()
            assertTrue(queued.await())
            assertEquals(listOf("old", "new"), repository.tasks.value.map { it.id })
            assertEquals(UploadStatus.INTERRUPTED, repository.tasks.value.first().status)
            assertFalse(store.loadOnMain.get())
            assertFalse(store.writeOnMain.get())
        } finally {
            gate.countDown()
        }
    }

    /** 写入完成前状态保持旧快照；磁盘失败后也不能提前显示成功。 */
    @Test
    fun statePublishesOnlyAfterPersistenceSucceeds(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(listOf(task("one", UploadStatus.RUNNING)))
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        store.failSave.set(true)
        try {
            withContext(Dispatchers.IO) { repository.updateUploadTask(task("one", UploadStatus.SUCCEEDED)) }
            error("应传播保存失败")
        } catch (expected: IOException) {
            assertEquals(UploadStatus.INTERRUPTED, repository.tasks.value.single().status)
        }
        store.failSave.set(false)
        withContext(Dispatchers.IO) { repository.updateUploadTask(task("one", UploadStatus.SUCCEEDED)) }
        assertEquals(UploadStatus.SUCCEEDED, repository.tasks.value.single().status)
        assertEquals(store.committed, repository.tasks.value)
    }

    /** 大量历史清理只提交一次完整快照，活动任务保留，空清理不写盘。 */
    @Test
    fun clearManyHistoryRecordsCommitsOnceAndKeepsActiveTask(): Unit = runBlocking {
        val history = (1..300).map { task("history-$it") }
        val store = RecordingUploadTaskStore(history)
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) { repository.queueUploadTask(task("active", UploadStatus.QUEUED)) }
        val before = store.writes.get()
        assertEquals(0, repository.clearUploadHistory())
        assertEquals(1, store.writes.get() - before)
        assertEquals(listOf("active"), repository.tasks.value.map { it.id })
        val after = store.writes.get()
        assertEquals(0, repository.clearUploadHistory())
        assertEquals(after, store.writes.get())
    }

    /** 部分文件不能删除时保留该历史，成功项仍一次提交清理结果。 */
    @Test
    fun failedFileCleanupKeepsRecordWhileRemovingSuccessfulHistory(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(listOf(task("ok"), task("../invalid")))
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        assertEquals(1, repository.clearUploadHistory())
        assertEquals(listOf("../invalid"), repository.tasks.value.map { it.id })
        assertEquals(1, store.writes.get())
    }

    /** 保存清理结果失败时保留历史，释放清理锁后允许用户再次尝试。 */
    @Test
    fun failedCleanupCommitKeepsHistoryAndAllowsRetry(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(listOf(task("one")))
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        store.failSave.set(true)
        assertEquals(1, repository.clearUploadHistory())
        assertEquals(listOf("one"), repository.tasks.value.map { it.id })
        store.failSave.set(false)
        assertEquals(0, repository.clearUploadHistory())
        assertTrue(repository.tasks.value.isEmpty())
    }

    /** 取消收尾期间禁止重试和清理；进度不能撤销取消，重试必须生成新代次。 */
    @Test
    fun cancellationAndRetryKeepDurableExecutionGeneration(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(emptyList())
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            assertTrue(repository.queueUploadTask(task("one", UploadStatus.QUEUED)))
            val first = repository.tasks.value.single()
            repository.updateUploadTask(first.copy(status = UploadStatus.RUNNING))
            assertTrue(repository.requestUploadCancellation("one"))
            repository.updateUploadProgress(first.copy(status = UploadStatus.RUNNING, sent = 50, total = 100))
            assertEquals(UploadStatus.CANCELLING, repository.tasks.value.single().status)
            assertFalse(repository.queueUploadTask(task("one")))
            assertEquals(0, repository.clearUploadHistory())
            repository.finishIdleUploadCancellation("one")
            assertEquals(UploadStatus.CANCELLED, repository.tasks.value.single().status)
            assertTrue(repository.queueUploadTask(task("one")))
            assertTrue(first.attemptId != repository.tasks.value.single().attemptId)
        }
    }

    /** 清理提交期间禁止重试，完成后迟到的历史重试也不能把已删除记录重新插入。 */
    @Test
    fun cleanupReservationBlocksRetryUntilCommitAndRejectsStaleRetry(): Unit = runBlocking {
        val old = task("old")
        val store = RecordingUploadTaskStore(listOf(old))
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        var checkedReservation = false
        store.beforeCommit = {
            assertFalse(repository.queueUploadTask(old))
            checkedReservation = true
        }
        assertEquals(0, repository.clearUploadHistory())
        assertTrue(checkedReservation)
        assertTrue(repository.tasks.value.isEmpty())
        withContext(Dispatchers.IO) { assertFalse(repository.queueUploadTask(old)) }
        assertEquals(1, store.writes.get())
    }

    /** 运行期旧代次进度和终态不能通过完整快照路径覆盖新排队/新运行任务。 */
    @Test
    fun staleCallbacksCannotOverwriteNewAttempt(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(emptyList())
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            repository.queueUploadTask(task("one", UploadStatus.QUEUED))
            val old = repository.tasks.value.single().copy(status = UploadStatus.RUNNING, sent = 50, total = 100)
            repository.updateUploadTask(old.copy(status = UploadStatus.FAILED))
            assertTrue(repository.queueUploadTask(old.copy(status = UploadStatus.FAILED)))
            val queued = repository.tasks.value.single()
            assertTrue(old.attemptId != queued.attemptId)
            repository.updateUploadTask(old.copy(status = UploadStatus.SUCCEEDED))
            assertEquals(queued, repository.tasks.value.single())
            val running = queued.copy(status = UploadStatus.RUNNING)
            repository.updateUploadTask(running)
            repository.updateUploadProgress(old.copy(sent = 90))
            repository.updateUploadTask(old.copy(status = UploadStatus.CANCELLED))
            assertEquals(running, repository.tasks.value.single())
        }
    }

    /** 即使观察器在新任务完成后才恢复，也不能把新完成误当成初始化历史而吞掉刷新。 */
    @Test
    fun startupBaselineNeverIncludesTasksCompletedAfterInitialization(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(listOf(task("history", UploadStatus.SUCCEEDED)))
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            repository.queueUploadTask(task("fresh", UploadStatus.QUEUED))
            val fresh = repository.tasks.value.first { it.id == "fresh" }
            repository.updateUploadTask(fresh.copy(status = UploadStatus.SUCCEEDED))
        }
        assertEquals(setOf("history"), repository.initialCompletedUploadIds)
        assertTrue(repository.tasks.value.any { it.id == "fresh" && it.status == UploadStatus.SUCCEEDED })
    }

    /** 复现入队取消与迟到开始保存的交错，RUNNING 不能撤销同代取消请求。 */
    @Test
    fun delayedStartCannotOverwriteQueuedCancellation(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(emptyList())
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            repository.queueUploadTask(task("one", UploadStatus.QUEUED))
            val queued = repository.tasks.value.single()
            assertTrue(repository.requestUploadCancellation("one"))
            repository.updateUploadTask(queued.copy(status = UploadStatus.RUNNING))
            assertEquals(UploadStatus.CANCELLING, repository.tasks.value.single().status)
        }
    }

    /** 用户取消可能晚于远端确认成功，成功收尾不能被开始守卫误拒绝。 */
    @Test
    fun confirmedRemoteSuccessCanFinalizeCancellingTask(): Unit = runBlocking {
        val store = RecordingUploadTaskStore(emptyList())
        val repository = UploadRepository(store, UploadFiles(temporaryFolder.root))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            repository.queueUploadTask(task("one", UploadStatus.QUEUED))
            val queued = repository.tasks.value.single()
            repository.requestUploadCancellation("one")
            repository.updateUploadTask(queued.copy(status = UploadStatus.SUCCEEDED))
            assertEquals(UploadStatus.SUCCEEDED, repository.tasks.value.single().status)
        }
    }

    /** 测试持久化器只记录已提交状态及线程，失败不修改 committed。 */
    private class RecordingUploadTaskStore(
        /** 初始旧历史。 */
        private val initial: List<UploadTask>,
        /** 可阻塞后台读取以验证构造器没有同步等待。 */
        private val loadGate: CountDownLatch? = null
    ) : UploadTaskStore {
        /** 保存调用数，用于断言批量清理不是 N 次 fsync。 */
        val writes = AtomicInteger()
        /** 模拟磁盘提交失败。 */
        val failSave = AtomicBoolean(false)
        /** 记录任何主线程持久化。 */
        val writeOnMain = AtomicBoolean(false)
        /** 记录初始化线程。 */
        val loadOnMain = AtomicBoolean(false)
        /** 可在提交边界注入重试，验证清理保留锁仍然有效。 */
        var beforeCommit: (() -> Unit)? = null
        /** 最近一次成功提交内容。 */
        @Volatile var committed: List<UploadTask> = initial

        /** 保持读取可控，不读取真实账号或远端对象。 */
        override fun loadUploadTasks(): List<UploadTask> {
            loadOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            check(loadGate?.await(5, TimeUnit.SECONDS) != false)
            return initial
        }

        /** 模拟原子提交边界，错误在状态发布前抛出。 */
        override fun saveUploadTasks(tasks: List<UploadTask>) {
            if (Looper.myLooper() == Looper.getMainLooper()) writeOnMain.set(true)
            writes.incrementAndGet()
            if (failSave.get()) throw IOException("test disk failure")
            beforeCommit?.invoke()
            committed = tasks
        }
    }
}
