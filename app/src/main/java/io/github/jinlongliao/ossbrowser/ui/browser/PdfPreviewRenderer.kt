package io.github.jinlongliao.ossbrowser.ui.browser

import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 单个PDF预览的native资源会话，只按可见页请求渲染，不预先创建所有页的Bitmap。
 * 访问通过mutex串行化；关闭只发起后台收尾，不在主线程等待正在执行的native渲染。
 * @param backend 由此会话独占的native资源；不能在会话之外调用或关闭。
 */
internal class PdfPreviewRenderer internal constructor(private val backend: PdfPageBackend) {
    /** 总页数用于按需创建列表项，不代表已经渲染所有页面。 */
    val pageCount: Int = backend.pageCount

    /** native openPage/render/close 必须互斥。 */
    private val renderMutex = Mutex()

    /** 关闭请求只执行一次，拒绝关闭后新发起的渲染。 */
    private val closing = AtomicBoolean(false)

    /** 收尾完成通知，供需要确认句柄已释放的内部调用者等待；UI销毁不阻塞等待。 */
    private val closed = CompletableDeferred<Unit>()

    /** 仅负责资源收尾，不依赖已销毁的Compose协程作用域。 */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 只渲染所请求页面。取消不能打断已进入的native render，但会阻止结果交给已关闭页面。
     * 未交付Bitmap立即回收；已交付UI的Bitmap只释放引用，不主动recycle。
     */
    suspend fun renderPage(index: Int, widthPixels: Int): PdfRenderedPage {
        var allocated: PdfRenderedPage? = null
        try {
            return withContext(Dispatchers.Default) {
                renderMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    check(!closing.get()) { "PDF预览已关闭" }
                    val page = backend.renderPage(index, widthPixels)
                    allocated = page
                    currentCoroutineContext().ensureActive()
                    check(!closing.get()) { "PDF预览已关闭" }
                    page
                }
            }
        } catch (exception: Throwable) {
            allocated?.bitmap?.recycle()
            throw exception
        }
    }

    /** 发起幂等关闭，等待已进入的render退出后在IO释放句柄，不阻塞UI。 */
    fun closeAsync() {
        if (!closing.compareAndSet(false, true)) return
        cleanupScope.launch {
            try {
                renderMutex.withLock { backend.close() }
            } catch (exception: Exception) {
                Log.e("PdfPreviewRenderer", "关闭PDF资源失败 pageCount=$pageCount", exception)
            } finally {
                closed.complete(Unit)
                cleanupScope.cancel()
            }
        }
    }

    /** 等待已请求的后台收尾完成；不得在UI同步阻塞调用。 */
    internal suspend fun awaitClosed() {
        closed.await()
    }

    companion object {
        /** 调用方须在IO线程打开资源，随后由会话管理全部生命周期。 */
        fun open(file: File): PdfPreviewRenderer = PdfPreviewRenderer(AndroidPdfPageBackend.open(file))
    }
}
