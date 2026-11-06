package io.github.jinlongliao.ossbrowser.upload

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 真实临时文件验证原子进度提交、故障恢复、重试代次与旧格式兼容，不访问远端。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AtomicUploadTaskStoreTest {
    /** 每项测试独立目录，故障注入只修改本次临时文件。 */
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** 一个带明确执行代次的运行快照。 */
    private fun running(id: String = "active", attempt: String = "attempt-one") = UploadTask(
        id, "test-profile", "test-bucket", "", "content://test/$id", "$id.bin", null,
        status = UploadStatus.RUNNING, total = 100, attemptId = attempt
    )

    /** 大历史下进度仅写单任务 journal，原完整历史文件字节保持不变；重启仍读到最新进度。 */
    @Test
    fun progressWritesOnlySingleTaskAndSurvivesReload() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        val history = (1..3000).map { running("done-$it").copy(status = UploadStatus.SUCCEEDED) }
        val first = history + running()
        store.saveUploadTasks(first)
        val original = file.readBytes()
        val updated = history + running().copy(sent = 50)
        store.saveUploadTasks(updated)
        assertTrue(original.contentEquals(file.readBytes()))
        assertTrue(store.progressFile("active").length() < file.length() / 100)
        assertEquals(updated, AtomicUploadTaskStore(file).loadUploadTasks())
    }

    /** 初始化必须先合并 journal 再标记中断，不能丢失最后已确认百分比。 */
    @Test
    fun repositoryRecoversLatestJournalBeforeMarkingInterrupted(): Unit = runBlocking {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        AtomicUploadTaskStore(file).also {
            it.saveUploadTasks(listOf(running()))
            it.saveUploadTasks(listOf(running().copy(sent = 75)))
        }
        val repository = UploadRepository(AtomicUploadTaskStore(file), UploadFiles(File(temporaryFolder.root, "files")))
        repository.awaitLoaded()
        assertEquals(UploadStatus.INTERRUPTED, repository.tasks.value.single().status)
        assertEquals(75L, repository.tasks.value.single().sent)
    }

    /** 原子历史失败时旧主快照和最新 journal 均保留，成功重试才吸收 journal。 */
    @Test
    fun terminalCommitFailurePreservesRecoverableProgress() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        store.saveUploadTasks(listOf(running()))
        store.saveUploadTasks(listOf(running().copy(sent = 60)))
        val blockedCommit = File(file.parentFile, file.name + ".commit").apply { mkdirs() }
        try {
            store.saveUploadTasks(listOf(running().copy(status = UploadStatus.SUCCEEDED, sent = 100)))
            error("被阻塞的原子提交必须失败")
        } catch (expected: IOException) {
            assertTrue(blockedCommit.isDirectory)
        }
        val restored = AtomicUploadTaskStore(file).loadUploadTasks().single()
        assertEquals(UploadStatus.RUNNING, restored.status)
        assertEquals(60L, restored.sent)
        blockedCommit.delete()
        store.saveUploadTasks(listOf(running().copy(status = UploadStatus.SUCCEEDED, sent = 100)))
        assertEquals(UploadStatus.SUCCEEDED, AtomicUploadTaskStore(file).loadUploadTasks().single().status)
        assertFalse(store.progressFile("active").exists())
    }

    /** journal 写失败不发布虚假进度，也不破坏已存在的完整历史。 */
    @Test
    fun failedJournalCommitDoesNotPublishProgress(): Unit = runBlocking {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        val repository = UploadRepository(store, UploadFiles(File(temporaryFolder.root, "files")))
        repository.awaitLoaded()
        withContext(Dispatchers.IO) {
            repository.queueUploadTask(running().copy(status = UploadStatus.QUEUED))
            val task = repository.tasks.value.single().copy(status = UploadStatus.RUNNING)
            repository.updateUploadTask(task)
            val progress = store.progressFile(task.id)
            requireNotNull(progress.parentFile).mkdirs()
            File(progress.parentFile, progress.name + ".commit").mkdirs()
            try {
                repository.updateUploadProgress(task.copy(sent = 50))
                error("进度提交应失败")
            } catch (expected: IOException) {
                assertEquals(0L, repository.tasks.value.single().sent)
                assertEquals(0L, AtomicUploadTaskStore(file).loadUploadTasks().single().sent)
            }
        }
    }

    /** 删除失败留下的旧 journal 不能复活终态或覆盖新排队/新运行代次。 */
    @Test
    fun staleJournalCannotOverrideTerminalOrNewAttempt() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        store.saveUploadTasks(listOf(running()))
        store.saveUploadTasks(listOf(running().copy(sent = 80)))
        val oldProgress = store.progressFile("active").readBytes()
        for (state in listOf(UploadStatus.SUCCEEDED, UploadStatus.FAILED, UploadStatus.CANCELLED, UploadStatus.QUEUED)) {
            val snapshot = running().copy(status = state)
            store.saveUploadTasks(listOf(snapshot))
            store.progressFile("active").writeBytes(oldProgress)
            assertEquals(snapshot, AtomicUploadTaskStore(file).loadUploadTasks().single())
        }
        val newAttempt = running(attempt = "attempt-two")
        store.saveUploadTasks(listOf(newAttempt))
        store.progressFile("active").writeBytes(oldProgress)
        assertEquals(newAttempt, AtomicUploadTaskStore(file).loadUploadTasks().single())
    }

    /** 旧版本不含 attemptId 的 JSON 仍能读取；半写 .commit 文件不能参与恢复。 */
    @Test
    fun oldHistoryAndPartialFilesRemainCompatible() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val legacy = running().copy(attemptId = "")
        file.writeText(Json.encodeToString(listOf(legacy)))
        val store = AtomicUploadTaskStore(file)
        store.progressDirectory.mkdirs()
        File(store.progressDirectory, "active.json.commit").writeText(Json.encodeToString(running().copy(sent = 99)))
        store.progressFile("active").writeText(Json.encodeToString(running().copy(sent = 90)))
        assertEquals(legacy, store.loadUploadTasks().single())
    }

    /** 历史清理提交删除任务的同时吸收/移除对应 journal，重启不重新插入已清理记录。 */
    @Test
    fun removingHistoryAlsoRemovesProgressJournal() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        store.saveUploadTasks(listOf(running()))
        store.saveUploadTasks(listOf(running().copy(sent = 30)))
        store.saveUploadTasks(emptyList())
        assertFalse(store.progressFile("active").exists())
        assertTrue(AtomicUploadTaskStore(file).loadUploadTasks().isEmpty())
    }
    /** 同代次内 SDK 重试导致进度回退并完整提交后，未删除的旧 journal 也不能覆盖新基线。 */
    @Test
    fun staleJournalCannotOverrideRebasedSnapshotInSameAttempt() {
        val file = File(temporaryFolder.root, "upload_tasks.json")
        val store = AtomicUploadTaskStore(file)
        store.saveUploadTasks(listOf(running()))
        store.saveUploadTasks(listOf(running().copy(sent = 80)))
        val stale = store.progressFile("active").readBytes()
        store.saveUploadTasks(listOf(running().copy(sent = 20)))
        store.progressFile("active").writeBytes(stale)
        assertEquals(20L, AtomicUploadTaskStore(file).loadUploadTasks().single().sent)
    }
}
