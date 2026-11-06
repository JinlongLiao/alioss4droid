package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** 服务端 key 分页与客户端全局排序的回归测试，全部使用本地元数据，不访问真实对象存储。 */
class ObjectDirectoryLoaderTest {
    /** 服务端第一页按 key 返回旧文件，最新文件在第二页时仍必须显示在首位。 */
    @Test
    fun newestFileOnLaterPageAppearsFirstWithoutManualRefresh() = runBlocking {
        val requests = mutableListOf<String?>()
        val page = loadSortedObjectDirectory(
            OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10, {}
        ) { marker, maxKeys ->
            requests += marker
            assertEquals(1000, maxKeys)
            if (marker == null) page(listOf(file("a-old", 1)), "next") else page(listOf(file("z-new", 10)))
        }
        assertEquals(listOf("a-old", "z-new").reversed(), page.items.map { it.key })
        assertEquals(listOf(null, "next"), requests)
        assertNull(page.nextMarker)
    }

    /** 名称倒序同样必须扫描后续 key 页，并保持目录在文件之前。 */
    @Test
    fun descendingNamesSortAcrossAllPages() = runBlocking {
        val result = loadSortedObjectDirectory(
            OssObjectSortField.NAME, OssObjectSortDirection.DESCENDING, 10, {}
        ) { marker, _ ->
            if (marker == null) page(listOf(file("a", 1)), "next")
            else page(listOf(file("z", 2), OssObjectItem.Folder("folder/")))
        }
        assertEquals(listOf("folder/", "z", "a"), result.items.map { it.key })
    }

    /** 名称正序保持按需分页，不为了展示首屏扫描整个目录。 */
    @Test
    fun ascendingNamesRetainLazyPagination() = runBlocking {
        var requests = 0
        val result = loadSortedObjectDirectory(
            OssObjectSortField.NAME, OssObjectSortDirection.ASCENDING, 10, {}
        ) { marker, maxKeys ->
            requests++
            assertNull(marker)
            assertEquals(10, maxKeys)
            page(listOf(file("a", 1)), "next")
        }
        assertEquals(1, requests)
        assertEquals("next", result.nextMarker)
    }

    /** 超限时不能返回一个看似已完成的局部时间排序结果。 */
    @Test
    fun oversizedDirectoryFailsExplicitlyInsteadOfReturningPartialOrder() = runBlocking {
        var requests = 0
        try {
            loadSortedObjectDirectory(
                OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10, {}
            ) { _, _ ->
                requests++
                page((1..1000).map { file("$requests-$it", it.toLong()) }, "page-$requests")
            }
            error("超限目录必须报错")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("10000"))
        }
        assertEquals(10, requests)
    }

    /** 已读满 10000 条而仍有令牌时立即拒绝，不能发出第 11 页请求。 */
    @Test
    fun fullLimitWithMorePagesStopsBeforeIssuingAnotherRequest() = runBlocking {
        var requests = 0
        try {
            loadSortedObjectDirectory(
                OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10, {}
            ) { _, _ ->
                requests++
                check(requests <= 10) { "不应读取第 11 页" }
                page((1..1000).map { file("$requests-$it", it.toLong()) }, "page-$requests")
            }
            error("达到上限但仍有下一页时必须拒绝完整排序")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("10000"))
        }
        assertEquals(10, requests)
    }

    /** 恰好 10000 条且最后一页无令牌时仍允许完整排序，不把上限误当作禁止值。 */
    @Test
    fun exactLimitWithoutMorePagesCompletesNormally() = runBlocking {
        var requests = 0
        val result = loadSortedObjectDirectory(
            OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10, {}
        ) { _, _ ->
            requests++
            page((1..1000).map { file("$requests-$it", it.toLong()) }, if (requests < 10) "page-$requests" else null)
        }
        assertEquals(10, requests)
        assertEquals(10_000, result.items.size)
        assertNull(result.nextMarker)
    }

    /** 用户切换账号、目录或刷新后，不继续旧目录的下一页请求。 */
    @Test
    fun obsoleteGenerationStopsDirectoryScanBeforeNextPage() = runBlocking {
        var current = true
        var requests = 0
        try {
            loadSortedObjectDirectory(
                OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10,
                { if (!current) throw CancellationException("过期目录") }
            ) { _, _ ->
                requests++
                current = false
                page(listOf(file("old", 1)), "next")
            }
            error("过期请求必须取消")
        } catch (_: CancellationException) {
            assertEquals(1, requests)
        }
    }

    /** 服务端重复令牌必须终止，不能造成无限请求。 */
    @Test
    fun repeatedPageTokenFailsWithoutLoopingForever() = runBlocking {
        var requests = 0
        try {
            loadSortedObjectDirectory(
                OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.ASCENDING, 10, {}
            ) { _, _ -> requests++; page(listOf(file("old", 1)), "same") }
            error("重复令牌必须报错")
        } catch (_: IllegalStateException) {
            assertEquals(2, requests)
        }
    }

    /** 元数据合并与全目录排序不能在调用方主线程执行，不依赖容易抖动的毫秒阈值。 */
    @Test
    fun directoryScanRunsAwayFromCallerThread() = runBlocking {
        val callerThread = Thread.currentThread().name
        val observedThreads = mutableListOf<String>()
        loadSortedObjectDirectory(
            OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING, 10, {}
        ) { _, _ ->
            observedThreads += Thread.currentThread().name
            page(listOf(file("a", 1)))
        }
        assertTrue(observedThreads.isNotEmpty())
        assertTrue(observedThreads.none { it == callerThread })
    }

    /** 构造单个元数据测试文件。 */
    private fun file(key: String, modified: Long) = OssObjectItem.File(key, 1, Date(modified), null)

    /** 构造服务端分页响应。 */
    private fun page(items: List<OssObjectItem>, marker: String? = null) = OssService.ListPage(items, marker)
}
