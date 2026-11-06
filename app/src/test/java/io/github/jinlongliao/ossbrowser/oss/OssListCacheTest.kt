package io.github.jinlongliao.ossbrowser.oss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date

/** 使用真实缓存文件验证目录失效与旧请求写回的竞态，不连接 OSS/S3。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class OssListCacheTest {
    /** 上传完成使后台目录缓存失效；此前开始的请求不能重新写回旧列表。 */
    @Test
    fun invalidationRejectsResponseStartedBeforeUploadCompleted() {
        val cache = OssListCache(RuntimeEnvironment.getApplication())
        cache.clear()
        val generation = cache.listGeneration("profile", "bucket", "folder/")
        cache.writeList("profile", "bucket", "folder/", listOf(file("old")), null, generation)
        cache.invalidateList("profile", "bucket", "folder/")
        assertFalse(cache.writeList("profile", "bucket", "folder/", listOf(file("old")), null, generation))
        assertNull(cache.readList("profile", "bucket", "folder/"))
        val currentGeneration = cache.listGeneration("profile", "bucket", "folder/")
        assertTrue(cache.writeList("profile", "bucket", "folder/", listOf(file("new")), null, currentGeneration))
        assertEquals(listOf("new"), cache.readList("profile", "bucket", "folder/")?.items?.map { it.key })
    }

    /** 清理全部缓存后，未单独登记过的目录也必须拒绝旧请求。 */
    @Test
    fun clearingAllCachesRejectsPreviouslyStartedRequest() {
        val cache = OssListCache(RuntimeEnvironment.getApplication())
        val generation = cache.listGeneration("profile", "bucket", "unseen/")
        cache.clear()
        assertFalse(cache.writeList("profile", "bucket", "unseen/", listOf(file("old")), null, generation))
        assertNull(cache.readList("profile", "bucket", "unseen/"))
    }

    /** 一个目录上传后，不使其他账号或目录的飞行请求失效。 */
    @Test
    fun directoryInvalidationRemainsScopedToTarget() {
        val cache = OssListCache(RuntimeEnvironment.getApplication())
        cache.clear()
        val generation = cache.listGeneration("other-profile", "bucket", "other/")
        cache.invalidateList("profile", "bucket", "folder/")
        assertTrue(cache.writeList("other-profile", "bucket", "other/", listOf(file("other")), null, generation))
    }

    /** 时间戳保留毫秒精度，缓存恢复后仍能用于时间排序。 */
    @Test
    fun diskCacheRetainsObjectModificationTimeAndCompleteness() {
        val context = RuntimeEnvironment.getApplication()
        val cache = OssListCache(context)
        cache.clear()
        val file = file("new").copy(lastModified = Date(1_735_689_600_123L))
        cache.writeList("profile", "bucket", "folder/", listOf(file), null)
        val restored = requireNotNull(OssListCache(context).readList("profile", "bucket", "folder/"))
        assertEquals(1_735_689_600_123L, (restored.items.single() as OssObjectItem.File).lastModified?.time)
        assertNull(restored.nextMarker)
    }

    /** 连续大目录不累计全部DTO；淘汰只影响内存，仍能从同一缓存实例的磁盘完整恢复。 */
    @Test
    fun memoryEvictionPreservesCompleteDiskSnapshotAndGenerationGuard() {
        val cache = OssListCache(RuntimeEnvironment.getApplication())
        cache.clear()
        val items = List(10_000) { file("file-$it") }
        repeat(3) { cache.writeList("profile", "bucket", "directory-$it/", items, null) }
        assertEquals(20_000, cache.memoryMetadataItemCount)
        assertEquals(2, cache.memorySnapshotCount)
        assertEquals(10_000, cache.readList("profile", "bucket", "directory-0/")?.items?.size)
        assertEquals(20_000, cache.memoryMetadataItemCount)
        val oldGeneration = cache.listGeneration("profile", "bucket", "directory-0/")
        cache.invalidateList("profile", "bucket", "directory-0/")
        repeat(40) { cache.writeList("profile", "bucket", "small-$it/", emptyList(), null) }
        assertTrue(cache.memorySnapshotCount <= 32)
        assertFalse(cache.writeList("profile", "bucket", "directory-0/", items, null, oldGeneration))
        assertNull(cache.readList("profile", "bucket", "directory-0/"))
    }

    /** 测试数据只包含元数据，不含文件正文。 */
    private fun file(key: String) = OssObjectItem.File(key, 1L, Date(1L), null)
}
