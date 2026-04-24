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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.data.OssProfile
import io.github.jinlongliao.ossbrowser.data.OssProfileRepository
import io.github.jinlongliao.ossbrowser.data.ProfileState
import io.github.jinlongliao.ossbrowser.oss.OssClientProvider
import io.github.jinlongliao.ossbrowser.oss.OssListCache
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class ProfileViewModel(
    private val repository: OssProfileRepository,
    private val clientProvider: OssClientProvider,
    private val listCache: OssListCache
) : ViewModel() {

    val state: StateFlow<ProfileState> = repository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.state.value)

    fun save(profile: OssProfile) {
        repository.upsert(profile)
        clientProvider.invalidate()
        // AK/SK 可能换到了别的账号，整体清空缓存更稳。
        listCache.clear()
    }

    fun delete(id: String) {
        repository.delete(id)
        clientProvider.invalidate()
        listCache.clear()
    }

    fun activate(id: String) {
        repository.activate(id)
        clientProvider.invalidate()
        // 纯切换激活账号，已有的缓存仍然可以复用（按 profileId 隔离）。
    }

    /** 导出：返回快照供设置页进一步加密与保存。 */
    fun snapshotForExport(): List<OssProfile> = repository.snapshot()

    /**
     * 合并导入的配置并使缓存/客户端失效。
     *
     * @return `Pair(added, updated)`
     */
    fun importMerge(list: List<OssProfile>): Pair<Int, Int> {
        val result = repository.importMerge(list)
        clientProvider.invalidate()
        listCache.clear()
        return result
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = OssBrowserApp.instance
                return ProfileViewModel(
                    repository = app.profileRepository,
                    clientProvider = app.ossClientProvider,
                    listCache = app.ossListCache
                ) as T
            }
        }
    }
}
