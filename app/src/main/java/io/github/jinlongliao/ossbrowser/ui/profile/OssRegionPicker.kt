package io.github.jinlongliao.ossbrowser.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.oss.findOssRegionByEndpoint
import io.github.jinlongliao.ossbrowser.oss.ossRegionOptions

/**
 * OSS 地域选择入口；仅在用户明确选中预置项时回填公网地址，搜索和取消均不改表单。
 * @param endpoint 当前地址；无法精确识别时展示自定义状态，保留路径、内网或专有域名。
 * @param onEndpointChange 用户选择后的完整 HTTPS 公网 Endpoint；保存仍由上层表单执行。
 */
@Composable
fun OssRegionPicker(endpoint: String, onEndpointChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val selected = remember(endpoint) { findOssRegionByEndpoint(endpoint) }
    val options = ossRegionOptions.map { it to stringResource(it.nameResource) }.filter { (option, name) ->
        query.isBlank() || option.code.contains(query.trim(), ignoreCase = true) ||
            name.contains(query.trim(), ignoreCase = true)
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let { "${stringResource(it.nameResource)} · ${it.code}" }
                ?: stringResource(R.string.profile_oss_region_custom),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.profile_oss_region)) },
            supportingText = { Text(stringResource(R.string.profile_oss_region_hint)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // 用独立弹层承载搜索，避免下拉菜单抢走输入焦点；打开时不触发地址回填。
        Box(Modifier.matchParentSize().clickable {
            query = ""
            expanded = true
        })
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.profile_oss_region)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_oss_region_search)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (options.isEmpty()) {
                        Text(
                            stringResource(R.string.profile_oss_region_empty),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(options, key = { it.first.code }) { (option, name) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onEndpointChange(option.endpoint)
                                    expanded = false
                                    keyboard?.hide()
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option.code == selected?.code, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(name)
                                    Text(
                                        option.code,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
