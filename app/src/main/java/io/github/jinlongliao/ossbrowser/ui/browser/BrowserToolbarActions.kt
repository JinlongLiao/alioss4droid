package io.github.jinlongliao.ossbrowser.ui.browser

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.jinlongliao.ossbrowser.data.BrowserLayoutMode
import io.github.jinlongliao.ossbrowser.data.OssObjectSortDirection
import io.github.jinlongliao.ossbrowser.data.OssObjectSortField

/**
 * 文件页只常驻刷新与更多按钮；其他入口按文件传输、浏览视图和配置分组，避免占据文件区。
 * 弹出菜单使用同一锚点切换主菜单与视图设置页，先关闭菜单再执行原有业务回调。
 *
 * @param hasActiveProfile 是否有激活配置，决定刷新和视图设置能否使用。
 * @param inBucketView 是否位于 Bucket 内；只有此时展示上传、新建目录、路径与对象排序。
 * @param layoutMode 已持久化的全局排布，菜单不维护独立的临时偏好。
 * @param sortField 已保存的文件排序字段。
 * @param sortDirection 已保存的文件排序方向。
 * @param activeUploadCount 正在执行或排队的上传数量；大于零时在入口与任务项显示角标。
 * @param onRefresh 刷新当前目录或 Bucket 列表。
 * @param onLayoutChange 保存全局排布偏好。
 * @param onSortChange 保存排序并按原流程重载对象。
 * @param onUploadFile 打开系统文件选择器。
 * @param onCreateFolder 打开新建目录表单。
 * @param onJumpToPath 打开路径定位表单。
 * @param onOpenProfiles 导航到对象存储配置页。
 * @param onOpenUploads 打开已有上传任务列表，不依赖当前目录或激活配置。
 */
@Composable
fun BrowserToolbarActions(
    hasActiveProfile: Boolean,
    inBucketView: Boolean,
    layoutMode: BrowserLayoutMode,
    sortField: OssObjectSortField,
    sortDirection: OssObjectSortDirection,
    activeUploadCount: Int,
    onRefresh: () -> Unit,
    onLayoutChange: (BrowserLayoutMode) -> Unit,
    onSortChange: (OssObjectSortField, OssObjectSortDirection) -> Unit,
    onUploadFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onJumpToPath: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenUploads: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showViewSettings by remember { mutableStateOf(false) }
    // 关闭时同时清除菜单层级，下次始终从分组主菜单进入。
    val dismissMenu = {
        menuOpen = false
        showViewSettings = false
    }
    IconButton(onClick = onRefresh, enabled = hasActiveProfile) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
    }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            BadgedBox(badge = {
                if (activeUploadCount > 0) Badge { Text(activeUploadCount.toString()) }
            }) {
                Icon(
                    Icons.Outlined.MoreVert,
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
            if (showViewSettings && hasActiveProfile) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_view_settings)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    onClick = { showViewSettings = false }
                )
                HorizontalDivider()
                BrowserMenuSection(R.string.browser_layout_section)
                BrowserLayoutSelector(selected = layoutMode, onSelect = {
                    dismissMenu()
                    onLayoutChange(it)
                })
                if (inBucketView) {
                    HorizontalDivider()
                    BrowserMenuSection(R.string.browser_sort)
                    BrowserSortOptions(sortField, sortDirection) { field, direction ->
                        dismissMenu()
                        onSortChange(field, direction)
                    }
                }
            } else {
                BrowserMenuSection(R.string.browser_files_transfers)
                if (inBucketView) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_upload_file)) },
                        leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        onClick = { dismissMenu(); onUploadFile() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_mkdir)) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { dismissMenu(); onCreateFolder() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.upload_tasks)) },
                    leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    trailingIcon = {
                        if (activeUploadCount > 0) Badge { Text(activeUploadCount.toString()) }
                    },
                    onClick = { dismissMenu(); onOpenUploads() }
                )
                if (hasActiveProfile) {
                    HorizontalDivider()
                    BrowserMenuSection(R.string.browser_browsing_section)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.browser_view_settings))
                                Text(
                                    stringResource(if (layoutMode == BrowserLayoutMode.LIST) {
                                        R.string.browser_layout_list
                                    } else R.string.browser_layout_waterfall),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.ViewModule, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                        onClick = { showViewSettings = true }
                    )
                    if (inBucketView) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_jump_path)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            onClick = { dismissMenu(); onJumpToPath() }
                        )
                    }
                }
                HorizontalDivider()
                BrowserMenuSection(R.string.browser_configuration_section)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profiles_title)) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                    onClick = { dismissMenu(); onOpenProfiles() }
                )
            }
        }
    }
}

/** 非交互的菜单分组标题；不伪装成可点击操作，分组文案由三语言资源提供。 */
@Composable
private fun BrowserMenuSection(@StringRes titleResource: Int) {
    Text(
        stringResource(titleResource),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
