package com.ossbrowser.app.ui.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.res.stringResource
import com.ossbrowser.app.R
import com.ossbrowser.app.oss.OssBucketInfo
import com.ossbrowser.app.oss.OssObjectItem
import com.ossbrowser.app.oss.iconForFileName
import com.ossbrowser.app.ui.settings.SettingsViewModel
import com.ossbrowser.app.util.FileUtils

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

    var pendingDownload by remember { mutableStateOf<OssObjectItem.File?>(null) }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var lastBackMillis by remember { mutableStateOf(0L) }

    // 手势/物理返回键统一逻辑：目录层层回退；bucket 列表需两次返回退出
    BackHandler(enabled = true) {
        val handled = vm.goBack()
        if (!handled) {
            val now = System.currentTimeMillis()
            if (now - lastBackMillis < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackMillis = now
                Toast.makeText(context, "再按一次返回退出应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 路径跳转后滚动到目标文件并给予短暂高亮；若目标尚未加载到，则持续翻页直到出现或到底
    LaunchedEffect(state.highlightKey, state.items, state.nextMarker, state.loadingMore) {
        val key = state.highlightKey ?: return@LaunchedEffect
        val idx = state.items.indexOfFirst { it.key == key }
        if (idx >= 0) {
            runCatching { listState.animateScrollToItem(idx) }
            kotlinx.coroutines.delay(2500L)
            vm.clearHighlight()
        } else if (state.nextMarker != null && !state.loadingMore) {
            vm.loadMore()
        } else if (state.nextMarker == null && !state.loadingMore) {
            // 翻完都没有，清掉高亮
            vm.clearHighlight()
        }
    }

    // 滚动到接近底部时自动加载下一页
    val shouldLoadMore by androidx.compose.runtime.derivedStateOf {
        val layout = listState.layoutInfo
        val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
        val total = layout.totalItemsCount
        total > 0 && last >= total - 3
    }
    LaunchedEffect(shouldLoadMore, state.nextMarker, state.loadingMore, state.currentBucket, state.prefix) {
        if (shouldLoadMore && state.nextMarker != null && !state.loadingMore && !state.loading) {
            vm.loadMore()
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.upload(context, it) } }

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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeProfile?.name ?: "未选择配置", fontWeight = FontWeight.SemiBold)
                        val subtitle = when {
                            state.activeProfile == null -> "请先在「密钥」页新建并激活"
                            state.currentBucket != null -> "bucket: ${state.currentBucket}"
                            else -> "Bucket 列表"
                        }
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                actions = {
                    if (inBucketView) {
                        IconButton(onClick = { dialog = BrowserDialog.NavigatePath }) {
                            Icon(Icons.Outlined.Search, contentDescription = "跳转到路径")
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onOpenProfiles) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = "密钥")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (inBucketView) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { fabMenuOpen = true },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("操作") }
                    )
                    DropdownMenu(
                        expanded = fabMenuOpen,
                        onDismissRequest = { fabMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("上传文件") },
                            leadingIcon = { Icon(Icons.Filled.Upload, null) },
                            onClick = {
                                fabMenuOpen = false
                                pickFile.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("新建目录") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                            onClick = {
                                fabMenuOpen = false
                                dialog = BrowserDialog.Mkdir
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                        "⚠ $msg（点此关闭）",
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
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.buckets, key = { it.name }) { b ->
                            BucketRow(
                                bucket = b,
                                onOpen = { vm.selectBucket(b.name, b.extranetEndpoint) }
                            )
                            HorizontalDivider()
                        }
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
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(state.items, key = { it.key }) { item ->
                        ObjectRow(
                            item = item,
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
                                    dialog = BrowserDialog.Presign(item)
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
                        HorizontalDivider()
                    }
                    item(key = "__footer__") {
                        ListFooter(
                            hasMore = state.nextMarker != null,
                            loadingMore = state.loadingMore,
                            onLoadMore = { vm.loadMore() }
                        )
                    }
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
            title = { Text("确认删除") },
            text = {
                Text(
                    if (d.item is OssObjectItem.Folder)
                        "将递归删除目录「${d.item.displayName}」下的全部对象，是否继续？"
                    else
                        "将删除文件「${d.item.displayName}」，是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(d.item.key); dialog = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
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
        is BrowserDialog.Presign -> PresignInputDialog(
            item = d.item,
            onDismiss = { dialog = null },
            onConfirm = { hours ->
                vm.presignUrl(d.item.key, hours)
                dialog = null
            }
        )
        null -> Unit
    }

    state.presignResult?.let { result ->
        PresignResultDialog(
            result = result,
            onDismiss = { vm.clearPresignResult() }
        )
    }

    state.previewState?.let { ps ->
        PreviewDialog(
            state = ps,
            autoPlayMedia = settings.autoPlayMedia,
            onDismiss = { vm.closePreview() }
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
    data class Presign(val item: OssObjectItem.File) : BrowserDialog()
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

@Composable
private fun BucketRow(bucket: OssBucketInfo, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(bucket.name, fontWeight = FontWeight.Medium)
            val meta = buildList {
                bucket.location?.takeIf { it.isNotBlank() }?.let { add(it) }
                bucket.storageClass?.takeIf { it.isNotBlank() }?.let { add(it) }
                bucket.createDate?.let { add(it.toString()) }
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            bucket.extranetEndpoint?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ObjectRow(
    item: OssObjectItem,
    highlighted: Boolean = false,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onPresign: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    val rowClick: () -> Unit = when (item) {
        is OssObjectItem.Folder -> onOpen
        is OssObjectItem.File -> onPreview
    }
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = rowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (iconVec, iconTint) = when (item) {
            is OssObjectItem.Folder -> Icons.Outlined.Folder to MaterialTheme.colorScheme.primary
            is OssObjectItem.File -> {
                val spec = iconForFileName(item.displayName)
                spec.icon to spec.tint
            }
        }
        Icon(iconVec, contentDescription = null, tint = iconTint)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.displayName, fontWeight = FontWeight.Medium)
            when (item) {
                is OssObjectItem.File -> Text(
                    "${FileUtils.humanSize(item.size)}" +
                            (item.lastModified?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                is OssObjectItem.Folder -> Text(
                    "目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        if (item is OssObjectItem.File) {
            IconButton(onClick = onPreview) {
                Icon(Icons.Outlined.Visibility, contentDescription = "预览")
            }
            IconButton(onClick = onPresign) {
                Icon(Icons.Outlined.Link, contentDescription = "生成链接")
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, contentDescription = "下载")
            }
        }
        IconButton(onClick = onMove) {
            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "移动/重命名")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除")
        }
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
                Text("加载中…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            hasMore -> TextButton(onClick = onLoadMore) { Text("加载更多") }
            else -> Text(
                "— 已全部加载 —",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Color.Gray)
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
        title = { Text("跳转到路径") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("相对 bucket 的路径") },
                    placeholder = { Text("dir1/sub/ 或 dir1/file.txt") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "• 以 / 结尾 → 进入该目录\n• 文件路径 → 跳到其所在目录并定位该文件\n• 留空 → 回到 bucket 根",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path) }) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MkdirDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建目录") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("目录名") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PresignInputDialog(
    item: OssObjectItem.File,
    onDismiss: () -> Unit,
    onConfirm: (hours: Int) -> Unit
) {
    var hoursText by remember { mutableStateOf("1") }
    val hours = hoursText.toIntOrNull()
    val valid = hours != null && hours in 1..168 // OSS 最长 7 天

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成临时下载链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "文件: ${item.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                    label = { Text("有效期（小时，1~168）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    "OSS 预签名链接上限为 7 天（168 小时）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { hours?.let(onConfirm) },
                enabled = valid
            ) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PresignResultDialog(
    result: PresignResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("临时链接已生成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "文件: ${result.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    "有效期: ${result.expireHours} 小时",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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
                        Text("  复制")
                    }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result.url)
                        }
                        context.startActivity(Intent.createChooser(send, "分享链接"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text("  分享")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
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
        title = { Text(if (item is OssObjectItem.Folder) "移动目录 / 重命名" else "移动文件 / 重命名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "源: ${item.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = { Text("目标父目录前缀（空=根，示例：dir1/sub/）") }
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dest, newName) },
                enabled = newName.isNotBlank()
            ) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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
        title = { Text("确认执行") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (item is OssObjectItem.Folder)
                        "即将移动 / 重命名目录，该操作会递归搬运目录下的全部对象。"
                    else
                        "即将移动 / 重命名文件。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("源:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(item.key, style = MaterialTheme.typography.bodySmall)
                Text("目标:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(targetKey, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认执行") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("返回修改") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewDialog(
    state: PreviewState,
    autoPlayMedia: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

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
                TopAppBar(
                    title = {
                        Text(
                            state.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        when (state) {
                            is PreviewState.Text -> {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.content))
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
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
                                            Icon(Icons.Outlined.OpenInNew, contentDescription = "用外部播放器打开")
                                        }
                                    }
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(url))
                                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接")
                                    }
                                    IconButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }
                                        context.startActivity(Intent.createChooser(send, "分享链接"))
                                    }) {
                                        Icon(Icons.Outlined.Share, contentDescription = "分享")
                                    }
                                }
                            }
                            is PreviewState.Pdf -> Unit
                            is PreviewState.Loading -> Unit
                        }
                    }
                )
                Box(Modifier.fillMaxSize()) {
                    when (state) {
                        is PreviewState.Loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        is PreviewState.Text -> TextPreviewBody(content = state.content)

                        is PreviewState.Image -> ImagePreviewBody(url = state.url)

                        is PreviewState.Media -> MediaPreviewBody(
                            url = state.url,
                            isVideo = state.video,
                            name = state.name,
                            autoPlay = autoPlayMedia
                        )

                        is PreviewState.Pdf -> PdfPreviewBody(filePath = state.filePath)
                    }
                }
            }
        }
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
private fun PdfPreviewBody(filePath: String) {
    val context = LocalContext.current
    val file = remember(filePath) { java.io.File(filePath) }
    // 通过 remember + produceState 在后台线程一次性渲染所有页；大 PDF 分页懒加载更优，
    // 这里优先简单实现。渲染出的 Bitmap 会随 composable 销毁释放。
    val pagesState = androidx.compose.runtime.produceState<List<android.graphics.Bitmap>?>(
        initialValue = null,
        filePath
    ) {
        value = runCatching {
            val pfd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val result = ArrayList<android.graphics.Bitmap>(renderer.pageCount)
            try {
                val dm = context.resources.displayMetrics
                val targetWidthPx = dm.widthPixels
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val ratio = targetWidthPx.toFloat() / page.width.toFloat()
                        val w = (page.width * ratio).toInt().coerceAtLeast(1)
                        val h = (page.height * ratio).toInt().coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(
                            w, h, android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bmp, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        result.add(bmp)
                    }
                }
            } finally {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
            result.toList()
        }.getOrElse {
            emptyList()
        }
    }

    val pages = pagesState.value
    when {
        pages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        pages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.preview_pdf_failed))
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pages.size) { idx ->
                val bmp = pages[idx]
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_pdf_page, idx + 1),
                        modifier = Modifier.fillMaxWidth()
                    )
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

@OptIn(UnstableApi::class)
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
                    "拖动下方进度条可快进 / 后退",
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
        context.startActivity(Intent.createChooser(intent, "选择播放器").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可用的播放器应用", Toast.LENGTH_LONG).show()
    }
}
