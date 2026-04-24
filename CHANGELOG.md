# Changelog

All notable, user-visible changes to **OSS Browser for Android** are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions use
calendar-ish tags since no public release has been cut yet.

## Unreleased (2026-04-24)

### Added

- **OSS profile encrypted backup / restore** – Settings → *Backup & share* now supports
  one-tap export to a `.aloss` file (AES-256-GCM; optional PBKDF2 password) and import
  through Android SAF.
- **Author entry** in Settings → About – tap to open
  <https://github.com/JinlongLiao/>.
- **Release signing pipeline** – `app/build.gradle.kts` reads `keystore.properties`;
  helper scripts under `scripts/gen-release-keystore.(ps1|sh)` automate keystore
  generation. `release` build enables R8 + resource shrinking.
- **Bilingual documentation** – `README`, `REQUIREMENTS`, `DESIGN`, `USAGE` now ship
  English as the default and `*.zh-CN.md` Chinese counterparts with cross-links.
- **In-app Markdown renderer** – zero-dependency Compose renderer (`MarkdownView.kt`)
  backs the Privacy Policy, Open-Source Notice, and first-launch consent dialog.
  Supports headings, lists, bold / italic / inline code, links and bare URL auto-linking.
- **One-click install scripts** – `install.ps1` / `install.sh` push APKs through
  `/data/local/tmp` + `pm install -r -t`, bypassing HyperOS `INSTALL_FAILED_USER_RESTRICTED`.

### Changed

- **App display name** set to **OSS Browser for Android** across all string resources,
  raw-text privacy policy, and documentation. The GitHub repository slug stays as
  `alioss4droid`.
- **Package rename** from `com.ossbrowser.app` → `io.github.jinlongliao.ossbrowser`.
- **GitHub URLs** normalised to `https://github.com/JinlongLiao/alioss4droid` (HTTPS)
  and `git@github.com:JinlongLiao/alioss4droid.git` (SSH).
- **Line endings** unified to LF via `.gitattributes` + `.editorconfig`; the only
  exception is `scripts/gen-release-keystore.ps1`, pinned to CRLF.
- **Kotlin file headers** use canonical `https://github.com/JinlongLiao/` and
  `yyyy-MM-dd` `@since` format.
- **Default export filename** is now `oss-browser-profiles-YYYYMMDDHHmm.aloss`.

### Fixed

- Debug install → auto-launch failed because the launcher package was missing the
  `applicationIdSuffix = ".debug"`. Scripts now target
  `io.github.jinlongliao.ossbrowser.debug/.MainActivity`.
- Untranslated "no-profile" subtitle on the Browser top bar is now fully i18n via
  `browser_no_profile_title` / `browser_no_profile_subtitle`.
- Hardcoded strings in `BrowserScreen.kt` dialogs (Move / Confirm / Preview / Presign)
  moved to `strings.xml`.

## 0.1 – Initial bootstrap

- Jetpack Compose + Material 3 scaffolding.
- OSS profile (AK/SK) management with `EncryptedSharedPreferences`.
- Bucket + object browsing with lazy pagination, cache, previews (text / image / media
  / PDF), upload / download / delete / move-rename, presigned URLs.
- i18n: English / 简体中文 / 繁體中文.
- Privacy consent gate; Apache 2.0 license.
