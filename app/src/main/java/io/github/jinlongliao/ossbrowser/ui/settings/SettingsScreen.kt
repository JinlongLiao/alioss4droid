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
package io.github.jinlongliao.ossbrowser.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import io.github.jinlongliao.ossbrowser.ui.theme.themedCardSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import io.github.jinlongliao.ossbrowser.ui.theme.themeVisualStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import io.github.jinlongliao.ossbrowser.ui.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jinlongliao.ossbrowser.ui.theme.appColorScheme
import io.github.jinlongliao.ossbrowser.R
import io.github.jinlongliao.ossbrowser.ui.TonalIcon
import io.github.jinlongliao.ossbrowser.data.ProfileBackupCodec
import io.github.jinlongliao.ossbrowser.data.ThemeMode
import io.github.jinlongliao.ossbrowser.data.ThemePalette
import io.github.jinlongliao.ossbrowser.ui.profile.ProfileViewModel
import io.github.jinlongliao.ossbrowser.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration) {
        context.createConfigurationContext(configuration).resources
    }
    val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)

    var showThemeDialog by remember { mutableStateOf(false) }
    /** 是否展示独立于明暗模式的配色主题选择弹窗。 */
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showTextMaxDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var exportDialogOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    // 导出：用户先在对话框选"导出"→ 我们拉起 SAF 让用户挑路径；此处暂存当前输入的密码。
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportPassword by remember { mutableStateOf<String?>(null) }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val profiles = profileVm.snapshotForExport()
            val bytes = ProfileBackupCodec.encode(profiles, pwd)
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: error("cannot open output stream")
            profiles.size
        }.onSuccess { count ->
            Toast.makeText(
                context,
                resources.getString(R.string.export_success, count),
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure { e ->
            Toast.makeText(
                context,
                resources.getString(R.string.export_failed, e.localizedMessage ?: e.toString()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pwd = pendingImportPassword
        pendingImportPassword = null
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            Toast.makeText(
                context,
                resources.getString(R.string.import_failed, "cannot read file"),
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val list = ProfileBackupCodec.decode(bytes, pwd)
            profileVm.importMerge(list)
        }.onSuccess { (added, updated) ->
            Toast.makeText(
                context,
                resources.getString(R.string.import_success, added, updated),
                Toast.LENGTH_LONG
            ).show()
        }.onFailure { e ->
            val msg = when (e) {
                is ProfileBackupCodec.WrongPasswordException ->
                    resources.getString(R.string.import_wrong_password)
                is ProfileBackupCodec.BadFormatException ->
                    resources.getString(R.string.import_bad_format)
                else -> resources.getString(R.string.import_failed, e.localizedMessage ?: e.toString())
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
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

            // 密钥管理：设置页优先级最高的入口。卡片显示当前激活项，未配置时提供同样清晰的引导。
            SectionTitle(stringResource(R.string.settings_section_profiles))
            ProfileEntryCard(
                activeProfileName = activeProfileName,
                totalProfiles = totalProfiles,
                onClick = onOpenProfiles
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_appearance))
            SettingRow(
                icon = SettingsIcons.Palette,
                title = stringResource(R.string.settings_color_theme),
                subtitle = paletteLabel(settings.themePalette),
                onClick = { showPaletteDialog = true }
            )
            SettingRow(
                icon = SettingsIcons.Mode,
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel(settings.themeMode),
                onClick = { showThemeDialog = true }
            )
            SettingRow(
                icon = SettingsIcons.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(settings.languageTag),
                onClick = { showLangDialog = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_browse))
            SettingRow(
                icon = SettingsIcons.Pages,
                title = stringResource(R.string.settings_page_size),
                subtitle = stringResource(R.string.settings_page_size_value, settings.pageSize),
                onClick = { showPageSizeDialog = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_preview))
            SettingRow(
                icon = SettingsIcons.TextPreview,
                title = stringResource(R.string.settings_text_preview_max),
                subtitle = FileUtils.humanSize(settings.textPreviewMaxBytes),
                onClick = { showTextMaxDialog = true }
            )
            SettingSwitch(
                icon = SettingsIcons.Playback,
                title = stringResource(R.string.settings_auto_play),
                subtitle = stringResource(R.string.settings_auto_play_desc),
                checked = settings.autoPlayMedia,
                onChange = vm::setAutoPlayMedia
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_security))
            SettingSwitch(
                icon = SettingsIcons.Security,
                title = stringResource(R.string.settings_confirm_destructive),
                subtitle = stringResource(R.string.settings_confirm_destructive_desc),
                checked = settings.confirmDestructiveOps,
                onChange = vm::setConfirmDestructive
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_storage))
            SettingRow(
                icon = SettingsIcons.Cache,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_desc),
                onClick = { showClearCacheConfirm = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_backup))
            SettingRow(
                icon = SettingsIcons.Export,
                title = stringResource(R.string.settings_export_profiles),
                subtitle = stringResource(R.string.settings_export_profiles_desc),
                onClick = { exportDialogOpen = true }
            )
            SettingRow(
                icon = SettingsIcons.Import,
                title = stringResource(R.string.settings_import_profiles),
                subtitle = stringResource(R.string.settings_import_profiles_desc),
                onClick = { importDialogOpen = true }
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_section_about))
            SettingRow(
                icon = SettingsIcons.Info,
                title = stringResource(R.string.settings_version),
                subtitle = appVersionName(context)
            )
            SettingRow(
                icon = SettingsIcons.Person,
                title = stringResource(R.string.settings_author),
                subtitle = stringResource(R.string.settings_author_desc),
                onClick = { openUrl(context, SettingsContent.AUTHOR_URL) }
            )
            SettingRow(
                icon = SettingsIcons.Project,
                title = stringResource(R.string.settings_project),
                subtitle = SettingsContent.PROJECT_URL,
                onClick = { openUrl(context, SettingsContent.PROJECT_URL) }
            )
            SettingRow(
                icon = SettingsIcons.License,
                title = stringResource(R.string.settings_licenses),
                subtitle = stringResource(R.string.settings_licenses_desc),
                onClick = onOpenLicenses
            )
            SettingRow(
                icon = SettingsIcons.Privacy,
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

    if (showPaletteDialog) {
        ThemePaletteDialog(
            selected = settings.themePalette,
            onSelect = { vm.setThemePalette(it); showPaletteDialog = false },
            onDismiss = { showPaletteDialog = false }
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
                FileUtils.humanSize(10L * 1024 * 1024) to 10L * 1024 * 1024,
                FileUtils.humanSize(30L * 1024 * 1024) to 30L * 1024 * 1024
            ),
            selected = settings.textPreviewMaxBytes,
            onSelect = { vm.setTextPreviewMax(it); showTextMaxDialog = false },
            onDismiss = { showTextMaxDialog = false }
        )
    }

    if (exportDialogOpen) {
        ExportDialog(
            onDismiss = { exportDialogOpen = false },
            onConfirm = { password ->
                exportDialogOpen = false
                val profiles = profileVm.snapshotForExport()
                if (profiles.isEmpty()) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.export_nothing),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@ExportDialog
                }
                pendingExportPassword = password
                val defaultName = resources.getString(
                    R.string.export_default_name,
                    SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                )
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, defaultName)
                }
                exportPicker.launch(intent)
            }
        )
    }

    if (importDialogOpen) {
        ImportDialog(
            onDismiss = { importDialogOpen = false },
            onConfirm = { password ->
                importDialogOpen = false
                pendingImportPassword = password
                importPicker.launch(arrayOf("*/*"))
            }
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
                        resources.getString(R.string.settings_clear_cache_result, FileUtils.humanSize(cleared)),
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
    val containerColor = MaterialTheme.colorScheme.surface
    val onContainer = MaterialTheme.colorScheme.onSurface

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
            TonalIcon(
                imageVector = SettingsIcons.Account,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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

/**
 * 配色选择弹窗在切换前展示浅色示意色块，减少只凭主题名称猜测的成本。
 *
 * @param selected 当前生效的主题，保存后的枚举名兼容应用升级。
 * @param onSelect 用户选择主题时调用；不改变单独设置的明暗模式。
 * @param onDismiss 关闭弹窗。
 * @see <a href="https://manual.raycast.com/themes">Raycast theme preview</a>
 */
@Composable
private fun ThemePaletteDialog(
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_theme)) },
        text = {
            LazyColumn(
                state = rememberLazyListState(
                    initialFirstVisibleItemIndex = ThemePalette.entries.indexOf(selected).coerceAtLeast(0)
                ),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ThemePalette.entries, key = { it.name }) { palette ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .selectable(
                                selected = palette == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(palette) }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = palette == selected, onClick = null)
                        Text(paletteLabel(palette), modifier = Modifier.weight(1f))
                        ThemePalettePreview(palette)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/**
 * 将真实色板、材质和圆角缩略呈现为两张内容卡，避免只用色块掩盖主题间的形态差异。
 * @param palette 需要预览的主题；以浅色示例展示，应用时仍遵守用户明暗设置。
 */
@Composable
private fun ThemePalettePreview(palette: ThemePalette) {
    val colors = appColorScheme(palette, false)
    val style = themeVisualStyle(palette, false)
    val background = if (style.backgroundColors.size > 1) style.backgroundColors
        else listOf(colors.background, colors.background)
    Box(
        Modifier.width(82.dp).height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(background))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(7.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(style.cornerRadius / 4))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
                    Box(Modifier.width(32.dp).height(3.dp).background(colors.onSurface.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

/** 返回当前 Locale 下的配色主题名称。 */
@Composable
private fun paletteLabel(palette: ThemePalette): String = when (palette) {
    ThemePalette.DEFAULT -> stringResource(R.string.settings_color_theme_default)
    ThemePalette.GEMINI -> stringResource(R.string.settings_color_theme_gemini)
    ThemePalette.SUMMER_SKY -> stringResource(R.string.settings_color_theme_summer_sky)
    ThemePalette.CELADON -> stringResource(R.string.settings_color_theme_celadon)
    ThemePalette.DUSK -> stringResource(R.string.settings_color_theme_dusk)
    ThemePalette.SAND -> stringResource(R.string.settings_color_theme_sand)
    ThemePalette.PRISM -> stringResource(R.string.settings_color_theme_prism)
    ThemePalette.AURORA -> stringResource(R.string.settings_color_theme_aurora)
    ThemePalette.GLACIER_GLASS -> stringResource(R.string.settings_color_theme_glacier_glass)
    ThemePalette.TWILIGHT_GLASS -> stringResource(R.string.settings_color_theme_twilight_glass)
    ThemePalette.MONOCHROME -> stringResource(R.string.settings_color_theme_monochrome)
    ThemePalette.TERMINAL -> stringResource(R.string.settings_color_theme_terminal)
    ThemePalette.CYBER -> stringResource(R.string.settings_color_theme_cyber)
    ThemePalette.PAPER -> stringResource(R.string.settings_color_theme_paper)
    ThemePalette.SAKURA -> stringResource(R.string.settings_color_theme_sakura)
    ThemePalette.FOREST -> stringResource(R.string.settings_color_theme_forest)
    ThemePalette.SEA_SALT -> stringResource(R.string.settings_color_theme_sea_salt)
    ThemePalette.SUNSET -> stringResource(R.string.settings_color_theme_sunset)
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "" -> stringResource(R.string.settings_language_system)
    "en" -> stringResource(R.string.settings_language_en)
    "zh-CN" -> stringResource(R.string.settings_language_zh_cn)
    "zh-TW" -> stringResource(R.string.settings_language_zh_tw)
    else -> tag
}

/** 设置分组标题，通过字重和留白区分层级。 */
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

/** 分组间使用轻量分隔，不横穿页面边缘。 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 设置项卡片；只有可操作项显示方向提示，文本仍支持系统字号。 */
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .themedCardSurface()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TonalIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 开关项与普通设置项共用卡片样式，点击和开关逻辑沿用既有行为。 */
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .themedCardSurface()
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TonalIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SettingsIcons.Export, contentDescription = null) },
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.export_dialog_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.export_password_label)) },
                    singleLine = true
                )
                Text(
                    stringResource(R.string.export_password_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(password.takeIf { it.isNotEmpty() })
            }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SettingsIcons.Import, contentDescription = null) },
        title = { Text(stringResource(R.string.import_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.import_dialog_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.import_password_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(password.takeIf { it.isNotEmpty() })
            }) { Text(stringResource(R.string.import_pick)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
