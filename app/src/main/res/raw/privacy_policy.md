# Privacy Policy

Effective date: 2026-08-26

## 1. Who we are

This application ("OSS Browser for Android") is an open-source Aliyun OSS client.
The source code is released under the Apache License 2.0.

## 2. How we handle your information

This app itself **does not collect or analyze** personal data, usage behavior
or device identifiers. Network features you explicitly enable can contact the
service described below.

- **Access keys (AccessKey ID / Secret)**: stored **only on device** through
  Android Jetpack Security (`EncryptedSharedPreferences`) with AES-256
  encryption. They are never sent to any third-party server.
- **OSS traffic**: all OSS requests go **directly** to the Aliyun OSS
  endpoints (`*.aliyuncs.com`) via the official SDK. What is sent between
  your device and Aliyun is governed by Aliyun's own privacy policy.
- **Alibaba Cloud web console**: when you open this optional page, an embedded
  WebView connects directly to Alibaba Cloud HTTPS domains. The app saves only
  your selected account type and account name for later prefilling. WebView
  retains Alibaba Cloud cookies and website storage so your session can persist;
  passwords and MFA codes are not read or saved by this app. Password saving or
  autofill, when available, is provided by your system autofill service. For
  diagnostics, the app reads current-site cookies in memory and immediately
  discards full values, retaining only cookie names and irreversible masked
  values; the default setting hides even those masked values. Full cookie values
  are never written to logs, settings, or copied diagnostic reports.
- **Web browser engine**: you can choose System WebView or Mozilla GeckoView,
  which is bundled with the app and does not download a browser core at runtime.
  GeckoView stores cookies and site data in the app's private directory. USB
  remote debugging is enabled only in debug builds and disabled in release builds.
- **Summer Sky theme**: when you select this theme, its wallpaper is downloaded
  from `haowallpaper.com`. That server can receive ordinary network metadata
  such as your IP address. Coil stores a private local copy for later use.
- **Full HTML preview**: safe static preview does not load external resources.
  If you explicitly enable full preview for the current file, its JavaScript
  and HTTPS external resources may contact domains specified by that HTML.
- **Preview and theme cache**: image previews and the Summer Sky wallpaper use Coil for local caching. You can
  clear it any time from *Settings → Clear preview cache*.
- **Analytics / crash reporting**: this app does **not** integrate any
  third-party analytics, crash reporting, advertising or tracking SDK.

## 3. Your control

- All keys and preferences live in the app's private directory. **Uninstalling**
  the app removes everything.
- You can also delete individual OSS profiles from the *Keys* page.
- You can clear Alibaba Cloud cookies, website storage, history, and WebView
  cache from the web console menu without deleting the saved account name.

## 4. Permissions

- **Internet**: to reach Aliyun OSS and its optional web console, download the
  Summer Sky wallpaper, and load resources you explicitly allow in full HTML preview
- **Read / write storage**: via the system Storage Access Framework (SAF)
  when you upload / download a file. The app never scans your filesystem.

## 5. Changes

Any changes will be published in the project repository and reflected in
this page.

## 6. Contact

For questions or compliance requests, please open an issue on the project
repository.
