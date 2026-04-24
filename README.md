# OSS Browser for Android

> Language / 语言：**English** · [简体中文](README.zh-CN.md)

> Repository slug: `alioss4droid` · the user-visible app name is **OSS Browser for Android**.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![targetSdk](https://img.shields.io/badge/targetSdk-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7f52ff)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BoM%202024.09-4285f4)

> A native Android client for **Aliyun OSS (Alibaba Cloud Object Storage)**, inspired by
> [aliyun/oss-browser](https://github.com/aliyun/oss-browser) but tailored for mobile.
> Built with **Kotlin + Jetpack Compose + Material 3**, suitable as a daily tool to manage buckets,
> browse objects and preview files on the go.

- App name: **OSS Browser for Android**
- Author: **jinlongliao** · <https://github.com/JinlongLiao/>
- Repository: <https://github.com/JinlongLiao/alioss4droid>
- Application ID: `io.github.jinlongliao.ossbrowser`
- License: [Apache License 2.0](LICENSE)

---

## ✨ Features

### Account & security

- Manage multiple OSS profiles (AK/SK + Endpoint): create / edit / delete / switch active.
- Credentials stored in `EncryptedSharedPreferences` (AES‑256‑GCM, Android Keystore backed).
- Multi-endpoint `OSSClient` cache (per profile × endpoint) — cross-region buckets just work.
- Privacy Policy consent gate on first launch; privacy & OSS notice surfaces inside Settings.
- **Encrypted one-click export / import of OSS profiles** for device-to-device sharing.
  - AES‑256‑GCM, optional PBKDF2 password; default mode decodable only by this app.

### Browse & file operations

- Bucket list and object list use **lazy pagination** (10 per page by default; auto-load on scroll).
- Folder-first sort, then case-insensitive name ascending.
- Breadcrumb / parent navigation / direct path jump (if the path is a file, jump to its parent and highlight).
- System back gesture mapping: inside a folder → go up one level; bucket list → two quick taps to exit.
- **Local cache** (in-memory + disk, 30-minute TTL): navigation is instant; tap Refresh to force network.
- Upload (SAF, with progress), download (`CREATE_DOCUMENT` lets you pick destination), delete,
  move / rename (recursive for folders).
- Destructive actions (delete / move) have a confirmation dialog — can be disabled in Settings.
- Generate presigned download URLs with an hour-granularity expiry (1–168h), with Copy / Share.

### File preview

| Kind | Implementation |
| --- | --- |
| Text (txt / json / xml / yaml / md / groovy / gradle / kts / plist / dart / scala / hcl …) | Streams into memory (size cap is configurable), `SelectionContainer` for copy. |
| Image (jpg / png / webp / gif / bmp …) | Coil + `coil-gif`, fetched via presigned URL. |
| Audio / Video | Media3 ExoPlayer, embedded playback with pause / seek / progress bar. |
| PDF | `PdfRenderer` page-by-page bitmap rendering. |

### Internationalization & look

- English / 简体中文 / 繁體中文; follows system locale by default, switchable in Settings.
- Light / Dark / Follow-system themes.
- Per-extension file icons (colored); unknown types fall back to a generic icon.

---

## 🧱 Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (Compose BoM 2024.09) + Material 3 |
| Navigation | Navigation Compose |
| State | ViewModel + StateFlow + Kotlin Coroutines |
| Secure storage | `androidx.security:security-crypto` (EncryptedSharedPreferences) |
| Serialization | `kotlinx.serialization` JSON |
| Cloud SDK | `com.aliyun.dpa:oss-android-sdk` 2.9.13 |
| Image | Coil 2.7.0 + coil-gif |
| Media | Media3 ExoPlayer 1.4.1 |
| Crypto | AES‑256‑GCM + PBKDF2‑HMAC‑SHA256 (200k) for profile export |
| Build | AGP 8.5.2 + Gradle 8.9 + Version Catalog |

---

## 🛠️ Build environment

| Requirement | Version |
| --- | --- |
| JDK | **17** (Temurin 17 LTS recommended) |
| Android SDK | `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34` |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.9 (wrapper committed) |
| Kotlin | 2.0.21 |
| Android Studio | **Koala Feature Drop (2024.1.2)+** |

> Aliyun Maven mirrors are pre-configured in `settings.gradle.kts` to speed up dependency resolution in mainland China.

---

## 🚀 Quick start

### Option A · Android Studio

1. `File ▸ Open` and pick this repository root.
2. Wait for Gradle sync (1–3 minutes depending on network).
3. Plug in a device (USB debugging on) or launch an emulator, click ▶ on `app`.

> Xiaomi / Redmi devices sometimes report `INSTALL_FAILED_USER_RESTRICTED` on first install —
> enable **"Install via USB"** in Developer Options.

### Option B · Command line

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Option C · One-shot script (recommended for Xiaomi HyperOS)

Use `install.ps1` (Windows) or `install.sh` (macOS / Linux) at the repo root. They push to
`/data/local/tmp` and invoke `pm install`, avoiding the HyperOS USB-install restriction.

```powershell
# Windows (PowerShell)
.\install.ps1 -Launch             # build + install + launch
.\install.ps1 -NoBuild -Launch    # install existing APK + launch
.\install.ps1 -Clean -Launch      # clean build + install + launch
```

```bash
# macOS / Linux
./install.sh --launch             # build + install + launch
./install.sh --no-build --launch  # install existing APK + launch
./install.sh --clean --launch     # clean build + install + launch
```

---

## 🔏 Release build & signing

1. Copy `keystore.properties.sample` to `keystore.properties`, or run the helper:

   ```powershell
   # Windows
   powershell -ExecutionPolicy Bypass -File .\scripts\gen-release-keystore.ps1
   ```

   ```bash
   # Linux / macOS
   bash scripts/gen-release-keystore.sh
   ```

   The script creates `alioss4droid-release.jks` plus a populated `keystore.properties`
   (both are listed in `.gitignore`).

2. Produce a signed APK / AAB:

   ```bash
   ./gradlew :app:assembleRelease  # app-release.apk
   ./gradlew :app:bundleRelease    # app-release.aab (for Google Play / Huawei AppGallery)
   ```

   Release builds use R8 minification + resource shrinking; ProGuard rules keep the Aliyun SDK
   and `kotlinx.serialization` generated classes.

3. Upload `.aab` / `.apk` to Play Console / Huawei / Xiaomi / OPPO / vivo. Data safety is already
   declared inside `app/src/main/res/raw/privacy_policy*.md` — copy it to the store form.

---

## 📦 OSS profile backup (encrypted)

*Settings → Backup & share → Export OSS profiles* produces an `.aloss` file structured as:

```
magic "ALOS" | version | mode | salt(16) | iv(12) | AES‑256‑GCM(ciphertext)
```

- **App key mode** (default): AES key = `SHA‑256(AppSecret || salt)` → any install of this app
  can decode the file. Intended for moving your own settings to another phone.
- **Password mode**: AES key = `PBKDF2WithHmacSHA256(password, salt, 200_000, 256)` → even another
  install cannot decode without the password. Recommended when sharing across accounts.

Importing merges by `id`, then by `(endpoint + AccessKey ID)`; duplicates are overwritten and new
entries are appended. Cached bucket / folder listings are invalidated automatically.

---

## 📱 Using the app (quick view)

1. **First launch**: review and accept the privacy policy.
2. **Add a profile**: *Settings → OSS Profile → Add* → fill AccessKey ID/Secret, endpoint
   (e.g. `https://oss-cn-hangzhou.aliyuncs.com`), optional default bucket → **Save & activate**.
3. **Browse**: bottom tab *Files* → bucket list → tap a bucket → folder view.
4. **Common actions**: tap the per-row icons for upload / download / preview / delete / presign /
   move-rename; long-press is not required.
5. **Refresh vs cache**: listings hit a 30-min cache by default; tap the refresh icon to bypass.
6. **Clear cache**: *Settings → Clear preview cache*.

Full walkthrough: [docs/USAGE.md](docs/USAGE.md) (English), [docs/USAGE.zh-CN.md](docs/USAGE.zh-CN.md) (中文).

---

## 🔐 Security & compliance

- AK/SK only live inside `EncryptedSharedPreferences`; the data key is Android-Keystore-derived,
  never leaving the device.
- Android backup policies (`fullBackupContent` / `dataExtractionRules`) exclude `oss_profiles.xml`
  so credentials do not end up in cloud backups.
- Network Security Config only allows cleartext for `*.aliyuncs.com` subdomains; everything else
  must be HTTPS.
- Recommended RAM sub-account policy: grant only the buckets you intend to access, with
  `ListObjects`, `GetObject`, `PutObject`, `DeleteObject`.

---

## 📚 Documentation

- [Requirements (PRD)](docs/REQUIREMENTS.md) · [中文](docs/REQUIREMENTS.zh-CN.md)
- [Design](docs/DESIGN.md) · [中文](docs/DESIGN.zh-CN.md)
- [User guide](docs/USAGE.md) · [中文](docs/USAGE.zh-CN.md)
- [Changelog](CHANGELOG.md)

---

## 🧭 Roadmap

- [ ] Resumable / multipart upload (`ResumableUploadRequest`).
- [ ] Multi-select batch delete / move / download.
- [ ] Object ACL & storage class editing.
- [ ] STS temporary credentials.
- [ ] QR code / bulk export of presigned links.

---

## 🤝 Contributing

Issues & PRs welcome. Please keep in mind:

1. Follow the `.editorconfig` formatting (4-space indent, LF in source).
2. New source files must carry the Apache 2.0 header.
3. All user-visible strings go through `res/values*/strings.xml` — no hardcoded Chinese/English in Kotlin.

---

## 📝 License

```
Copyright 2026 jinlongliao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

For bundled third-party components and their licenses see **Settings → Open source licenses** in
the app, or `app/src/main/java/io/github/jinlongliao/ossbrowser/ui/settings/SettingsContent.kt`.
