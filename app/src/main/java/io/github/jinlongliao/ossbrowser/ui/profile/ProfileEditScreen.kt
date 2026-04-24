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
package io.github.jinlongliao.ossbrowser.ui.profile

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.data.OssProfile

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
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.profile_title_new
                            else R.string.profile_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
                label = { Text(stringResource(R.string.profile_field_name_required)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.profile_field_endpoint_required)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bucket,
                onValueChange = { bucket = it },
                label = { Text(stringResource(R.string.profile_field_bucket_optional)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ak,
                onValueChange = { ak = it },
                label = { Text(stringResource(R.string.profile_field_ak_required)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sk,
                onValueChange = { sk = it },
                label = { Text(stringResource(R.string.profile_field_sk_required)) },
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                OutlinedButton(onClick = { showSecret = !showSecret }) {
                    Text(
                        stringResource(
                            if (showSecret) R.string.profile_hide_secret
                            else R.string.profile_show_secret
                        )
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.profile_field_note)) },
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
            ) { Text(stringResource(R.string.profile_save_and_activate)) }
        }
    }
}
