package io.github.jinlongliao.ossbrowser.ui.browser

import android.graphics.Bitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 本地模拟native后端，验证按需渲染和资源互斥，不依赖真实文件或网络。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PdfPreviewRendererTest {
    /** 页数再多也只渲染实际请求的页面，关闭不回收已经交付UI的Bitmap。 */
    @Test
    fun largeDocumentRendersOnlyRequestedPagesAndKeepsDeliveredBitmaps() = runBlocking {
        val backend = RecordingPdfPageBackend()
        val session = PdfPreviewRenderer(backend)
        assertEquals(1000, session.pageCount)
        assertEquals(0, backend.renderCount.get())
        val first = session.renderPage(0, 1200)
        val second = session.renderPage(999, 1200)
        assertEquals(2, backend.renderCount.get())
        session.closeAsync()
        assertTrue(backend.closed.await(5, TimeUnit.SECONDS))
        assertFalse(first.bitmap.isRecycled)
        assertFalse(second.bitmap.isRecycled)
        assertEquals(0.5f, first.aspectRatio)
    }

    /** Dispose与在途native渲染互斥，旧结果不交给已关闭文档，资源只关闭一次。 */
    @Test
    fun closingWaitsForInFlightRenderAndDoesNotPublishItsResult() = runBlocking {
        val backend = RecordingPdfPageBackend(blockRender = true)
        val session = PdfPreviewRenderer(backend)
        val rejectedResult = AtomicBoolean(false)
        val job = launch(Dispatchers.IO) {
            try {
                session.renderPage(0, 1200)
            } catch (_: IllegalStateException) {
                rejectedResult.set(true)
            }
        }
        assertTrue(backend.started.await(5, TimeUnit.SECONDS))
        session.closeAsync()
        session.closeAsync()
        backend.release.countDown()
        withTimeout(5000) { job.join() }
        assertTrue(backend.closed.await(5, TimeUnit.SECONDS))
        assertTrue(rejectedResult.get())
        assertFalse(backend.closeRacedWithRender.get())
        assertEquals(1, backend.closeCount.get())
        assertTrue(backend.lastBitmap?.isRecycled == true)
    }

    /** 调用方取消后仍安全收尾，不能泄漏未交付UI的Bitmap或native资源。 */
    @Test
    fun cancelledRenderDiscardsUndeliveredBitmapAndCanBeClosed() = runBlocking {
        val backend = RecordingPdfPageBackend(blockRender = true)
        val session = PdfPreviewRenderer(backend)
        val job = launch(Dispatchers.IO) { session.renderPage(0, 1200) }
        try {
            assertTrue(backend.started.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            backend.release.countDown()
            withTimeout(5000) { job.join() }
            session.closeAsync()
        }
        assertTrue(backend.closed.await(5, TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
        assertTrue(backend.lastBitmap?.isRecycled == true)
        assertFalse(backend.closeRacedWithRender.get())
    }
}

/** 与真实native后端相同的资源契约，用计数和latch验证顺序而非毫秒性能推断。 */
private class RecordingPdfPageBackend(private val blockRender: Boolean = false) : PdfPageBackend {
    /** 测试大文档的声明页数，不预先分配任何Bitmap。 */
    override val pageCount: Int = 1000
    /** 实际进入native渲染次数。 */
    val renderCount = AtomicInteger()
    /** native资源关闭次数。 */
    val closeCount = AtomicInteger()
    /** 测试线程确认render已经开始。 */
    val started = CountDownLatch(1)
    /** 模拟不可中断native操作的结束边界。 */
    val release = CountDownLatch(1)
    /** 通知异步关闭已经完成。 */
    val closed = CountDownLatch(1)
    /** 检测资源是否被并发关闭。 */
    val closeRacedWithRender = AtomicBoolean(false)
    /** 模拟native临界区。 */
    private val rendering = AtomicBoolean(false)
    /** 最近一次分配的测试图像，用于验证取消结果被回收。 */
    @Volatile var lastBitmap: Bitmap? = null

    /** 分配小Bitmap模拟完整页面，保持比例独立于显示宽度。 */
    override fun renderPage(index: Int, displayWidthPixels: Int): PdfRenderedPage {
        rendering.set(true)
        try {
            renderCount.incrementAndGet()
            started.countDown()
            if (blockRender) check(release.await(5, TimeUnit.SECONDS))
            return PdfRenderedPage(Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888).also { lastBitmap = it }, 0.5f)
        } finally {
            rendering.set(false)
        }
    }

    /** 验证与render互斥，且只关闭一次。 */
    override fun close() {
        if (rendering.get()) closeRacedWithRender.set(true)
        closeCount.incrementAndGet()
        closed.countDown()
    }
}
