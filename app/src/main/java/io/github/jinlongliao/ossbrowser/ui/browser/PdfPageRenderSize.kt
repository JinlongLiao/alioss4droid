package io.github.jinlongliao.ossbrowser.ui.browser

import kotlin.math.sqrt

/** 单页ARGB像素预算约32MB，不裁剪内容；超大页面内部等比降采样后仍按原比例显示。 */
internal const val MAX_PDF_PAGE_PIXELS = 8_000_000L

/** 保守限制Bitmap单边，降低移动设备native纹理维度超限的风险。 */
internal const val MAX_PDF_BITMAP_EDGE = 4_096

/** PDF实际渲染分辨率，不等于UI显示尺寸。 */
internal data class PdfPageRenderSize(
    /** 采样后的Bitmap宽度，单位像素，至少1。 */
    val width: Int,
    /** 采样后的Bitmap高度，单位像素，至少1。 */
    val height: Int
)

/**
 * 普通页面沿用原显示宽度；超大页面按像素预算和单边预算等比降采样，不裁剪文件内容。
 * 使用Double计算乘积避免极端页面尺寸溢出；UI独立使用原始页面比例，不能用取整后的Bitmap比例。
 */
internal fun pdfPageRenderSize(pageWidth: Int, pageHeight: Int, displayWidthPixels: Int): PdfPageRenderSize {
    require(pageWidth > 0 && pageHeight > 0 && displayWidthPixels > 0) { "PDF页面尺寸无效" }
    val width = displayWidthPixels.toDouble()
    val height = width * pageHeight.toDouble() / pageWidth.toDouble()
    val scale = minOf(
        1.0,
        MAX_PDF_BITMAP_EDGE / maxOf(width, height),
        sqrt(MAX_PDF_PAGE_PIXELS.toDouble() / width / height)
    )
    return PdfPageRenderSize(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1)
    )
}

/** 保守低于Compose约束编码上限，避免极瘦长PDF按屏宽展开成数百万像素的布局。 */
internal const val MAX_PDF_LAYOUT_EDGE = 16_384

/** 显示尺寸按原始页面比例计算，与受预算限制的Bitmap尺寸分离。 */
internal data class PdfPageDisplaySize(
    /** 最终布局宽度，像素；极端长页会整体缩小。 */
    val width: Float,
    /** 最终布局高度，像素；不超过Compose安全布局边界。 */
    val height: Float,
    /** 极端比例是否触发整体缩小，UI提示可下载原文件查看。 */
    val reduced: Boolean
)

/** 完整页面等比放进安全布局边界，不裁剪；普通页保持填满可用宽度的原有行为。 */
internal fun pdfPageDisplaySize(aspectRatio: Float, availableWidthPixels: Float): PdfPageDisplaySize {
    require(aspectRatio.isFinite() && aspectRatio > 0 && availableWidthPixels.isFinite() && availableWidthPixels > 0)
    val width = availableWidthPixels.toDouble()
    val height = width / aspectRatio
    val scale = minOf(1.0, MAX_PDF_LAYOUT_EDGE / maxOf(width, height))
    return PdfPageDisplaySize(
        (width * scale).toFloat().coerceAtLeast(1f),
        (height * scale).toFloat().coerceAtLeast(1f),
        scale < 1.0
    )
}
