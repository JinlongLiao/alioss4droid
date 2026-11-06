package io.github.jinlongliao.ossbrowser.upload

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 回归多文件进度隔离、稳定目标及进程中断恢复。 */
class UploadTaskTest {
    /** 使用不同 ID 模拟同名文件，确保匹配不依赖文件名或当前页面。 */
    private fun createUploadTask(id: String) = UploadTask(
        id = id, profileId = "account-a", bucket = "bucket-a", prefix = "folder/",
        uri = "content://documents/$id", name = "same.txt", endpoint = "https://oss.example.com"
    )

    /** 两个 SDK 回调交替到达时只更新各自记录，且保存账号与路径。 */
    @Test
    fun interleavedProgressUpdatesRemainIsolated() {
        val first = createUploadTask("first")
        val second = createUploadTask("second")
        val tasks = mergeUploadTask(listOf(first, second), first.copy(sent = 25, total = 100))
        assertSame(second, tasks[1])
        val updated = mergeUploadTask(tasks, second.copy(sent = 80, total = 200))
        assertEquals(25L, updated[0].sent)
        assertEquals(80L, updated[1].sent)
        assertEquals("account-a", updated[0].profileId)
        assertEquals("folder/", updated[1].prefix)
    }

    /** 序列化后重建：未完成任务可辨识为中断，历史成功/失败及进度仍保留。 */
    @Test
    fun processRecoveryPreservesHistoryAndInterruptsUnfinishedTasks() {
        val tasks = UploadStatus.entries.map { createUploadTask(it.name).copy(status = it, sent = 20, total = 100) }
        val restored = recoverUploadTasks(Json.decodeFromString<List<UploadTask>>(Json.encodeToString(tasks)))
        assertEquals(listOf(
            UploadStatus.INTERRUPTED, UploadStatus.INTERRUPTED, UploadStatus.SUCCEEDED,
            UploadStatus.FAILED, UploadStatus.INTERRUPTED, UploadStatus.INTERRUPTED, UploadStatus.CANCELLED
        ), restored.map { it.status })
        assertEquals(tasks.map { it.id }, restored.map { it.id })
        assertEquals(tasks.map { it.sent }, restored.map { it.sent })
    }

    /** 新文件加入队尾；相同 ID 的后续更新不会产生重复行。 */
    @Test
    fun enqueueAndRetryKeepStableTaskIdentity() {
        val first = createUploadTask("first")
        val tasks = mergeUploadTask(listOf(first), createUploadTask("second"))
        val retried = mergeUploadTask(tasks, first.copy(status = UploadStatus.QUEUED))
        assertEquals(listOf("first", "second"), retried.map { it.id })
    }
    /** 清理只选终态，取消收尾中的任务不会被误删。 */
    @Test
    fun historySelectionExcludesAllActiveStates() {
        val tasks = UploadStatus.entries.map { createUploadTask(it.name).copy(status = it) }
        assertEquals(
            setOf(UploadStatus.SUCCEEDED, UploadStatus.FAILED, UploadStatus.INTERRUPTED, UploadStatus.CANCELLED),
            selectUploadHistory(tasks).map { it.status }.toSet()
        )
        assertEquals(emptyList<UploadTask>(), selectUploadHistory(emptyList()))
    }

    /** 旧版快照缺少新增状态字段时仍可读取，不会影响既有历史。 */
    @Test
    fun olderTaskSnapshotStillLoads() {
        val raw = """{"id":"old","profileId":"account-a","bucket":"bucket-a","prefix":"folder/",
            "uri":"content://documents/old","name":"same.txt","endpoint":"https://oss.example.com"}"""
        assertEquals(createUploadTask("old"), Json.decodeFromString<UploadTask>(raw))
    }
}
