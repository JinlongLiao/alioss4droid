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

/**
 * 按用户偏好排序 OSS 对象。
 *
 * 目录始终位于文件之前，并按名称和所选方向排列。文件可按名称或修改时间排列；修改时间未知的
 * 文件固定放在同组末尾，避免正序/倒序切换时未知值穿插在有效时间中。
 *
 * @param items 待排序对象；不会修改原列表。
 * @param field 文件排序字段。
 * @param direction 排序方向。
 * @return 新的已排序列表。
 */
fun sortOssObjectItems(
    items: List<OssObjectItem>,
    field: OssObjectSortField,
    direction: OssObjectSortDirection,
): List<OssObjectItem> {
    val folders = items.filterIsInstance<OssObjectItem.Folder>()
        .sortedWith(displayNameComparator(direction))
    val files = items.filterIsInstance<OssObjectItem.File>()
        .sortedWith(fileComparator(field, direction))
    return folders + files
}

/** 根据方向构造不区分大小写的展示名称比较器。 */
private fun <T : OssObjectItem> displayNameComparator(
    direction: OssObjectSortDirection,
): Comparator<T> = Comparator { left, right ->
    val nameResult = left.displayName.compareTo(right.displayName, ignoreCase = true)
    // 大小写不敏感同名和同时间文件也要有稳定次序，避免分页合并/缓存恢复后随机换位。
    applySortDirection(if (nameResult != 0) nameResult else left.key.compareTo(right.key), direction)
}

/** 根据字段与方向构造文件比较器，并将未知修改时间固定放在末尾。 */
private fun fileComparator(
    field: OssObjectSortField,
    direction: OssObjectSortDirection,
): Comparator<OssObjectItem.File> = Comparator { left, right ->
    when (field) {
        OssObjectSortField.NAME -> displayNameComparator<OssObjectItem.File>(direction).compare(left, right)
        OssObjectSortField.MODIFIED_TIME -> {
            val leftTime = left.lastModified
            val rightTime = right.lastModified
            when {
                leftTime == null && rightTime == null ->
                    displayNameComparator<OssObjectItem.File>(direction).compare(left, right)

                leftTime == null -> 1
                rightTime == null -> -1
                else -> {
                    val timeResult = applySortDirection(leftTime.compareTo(rightTime), direction)
                    if (timeResult != 0) {
                        timeResult
                    } else {
                        displayNameComparator<OssObjectItem.File>(direction).compare(left, right)
                    }
                }
            }
        }
    }
}

/** 将基础升序比较结果转换为用户选择的方向。 */
private fun applySortDirection(
    ascendingResult: Int,
    direction: OssObjectSortDirection,
): Int = when (direction) {
    OssObjectSortDirection.ASCENDING -> ascendingResult
    OssObjectSortDirection.DESCENDING -> -ascendingResult
}
