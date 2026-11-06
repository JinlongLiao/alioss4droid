package io.github.jinlongliao.ossbrowser.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.s3.isS3ConnectionValid
import io.github.jinlongliao.ossbrowser.s3.s3RegionOptions

/**
 * 区域选择框：搜索地区名称/代码，或确认手动输入的新代码；兼容当前 Material3 的键盘焦点行为。
 * @param region 当前保存值，打开选择列表不覆盖未知新区域。
 * @param onRegionChange 只提交区域代码给表单，不在选择时发起任何网络调用。
 */
@Composable
fun S3RegionPicker(region: String, onRegionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val options = s3RegionOptions.map { it to stringResource(it.nameResource) }.filter { (option, name) ->
        query.isBlank() || option.code.contains(query.trim(), ignoreCase = true) ||
            name.contains(query.trim(), ignoreCase = true)
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = region,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.profile_s3_region)) },
            supportingText = { Text(stringResource(R.string.profile_s3_region_hint)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // 选择与搜索分开承载，避免旧版菜单弹窗抢走可编辑字段焦点。
        Box(Modifier.matchParentSize().clickable {
            query = ""
            expanded = true
        })
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.profile_s3_region)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_s3_region_search)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(options, key = { it.first.code }) { (option, name) ->
                            Row(Modifier.fillMaxWidth().clickable {
                                onRegionChange(option.code)
                                expanded = false
                                keyboard?.hide()
                            }) {
                                RadioButton(selected = option.code == region, onClick = null)
                                Text("$name · ${option.code}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isS3ConnectionValid(query.trim(), ""),
                    onClick = {
                        onRegionChange(query.trim())
                        expanded = false
                        keyboard?.hide()
                    }
                ) { Text(stringResource(R.string.profile_s3_region_use)) }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
