# Design

> Language / 语言：**English** · [简体中文](DESIGN.zh-CN.md)

> **App**: OSS Browser for Android  ·  **Author**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **Repo**: <https://github.com/JinlongLiao/alioss4droid>  ·  **License**: [Apache License 2.0](../LICENSE)  ·  **Application ID**: `io.github.jinlongliao.ossbrowser`
>
> Document version: v1.4 · Last updated: 2026-04-24

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

`OssPreviewKind` drives the branch; the dialog host in `BrowserScreen.kt` keeps the dialog open
and swaps the body composable as `PreviewState` transitions:

```
PreviewState ::= Loading(totalBytes, downloadedBytes)
              | Text(isMarkdown) | Docx | Image | Media(audio/video) | Pdf
              | TooLarge(sizeBytes, limitBytes)
              | Error(message)
```

- Text: pre-check `Content-Length` against `textPreviewMaxBytes`; stream into a buffer, render
  in `SelectionContainer`. Extensions treated as text cover `txt, json, xml, yaml, yml, md,
  markdown, mdown, mkd, mkdn, groovy, gradle, kts, plist, dart, scala, hcl, ini, toml, properties,
  sh, py, js, ts, go, java, kt, c, cpp, h, hpp, cs, rs, php, rb, log, csv, tsv, env, gitignore,
  dockerfile`. Markdown files get an extra top-bar toggle between the rich-text renderer and the
  monospace raw view (see §9).
- DOCX: the file is a ZIP; `util/DocxText.kt` opens it with `ZipInputStream`, locates
  `word/document.xml` (bounded to 32 MB to avoid OOM), and extracts `<w:p>` paragraphs via
  `XmlPullParser` (handles `<w:t>`, `<w:tab>`, `<w:br>`). Rendered as a `LazyColumn` of selectable
  paragraphs with a one-tap "copy all" action. Binary `.doc` is intentionally not covered.
- Image: fetch presigned URL via Coil with a dedicated `ImageLoader` that registers GIF decoders.
- Audio/Video: Media3 ExoPlayer with lifecycle-aware pause/release; presigned URL is the media item.
- PDF: `ParcelFileDescriptor.open(...)` + `PdfRenderer`; each page scaled to device width and
  cached as `Bitmap` for the lifetime of the dialog. The underlying `downloadToFile` reports bytes
  through `onProgress`, wired back into `PreviewState.Loading.downloadedBytes` so the dialog shows
  a real progress bar instead of an indeterminate spinner.

**Friendly preview states.** Three situations used to be handled via transient snackbars and are
now first-class states rendered inside the preview dialog:

- *Loading* (`LoadingPreviewBody`) — file icon + filename + predicted size from `OssObjectItem.size`;
  `LinearProgressIndicator` is determinate while a PDF is downloading, indeterminate for
  presign / in-memory fetches, with a "fetching data…" caption.
- *Too large* (`TooLargePreviewBody`) — `BrowserViewModel.preview()` compares `item.size` against
  `textPreviewMaxBytes` for `TEXT` / `DOCX` *before* issuing any request, and short-circuits to a
  guidance page that reports both numbers and offers two shortcuts: "Download to device"
  (reuses the SAF `ACTION_CREATE_DOCUMENT` flow) and "Generate temporary link" (jumps to the
  existing Presign dialog). The dialog is held open via `currentPreviewItem()` so the ViewModel
  still knows which file to act on.
- *Error* (`ErrorPreviewBody`) — any exception in the preview pipeline produces `PreviewState.Error`
  with the original message; the UI offers "Retry" (`retryPreview()` replays `preview(item)`),
  "Download" (same SAF path), and "Close".

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

- `OssPreviewKind` is an enum — adding new previewable types only requires a new case, an
  extension-set entry in `OssPreviewKind.kt`, a ViewModel branch in `preview()`, and a UI body
  composable. DOCX was added this way (`DocxText` extractor + `DocxPreviewBody`).
- `ProfileBackupCodec` is versioned (`VERSION = 1`); future layouts can bump the byte and branch.
- `OssService` abstracts the Aliyun SDK; other S3-compatible SDKs can plug in behind the same
  interface.

## 9. In-app Markdown renderer

The app ships Privacy Policy and Open-Source Notice as locale-specific Markdown inside
`res/raw[-zh-rCN|-zh-rTW]/*.md`, and also uses the same renderer for in-browser `.md` object
preview. Rather than depending on a third-party renderer, a self-contained Compose renderer
lives in `ui/settings/MarkdownView.kt` (~600 lines, single file):

- Block grammar:
  - ATX headings `#` … `######` (trailing `#` stripped) and Setext headings
    (`====` → H1, `----` → H2).
  - Paragraphs with soft-wrap join; hard line break via trailing two-space rule.
  - Bullet (`-` / `*` / `+`) and numbered (`1.`, `42.`) lists with indent-based nesting
    (2 spaces = one level, capped at 5) and multi-line continuation.
  - Task list items `- [ ]` / `- [x]` rendered as ☐ / ☑.
  - Blockquote as consecutive `>` lines collapsed into one multi-line quote.
  - Fenced code block `\`\`\`` / `~~~` with optional language tag.
  - Horizontal rules `---` / `***` / `___` (disambiguated against Setext by context).
  - GFM tables: `|h|h|` + `|:--|:--:|--:|` alignment row + rows, with `\|` escape.
  - Image-only paragraph `![alt](url)` promoted to block-level image (Coil `AsyncImage`).
- Inline grammar: `**bold**`, `*italic*` / `_italic_`, `***bold italic***`, `~~strike~~`,
  `` `code` ``, `[label](url)`, `![alt](url)` (rendered as link-style 🖼 placeholder),
  autolink `<http…>`, bare `https?://` URL, and `\X` punctuation escape.
- Reference-style links: `[id]: url "title"` definitions collected in pass 1; `[text][id]`
  and collapsed `[id]` references resolved in the inline pass.
- Styling is driven by `MaterialTheme.colorScheme` / `typography`, so the rendered text
  follows the active light/dark theme without extra work.
- Links use Compose 1.7 `LinkAnnotation.Url` + `TextLinkStyles` — no deprecated `ClickableText`.

Entry points that consume it: `PrivacyScreen`, `LicensesScreen` (project license card),
`PrivacyConsentGate` (first-launch dialog), and `BrowserScreen`'s preview dialog for
`.md` / `.markdown` / `.mdown` / `.mkd` / `.mkdn` objects (toggleable to raw-source view).

## 10. Source encoding & line endings

- Whole repo uses **LF** line endings enforced by `.gitattributes` and `.editorconfig`.
- The only exception is `scripts/gen-release-keystore.ps1`, which is pinned to **CRLF** so
  `powershell.exe` treats it identically across Windows locales (double-click experience).
- Kotlin sources carry an Apache 2.0 file header with
  `Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)`. `@author` / `@since`
  KDoc fields follow `yyyy-MM-dd` format.
