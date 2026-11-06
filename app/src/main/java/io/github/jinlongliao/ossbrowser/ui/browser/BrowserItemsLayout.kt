package io.github.jinlongliao.ossbrowser.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.data.BrowserLayoutMode

/**
 * Bucket 和对象共用的惰性布局；仅改变排布，不复制文件操作和网络加载逻辑。
 *
 * @param entries 已加载的显示数据，顺序由调用方的排序规则决定。
 * @param layoutMode 全局布局偏好。
 * @param itemKey 资源唯一键；内部添加前缀，避免与底部分页项碰撞。
 * @param bottomPadding 为浮动操作按钮预留的底部空间。
 * @param listState 单列滚动状态。
 * @param waterfallState 瀑布流滚动状态，供路径定位与翻页观察使用。
 * @param footer 分页信息；瀑布流中跨两列显示。
 * @param itemContent 单项绘制，第二个参数表示是否使用紧凑的竖向卡片。
 * @see <a href="https://developer.android.com/develop/ui/compose/lists">Compose lazy lists and grids</a>
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> BrowserItemsLayout(
    entries: List<T>,
    layoutMode: BrowserLayoutMode,
    itemKey: (T) -> String,
    bottomPadding: Dp,
    listState: LazyListState = rememberLazyListState(),
    waterfallState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T, Boolean) -> Unit
) {
    if (layoutMode == BrowserLayoutMode.WATERFALL) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = waterfallState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            staggeredItems(entries, key = { "entry:${itemKey(it)}" }) { itemContent(it, true) }
            if (footer != null) {
                item(key = "browser-footer", span = StaggeredGridItemSpan.FullLine) { footer() }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            items(entries, key = { "entry:${itemKey(it)}" }) { itemContent(it, false) }
            if (footer != null) {
                item(key = "browser-footer") { footer() }
            }
        }
    }
}
