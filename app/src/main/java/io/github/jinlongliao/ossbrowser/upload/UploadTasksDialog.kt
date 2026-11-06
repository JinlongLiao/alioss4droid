package io.github.jinlongliao.ossbrowser.upload

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.util.FileUtils
import kotlinx.coroutines.launch

/** 独立上传列表，以稳定任务 ID 的卡片展示每文件进度；失败、中断和取消任务允许恢复，历史清理仅操作终态记录。 */
@Composable
fun UploadTasksDialog(tasks: List<UploadTask>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loaded by OssBrowserApp.instance.uploadRepository.isLoaded.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.upload_clear_history)) },
        text = { Text(stringResource(R.string.upload_clear_hint)) },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                clearing = true
                scope.launch {
                    try {
                        val failures = OssBrowserApp.instance.uploadRepository.clearUploadHistory()
                        if (failures > 0) {
                            Toast.makeText(context, R.string.upload_clear_failed, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        clearing = false
                    }
                }
            }) { Text(stringResource(R.string.upload_clear_history)) }
        },
        dismissButton = {
            TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.upload_close)) }
        }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_tasks)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.upload_queue_hint))
                TextButton(
                    enabled = loaded && !clearing && selectUploadHistory(tasks).isNotEmpty(),
                    onClick = { confirmClear = true }
                ) { Text(stringResource(R.string.upload_clear_history)) }
                if (!loaded) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.upload_history_loading))
                } else if (tasks.isEmpty()) Text(stringResource(R.string.upload_empty))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(tasks.asReversed(), key = { it.id }) { task ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(task.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${task.bucket}/${task.prefix}${task.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(stringResource(when (task.status) {
                                UploadStatus.QUEUED -> R.string.upload_queued
                                UploadStatus.RUNNING -> R.string.upload_running
                                UploadStatus.SUCCEEDED -> R.string.upload_succeeded
                                UploadStatus.FAILED -> R.string.upload_failed
                                UploadStatus.INTERRUPTED -> R.string.upload_interrupted
                                UploadStatus.CANCELLING -> R.string.upload_cancelling
                                UploadStatus.CANCELLED -> R.string.upload_cancelled
                            }))
                            if (task.status == UploadStatus.RUNNING) {
                                if (task.total > 0) {
                                    LinearProgressIndicator(
                                        progress = { (task.sent.toFloat() / task.total).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (task.total > 0) Text(
                                "${FileUtils.humanSize(task.sent)} / ${FileUtils.humanSize(task.total)}"
                            )
                            task.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (task.status in setOf(UploadStatus.QUEUED, UploadStatus.RUNNING)) {
                                TextButton(onClick = { UploadService.cancelUploadTask(context, task) }) {
                                    Text(stringResource(R.string.upload_cancel))
                                }
                            }
                            if (task.status in setOf(
                                    UploadStatus.FAILED, UploadStatus.INTERRUPTED, UploadStatus.CANCELLED
                                )) {
                                TextButton(enabled = !clearing, onClick = {
                                    if (OssBrowserApp.instance.uploadRepository.tasks.value.any { it.id == task.id }) {
                                        UploadService.enqueueUploadTask(context, task)
                                    }
                                }) {
                                    Text(stringResource(R.string.upload_retry))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.upload_close)) } }
    )
}
