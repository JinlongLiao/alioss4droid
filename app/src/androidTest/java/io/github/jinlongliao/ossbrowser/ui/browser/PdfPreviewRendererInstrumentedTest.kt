package io.github.jinlongliao.ossbrowser.ui.browser

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真机仅生成、渲染和删除应用私有cache中的PDF样本，禁止调用网络或真实OSS/S3文件接口。 */
@RunWith(AndroidJUnit4::class)
class PdfPreviewRendererInstrumentedTest {
    /** 40页文档按需渲染首末两页，检查完整上下角标、请求页序、尺寸和后台资源关闭。 */
    @Test
    fun rendersRequestedLocalPdfPagesWithoutCroppingOrEagerAllocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("local-pdf-test-", ".pdf", context.cacheDir)
        var session: PdfPreviewRenderer? = null
        try {
            withContext(Dispatchers.IO) { writeLocalPdfSample(file) }
            val opened = withContext(Dispatchers.IO) { PdfPreviewRenderer.open(file) }
            session = opened
            assertEquals(40, opened.pageCount)
            val first = opened.renderPage(0, 1200)
            val last = opened.renderPage(39, 1200)
            try {
                assertEquals(1200, first.bitmap.width)
                assertEquals(1698, first.bitmap.height)
                assertEquals(595f / 842f, first.aspectRatio, 0.000001f)
                assertEquals(Color.RED, first.bitmap.getPixel(40, 40))
                assertEquals(Color.GREEN, last.bitmap.getPixel(40, 40))
                assertEquals(Color.BLUE, first.bitmap.getPixel(1159, 1657))
                assertEquals(Color.BLUE, last.bitmap.getPixel(1159, 1657))
                assertTrue(first.bitmap.width.toLong() * first.bitmap.height <= MAX_PDF_PAGE_PIXELS)
            } finally {
                // 测试持有的Bitmap没有交给Compose/GPU，断言结束后可以显式回收。
                first.bitmap.recycle()
                last.bitmap.recycle()
            }
        } finally {
            session?.closeAsync()
            withTimeout(10_000) { session?.awaitClosed() }
            file.delete()
        }
    }

    /** 本地PDF每页的左上角按页号变色，右下角保持蓝色，便于证明无裁剪且页序正确。 */
    private fun writeLocalPdfSample(file: File) {
        val document = PdfDocument()
        try {
            val paint = Paint()
            repeat(40) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                paint.color = if (index % 2 == 0) Color.RED else Color.GREEN
                page.canvas.drawRect(5f, 5f, 45f, 45f, paint)
                paint.color = Color.BLUE
                page.canvas.drawRect(550f, 797f, 590f, 837f, paint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
