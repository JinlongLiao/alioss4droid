package io.github.jinlongliao.ossbrowser.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验证浏览布局使用真实 SharedPreferences 持久化，覆盖旧版本和未来未知值兼容。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BrowserLayoutSettingsTest {
    /** 各测试使用 Robolectric 沙箱中的配置，不读取设备或开发者账号数据。 */
    @Before
    fun clearBrowserPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /** 首次安装及缺少新增字段的旧版本均使用用户要求的单列列表。 */
    @Test
    fun defaultsToListForExistingAndFreshSettings() {
        val initial = SettingsRepository(RuntimeEnvironment.getApplication()).settings.value
        assertEquals(BrowserLayoutMode.LIST, initial.browserLayoutMode)
        assertEquals(BrowserLayoutMode.LIST, restoreBrowserLayoutMode(null))
        assertEquals(BrowserLayoutMode.LIST, restoreBrowserLayoutMode("FUTURE_LAYOUT"))
    }

    /** 切换后重建仓库仍保留瀑布流，调整其他设置也不会重置显示偏好。 */
    @Test
    fun retainsSelectedLayoutAcrossRepositoryRecreationAndOtherSettingsChanges() {
        val context = RuntimeEnvironment.getApplication()
        val original = SettingsRepository(context)
        original.update { it.copy(browserLayoutMode = BrowserLayoutMode.WATERFALL) }
        val reloaded = SettingsRepository(context)
        assertEquals(BrowserLayoutMode.WATERFALL, reloaded.settings.value.browserLayoutMode)
        reloaded.update { it.copy(pageSize = 30, themePalette = ThemePalette.GEMINI) }
        val latest = SettingsRepository(context).settings.value
        assertEquals(BrowserLayoutMode.WATERFALL, latest.browserLayoutMode)
        assertEquals(30, latest.pageSize)
        assertEquals(ThemePalette.GEMINI, latest.themePalette)
        reloaded.update { it.copy(browserLayoutMode = BrowserLayoutMode.LIST) }
        assertEquals(BrowserLayoutMode.LIST, SettingsRepository(context).settings.value.browserLayoutMode)
    }

    /** 未知布局值只回退显示方式，不清空原有主题等设置。 */
    @Test
    fun unknownStoredLayoutFallsBackWithoutResettingOtherSettings() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(SettingsRepository.KEY_BROWSER_LAYOUT_MODE, "FUTURE_LAYOUT")
            .putString(SettingsRepository.KEY_THEME_PALETTE, ThemePalette.PAPER.name).commit()
        val settings = SettingsRepository(context).settings.value
        assertEquals(BrowserLayoutMode.LIST, settings.browserLayoutMode)
        assertEquals(ThemePalette.PAPER, settings.themePalette)
    }
}
