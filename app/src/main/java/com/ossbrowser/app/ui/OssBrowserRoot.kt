package com.ossbrowser.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ossbrowser.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ossbrowser.app.ui.browser.BrowserScreen
import com.ossbrowser.app.ui.profile.ProfileEditScreen
import com.ossbrowser.app.ui.profile.ProfileListScreen
import com.ossbrowser.app.ui.settings.LicensesScreen
import com.ossbrowser.app.ui.settings.PrivacyConsentDialog
import com.ossbrowser.app.ui.settings.PrivacyScreen
import com.ossbrowser.app.ui.settings.SettingsScreen
import com.ossbrowser.app.ui.settings.SettingsViewModel

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
    val context = LocalContext.current

    val showBottomBar = currentRoute == Routes.BROWSER ||
        currentRoute == Routes.PROFILES ||
        currentRoute == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.BROWSER,
                        onClick = {
                            if (currentRoute != Routes.BROWSER) {
                                navController.navigate(Routes.BROWSER) {
                                    popUpTo(Routes.BROWSER) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_files)) }
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hierarchy?.any { it.route?.startsWith("profile") == true } == true,
                        onClick = {
                            if (currentRoute != Routes.PROFILES) {
                                navController.navigate(Routes.PROFILES) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_profiles)) }
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hierarchy?.any { it.route?.startsWith("settings") == true } == true,
                        onClick = {
                            if (currentRoute != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSER,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Routes.BROWSER) {
                BrowserScreen(
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) }
                )
            }
            composable(Routes.PROFILES) {
                ProfileListScreen(
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
                    onOpenPrivacy = { navController.navigate(Routes.SETTINGS_PRIVACY) },
                    onOpenLicenses = { navController.navigate(Routes.SETTINGS_LICENSES) },
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

    // 首次启动隐私协议同意拦截
    if (!settings.privacyAccepted) {
        PrivacyConsentDialog(
            onAccept = { settingsVm.acceptPrivacy() },
            onReject = { (context as? Activity)?.finish() }
        )
    }
}
