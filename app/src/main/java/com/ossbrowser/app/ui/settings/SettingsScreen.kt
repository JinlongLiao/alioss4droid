package com.ossbrowser.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ossbrowser.app.R
import com.ossbrowser.app.data.ThemeMode
import com.ossbrowser.app.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    activeProfileName: String? = null,
    totalProfiles: Int = 0,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by vm.state.collectAsState()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showTextMaxDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 密钥管理：设置页优先级最高的入口。醒目卡片突出显示当前激活项，未配置时以警示色提示。
            SectionTitle(stringResource(R.string.settings_section_profiles))
            ProfileEntryCard(
                activeProfileName = activeProfileName,
                totalProfiles = totalProfiles,
                onClick = onOpenProfiles
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_appearance))
            SettingRow(
                icon = Icons.Outlined.Brightness6,
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel(settings.themeMode),
                onClick = { showThemeDialog = true }
            )
            SettingRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(settings.languageTag),
                onClick = { showLangDialog = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_browse))
            SettingRow(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.settings_page_size),
                subtitle = stringResource(R.string.settings_page_size_value, settings.pageSize),
                onClick = { showPageSizeDialog = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_preview))
            SettingRow(
                icon = Icons.Outlined.TextSnippet,
                title = stringResource(R.string.settings_text_preview_max),
                subtitle = FileUtils.humanSize(settings.textPreviewMaxBytes),
                onClick = { showTextMaxDialog = true }
            )
            SettingSwitch(
                icon = Icons.Outlined.PlayCircle,
                title = stringResource(R.string.settings_auto_play),
                subtitle = stringResource(R.string.settings_auto_play_desc),
                checked = settings.autoPlayMedia,
                onChange = vm::setAutoPlayMedia
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_security))
            SettingSwitch(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.settings_confirm_destructive),
                subtitle = stringResource(R.string.settings_confirm_destructive_desc),
                checked = settings.confirmDestructiveOps,
                onChange = vm::setConfirmDestructive
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_storage))
            SettingRow(
                icon = Icons.Outlined.CleaningServices,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_desc),
                onClick = { showClearCacheConfirm = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_about))
            SettingRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_version),
                subtitle = appVersionName(context)
            )
            SettingRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.settings_project),
                subtitle = SettingsContent.PROJECT_URL,
                onClick = { openUrl(context, SettingsContent.PROJECT_URL) }
            )
            SettingRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.settings_licenses_desc),
                onClick = onOpenLicenses
            )
            SettingRow(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_desc),
                onClick = onOpenPrivacy
            )
        }
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                stringResource(R.string.settings_theme_system) to ThemeMode.SYSTEM,
                stringResource(R.string.settings_theme_light) to ThemeMode.LIGHT,
                stringResource(R.string.settings_theme_dark) to ThemeMode.DARK
            ),
            selected = settings.themeMode,
            onSelect = { vm.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLangDialog) {
        val options = listOf(
            stringResource(R.string.settings_language_system) to "",
            stringResource(R.string.settings_language_en) to "en",
            stringResource(R.string.settings_language_zh_cn) to "zh-CN",
            stringResource(R.string.settings_language_zh_tw) to "zh-TW"
        )
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = options,
            selected = settings.languageTag,
            onSelect = { tag ->
                showLangDialog = false
                if (tag != settings.languageTag) {
                    vm.setLanguageTag(tag)
                    // 切换语言必须 recreate Activity 才能让资源重新加载
                    (context as? Activity)?.recreate()
                }
            },
            onDismiss = { showLangDialog = false }
        )
    }

    if (showPageSizeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_page_size),
            options = listOf(
                stringResource(R.string.settings_page_size_value, 10) to 10,
                stringResource(R.string.settings_page_size_value, 20) to 20,
                stringResource(R.string.settings_page_size_value, 50) to 50,
                stringResource(R.string.settings_page_size_value, 100) to 100
            ),
            selected = settings.pageSize,
            onSelect = { vm.setPageSize(it); showPageSizeDialog = false },
            onDismiss = { showPageSizeDialog = false }
        )
    }

    if (showTextMaxDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_text_preview_max),
            options = listOf(
                FileUtils.humanSize(512L * 1024) to 512L * 1024,
                FileUtils.humanSize(2L * 1024 * 1024) to 2L * 1024 * 1024,
                FileUtils.humanSize(5L * 1024 * 1024) to 5L * 1024 * 1024,
                FileUtils.humanSize(10L * 1024 * 1024) to 10L * 1024 * 1024
            ),
            selected = settings.textPreviewMaxBytes,
            onSelect = { vm.setTextPreviewMax(it); showTextMaxDialog = false },
            onDismiss = { showTextMaxDialog = false }
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    val cleared = clearAppCache(context)
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_clear_cache_result, FileUtils.humanSize(cleared)),
                        Toast.LENGTH_SHORT
                    ).show()
                    showClearCacheConfirm = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileEntryCard(
    activeProfileName: String?,
    totalProfiles: Int,
    onClick: () -> Unit
) {
    val hasActive = !activeProfileName.isNullOrBlank()
    val containerColor = if (hasActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.errorContainer
    val onContainer = if (hasActive)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasActive) Icons.Outlined.VpnKey else Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = onContainer
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = when {
                    hasActive -> stringResource(
                        R.string.settings_profiles_active, activeProfileName!!, totalProfiles
                    )
                    totalProfiles > 0 -> stringResource(
                        R.string.settings_profiles_not_activated, totalProfiles
                    )
                    else -> stringResource(R.string.settings_profiles_none)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = onContainer
            )
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "" -> stringResource(R.string.settings_language_system)
    "en" -> stringResource(R.string.settings_language_en)
    "zh-CN" -> stringResource(R.string.settings_language_zh_cn)
    "zh-TW" -> stringResource(R.string.settings_language_zh_tw)
    else -> tag
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.padding(start = 16.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, it.localizedMessage ?: "Failed to open URL", Toast.LENGTH_SHORT).show()
    }
}

private fun appVersionName(context: Context): String = runCatching {
    val pm = context.packageManager
    val info = pm.getPackageInfo(context.packageName, 0)
    "${info.versionName} (${info.longVersionCode})"
}.getOrDefault("-")

/** 递归删除 `cacheDir` 下所有内容，返回清理的字节数。 */
private fun clearAppCache(context: Context): Long {
    val root = context.cacheDir ?: return 0L
    var cleared = 0L
    fun walk(f: File) {
        if (f.isDirectory) {
            f.listFiles()?.forEach { walk(it) }
        } else if (f.isFile) {
            cleared += f.length()
            f.delete()
        }
    }
    root.listFiles()?.forEach { walk(it) }
    return cleared
}
