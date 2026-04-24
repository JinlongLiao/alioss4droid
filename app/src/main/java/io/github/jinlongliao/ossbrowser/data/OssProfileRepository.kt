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
package io.github.jinlongliao.ossbrowser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * OSS 配置仓库，使用 EncryptedSharedPreferences 对 AK/SK 进行本地加密存储。
 */
class OssProfileRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _state = MutableStateFlow(load())

    /** 当前所有配置与激活配置的最新快照。 */
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private fun load(): ProfileState {
        val raw = prefs.getString(KEY_STORE, null) ?: return ProfileState(emptyList(), null)
        return runCatching {
            val store = json.decodeFromString(ProfileStore.serializer(), raw)
            ProfileState(store.profiles, store.activeId?.takeIf { id -> store.profiles.any { it.id == id } })
        }.getOrElse { ProfileState(emptyList(), null) }
    }

    private fun persist(state: ProfileState) {
        val store = ProfileStore(state.profiles, state.activeId)
        prefs.edit()
            .putString(KEY_STORE, json.encodeToString(ProfileStore.serializer(), store))
            .apply()
        _state.value = state
    }

    fun upsert(profile: OssProfile) {
        val current = _state.value
        val list = current.profiles.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        val active = current.activeId ?: profile.id
        persist(ProfileState(list, active))
    }

    fun delete(id: String) {
        val current = _state.value
        val list = current.profiles.filterNot { it.id == id }
        val active = if (current.activeId == id) list.firstOrNull()?.id else current.activeId
        persist(ProfileState(list, active))
    }

    fun activate(id: String) {
        val current = _state.value
        if (current.profiles.none { it.id == id }) return
        persist(current.copy(activeId = id))
    }

    fun active(): OssProfile? {
        val s = _state.value
        return s.profiles.firstOrNull { it.id == s.activeId }
    }

    /** 当前所有配置快照（用于导出）。 */
    fun snapshot(): List<OssProfile> = _state.value.profiles.toList()

    /**
     * 将导入的配置与现有配置合并。
     *
     * 合并策略：
     * - 相同 `id` 视为同一条配置 → 覆盖更新；
     * - 若 `id` 不存在但 `endpoint + accessKeyId` 相同，也视为重复 → 覆盖；
     * - 其余视为新增。
     *
     * @return `Pair(added, updated)`。
     */
    fun importMerge(incoming: List<OssProfile>): Pair<Int, Int> {
        val current = _state.value
        val merged = current.profiles.toMutableList()
        var added = 0
        var updated = 0
        for (p in incoming) {
            val byId = merged.indexOfFirst { it.id == p.id }
            val idx = if (byId >= 0) byId else merged.indexOfFirst {
                it.endpoint.equals(p.endpoint, true) && it.accessKeyId == p.accessKeyId
            }
            if (idx >= 0) {
                merged[idx] = p.copy(id = merged[idx].id)
                updated++
            } else {
                merged.add(p)
                added++
            }
        }
        val active = current.activeId ?: merged.firstOrNull()?.id
        persist(ProfileState(merged, active))
        return added to updated
    }

    companion object {
        private const val PREFS_NAME = "oss_profiles"
        private const val KEY_STORE = "store"
    }
}

data class ProfileState(
    val profiles: List<OssProfile>,
    val activeId: String?
) {
    val active: OssProfile? get() = profiles.firstOrNull { it.id == activeId }
}
