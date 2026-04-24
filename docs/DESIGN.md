# Design

> Language / 语言：**English** · [简体中文](DESIGN.zh-CN.md)

> **App**: OSS Browser for Android  ·  **Author**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **Repo**: <https://github.com/JinlongLiao/alioss4droid>  ·  **License**: [Apache License 2.0](../LICENSE)  ·  **Application ID**: `io.github.jinlongliao.ossbrowser`
>
> Document version: v1.3 · Last updated: 2026-04-24

## 1. Architecture overview

```
┌─────────────────────── UI layer (Jetpack Compose) ───────────────────────┐
│ OssBrowserRoot (NavHost)                                                 │
│  ├── BrowserScreen         — bucket list / object list / preview host    │
│  ├── ProfileListScreen     — OSS profile management                      │
│  ├── ProfileEditScreen     — form-based create / edit                    │
│  └── SettingsScreen        — appearance / privacy / backup / licenses    │
└────────────┬────────────────────────────────────────────────────────────┘
             │ state: StateFlow
┌────────────┴─── ViewModel layer ─────────────┐
│ BrowserViewModel · ProfileViewModel ·         │
│ SettingsViewModel                             │
└────────────┬─────────────────────────────────┘
             │ coroutines
┌────────────┴────── Data / service layer ─────────────────────────────────┐
│ OssProfileRepository  EncryptedSharedPreferences   (AES-256-GCM)         │
│ SettingsRepository    SharedPreferences                                  │
│ OssListCache          in-memory LRU + disk JSON (30-min TTL)             │
│ OssClientProvider     cache `OSSClient` per (profile × endpoint)         │
│ OssService            listBuckets / listObjects / upload / download /    │
│                       delete / move / presign                            │
│ ProfileBackupCodec    AES-256-GCM + PBKDF2-HmacSHA256 for backups        │
└────────────┬─────────────────────────────────────────────────────────────┘
             │ com.aliyun.dpa:oss-android-sdk 2.9.13
         Aliyun OSS REST API (HTTPS)
```

## 2. Navigation

Single-activity, Compose Navigation with the following routes:

- `browser` — default landing.
- `profiles` / `profile_edit?id={id}` — accessed from *Settings* and *Browser → Keys icon*.
- `settings` / `settings/privacy` / `settings/licenses`.

Bottom navigation keeps "Files / Settings". The "Keys" entry lives inside Settings because it is
the single most important configuration, deserving a prominent card rather than a tab.

## 3. Key data flows

### 3.1 Bucket / object listing

1. User enters a profile or bucket.
2. ViewModel computes a cache key = `profileId + bucket + prefix + marker`.
3. `OssListCache.get(key)` returns immediately if fresh; UI renders.
4. In parallel (if stale or forced), `OssService.listObjectsV2` is called.
5. `refreshSeq` guard: increments on every cancel/refresh; stale responses from long-running
   SDK calls are discarded to avoid UI getting stuck.
6. On success, cache is updated and the UI re-renders; on failure, the cached view is preserved
   and an error card appears on top.

### 3.2 Encrypted profile backup

Export / import uses `ProfileBackupCodec` with two modes:

| Mode | Key derivation | Use case |
| --- | --- | --- |
| `APP` (default) | `SHA-256(APP_SECRET ‖ salt)` | Device-to-device with the same app. |
| `PASSWORD` | `PBKDF2WithHmacSHA256(password, salt, 200_000, 256)` | Sharing across users. |

File layout (binary):

```
magic[4] = "ALOS"
version  = 0x01
mode     = 0x00 (APP) | 0x01 (PASSWORD)
salt(16) | iv(12) | ciphertext‖tag(16)
```

GCM tag failure ⇒ "wrong password"; magic/version mismatch ⇒ "bad format". Import is
idempotent: matches existing profiles by `id`, falling back to `(endpoint, AccessKey ID)`.

### 3.3 Preview pipeline

- Text: pre-check `Content-Length` against `textPreviewMaxBytes`; stream into a buffer, render
  in `SelectionContainer`. Extensions treated as text cover `txt, json, xml, yaml, yml, md, groovy,
  gradle, kts, plist, dart, scala, hcl, ini, toml, properties, sh, py, js, ts, go, java, kt, c, cpp, h, hpp, cs, rs, php, rb, log, csv, tsv, env, gitignore, dockerfile`.
