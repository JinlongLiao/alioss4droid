package com.ossbrowser.app.data

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
