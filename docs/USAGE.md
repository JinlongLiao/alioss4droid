# User guide

> 文档定位：保留的初始设计、需求或旧版英文使用说明。当前 1.0.5 的实现、构建要求与验收边界以[文档索引](文档索引.md)、[中文使用手册](USAGE.zh-CN.md)和[发布记录](1.0.5发布与清理记录.md)为准。

> Language / 语言：**English** · [简体中文](USAGE.zh-CN.md)

> **App**: OSS Browser for Android  ·  **Author**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **Repo**: <https://github.com/JinlongLiao/alioss4droid>  ·  **License**: [Apache License 2.0](../LICENSE)  ·  **Application ID**: `io.github.jinlongliao.ossbrowser`

## 1. First launch

1. Install the APK (see README for one-shot scripts).
2. Read the Privacy Policy dialog and tap **Accept and continue**. Declining closes the app.
3. Choose a theme and language (can be revisited any time in *Settings*).

## 2. Add your first OSS profile

Open *Settings* → the prominent **OSS Keys / Profiles** card → `+`.

Fill in:

| Field | Notes |
| --- | --- |
| Profile name | Any human-readable label. |
| Endpoint | Full URL, e.g. `https://oss-cn-hangzhou.aliyuncs.com`. |
| Default bucket | *Optional*. Empty lists all buckets for the account on entering the Files tab. |
| AccessKey ID / Secret | From RAM console. Subaccount with minimal policy recommended. |
| Note | Free-form. |

Tap **Save & activate**. You now have one active profile; add more via the `+` button.

## 3. Browse

- Tap **Files** (bottom tab) → see bucket list for the active profile.
- Tap a bucket → enter its root prefix. Breadcrumb chips show the current path.
- Tap the **search icon** in the top bar → jump directly to a path (`dir1/sub/` enters that
  folder; `dir1/file.txt` jumps to the file's parent and highlights the file row).
- Scroll down: the next 10 items are loaded automatically.
- **Gesture back** / system back: one level up inside a bucket; at the bucket list, press twice
  quickly within 2 s to exit.

## 4. File actions

Each file row shows these icons (folder rows hide the file-only ones):

| Icon | Action |
| --- | --- |
| 👁 | Preview. |
| 🔗 | Generate a presigned URL (hours as expiry, 1–168). |
| ⬇ | Download via `CREATE_DOCUMENT` (you pick destination). |
| ↔ | Move / rename (folders are copied recursively, then the source is deleted). |
| 🗑 | Delete. Confirmation dialog by default. |

Tap the row itself: folder enters it, file opens preview.

## 5. Refresh vs cache

Directory listings use a 30-minute cache. Tap the **refresh icon** in the top bar to force a
network call for the current view. To drop everything: *Settings → Clear preview cache*.

## 6. Backup / export profiles

*Settings → Backup & share → Export OSS profiles*:

1. Optionally set a password. Leave empty to let any install of this app decrypt.
2. Pick a destination with the system file picker. A `.aloss` file is written, encrypted.

To restore on another device:

*Settings → Backup & share → Import OSS profiles* → enter the password (or leave empty) → pick
the `.aloss` file. Duplicates (same `id` or same endpoint+AccessKey ID) are overwritten; new
profiles are appended.

## 7. Settings reference

| Section | Key items |
| --- | --- |
| OSS Profile | Active profile card + link to full list. |
| Appearance | Theme (system/light/dark); Language (system/English/简体/繁體). |
| Browse | Items per page (10 / 20 / 50 / 100). |
| Preview | Text preview size cap; Auto-play media. |
| Security | Confirm destructive actions toggle. |
| Storage | Clear preview cache. |
| Backup & share | Export / import OSS profiles (encrypted). |
| About | Version, **Author** (tap to open GitHub), project page, open-source licenses, privacy policy. |

> *Open source licenses* and *Privacy policy* screens render the shipped Markdown
> (`res/raw*/*.md`) with inline headings, bullets, bold, inline code and clickable links
> — no browser / WebView involved.

## 8. Troubleshooting

| Symptom | Possible cause / fix |
| --- | --- |
| `INSTALL_FAILED_USER_RESTRICTED` on Xiaomi / Redmi | Enable *Install via USB* in Developer Options. |
| "The bucket you are attempting to access must be addressed using the specified endpoint" | The profile's endpoint doesn't match the bucket's region. Re-enter the bucket via the Bucket list — the app caches an `OSSClient` per endpoint and switches automatically. |
| `Connections closed` / indefinite loading | Tap **Cancel** in the empty state, then **Refresh**. Rate limit on the OSS endpoint may be the cause. |
| Preview opens but player hangs | Use *Open in external player* (top-right icon in the preview dialog) and pick a system player. |
| Import "Wrong password" | The exported file used a password; enter the same string. Or re-export without a password if device-to-device. |
| Language won't switch | The app briefly recreates the Activity to reload resources; if you see partial strings, tap the Files tab once to refresh state. |

## 9. Uninstall & data removal

Uninstalling the app removes:

- All OSS profiles (encrypted preferences).
- All cached listings and preview files.
- All settings (theme, language, page size, etc.).

Nothing is synced off-device, so there is no server-side residue.
