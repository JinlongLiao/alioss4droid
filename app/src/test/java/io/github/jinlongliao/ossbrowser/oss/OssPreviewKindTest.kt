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

import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件扩展名到预览类型的回归测试。 */
class OssPreviewKindTest {

    /** HTML 家族应进入网页渲染流程，不再作为普通源码文本预览。 */
    @Test
    fun previewKind_htmlExtensions_useHtmlPreview() {
        listOf("index.html", "page.HTM", "document.xhtml").forEach { name ->
            val item = OssObjectItem.File(
                key = "site/$name",
                size = 1L,
                lastModified = null,
                etag = null
            )

            assertEquals(OssPreviewKind.HTML, item.previewKind())
        }
    }

    /** CSS 和 JavaScript 仍按源码文本展示，避免脱离 HTML 上下文执行。 */
    @Test
    fun previewKind_webAssets_remainTextPreview() {
        listOf("theme.css", "app.js").forEach { name ->
            val item = OssObjectItem.File(
                key = "site/$name",
                size = 1L,
                lastModified = null,
                etag = null
            )

            assertEquals(OssPreviewKind.TEXT, item.previewKind())
        }
    }
}
