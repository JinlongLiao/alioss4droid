package com.ossbrowser.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ossbrowser.app.OssBrowserApp
import com.ossbrowser.app.data.AppSettings
import com.ossbrowser.app.data.SettingsRepository
import com.ossbrowser.app.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val state: StateFlow<AppSettings> = repo.settings

    fun setThemeMode(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    fun setPageSize(size: Int) = repo.update { it.copy(pageSize = size.coerceIn(1, 1000)) }
    fun setTextPreviewMax(bytes: Long) = repo.update { it.copy(textPreviewMaxBytes = bytes) }
    fun setAutoPlayMedia(enabled: Boolean) = repo.update { it.copy(autoPlayMedia = enabled) }
    fun setConfirmDestructive(enabled: Boolean) = repo.update { it.copy(confirmDestructiveOps = enabled) }
    fun setLanguageTag(tag: String) = repo.update { it.copy(languageTag = tag) }
    fun acceptPrivacy() = repo.acceptPrivacy()

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(OssBrowserApp.instance.settingsRepository) as T
        }
    }
}
