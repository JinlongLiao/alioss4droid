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

import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/** OSS 对象列表排序规则的单元测试。 */
class OssObjectSortTest {

    /** 名称排序无论方向如何都应保持目录在文件之前。 */
    @Test
    fun sortOssObjectItems_nameDirection_keepsFoldersFirst() {
        val items = listOf(
            file("b.txt", 2L),
            folder("z-dir/"),
            file("a.txt", 1L),
            folder("a-dir/")
        )

        val ascending = sortOssObjectItems(
            items,
            OssObjectSortField.NAME,
            OssObjectSortDirection.ASCENDING
        )
        val descending = sortOssObjectItems(
            items,
            OssObjectSortField.NAME,
            OssObjectSortDirection.DESCENDING
        )

        assertEquals(listOf("a-dir/", "z-dir/", "a.txt", "b.txt"), ascending.map { it.key })
        assertEquals(listOf("z-dir/", "a-dir/", "b.txt", "a.txt"), descending.map { it.key })
    }

    /** 修改时间排序应按方向排列有效时间，并把未知时间文件固定放在末尾。 */
    @Test
    fun sortOssObjectItems_modifiedTimeDirection_placesUnknownTimeLast() {
        val items = listOf(
            file("unknown.txt", null),
            file("new.txt", 3L),
            folder("folder/"),
            file("old.txt", 1L)
        )

        val ascending = sortOssObjectItems(
            items,
            OssObjectSortField.MODIFIED_TIME,
            OssObjectSortDirection.ASCENDING
        )
        val descending = sortOssObjectItems(
            items,
            OssObjectSortField.MODIFIED_TIME,
            OssObjectSortDirection.DESCENDING
        )

        assertEquals(listOf("folder/", "old.txt", "new.txt", "unknown.txt"), ascending.map { it.key })
        assertEquals(listOf("folder/", "new.txt", "old.txt", "unknown.txt"), descending.map { it.key })
    }

    /** 同时间且只在大小写上不同的文件，在缓存/分页输入顺序变化后仍有确定次序。 */
    @Test
    fun identicalTimesAndCaseInsensitiveNamesHaveStableOrder() {
        val items = listOf(file("a.txt", 1), file("A.txt", 1))
        val first = sortOssObjectItems(items, OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING)
        val reversedInput = sortOssObjectItems(items.reversed(), OssObjectSortField.MODIFIED_TIME, OssObjectSortDirection.DESCENDING)
        assertEquals(first, reversedInput)
    }

    /** 创建测试目录对象。 */
    private fun folder(key: String): OssObjectItem.Folder = OssObjectItem.Folder(key)

    /** 创建测试文件对象；[modifiedAtMillis] 为空表示 OSS 未返回修改时间。 */
    private fun file(key: String, modifiedAtMillis: Long?): OssObjectItem.File = OssObjectItem.File(
        key = key,
        size = 1L,
        lastModified = modifiedAtMillis?.let(::Date),
        etag = null
    )
}
