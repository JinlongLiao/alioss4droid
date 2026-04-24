# Requirements (PRD)

> Language / 语言：**English** · [简体中文](REQUIREMENTS.zh-CN.md)

> **App**: OSS Browser for Android  ·  **Author**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **Repo**: <https://github.com/JinlongLiao/alioss4droid>  ·  **License**: [Apache License 2.0](../LICENSE)  ·  **Application ID**: `io.github.jinlongliao.ossbrowser`

> Document version: v1.1 · Last updated: 2026-04-24

## 1. Background

Aliyun provides [`aliyun/oss-browser`](https://github.com/aliyun/oss-browser) as a cross-platform
desktop GUI. There is no first-party Android app with the same set of features, so power users
need a phone-friendly way to inspect buckets, share links, and retrieve files from OSS while on
the go. **OSS Browser for Android** aims to fill this gap.

## 2. Target users

- OSS power users / SRE / backend developers who need bucket access from a phone.
- QA / product owners who need to pull sample files or share presigned URLs on mobile.
- Individuals managing personal OSS buckets (photo backups, media library, etc.).

## 3. Scope

### 3.1 Must have

| # | Capability |
| --- | --- |
| R1 | Manage N independent OSS profiles (AK/SK + Endpoint) with one active at a time. |
| R2 | List buckets under the active account; enter any bucket regardless of the "default bucket" field. |
| R3 | List objects inside a bucket / prefix with **lazy pagination** (10/page default, configurable). |
| R4 | Breadcrumb / go-up navigation; gesture-back mapped to "parent folder"; bucket-list-level back requires a double tap. |
| R5 | Upload files via SAF with progress; download via `CREATE_DOCUMENT`. |
| R6 | Delete / move / rename with recursive handling for folders; destructive confirm configurable. |
| R7 | Generate presigned URLs with hour-level expiration (1–168h), copyable & shareable. |
| R8 | Preview text (txt/json/xml/yaml/md/groovy/gradle/…), images (incl. gif), audio/video, PDF. |
| R9 | Local cache for listings (30-minute TTL in memory + disk), manual refresh forces network. |
| R10 | i18n: English, Simplified Chinese, Traditional Chinese; follow-system + in-app override. |
| R11 | Privacy policy consent gate on first launch; settings expose policy + OSS notice. |
| R12 | Credentials stored encrypted via `EncryptedSharedPreferences`. |
| R13 | **Encrypted backup**: export / import OSS profiles as a single file (AES‑256‑GCM). |

### 3.2 Nice to have / roadmap

| # | Capability |
| --- | --- |
| N1 | Resumable / multipart upload (`ResumableUploadRequest`). |
| N2 | Multi-select batch delete / move / download. |
| N3 | Object ACL / storage class editing. |
| N4 | STS temporary credentials. |
| N5 | QR code for presigned URLs; bulk export of multiple links. |

### 3.3 Out of scope (v1)

- Server-side OSS administration (bucket creation, policy, CORS, static website config).
- Third-party S3-compatible endpoints (design is generic but not validated).
- Background / batch download queue with retry.

## 4. Non-functional requirements

- **Security**: No network traffic leaves the device except to Aliyun endpoints. AK/SK encrypted
  at rest. Cleartext HTTP permitted only for `*.aliyuncs.com` subdomains.
- **Performance**: Cache-first design so folder navigation is ≤100 ms for cached prefixes.
- **Compatibility**: `minSdk = 26` (Android 8.0) for adaptive icons; `targetSdk = 34`.
- **Localization parity**: all user-visible strings defined in `strings.xml` (no hardcoded
  Chinese/English inside Kotlin).
- **Offline tolerance**: stale cache rendered with a manual refresh hint instead of infinite spinner.

## 5. Key UX principles

1. "Files" is the primary tab — keep it one tap away.
2. Irreversible operations always ask twice unless explicitly disabled.
3. Progress must always be visible (upload/download bars, pagination spinner).
4. Credentials are **never** shown in logs, toasts, or crash reports.
5. Language switching happens in-place; avoid restarting the process.

## 6. Success metrics

- Cold start → first folder view ≤ 3 s on mid-range device with warm cache.
- Encrypted export round-trip (export → reinstall app → import) recovers identical profile set.
- Play Console pre-launch report: 0 crashes, 0 ANRs, no permission warnings.
