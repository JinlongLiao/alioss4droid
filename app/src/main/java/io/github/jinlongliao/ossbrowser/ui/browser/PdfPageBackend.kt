package io.github.jinlongliao.ossbrowser.ui.browser

import android.graphics.Bitmap

/** 已渲染的完整页面；显示比例独立于降采样后的Bitmap整数尺寸。 */
internal data class PdfRenderedPage(
    /** 整页图像，交付UI之后由Compose/GC管理，不能在关闭renderer时主动回收。 */
    val bitmap: Bitmap,
    /** 原始页面宽/高比例，用于保持布局与文件内容比例。 */
    val aspectRatio: Float
)

/** native PDF资源边界；所有方法由预览会话串行调用，便于验证生命周期和关闭竞态。 */
internal interface PdfPageBackend : AutoCloseable {
    /** 只读总页数，读取此值不应预先渲染所有页面。 */
    val pageCount: Int

    /** 渲染一页完整内容，不得裁剪；原生异常必须向上传播。 */
    fun renderPage(index: Int, displayWidthPixels: Int): PdfRenderedPage

    /** 释放native句柄和文件描述符，仅在全部已进入的render结束后调用。 */
    override fun close()
}
