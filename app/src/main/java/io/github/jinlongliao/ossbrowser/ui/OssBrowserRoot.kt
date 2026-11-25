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
package io.github.jinlongliao.ossbrowser.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.jinlongliao.ossbrowser.OssBrowserApp
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.ui.browser.BrowserScreen
import io.github.jinlongliao.ossbrowser.ui.profile.ProfileEditScreen
import io.github.jinlongliao.ossbrowser.ui.profile.ProfileListScreen
import io.github.jinlongliao.ossbrowser.ui.settings.LicensesScreen
import io.github.jinlongliao.ossbrowser.ui.settings.PrivacyConsentDialog
import io.github.jinlongliao.ossbrowser.ui.settings.PrivacyScreen
import io.github.jinlongliao.ossbrowser.ui.settings.SettingsScreen
import io.github.jinlongliao.ossbrowser.ui.settings.SettingsViewModel
import io.github.jinlongliao.ossbrowser.ui.theme.LocalThemeVisualStyle
import io.github.jinlongliao.ossbrowser.ui.theme.ThemeBackdrop

private object Routes {
    const val BROWSER = "browser"
    const val PROFILES = "profiles"
    const val PROFILE_EDIT = "profile_edit?id={id}"
    const val SETTINGS = "settings"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_LICENSES = "settings/licenses"
    fun profileEdit(id: String?) = "profile_edit?id=${id ?: ""}"
}

@Composable
fun OssBrowserRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val settings by settingsVm.state.collectAsState()
    val profileState by OssBrowserApp.instance.profileRepository.state.collectAsState()
    val context = LocalContext.current

    // 一级入口仅保留 OSS SDK 文件管理和本地设置。
    val showBottomBar = currentRoute == Routes.BROWSER ||
        currentRoute == Routes.SETTINGS

    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            contentColor = MaterialTheme.colorScheme.onBackground,
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    BrowserBottomBar(
                        showingFiles = currentRoute == Routes.BROWSER,
                        onOpenFiles = {
                            if (currentRoute != Routes.BROWSER) {
                                navController.navigate(Routes.BROWSER) {
                                    popUpTo(Routes.BROWSER) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenSettings = {
                            if (currentRoute != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.BROWSER,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.BROWSER) {
                    BrowserScreen(
                        onAddProfile = { navController.navigate(Routes.profileEdit(null)) },
                        onManageAccounts = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
                    )
                }
                composable(Routes.PROFILES) {
                    ProfileListScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { navController.navigate(Routes.profileEdit(null)) },
                        onEdit = { id -> navController.navigate(Routes.profileEdit(id)) }
                    )
                }
                composable(Routes.PROFILE_EDIT) { entry ->
                    val id = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() }
                    ProfileEditScreen(
                        profileId = id,
                        onDone = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Routes.BROWSER) { launchSingleTop = true }
                            }
                        },
                        onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                        onOpenPrivacy = { navController.navigate(Routes.SETTINGS_PRIVACY) },
                        onOpenLicenses = { navController.navigate(Routes.SETTINGS_LICENSES) },
                        activeProfileName = profileState.active?.name,
                        totalProfiles = profileState.profiles.size,
                        vm = settingsVm
                    )
                }
                composable(Routes.SETTINGS_PRIVACY) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS_LICENSES) {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    // 首次启动隐私协议同意拦截
    if (!settings.privacyAccepted) {
        PrivacyConsentDialog(
            onAccept = { settingsVm.acceptPrivacy() },
            onReject = { (context as? Activity)?.finish() }
        )
    }
}

/** 浮动底栏与系统导航区留出间距，页面主题可从两侧透出。 */
@Composable
private fun BrowserBottomBar(
    showingFiles: Boolean,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 4.dp
        ) {
            Row(Modifier.selectableGroup().padding(4.dp)) {
                BrowserBottomTab(Icons.Outlined.Folder, stringResource(R.string.nav_files), showingFiles, onOpenFiles)
                BrowserBottomTab(
                    Icons.Outlined.Settings, stringResource(R.string.nav_settings), !showingFiles, onOpenSettings
                )
            }
        }
    }
}

/** 图标和标签共享选中背景，整个胶囊区域都可以点击。 */
@Composable
private fun BrowserBottomTab(imageVector: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalThemeVisualStyle.current.accentColors
    val useGradient = selected && accent.isNotEmpty()
    val background = when {
        useGradient -> Modifier.background(Brush.horizontalGradient(accent))
        selected -> Modifier.background(colors.secondaryContainer)
        else -> Modifier
    }
    val tint = when {
        useGradient -> Color.White
        selected -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    Column(
        modifier = Modifier.clip(CircleShape).then(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .sizeIn(minWidth = 88.dp, minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
