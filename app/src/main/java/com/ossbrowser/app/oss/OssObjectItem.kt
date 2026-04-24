package com.ossbrowser.app.oss

import java.util.Date

/** 账号下的一个 bucket。 */
data class OssBucketInfo(
    val name: String,
    val location: String?,
    val createDate: Date?,
    val extranetEndpoint: String?,
    val storageClass: String?
)

/** 列表中的一项：目录（prefix）或文件（object）。 */
sealed class OssObjectItem {
    abstract val key: String
    abstract val displayName: String

    data class Folder(
        override val key: String
    ) : OssObjectItem() {
        override val displayName: String =
            key.trimEnd('/').substringAfterLast('/', key.trimEnd('/'))
    }

    data class File(
        override val key: String,
        val size: Long,
        val lastModified: Date?,
        val etag: String?
    ) : OssObjectItem() {
        override val displayName: String = key.substringAfterLast('/', key)
    }
}
