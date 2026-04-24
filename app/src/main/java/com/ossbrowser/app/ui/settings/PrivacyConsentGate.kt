package com.ossbrowser.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * 首次启动隐私协议拦截：未同意时弹出全屏阻断对话框。
 * 用户同意后通过 [onAccept] 持久化；拒绝时通过 [onReject] 退出应用。
 */
@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                "欢迎使用 ${SettingsContent.APP_NAME}",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "在开始使用前，请阅读并同意《隐私政策》。本应用完全开源，不收集任何个人数据；所有 OSS 密钥仅加密保存在本机。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    SettingsContent.PRIVACY_POLICY,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝并退出") }
        }
    )
}
