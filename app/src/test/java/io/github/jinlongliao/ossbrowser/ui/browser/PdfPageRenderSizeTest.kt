package io.github.jinlongliao.ossbrowser.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 单页内存预算测试不分配大Bitmap，也不使用易抖动的执行时间阈值。 */
class PdfPageRenderSizeTest {
    /** 普通A4页保留原显示宽度和比例，不进行无必要的降采样。 */
    @Test
    fun normalPageKeepsOriginalRenderingWidth() {
        val size = pdfPageRenderSize(595, 842, 1200)
        assertEquals(1200, size.width)
        assertEquals(1698, size.height)
    }

    /** 大页面等比降采样，既限制ARGB总像素，也限制native纹理单边。 */
    @Test
    fun hugePagesFitPixelAndTextureBudgets() {
        listOf(
            Triple(1, Int.MAX_VALUE, 1200),
            Triple(Int.MAX_VALUE, 1, 1200),
            Triple(100, 100, 100_000),
            Triple(10, 100_000, 1200)
        ).forEach { (width, height, target) ->
            val rendered = pdfPageRenderSize(width, height, target)
            assertTrue(rendered.width in 1..MAX_PDF_BITMAP_EDGE)
            assertTrue(rendered.height in 1..MAX_PDF_BITMAP_EDGE)
            assertTrue(rendered.width.toLong() * rendered.height <= MAX_PDF_PAGE_PIXELS)
        }
    }

    /** 大方页降采样后仍是方形，完整内容不裁剪；UI保持源比例而非改变显示宽度。 */
    @Test
    fun largeSquarePageIsDownsampledWithoutChangingAspect() {
        val rendered = pdfPageRenderSize(1000, 1000, 10_000)
        assertEquals(rendered.width, rendered.height)
        assertTrue(rendered.width < 10_000)
    }

    /** 普通页面保持铺满宽度；布局比例独立于Bitmap降采样取整。 */
    @Test
    fun normalPageDisplayRetainsOriginalAspectRatio() {
        val size = pdfPageDisplaySize(595f / 842f, 1200f)
        assertEquals(1200f, size.width, 0.01f)
        assertEquals(595f / 842f, size.width / size.height, 0.00001f)
        assertTrue(!size.reduced)
    }

    /** 极瘦长页等比缩小，既不会撑爆Compose约束，也不裁掉尾部内容。 */
    @Test
    fun extremePageDisplayFitsSafeLayoutWithoutCropping() {
        val size = pdfPageDisplaySize(1f / 14_400f, 1200f)
        assertTrue(size.reduced)
        assertTrue(size.width in 1f..MAX_PDF_LAYOUT_EDGE.toFloat())
        assertEquals(MAX_PDF_LAYOUT_EDGE.toFloat(), size.height, 0.01f)
        assertEquals(1f / 14_400f, size.width / size.height, 0.000001f)
        val impossibleRatio = pdfPageDisplaySize(1f / Int.MAX_VALUE, 1200f)
        assertTrue(impossibleRatio.width >= 1f && impossibleRatio.height <= MAX_PDF_LAYOUT_EDGE)
    }

    /** 原生异常尺寸不能引发整数溢出或尝试分配非法Bitmap。 */
    @Test(expected = IllegalArgumentException::class)
    fun invalidPageDimensionsAreRejectedBeforeAllocation() {
        pdfPageRenderSize(0, 100, 1200)
    }
}
