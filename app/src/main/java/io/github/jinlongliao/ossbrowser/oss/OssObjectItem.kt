/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jinlongliao.ossbrowser.oss

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
