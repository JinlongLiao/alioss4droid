package io.github.jinlongliao.ossbrowser.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Android PdfRenderer的资源适配，构造、渲染和关闭均由会话放在后台线程。 */
internal class AndroidPdfPageBackend private constructor(
    /** 原始文件的随机读取句柄。 */
    private val descriptor: ParcelFileDescriptor,
    /** 只能串行打开Page的native渲染器。 */
    private val renderer: PdfRenderer
) : PdfPageBackend {
    /** 初始化只读取页数，禁止预分配页面Bitmap。 */
    override val pageCount: Int = renderer.pageCount

    /**
     * 原生render使用完整Page，超大页面只降采样不裁剪；失败时回收未交付Bitmap。
     * @see PdfRenderer.Page.render
     */
    override fun renderPage(index: Int, displayWidthPixels: Int): PdfRenderedPage =
        renderer.openPage(index).use { page ->
            val size = pdfPageRenderSize(page.width, page.height, displayWidthPixels)
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfRenderedPage(bitmap, page.width.toFloat() / page.height.toFloat())
            } catch (exception: Throwable) {
                bitmap.recycle()
                throw exception
            }
        }

    /** 即使renderer关闭失败，也必须释放文件描述符。 */
    override fun close() {
        try {
            renderer.close()
        } finally {
            descriptor.close()
        }
    }

    companion object {
        /** 调用者须在IO线程打开；构造失败时确保文件描述符不会泄漏。 */
        fun open(file: File): AndroidPdfPageBackend {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                AndroidPdfPageBackend(descriptor, PdfRenderer(descriptor))
            } catch (exception: Throwable) {
                descriptor.close()
                throw exception
            }
        }
    }
}
