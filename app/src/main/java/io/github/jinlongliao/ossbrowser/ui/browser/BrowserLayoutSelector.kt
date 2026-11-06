package io.github.jinlongliao.ossbrowser.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.BrowserLayoutMode

/**
 * 并排显示两种排布及当前选中项；选择通过设置仓库保存，不在组件内维护临时偏好。
 * @param selected 当前全局布局。
 * @param onSelect 用户选中的布局，由调用方持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserLayoutSelector(selected: BrowserLayoutMode, onSelect: (BrowserLayoutMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BrowserLayoutMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, BrowserLayoutMode.entries.size)
            ) {
                Text(stringResource(if (mode == BrowserLayoutMode.LIST) {
                    R.string.browser_layout_list
                } else R.string.browser_layout_waterfall))
            }
        }
    }
}
