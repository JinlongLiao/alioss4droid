package com.ossbrowser.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ossbrowser.app.OssBrowserApp
import com.ossbrowser.app.data.OssProfile
import com.ossbrowser.app.data.OssProfileRepository
import com.ossbrowser.app.data.ProfileState
import com.ossbrowser.app.oss.OssClientProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class ProfileViewModel(
    private val repository: OssProfileRepository,
    private val clientProvider: OssClientProvider
) : ViewModel() {

    val state: StateFlow<ProfileState> = repository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.state.value)

    fun save(profile: OssProfile) {
        repository.upsert(profile)
        clientProvider.invalidate()
    }

    fun delete(id: String) {
        repository.delete(id)
        clientProvider.invalidate()
    }

    fun activate(id: String) {
        repository.activate(id)
        clientProvider.invalidate()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = OssBrowserApp.instance
                return ProfileViewModel(app.profileRepository, app.ossClientProvider) as T
            }
        }
    }
}
