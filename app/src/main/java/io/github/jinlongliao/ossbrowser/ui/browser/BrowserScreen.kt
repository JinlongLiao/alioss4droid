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
package io.github.jinlongliao.ossbrowser.ui.browser

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import java.io.File
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import io.github.jinlongliao.ossbrowser.ui.theme.LocalThemeVisualStyle
import io.github.jinlongliao.ossbrowser.ui.theme.themedCardSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import io.github.jinlongliao.ossbrowser.data.BrowserLayoutMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.github.jinlongliao.ossbrowser.ui.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.ui.CloudPageHeader
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.ui.TonalIcon
import io.github.jinlongliao.ossbrowser.oss.StorageIcons
import io.github.jinlongliao.ossbrowser.oss.OssBucketInfo
import io.github.jinlongliao.ossbrowser.oss.OssCnameInfo
import io.github.jinlongliao.ossbrowser.util.PresentationSlidePreview
import io.github.jinlongliao.ossbrowser.util.SpreadsheetSheetPreview
import io.github.jinlongliao.ossbrowser.oss.OssObjectItem
import io.github.jinlongliao.ossbrowser.oss.iconForFileName
import io.github.jinlongliao.ossbrowser.ui.settings.MarkdownText
import io.github.jinlongliao.ossbrowser.ui.settings.SettingsViewModel
import io.github.jinlongliao.ossbrowser.util.FileUtils
import java.text.DateFormat
import io.github.jinlongliao.ossbrowser.upload.isUploadActive
import io.github.jinlongliao.ossbrowser.upload.UploadTasksDialog
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenProfiles: () -> Unit,
    vm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory)
) {
    val state by vm.state.collectAsState()
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val settings by settingsVm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // 任务来源独立于浏览器状态；刷新、切目录和重建页面都不会清空任务。
    val uploadTasks by OssBrowserApp.instance.uploadRepository.tasks.collectAsState()
    var showUploads by remember { mutableStateOf(false) }
    if (showUploads) UploadTasksDialog(uploadTasks) { showUploads = false }

    var pendingDownload by remember { mutableStateOf<OssObjectItem.File?>(null) }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    val listState = rememberLazyListState()
    val waterfallState = rememberLazyStaggeredGridState()
    val layoutMode = settings.browserLayoutMode
    // 排序或布局切换后回到顶部；两种容器均重置，避免旧布局的滚动锚点影响新首项。
    LaunchedEffect(settings.ossObjectSortField, settings.ossObjectSortDirection, layoutMode) {
        listState.scrollToItem(0)
        waterfallState.scrollToItem(0)
    }
    var lastBackMillis by remember { mutableStateOf(0L) }
    val backExitMsg = stringResource(R.string.browser_goback_exit)

    // 手势/物理返回键统一逻辑：目录层层回退；bucket 列表需两次返回退出
    BackHandler(enabled = true) {
        val handled = vm.goBack()
        if (!handled) {
            val now = System.currentTimeMillis()
            if (now - lastBackMillis < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackMillis = now
                Toast.makeText(context, backExitMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 路径跳转后滚动到目标文件并给予短暂高亮；若目标尚未加载到，则持续翻页直到出现或到底
    LaunchedEffect(state.highlightKey, state.items, state.nextMarker, state.loadingMore, layoutMode) {
        val key = state.highlightKey ?: return@LaunchedEffect
        val idx = state.items.indexOfFirst { it.key == key }
        if (idx >= 0) {
            runCatching {
                if (layoutMode == BrowserLayoutMode.WATERFALL) waterfallState.animateScrollToItem(idx)
                else listState.animateScrollToItem(idx)
            }
            kotlinx.coroutines.delay(2500L)
            vm.clearHighlight()
        } else if (state.nextMarker != null && !state.loadingMore) {
            vm.loadMore()
        } else if (state.nextMarker == null && !state.loadingMore) {
            // 翻完都没有，清掉高亮
            vm.clearHighlight()
        }
    }

    // 两种容器接近底部时均沿用原有分页流程；瀑布流可见项按最大索引判断，不依赖列顺序。
    val shouldLoadMore by remember(layoutMode) {
        androidx.compose.runtime.derivedStateOf {
            val last: Int
            val total: Int
            if (layoutMode == BrowserLayoutMode.WATERFALL) {
                val layout = waterfallState.layoutInfo
                last = layout.visibleItemsInfo.maxOfOrNull { it.index } ?: return@derivedStateOf false
                total = layout.totalItemsCount
            } else {
                val layout = listState.layoutInfo
                last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                total = layout.totalItemsCount
            }
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.nextMarker, state.loadingMore, state.currentBucket, state.prefix) {
        if (shouldLoadMore && state.nextMarker != null && !state.loadingMore && !state.loading) {
            vm.loadMore()
        }
    }

    // Android 13 起通知需要运行时授权；拒绝后仍可在任务列表查看上传。
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, R.string.upload_notification_denied, Toast.LENGTH_LONG).show()
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (exception: SecurityException) {
                    // 部分提供方只支持临时授权；当前上传仍可执行，重启后可能需要重新选择文件。
                    Log.w("BrowserScreen", "文档不支持持久读取授权 uri=$uri", exception)
                }
                vm.upload(context, uri)
            }
            showUploads = true
        }
    }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val file = pendingDownload
        if (uri != null && file != null && result.resultCode == Activity.RESULT_OK) {
            val os = context.contentResolver.openOutputStream(uri)
            if (os != null) vm.download(file.key, os, file.displayName)
        }
        pendingDownload = null
    }

    LaunchedEffect(state.task) {
        val task = state.task ?: return@LaunchedEffect
        when (task) {
            is TaskState.Done -> {
                snackbar.showSnackbar(task.message)
                vm.clearTask()
            }
            is TaskState.Failed -> {
                snackbar.showSnackbar(task.message)
                vm.clearTask()
            }
            is TaskState.Progress -> Unit
        }
    }

    val inBucketView = state.currentBucket != null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(
                            state.activeProfile?.name
                                ?: stringResource(R.string.browser_no_profile_title),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when {
                            state.activeProfile == null ->
                                stringResource(R.string.browser_no_profile_subtitle)
                            state.currentBucket != null ->
                                stringResource(R.string.browser_bucket_subtitle, state.currentBucket!!)
                            else ->
                                stringResource(R.string.browser_bucket_list_subtitle)
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    BrowserToolbarActions(
                        hasActiveProfile = state.activeProfile != null,
                        inBucketView = inBucketView,
                        layoutMode = layoutMode,
                        sortField = settings.ossObjectSortField,
                        sortDirection = settings.ossObjectSortDirection,
                        activeUploadCount = uploadTasks.count { it.status.isUploadActive() },
                        onRefresh = vm::refresh,
                        onLayoutChange = settingsVm::setBrowserLayoutMode,
                        onSortChange = vm::updateObjectSort,
                        onUploadFile = { pickFile.launch(arrayOf("*/*")) },
                        onCreateFolder = { dialog = BrowserDialog.Mkdir },
                        onJumpToPath = { dialog = BrowserDialog.NavigatePath },
                        onOpenProfiles = onOpenProfiles,
                        onOpenUploads = { showUploads = true }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.activeProfile == null) {
                CloudPageHeader(
                    title = stringResource(R.string.brand_files_title),
                    subtitle = stringResource(R.string.brand_files_description)
                )
            }
            if (inBucketView) {
                Breadcrumb(
                    currentBucket = state.currentBucket!!,
                    prefix = state.prefix,
                    onBackToBuckets = { vm.exitBucket() },
                    onJumpInBucket = vm::jumpTo
                )
            }

            if (state.task is TaskState.Progress) {
                val p = state.task as TaskState.Progress
                val frac = if (p.total > 0) p.current.toFloat() / p.total.toFloat() else 0f
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${p.label}  ${FileUtils.humanSize(p.current)} / ${FileUtils.humanSize(p.total)}",
                        style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { frac.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            state.errorMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    onClick = { vm.clearError() }
                ) {
                    Text(
                        stringResource(R.string.browser_error_prefix, msg),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when {
                state.activeProfile == null -> EmptyHint(
                    stringResource(R.string.browser_no_active_profile)
                ) {
                    TextButton(onClick = onOpenProfiles) {
                        Text(stringResource(R.string.browser_go_configure))
                    }
                }
                // === Bucket 列表视图 ===
                !inBucketView -> when {
                    state.loading && state.buckets.isEmpty() -> EmptyHint(
                        stringResource(R.string.browser_loading_buckets)
                    ) {
                        TextButton(onClick = { vm.cancelLoading() }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                    state.buckets.isEmpty() -> EmptyHint(stringResource(R.string.browser_empty_buckets)) {
                        TextButton(onClick = { vm.refresh() }) {
                            Text(stringResource(R.string.common_refresh))
                        }
                    }
                    else -> BrowserItemsLayout(
                        entries = state.buckets,
                        layoutMode = layoutMode,
                        itemKey = { it.name },
                        bottomPadding = 24.dp
                    ) { bucket, waterfall ->
                        BucketRow(
                            bucket = bucket,
                            waterfall = waterfall,
                            onOpen = { vm.selectBucket(bucket.name, bucket.extranetEndpoint) }
                        )
                    }
                }
                // === Object 列表视图 ===
                state.loading && state.items.isEmpty() -> EmptyHint(
                    stringResource(R.string.browser_loading)
                ) {
                    TextButton(onClick = { vm.cancelLoading() }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
                state.items.isEmpty() -> EmptyHint(stringResource(R.string.browser_empty_folder)) {
                    TextButton(onClick = { vm.refresh() }) {
                        Text(stringResource(R.string.common_refresh))
                    }
                }
                else -> BrowserItemsLayout(
                    entries = state.items,
                    layoutMode = layoutMode,
                    itemKey = { it.key },
                    bottomPadding = 24.dp,
                    listState = listState,
                    waterfallState = waterfallState,
                    footer = {
                        ListFooter(
                            hasMore = state.nextMarker != null,
                            loadingMore = state.loadingMore,
                            onLoadMore = { vm.loadMore() }
                        )
                    }
                ) { item, waterfall ->
                    ObjectRow(
                        item = item,
                        waterfall = waterfall,
                        highlighted = item.key == state.highlightKey,
                        onOpen = {
                            if (item is OssObjectItem.Folder) vm.enterFolder(item.key)
                        },
                        onPreview = {
                            if (item is OssObjectItem.File) vm.preview(item)
                        },
                        onDownload = {
                            if (item is OssObjectItem.File) {
                                pendingDownload = item
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_TITLE, item.displayName)
                                }
                                createFile.launch(intent)
                            }
                        },
                        onPresign = {
                            if (item is OssObjectItem.File) {
                                vm.clearCustomDomains()
                                dialog = BrowserDialog.ShareLink(item)
                            }
                        },
                        onDelete = {
                            if (settings.confirmDestructiveOps) {
                                dialog = BrowserDialog.ConfirmDelete(item)
                            } else {
                                vm.delete(item.key)
                            }
                        },
                        onMove = { dialog = BrowserDialog.Move(item) }
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        is BrowserDialog.Mkdir -> MkdirDialog(
            onDismiss = { dialog = null },
            onConfirm = {
                vm.mkdir(it)
                dialog = null
            }
        )
        is BrowserDialog.NavigatePath -> NavigatePathDialog(
            currentPrefix = state.prefix,
            onDismiss = { dialog = null },
            onConfirm = { path ->
                vm.navigateToPath(path)
                dialog = null
            }
        )
        is BrowserDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    if (d.item is OssObjectItem.Folder)
                        stringResource(R.string.confirm_delete_folder, d.item.displayName)
                    else
                        stringResource(R.string.confirm_delete_file, d.item.displayName)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(d.item.key); dialog = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
        is BrowserDialog.Move -> MoveDialog(
            item = d.item,
            currentPrefix = state.prefix,
            onDismiss = { dialog = null },
            onConfirm = { destPrefix, newName ->
                if (settings.confirmDestructiveOps) {
                    dialog = BrowserDialog.ConfirmMove(d.item, destPrefix, newName)
                } else {
                    vm.move(d.item.key, destPrefix, newName)
                    dialog = null
                }
            }
        )
        is BrowserDialog.ConfirmMove -> ConfirmMoveDialog(
            item = d.item,
            destPrefix = d.destPrefix,
            newName = d.newName,
            onDismiss = { dialog = BrowserDialog.Move(d.item) },
            onConfirm = {
                vm.move(d.item.key, d.destPrefix, d.newName)
                dialog = null
            }
        )
        is BrowserDialog.ShareLink -> ShareLinkInputDialog(
            item = d.item,
            customDomainLookup = state.customDomainLookup,
            onPublicModeSelected = { vm.loadCustomDomains() },
            onDismiss = {
                vm.clearCustomDomains()
                dialog = null
            },
            onConfirm = { mode, hours, customDomain ->
                vm.generateShareUrl(d.item.key, mode, hours, customDomain)
                vm.clearCustomDomains()
                dialog = null
            }
        )
        null -> Unit
    }

    state.shareLinkResult?.let { result ->
        ShareLinkResultDialog(
            result = result,
            onDismiss = { vm.clearShareLinkResult() }
        )
    }

    state.previewState?.let { ps ->
        PreviewDialog(
            state = ps,
            autoPlayMedia = settings.autoPlayMedia,
            onDismiss = { vm.closePreview() },
            onRetry = { vm.retryPreview() },
            onDownload = {
                val file = vm.currentPreviewItem() ?: return@PreviewDialog
                pendingDownload = file
                vm.closePreview()
                createFile.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_TITLE, file.displayName)
                    }
                )
            },
            onPresign = {
                val file = vm.currentPreviewItem() ?: return@PreviewDialog
                vm.closePreview()
                vm.clearCustomDomains()
                dialog = BrowserDialog.ShareLink(file)
            }
        )
    }
}

private sealed class BrowserDialog {
    data object Mkdir : BrowserDialog()
    data object NavigatePath : BrowserDialog()
    data class ConfirmDelete(val item: OssObjectItem) : BrowserDialog()
    data class Move(val item: OssObjectItem) : BrowserDialog()
    data class ConfirmMove(
        val item: OssObjectItem,
        val destPrefix: String,
        val newName: String?
    ) : BrowserDialog()
    data class ShareLink(val item: OssObjectItem.File) : BrowserDialog()
}

@Composable
private fun Breadcrumb(
    currentBucket: String,
    prefix: String,
    onBackToBuckets: () -> Unit,
    onJumpInBucket: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(
            onClick = onBackToBuckets,
            label = { Text("Buckets") },
            leadingIcon = { Icon(Icons.Filled.Storage, null) }
        )
        Text("/")
        AssistChip(
            onClick = { onJumpInBucket("") },
            label = { Text(currentBucket) }
        )
        if (prefix.isNotEmpty()) {
            val parts = prefix.trimEnd('/').split('/')
            var acc = ""
            parts.forEach { p ->
                acc += "$p/"
                val target = acc
                Text("/")
                AssistChip(onClick = { onJumpInBucket(target) }, label = { Text(p) })
            }
        }
    }
}

/** Bucket 摘要卡；列表横排，瀑布流纵排并依内容自适应高度，点击沿用目录流程。 */
@Composable
private fun BucketRow(bucket: OssBucketInfo, waterfall: Boolean = false, onOpen: () -> Unit) {
    val bucketColor = MaterialTheme.colorScheme.primary
    val iconStyle = LocalThemeVisualStyle.current.iconStyle
    val bucketIcon = remember(bucketColor, iconStyle) { StorageIcons.createBucketIcon(bucketColor, iconStyle) }
    val container = Modifier.fillMaxWidth()
        .then(if (waterfall) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
        .themedCardSurface()
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    if (waterfall) {
        Column(container, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(rememberVectorPainter(bucketIcon), contentDescription = null, modifier = Modifier.size(48.dp))
            BucketDescription(bucket)
        }
    } else {
        Row(container, verticalAlignment = Alignment.CenterVertically) {
            Image(rememberVectorPainter(bucketIcon), contentDescription = null, modifier = Modifier.size(44.dp))
            BucketDescription(bucket, Modifier.weight(1f).padding(horizontal = 12.dp))
        }
    }
}

/** 两种排布共用的 Bucket 名称、地域与 Endpoint，元数据不因切换布局丢失。 */
@Composable
private fun BucketDescription(bucket: OssBucketInfo, modifier: Modifier = Modifier) {
    val formatDateTime = rememberLocalDateTimeFormatter()
    Column(modifier) {
        Text(bucket.name, fontWeight = FontWeight.Medium)
        val meta = buildList {
            bucket.location?.takeIf { it.isNotBlank() }?.let { add(it) }
            bucket.storageClass?.takeIf { it.isNotBlank() }?.let { add(it) }
            bucket.createDate?.let { add(formatDateTime(it)) }
        }.joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        bucket.extranetEndpoint?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 文件和目录卡；双列使用竖向内容，两种布局共享预览、菜单及稳定文件 key。 */
@Composable
private fun ObjectRow(
    item: OssObjectItem,
    highlighted: Boolean = false,
    waterfall: Boolean = false,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onPresign: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    var moreMenuOpen by remember { mutableStateOf(false) }
    val rowClick: () -> Unit = when (item) {
        is OssObjectItem.Folder -> onOpen
        is OssObjectItem.File -> onPreview
    }
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier.fillMaxWidth()
            .then(if (waterfall) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            .themedCardSurface(bg)
            .clickable(onClick = rowClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val folderColor = MaterialTheme.colorScheme.primary
            val iconStyle = LocalThemeVisualStyle.current.iconStyle
            val folderIcon = remember(folderColor, iconStyle) { StorageIcons.createFolderIcon(folderColor, iconStyle) }
            val itemIcon = when (item) {
                is OssObjectItem.Folder -> folderIcon
                is OssObjectItem.File -> remember(item.displayName) {
                    iconForFileName(item.displayName).icon
                }
            }
            Image(
                painter = rememberVectorPainter(itemIcon),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            if (waterfall) {
                Spacer(Modifier.weight(1f))
            } else {
                ObjectItemDescription(item, Modifier.weight(1f).padding(horizontal = 12.dp))
                if (item is OssObjectItem.File) {
                    IconButton(onClick = onPreview) {
                        Icon(Icons.Outlined.Visibility, contentDescription = stringResource(R.string.cd_preview))
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.browser_more_actions)
                    )
                }
                DropdownMenu(
                    expanded = moreMenuOpen,
                    onDismissRequest = { moreMenuOpen = false }
                ) {
                    if (item is OssObjectItem.File) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_presign)) },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            onClick = {
                                moreMenuOpen = false
                                onPresign()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_download)) },
                            leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                            onClick = {
                                moreMenuOpen = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_move)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        },
                        onClick = {
                            moreMenuOpen = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            moreMenuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
        if (waterfall) {
            ObjectItemDescription(item, Modifier.padding(top = 10.dp), maxNameLines = 4)
        }
    }
}

/** 名称与文件大小/修改时间展示，两种排布均使用同一份元数据格式。 */
@Composable
private fun ObjectItemDescription(item: OssObjectItem, modifier: Modifier = Modifier, maxNameLines: Int = 2) {
    val formatDateTime = rememberLocalDateTimeFormatter()
    Column(modifier) {
        Text(
            text = item.displayName,
            fontWeight = FontWeight.Medium,
            maxLines = maxNameLines,
            overflow = TextOverflow.Ellipsis
        )
        val details = when (item) {
            is OssObjectItem.File -> FileUtils.humanSize(item.size) +
                (item.lastModified?.let { "  ·  ${formatDateTime(it)}" } ?: "")
            is OssObjectItem.Folder -> stringResource(R.string.browser_item_folder_label)
        }
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 根据当前应用 locale（受设置里"语言"项影响）构造日期时间格式化器。
 *
 * 用 `DateFormat.getDateTimeInstance(MEDIUM, SHORT, locale)`：
 * - 英文 → `Apr 24, 2026, 3:20 PM`
 * - 简体中文 → `2026年4月24日 下午3:20`
 * - 繁体中文 → `2026年4月24日 下午3:20`
 *
 * 注：`DateFormat` 本身非线程安全，但这里仅在 UI 线程使用，且通过 `remember` 保证单实例。
 */
@Composable
private fun rememberLocalDateTimeFormatter(): (Date) -> String {
    val configuration = LocalConfiguration.current
    val locale: Locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    return remember(locale) {
        val formatter = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT, locale
        )
        val fn: (Date) -> String = { formatter.format(it) }
        fn
    }
}

@Composable
private fun ListFooter(
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loadingMore -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(stringResource(R.string.browser_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hasMore -> TextButton(onClick = onLoadMore) { Text(stringResource(R.string.browser_load_more)) }
            else -> Text(
                stringResource(R.string.browser_all_loaded),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 有留白的空态提示；不改变调用方的配置、刷新或取消操作。 */
@Composable
private fun EmptyHint(text: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(64.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun NavigatePathDialog(
    currentPrefix: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPrefix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_path_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.nav_path_label)) },
                    placeholder = { Text(stringResource(R.string.nav_path_placeholder)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.nav_path_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path) }) {
                Text(stringResource(R.string.nav_path_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun MkdirDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_mkdir)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.browser_mkdir_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * 分享链接配置弹窗：签名链接保留原有有效期输入；公开链接按需查询并列出可选自定义域名。
 * 自定义域名查询失败不禁用生成按钮，用户仍可使用 OSS 默认公网域名。
 */
@Composable
private fun ShareLinkInputDialog(
    item: OssObjectItem.File,
    customDomainLookup: CustomDomainLookupState,
    onPublicModeSelected: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (mode: ShareLinkMode, hours: Int?, customDomain: OssCnameInfo?) -> Unit
) {
    var hoursText by remember { mutableStateOf("1") }
    var mode by remember { mutableStateOf(ShareLinkMode.SIGNED) }
    var selectedDomain by remember { mutableStateOf<String?>(null) }
    val hours = hoursText.toIntOrNull()
    val valid = mode == ShareLinkMode.PUBLIC || hours != null && hours in 1..168 // OSS 最长 7 天
    val customDomain = customDomainLookup.domains.firstOrNull { it.domain == selectedDomain }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presign_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.presign_file_label, item.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ShareModeOption(
                    selected = mode == ShareLinkMode.SIGNED,
                    label = stringResource(R.string.share_mode_signed),
                    onClick = { mode = ShareLinkMode.SIGNED }
                )
                ShareModeOption(
                    selected = mode == ShareLinkMode.PUBLIC,
                    label = stringResource(R.string.share_mode_public),
                    onClick = {
                        mode = ShareLinkMode.PUBLIC
                        onPublicModeSelected()
                    }
                )
                if (mode == ShareLinkMode.SIGNED) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.presign_expire_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        stringResource(R.string.presign_expire_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.share_domain_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                    ShareModeOption(
                        selected = selectedDomain == null,
                        label = stringResource(R.string.share_domain_default),
                        onClick = { selectedDomain = null }
                    )
                    customDomainLookup.domains.forEach { cname ->
                        ShareModeOption(
                            selected = selectedDomain == cname.domain,
                            label = "${cname.scheme}://${cname.domain}",
                            onClick = { selectedDomain = cname.domain }
                        )
                    }
                    when {
                        customDomainLookup.loading -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.share_cname_loading),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        customDomainLookup.warning != null -> Text(
                            customDomainLookup.warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.share_public_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode, hours, customDomain) },
                enabled = valid
            ) { Text(stringResource(R.string.common_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 单个分享方式或域名选项，整行均可点击。 */
@Composable
private fun ShareModeOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** 展示已生成的签名或公开链接，并提供复制与系统分享入口。 */
@Composable
private fun ShareLinkResultDialog(
    result: ShareLinkResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration) {
        context.createConfigurationContext(configuration).resources
    }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presign_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.presign_file_label, result.key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.expireHours != null) {
                    Text(
                        stringResource(R.string.presign_result_expire, result.expireHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (result.customDomain == null) {
                            stringResource(R.string.share_result_public_default)
                        } else {
                            stringResource(R.string.share_result_public_cname, result.customDomain)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = result.url,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(result.url))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Text("  " + stringResource(R.string.common_copy))
                    }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result.url)
                        }
                        context.startActivity(
                            Intent.createChooser(send, resources.getString(R.string.preview_share_link))
                        )
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text("  " + stringResource(R.string.common_share))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun MoveDialog(
    item: OssObjectItem,
    currentPrefix: String,
    onDismiss: () -> Unit,
    onConfirm: (destPrefix: String, newName: String?) -> Unit
) {
    var dest by remember { mutableStateOf(currentPrefix) }
    val baseName = when (item) {
        is OssObjectItem.Folder -> item.key.trimEnd('/').substringAfterLast('/')
        is OssObjectItem.File -> item.displayName
    }
    var newName by remember { mutableStateOf(baseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (item is OssObjectItem.Folder) R.string.move_title_folder
                    else R.string.move_title_file
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.move_source_label, item.key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = { Text(stringResource(R.string.move_dest_label)) }
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.move_name_label)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dest, newName) },
                enabled = newName.isNotBlank()
            ) { Text(stringResource(R.string.common_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun ConfirmMoveDialog(
    item: OssObjectItem,
    destPrefix: String,
    newName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val targetKey = buildString {
        append(destPrefix)
        val name = newName?.takeIf { it.isNotBlank() } ?: when (item) {
            is OssObjectItem.Folder -> item.key.trimEnd('/').substringAfterLast('/')
            is OssObjectItem.File -> item.displayName
        }
        append(name)
        if (item is OssObjectItem.Folder && !endsWith('/')) append('/')
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_move_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        if (item is OssObjectItem.Folder) R.string.confirm_move_folder
                        else R.string.confirm_move_file
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.confirm_move_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(item.key, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.confirm_move_target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(targetKey, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm_execute)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_back_edit)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewDialog(
    state: PreviewState,
    autoPlayMedia: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onPresign: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 若预览对象是 Markdown，默认使用富文本渲染；用户可在顶栏切换到源码视图。
    var renderMarkdown by remember(state.key) {
        mutableStateOf(state is PreviewState.Text && state.isMarkdown)
    }
    // 完整网页能力只对当前文件的本次预览有效；关闭或切换文件后恢复安全静态模式。
    var fullHtmlPreview by remember(state.key) { mutableStateOf(false) }
    var showFullHtmlConfirmation by remember(state.key) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                AppTopBar(
                    title = {
                        Text(
                            state.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_close)
                            )
                        }
                    },
                    actions = {
                        when (state) {
                            is PreviewState.Text -> {
                                if (state.isMarkdown) {
                                    val switchDesc = if (renderMarkdown) {
                                        stringResource(R.string.preview_toggle_markdown_source)
                                    } else {
                                        stringResource(R.string.preview_toggle_markdown_rich)
                                    }
                                    IconButton(onClick = { renderMarkdown = !renderMarkdown }) {
                                        Icon(
                                            imageVector = if (renderMarkdown) Icons.Outlined.Code
                                            else Icons.AutoMirrored.Outlined.Article,
                                            contentDescription = switchDesc
                                        )
                                    }
                                }
                                val copiedMsg = stringResource(R.string.preview_copy_to_clipboard)
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.content))
                                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.common_copy)
                                    )
                                }
                            }
                            is PreviewState.Html -> {
                                IconButton(onClick = {
                                    if (fullHtmlPreview) {
                                        fullHtmlPreview = false
                                    } else {
                                        showFullHtmlConfirmation = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (fullHtmlPreview) Icons.Outlined.Shield
                                        else Icons.Outlined.Public,
                                        contentDescription = if (fullHtmlPreview) {
                                            stringResource(R.string.preview_html_switch_safe)
                                        } else {
                                            stringResource(R.string.preview_html_switch_full)
                                        }
                                    )
                                }
                                val copiedMsg = stringResource(R.string.preview_copy_to_clipboard)
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.content))
                                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.common_copy)
                                    )
                                }
                            }
                            is PreviewState.Docx -> {
                                val copiedMsg = stringResource(R.string.preview_copy_to_clipboard)
                                IconButton(onClick = {
                                    val merged = state.paragraphs.joinToString("\n")
                                    clipboard.setText(AnnotatedString(merged))
                                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.common_copy)
                                    )
                                }
                            }
                            is PreviewState.Spreadsheet -> {
                                val copiedMsg = stringResource(R.string.preview_copy_to_clipboard)
                                IconButton(onClick = {
                                    val merged = state.sheets.joinToString("\n\n") { sheet ->
                                        sheet.name + "\n" + sheet.rows.joinToString("\n") { it.joinToString("\t") }
                                    }
                                    clipboard.setText(AnnotatedString(merged))
                                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                                }
                            }
                            is PreviewState.Presentation -> {
                                val copiedMsg = stringResource(R.string.preview_copy_to_clipboard)
                                IconButton(onClick = {
                                    val merged = state.slides.joinToString("\n\n") { slide ->
                                        slide.texts.joinToString("\n")
                                    }
                                    clipboard.setText(AnnotatedString(merged))
                                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                                }
                            }
                            is PreviewState.Image, is PreviewState.Media -> {
                                val url = when (state) {
                                    is PreviewState.Image -> state.url
                                    is PreviewState.Media -> state.url
                                    else -> null
                                }
                                if (url != null) {
                                    if (state is PreviewState.Media) {
                                        IconButton(onClick = {
                                            tryOpenExternal(context, url, state.video)
                                        }) {
                                            Icon(
                                                Icons.Outlined.OpenInNew,
                                                contentDescription = stringResource(R.string.preview_open_external)
                                            )
                                        }
                                    }
                                    val copiedLinkMsg = stringResource(R.string.preview_copy_link)
                                    val shareLinkTitle = stringResource(R.string.preview_share_link)
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(url))
                                        Toast.makeText(context, copiedLinkMsg, Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            Icons.Outlined.ContentCopy,
                                            contentDescription = stringResource(R.string.cd_copy_link)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }
                                        context.startActivity(Intent.createChooser(send, shareLinkTitle))
                                    }) {
                                        Icon(
                                            Icons.Outlined.Share,
                                            contentDescription = stringResource(R.string.common_share)
                                        )
                                    }
                                }
                            }
                            is PreviewState.Pdf,
                            is PreviewState.Loading,
                            is PreviewState.TooLarge,
                            is PreviewState.Error -> Unit
                        }
                    }
                )
                Box(Modifier.fillMaxSize()) {
                    when (state) {
                        is PreviewState.Loading -> LoadingPreviewBody(state)

                        is PreviewState.Text ->
                            if (state.isMarkdown && renderMarkdown) {
                                MarkdownPreviewBody(content = state.content)
                            } else {
                                TextPreviewBody(content = state.content)
                            }

                        is PreviewState.Html -> HtmlPreviewBody(
                            content = state.content,
                            fullWebPreview = fullHtmlPreview
                        )

                        is PreviewState.Docx -> DocxPreviewBody(paragraphs = state.paragraphs)

                        is PreviewState.Spreadsheet -> SpreadsheetPreviewBody(sheets = state.sheets)

                        is PreviewState.Presentation -> PresentationPreviewBody(slides = state.slides)

                        is PreviewState.Image -> ImagePreviewBody(url = state.url)

                        is PreviewState.Media -> MediaPreviewBody(
                            url = state.url,
                            isVideo = state.video,
                            name = state.name,
                            autoPlay = autoPlayMedia
                        )

                        is PreviewState.Pdf -> PdfPreviewBody(filePath = state.filePath)

                        is PreviewState.TooLarge -> TooLargePreviewBody(
                            state = state,
                            onDownload = onDownload,
                            onPresign = onPresign,
                            onClose = onDismiss
                        )

                        is PreviewState.Error -> ErrorPreviewBody(
                            state = state,
                            onRetry = onRetry,
                            onDownload = onDownload,
                            onClose = onDismiss
                        )
                    }
                }
            }
        }
    }

    if (showFullHtmlConfirmation) {
        AlertDialog(
            onDismissRequest = { showFullHtmlConfirmation = false },
            title = { Text(stringResource(R.string.preview_html_full_confirm_title)) },
            text = { Text(stringResource(R.string.preview_html_full_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    fullHtmlPreview = true
                    showFullHtmlConfirmation = false
                }) {
                    Text(stringResource(R.string.preview_html_enable_full))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullHtmlConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun TextPreviewBody(content: String) {
    SelectionContainer(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = content,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownPreviewBody(content: String) {
    SelectionContainer(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            MarkdownText(source = content)
        }
    }
}

/**
 * 使用 WebView 渲染 HTML 内容。
 *
 * 安全静态模式禁用 JavaScript、DOM 存储、文件/Content URI 访问及所有外部网络请求，
 * 仅呈现文档内联 HTML/CSS。完整网页模式经用户确认后开启 JavaScript、DOM 存储和
 * HTTPS 外部资源，但仍禁止文件访问、混合内容与主页面跳转；不向页面注入原生接口。
 * WebView 在离开预览时主动销毁，避免脚本或网络任务越过当前会话继续运行。
 *
 * @param content UTF-8 HTML 源码。
 * @param fullWebPreview 是否启用当前会话的完整网页能力。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPreviewBody(content: String, fullWebPreview: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    /** 阻止点击或脚本把主页面导航到其他地址；子资源仍由网络策略单独控制。 */
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true
                }
            }
        },
        update = { webView ->
            val modeTag = if (fullWebPreview) "full" else "safe"
            val contentTag = "$modeTag:${content.hashCode()}"
            if (webView.tag == contentTag) return@AndroidView
            webView.tag = contentTag
            webView.settings.apply {
                javaScriptEnabled = fullWebPreview
                domStorageEnabled = fullWebPreview
                blockNetworkLoads = !fullWebPreview
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webView.loadDataWithBaseURL(
                null,
                content,
                "text/html",
                Charsets.UTF_8.name(),
                null
            )
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    )
}

@Composable
private fun DocxPreviewBody(paragraphs: List<String>) {
    if (paragraphs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.preview_docx_empty))
        }
        return
    }
    SelectionContainer(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(paragraphs.size) { idx ->
                val para = paragraphs[idx]
                if (para.isBlank()) {
                    // 空段落保留视觉间距即可
                    Box(Modifier.fillMaxWidth())
                } else {
                    Text(
                        text = para,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** 本地展示 XLSX 工作表；每个单元格使用固定宽度，表格可横向滚动。 */
@Composable
private fun SpreadsheetPreviewBody(sheets: List<SpreadsheetSheetPreview>) {
    if (sheets.all { it.rows.isEmpty() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.preview_xlsx_empty))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sheets.size) { sheetIndex ->
            val sheet = sheets[sheetIndex]
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sheet.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    sheet.rows.forEach { row ->
                        Row {
                            row.forEach { value ->
                                Text(
                                    text = value,
                                    modifier = Modifier.width(140.dp).padding(8.dp),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 本地按页展示 PPTX 中按文档顺序提取的文字内容。 */
@Composable
private fun PresentationPreviewBody(slides: List<PresentationSlidePreview>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(slides.size) { slideIndex ->
            val slide = slides[slideIndex]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.preview_pptx_slide, slide.number),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (slide.texts.isEmpty()) {
                        Text(stringResource(R.string.preview_pptx_empty_slide))
                    } else {
                        slide.texts.forEach { text -> Text(text, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

/**
 * 预览加载中态：大图标 + 文件名 + 预估大小 / 下载进度（PDF 实时更新）。
 * 视觉上比单独的 CircularProgressIndicator 更稳妥，能明确告诉用户"正在拿什么"。
 */
@Composable
private fun LoadingPreviewBody(state: PreviewState.Loading) {
    // 文件类型图标始终使用固定图形和类别色，与目录图标的主题材质独立。
    val spec = remember(state.name) { iconForFileName(state.name) }
    val downloaded = state.downloadedBytes
    val total = state.totalBytes
    val hasProgress = downloaded > 0 && total > 0
    val fraction = if (hasProgress) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            state.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
        Spacer(Modifier.size(6.dp))
        val sizeLabel = if (total > 0) FileUtils.humanSize(total)
        else stringResource(R.string.preview_loading_size_unknown)
        Text(
            sizeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        if (hasProgress) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(
                    R.string.preview_loading_download,
                    FileUtils.humanSize(downloaded),
                    FileUtils.humanSize(total)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.preview_loading_fetching),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 预览超限引导页：一个 Warn 图标 + 文件名 + 大小/上限 + 三个主要动作。 */
@Composable
private fun TooLargePreviewBody(
    state: PreviewState.TooLarge,
    onDownload: () -> Unit,
    onPresign: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.preview_too_large_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(6.dp))
        Text(
            state.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(
                R.string.preview_too_large_desc,
                FileUtils.humanSize(state.sizeBytes),
                FileUtils.humanSize(state.limitBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.preview_action_download))
        }
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(onClick = onPresign, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Link, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.preview_action_presign))
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.preview_action_close))
        }
    }
}

/** 预览失败引导页：告知错误并提供「重试 / 下载 / 关闭」。 */
@Composable
private fun ErrorPreviewBody(
    state: PreviewState.Error,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.preview_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(6.dp))
        Text(
            state.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        Spacer(Modifier.size(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.preview_error_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.preview_action_retry))
        }
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.preview_action_download))
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.preview_action_close))
        }
    }
}

/** PDF只为可见列表项后台渲染Bitmap，不在Main一次性分配所有页面。 */
@Composable
private fun PdfPreviewBody(filePath: String) {
    // produceState更换key不保证清除旧value；按文档隔离整个子树，避免A页闪现在B标题下。
    key(filePath) { PdfPreviewDocumentBody(filePath) }
}

/** 每个文档独占一个渲染会话；屏幕宽度变化只重建页面任务，继续复用同一native renderer。 */
@Composable
private fun PdfPreviewDocumentBody(filePath: String) {
    val context = LocalContext.current
    val file = remember(filePath) { File(filePath) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val renderWidthPixels = remember(screenWidthDp, context.resources.displayMetrics.densityDpi) {
        context.resources.displayMetrics.widthPixels
    }
    val sessionState = produceState<Result<PdfPreviewRenderer>?>(null, filePath) {
        var opened: PdfPreviewRenderer? = null
        try {
            val session = withContext(Dispatchers.IO) { PdfPreviewRenderer.open(file).also { opened = it } }
            value = Result.success(session)
            awaitDispose { session.closeAsync() }
        } catch (exception: CancellationException) {
            opened?.closeAsync()
            throw exception
        } catch (exception: Exception) {
            opened?.closeAsync()
            Log.e("PdfPreview", "打开PDF失败 path=$filePath", exception)
            value = Result.failure(exception)
        }
    }
    val result = sessionState.value
    val session = result?.getOrNull()
    when {
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        session == null || session.pageCount == 0 -> Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.preview_pdf_failed)) }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(session.pageCount) { index ->
                key(session, index, renderWidthPixels) {
                    // LazyColumn仅组合可见/预取项，离屏状态销毁后不再持有整份文档的Bitmap集合。
                    val pageState = produceState<Result<PdfRenderedPage>?>(null, session, index, renderWidthPixels) {
                        value = try {
                            Result.success(session.renderPage(index, renderWidthPixels))
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            Log.e("PdfPreview", "渲染PDF失败 path=$filePath page=$index", exception)
                            Result.failure(exception)
                        }
                    }
                    val page = pageState.value?.getOrNull()
                    val bitmap = page?.bitmap
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            bitmap != null -> {
                                val density = LocalDensity.current
                                val displaySize = pdfPageDisplaySize(
                                    requireNotNull(page).aspectRatio, with(density) { maxWidth.toPx() }
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.preview_pdf_page, index + 1),
                                        modifier = with(density) {
                                            Modifier.size(displaySize.width.toDp(), displaySize.height.toDp())
                                        },
                                        contentScale = ContentScale.FillBounds
                                    )
                                    if (displaySize.reduced) {
                                        Text(stringResource(R.string.preview_pdf_extreme_page_hint))
                                    }
                                }
                            }
                            pageState.value == null -> Box(
                                Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                            else -> Text(stringResource(R.string.preview_pdf_failed))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewBody(url: String) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            imageLoader = loader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        )
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun MediaPreviewBody(
    url: String,
    isVideo: Boolean,
    name: String,
    autoPlay: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = autoPlay
        }
    }

    // 跟随生命周期暂停/恢复；关闭时释放
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 2500
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // 音频：显示控件 + 文件名占位
                    if (!isVideo) {
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        defaultArtwork = null
                        useArtwork = false
                    }
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )
        if (!isVideo) {
            // 在音频底下透出文件名
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.preview_audio_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun tryOpenExternal(
    context: android.content.Context,
    url: String,
    isVideo: Boolean
) {
    val mime = if (isVideo) "video/*" else "audio/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), mime)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.preview_choose_player)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.preview_no_player_app),
            Toast.LENGTH_LONG
        ).show()
    }
}