- Image: fetch presigned URL via Coil with a dedicated `ImageLoader` that registers GIF decoders.
- Audio/Video: Media3 ExoPlayer with lifecycle-aware pause/release; presigned URL is the media item.
- PDF: `ParcelFileDescriptor.open(...)` + `PdfRenderer`; each page scaled to device width and
  cached as `Bitmap` for the lifetime of the dialog.

## 4. Caching strategy

Two-tier cache in `OssListCache`:

- **Memory**: `ConcurrentHashMap<String, CacheEntry<T>>`; evicted by process death.
- **Disk**: JSON files under `context.cacheDir/oss_list_cache/`; survive process restart.

Every entry carries an `updatedAtMs`. Fresh lookup: `now - updatedAtMs <= 30 min`. Refresh button
or invalidation via `clientProvider.invalidate()` (triggered on profile changes) clears both tiers.

## 5. Threading model

- All network / SDK calls happen in `viewModelScope` using `Dispatchers.IO` inside `withContext`.
- `BrowserViewModel.cancelLoading()` increments the `refreshSeq` rather than cancelling the
  coroutine, since the Aliyun SDK performs blocking work that doesn't respond to coroutine
  cancellation; the UI still recovers immediately because stale responses are dropped.
- Compose recomposition reads `StateFlow`s; no direct `LiveData` usage.

## 6. Release signing & compliance

- `app/build.gradle.kts` reads `keystore.properties` (git-ignored). When present, `release` build
  uses it; otherwise builds an unsigned APK.
- `release` build enables `isMinifyEnabled = true` + `isShrinkResources = true` with rules in
  `proguard-rules.pro`.
- `resourceConfigurations` whitelists `en`, `zh-rCN`, `zh-rTW` to keep APK size small and to
  avoid crash-on-locale-missing during market review.
- Privacy policy + open-source notice are shipped as raw resources (`res/raw*/*.md`) so
  store-listing forms can copy-paste them verbatim.

## 7. Error handling philosophy

- User-facing errors are localized via `R.string.task_*_failed` templates.
- `CancellationException` is rethrown, never logged as error (avoids misleading "cancelled" toast).
- SDK timeouts are capped at 15 seconds to surface misconfigurations quickly.

## 8. Extensibility

- `OssPreviewKind` is a sealed hierarchy — adding new previewable types (e.g. office docs) only
  requires a new case and a UI composable.
- `ProfileBackupCodec` is versioned (`VERSION = 1`); future layouts can bump the byte and branch.
- `OssService` abstracts the Aliyun SDK; other S3-compatible SDKs can plug in behind the same
  interface.

## 9. In-app Markdown renderer

The app ships Privacy Policy and Open-Source Notice as locale-specific Markdown inside
`res/raw[-zh-rCN|-zh-rTW]/*.md`. Rather than depending on a third-party renderer, a small
(~250 lines) zero-dependency Compose renderer lives in
`ui/settings/MarkdownView.kt`:

- Block grammar: ATX headings (`#` / `##` / `###`), paragraphs, bullet (`- ` / `* `) and
  numbered (`1. `) lists with soft-wrap continuation, blockquotes (`> `), fenced code blocks,
  and horizontal rules (`---` / `***` / `___`).
- Inline grammar: `**bold**`, `*italic*` / `_italic_`, `` `code` ``, `[label](url)`, and bare
  `https?://` auto-links.
- Styling is driven by `MaterialTheme.colorScheme` / `typography`, so the rendered text
  follows the active light/dark theme without extra work.
- Links use Compose 1.7 `LinkAnnotation.Url` + `TextLinkStyles` — no deprecated `ClickableText`.

Entry points that consume it: `PrivacyScreen`, `LicensesScreen` (project license card),
`PrivacyConsentGate` (first-launch dialog).

## 10. Source encoding & line endings

- Whole repo uses **LF** line endings enforced by `.gitattributes` and `.editorconfig`.
- The only exception is `scripts/gen-release-keystore.ps1`, which is pinned to **CRLF** so
  `powershell.exe` treats it identically across Windows locales (double-click experience).
- Kotlin sources carry an Apache 2.0 file header with
  `Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)`. `@author` / `@since`
  KDoc fields follow `yyyy-MM-dd` format.
