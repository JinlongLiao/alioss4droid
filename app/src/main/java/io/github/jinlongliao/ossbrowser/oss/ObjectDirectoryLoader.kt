package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 单次扫描的元数据条目上限，避免 Android 内存和请求量无界增长；不读取文件内容。 */
const val MAX_SORTED_DIRECTORY_ITEMS = 10_000

/** 单次请求的服务端条目上限，完整扫描时减少小分页导致的往返次数。 */
private const val DIRECTORY_SCAN_PAGE_SIZE = 1_000

/** OSS/S3 只提供 key 正序分页，时间排序及名称倒序必须先取完整当前目录。 */
fun requiresCompleteObjectDirectory(field: OssObjectSortField, direction: OssObjectSortDirection): Boolean =
    field == OssObjectSortField.MODIFIED_TIME || direction == OssObjectSortDirection.DESCENDING

/**
 * 加载目录并按选定字段排序。名称正序保留原按需分页；其他排序在完整元数据返回后才发布结果。
 * 当前目录超过上限或服务端分页令牌循环时明确失败，不能把局部排序伪装成全局有序。
 *
 * @param field 排序字段。
 * @param direction 排序方向。
 * @param pageSize 名称正序时使用的页面大小。
 * @param ensureCurrent 在请求前后检查账号/目录/刷新代次；已过期时应抛出取消异常，阻止继续扫描。
 * @param fetchPage 当前目录的非递归元数据请求，不允许读取文件正文。
 * @return 已排序数据；完整排序的 nextMarker 必定为空。
 */
suspend fun loadSortedObjectDirectory(
    field: OssObjectSortField,
    direction: OssObjectSortDirection,
    pageSize: Int,
    ensureCurrent: () -> Unit,
    fetchPage: suspend (marker: String?, maxKeys: Int) -> OssService.ListPage
): OssService.ListPage = withContext(Dispatchers.Default) {
    val complete = requiresCompleteObjectDirectory(field, direction)
    val items = HashMap<String, OssObjectItem>(if (complete) DIRECTORY_SCAN_PAGE_SIZE else pageSize)
    val seenMarkers = HashSet<String>()
    var marker: String? = null
    var visitedItems = 0
    do {
        currentCoroutineContext().ensureActive()
        ensureCurrent()
        val page = fetchPage(marker, if (complete) DIRECTORY_SCAN_PAGE_SIZE else pageSize)
        currentCoroutineContext().ensureActive()
        ensureCurrent()
        visitedItems += page.items.size
        check(!complete || visitedItems <= MAX_SORTED_DIRECTORY_ITEMS) {
            "当前目录超过 $MAX_SORTED_DIRECTORY_ITEMS 条，无法完整排序；请切换为名称正序或进入子目录。"
        }
        page.items.forEach { items[it.key] = it }
        marker = page.nextMarker
        // 已读满上限且服务端仍有下一页时立即停止，不能为了确认超限再多读取一页。
        check(!complete || visitedItems < MAX_SORTED_DIRECTORY_ITEMS || marker == null) {
            "当前目录超过 $MAX_SORTED_DIRECTORY_ITEMS 条，无法完整排序；请切换为名称正序或进入子目录。"
        }
        check(!complete || page.items.isNotEmpty() || marker == null) {
            "目录分页返回空页但仍有下一页，无法确定完整排序结果，请刷新重试。"
        }
        if (marker != null) {
            check(seenMarkers.add(marker)) { "目录分页令牌重复，无法确定完整排序结果，请刷新重试。" }
        }
    } while (complete && marker != null)
    OssService.ListPage(sortOssObjectItems(items.values.toList(), field, direction), marker)
}
