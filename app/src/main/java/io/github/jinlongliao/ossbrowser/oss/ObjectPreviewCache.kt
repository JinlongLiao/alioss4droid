package io.github.jinlongliao.ossbrowser.oss

import java.security.MessageDigest

/**
 * PDF 随机读取缓存使用账号、Bucket、key 和版本元数据隔离，不能跨账号或覆盖版本复用。
 * 摘要文件名固定长度，不将远端文件名拼进本地路径，避免超长文件名和路径字符问题。
 * @param profileId 发起预览时的账号 ID，不包含凭据。
 * @param bucket 发起预览时的 Bucket。
 * @param item 服务端返回的对象元数据。
 * @return 固定长度、包含版本隔离信息的 PDF 缓存文件名。
 */
fun pdfObjectPreviewCacheName(profileId: String, bucket: String, item: OssObjectItem.File): String {
    val identity = listOf(profileId, bucket, item.key, item.etag.orEmpty(), item.size.toString(),
        item.lastModified?.time?.toString().orEmpty())
        .joinToString("") { "${it.length}:$it" }
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "preview_$digest.pdf"
}

/** 缓存必须完整且有版本依据；没有ETag/修改时间时宁可重新读取，不能猜测是同一版本。 */
fun canReusePdfObjectPreviewCache(item: OssObjectItem.File, cachedLength: Long): Boolean =
    cachedLength > 0 && (!item.etag.isNullOrBlank() || item.lastModified != null) &&
        (item.size <= 0 || cachedLength == item.size)
