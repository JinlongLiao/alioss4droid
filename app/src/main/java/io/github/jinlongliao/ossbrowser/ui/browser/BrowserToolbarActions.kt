package io.github.jinlongliao.ossbrowser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField

/** 顶栏更多菜单与 Apple 端共用操作顺序；排序沿用已有的持久化及重载流程。 */
@Composable
fun BrowserToolbarActions(
    hasActiveProfile: Boolean,
    inBucketView: Boolean,
    sortField: OssObjectSortField,
    sortDirection: OssObjectSortDirection,
    activeUploadCount: Int,
    onRefresh: () -> Unit,
    onSortChange: (OssObjectSortField, OssObjectSortDirection) -> Unit,
    onCreateFolder: () -> Unit,
    onOpenUploads: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val dismissMenu = {
        menuOpen = false
        showSort = false
    }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            BadgedBox(badge = {
                if (activeUploadCount > 0) Badge { Text(activeUploadCount.toString()) }
            }) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = if (activeUploadCount > 0) {
                        stringResource(R.string.browser_more_with_uploads, activeUploadCount)
                    } else stringResource(R.string.browser_more_actions)
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = dismissMenu,
            modifier = Modifier.width(288.dp)
        ) {
            if (showSort && inBucketView) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_sort)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    onClick = { showSort = false }
                )
                HorizontalDivider()
                BrowserSortOptions(sortField, sortDirection) { field, direction ->
                    dismissMenu()
                    onSortChange(field, direction)
                }
            } else {
                if (inBucketView) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_mkdir)) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { dismissMenu(); onCreateFolder() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_sort)) },
                        leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                        onClick = { showSort = true }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_refresh)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    enabled = hasActiveProfile,
                    onClick = { dismissMenu(); onRefresh() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.upload_tasks)) },
                    leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    trailingIcon = {
                        if (activeUploadCount > 0) Badge { Text(activeUploadCount.toString()) }
                    },
                    onClick = { dismissMenu(); onOpenUploads() }
                )
            }
        }
    }
}

/** 当前支持的对象排序组合；点击项先交给上层保存，再沿用已有的重载及滚动流程。 */
@Composable
private fun BrowserSortOptions(
    selectedField: OssObjectSortField,
    selectedDirection: OssObjectSortDirection,
    onSelect: (OssObjectSortField, OssObjectSortDirection) -> Unit
) {
    OssObjectSortField.entries.forEach { field ->
        OssObjectSortDirection.entries.forEach { direction ->
            val labelResource = when (field) {
                OssObjectSortField.NAME -> when (direction) {
                    OssObjectSortDirection.ASCENDING -> R.string.browser_sort_name_ascending
                    OssObjectSortDirection.DESCENDING -> R.string.browser_sort_name_descending
                }
                OssObjectSortField.MODIFIED_TIME -> when (direction) {
                    OssObjectSortDirection.ASCENDING -> R.string.browser_sort_time_ascending
                    OssObjectSortDirection.DESCENDING -> R.string.browser_sort_time_descending
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(labelResource)) },
                leadingIcon = {
                    RadioButton(selected = field == selectedField && direction == selectedDirection, onClick = null)
                },
                onClick = { onSelect(field, direction) }
            )
        }
    }
}
