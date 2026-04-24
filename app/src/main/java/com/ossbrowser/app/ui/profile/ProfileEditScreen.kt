package com.ossbrowser.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ossbrowser.app.data.OssProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: String?,
    onDone: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val state by vm.state.collectAsState()
    val existing = remember(profileId, state) {
        profileId?.let { id -> state.profiles.firstOrNull { it.id == id } }
    }
    val initial = existing ?: OssProfile.empty()

    var name by rememberSaveable { mutableStateOf(initial.name) }
    var endpoint by rememberSaveable { mutableStateOf(initial.endpoint) }
    var ak by rememberSaveable { mutableStateOf(initial.accessKeyId) }
    var sk by rememberSaveable { mutableStateOf(initial.accessKeySecret) }
    var bucket by rememberSaveable { mutableStateOf(initial.bucket) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新增 OSS 配置" else "编辑 OSS 配置") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("配置名称 *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Endpoint（如 https://oss-cn-hangzhou.aliyuncs.com） *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bucket,
                onValueChange = { bucket = it },
                label = { Text("默认 Bucket（可选，为空则进入后显示全部 Bucket）") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ak,
                onValueChange = { ak = it },
                label = { Text("AccessKey ID *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sk,
                onValueChange = { sk = it },
                label = { Text("AccessKey Secret *") },
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                OutlinedButton(onClick = { showSecret = !showSecret }) {
                    Text(if (showSecret) "隐藏 Secret" else "显示 Secret")
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && endpoint.isNotBlank() &&
                        ak.isNotBlank() && sk.isNotBlank(),
                onClick = {
                    vm.save(
                        initial.copy(
                            name = name.trim(),
                            endpoint = endpoint.trim(),
                            accessKeyId = ak.trim(),
                            accessKeySecret = sk.trim(),
                            bucket = bucket.trim(),
                            note = note.trim()
                        )
                    )
                    onDone()
                }
            ) { Text("保存并激活") }
        }
    }
}
