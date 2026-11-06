package io.github.jinlongliao.ossbrowser.oss

import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件图标回归测试：避免安装包、网页和结构化数据退化成同一通用符号。 */
class OssFileIconTest {
    /** 用户指出的六类文件必须有可辨识的独立轮廓，不能只靠不同颜色区分。 */
    @Test
    fun reportedFileTypesHaveDistinctGlyphs() {
        val names = listOf("app.apk", "config.json", "index.html", "setup.exe", "disk.dmg", "installer.pkg")
        val glyphs = names.map { iconForFileName(it).glyph }
        assertEquals("APK、JSON、HTML、EXE、DMG、PKG 不应共用通用图形", names.size, glyphs.toSet().size)
    }

    /** 平台安装包、网页、数据和脚本使用对应语义，不再仅仅换色。 */
    @Test
    fun commonFormatsUseRecognizableTypeGlyphs() {
        val examples = mapOf(
            StorageGlyph.ANDROID to listOf("APP.APK", "bundle.aab", "split.apks", "release.xapk", "lib.aar"),
            StorageGlyph.JSON to listOf("config.json", "data.jsonl", "settings.jsonc", "map.geojson"),
            StorageGlyph.WEB to listOf("index.html", "index.HTM", "page.xhtml", "offline.mhtml"),
            StorageGlyph.WINDOWS to listOf("setup.exe", "tool.com"),
            StorageGlyph.DISK to listOf("app.dmg", "system.iso", "drive.img", "disk.vhdx", "disk.qcow2"),
            StorageGlyph.PACKAGE to listOf("installer.pkg", "setup.msi", "app.msix", "app.deb", "app.rpm"),
            StorageGlyph.APP to listOf("desktop.app", "portable.AppImage", "ios.ipa"),
            StorageGlyph.TERMINAL to listOf("run.sh", "start.bat", "script.cmd", "run.ps1", "Dockerfile", "Makefile"),
            StorageGlyph.CONFIG to listOf(
                "settings.yaml", "config.toml", "app.ini", ".env", ".env.production", ".gitignore"
            ),
            StorageGlyph.DATABASE to listOf("cache.db", "store.sqlite3", "schema.sql"),
            StorageGlyph.LIBRARY to listOf("tool.dll", "lib.so", "lib.so.1.2", "lib.dylib", "program.wasm"),
            StorageGlyph.PDF to listOf("report.pdf"),
            StorageGlyph.WORD to listOf("report.docx", "document.odt"),
            StorageGlyph.MARKDOWN to listOf("README.md", "guide.markdown"),
            StorageGlyph.STYLE to listOf("style.css", "app.scss"),
            StorageGlyph.CODE to listOf("main.kt", "main.ts", "app.js", "data.xml"),
            StorageGlyph.JAVA to listOf("app.jar", "server.war", "Main.java")
        )
        examples.forEach { (expected, names) ->
            names.forEach { assertEquals(it, expected, iconForFileName(it).glyph) }
        }
    }

    /** 目录中的点、中文、大小写不会影响文件类型，列表和预览查找复用同一矢量实例。 */
    @Test
    fun pathsAndCaseShareTheSameFixedFileIcon() {
        assertSame(iconForFileName("app.apk").icon, iconForFileName("版本.v1/安装包/APP.APK").icon)
        assertSame(iconForFileName("data.json").icon, iconForFileName("C:\\files.v2\\DATA.JSON").icon)
        assertEquals(StorageGlyph.TERMINAL, iconForFileName("dir.with.dots/Dockerfile").glyph)
        assertEquals(StorageGlyph.UNKNOWN, iconForFileName("dir.json/file-without-extension").glyph)
    }

    /** 以最终后缀判断，双扩展名不能伪装成安装程序；未知格式保持中性未知类型。 */
    @Test
    fun finalSuffixAndUnknownFormatsRemainUnambiguous() {
        assertEquals(StorageGlyph.ARCHIVE, iconForFileName("release.apk.zip").glyph)
        assertEquals(StorageGlyph.TEXT, iconForFileName("index.html.txt").glyph)
        assertEquals(StorageGlyph.UNKNOWN, iconForFileName("file.unrecognized").glyph)
        assertEquals(StorageGlyph.UNKNOWN, iconForFileName("").glyph)
        assertEquals(StorageGlyph.IMAGE, iconForFileName("photo.avif").glyph)
        assertEquals(StorageGlyph.VIDEO, iconForFileName("movie.mp4").glyph)
        assertEquals(StorageGlyph.AUDIO, iconForFileName("music.flac").glyph)
        assertEquals(StorageGlyph.TABLE, iconForFileName("report.xlsx").glyph)
        assertEquals(StorageGlyph.SLIDES, iconForFileName("deck.pptx").glyph)
    }
}
