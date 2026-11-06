package io.github.jinlongliao.ossbrowser.oss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Date

/** 验证 PDF 缓存跨账号/路径/版本隔离，并保持相同对象可复用缓存。 */
class ObjectPreviewCacheTest {
    /** 多维元数据不同不能复用旧内容。 */
    @Test
    fun accountsBucketsAndVersionsUseDifferentFiles() {
        val item = OssObjectItem.File("docs/report.pdf", 100, Date(10), "etag-a")
        val baseline = pdfObjectPreviewCacheName("profile-a", "bucket-a", item)
        assertNotEquals(baseline, pdfObjectPreviewCacheName("profile-b", "bucket-a", item))
        assertNotEquals(baseline, pdfObjectPreviewCacheName("profile-a", "bucket-b", item))
        assertNotEquals(baseline, pdfObjectPreviewCacheName("profile-a", "bucket-a", item.copy(etag = "etag-b")))
        assertNotEquals(baseline, pdfObjectPreviewCacheName("profile-a", "bucket-a", item.copy(lastModified = Date(20))))
        assertEquals(baseline, pdfObjectPreviewCacheName("profile-a", "bucket-a", item.copy()))
    }

    /** 长远端名称不会被直接写入路径，摘要也不包含账号或业务文件名。 */
    @Test
    fun cacheNameHasBoundedLengthAndNoPathComponents() {
        val item = OssObjectItem.File("../" + "长".repeat(300) + ".pdf", 100, Date(10), "etag")
        val name = pdfObjectPreviewCacheName("profile", "bucket", item)
        assertEquals(76, name.length)
        assertFalse(name.contains('/'))
        assertFalse(name.contains("长"))
    }
    /** 没有版本元数据或只有半成品时不能复用缓存；完整同版本文件仍可复用。 */
    @Test
    fun cacheReuseRequiresCompleteVersionedContent() {
        val item = OssObjectItem.File("report.pdf", 100, Date(10), "etag")
        assertEquals(true, canReusePdfObjectPreviewCache(item, 100))
        assertEquals(false, canReusePdfObjectPreviewCache(item, 10))
        assertEquals(false, canReusePdfObjectPreviewCache(item, 0))
        assertEquals(false, canReusePdfObjectPreviewCache(item.copy(etag = null, lastModified = null), 100))
    }
}
