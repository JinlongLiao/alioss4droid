# Privacy Policy

Effective date: 2026-04-24

## 1. Who we are

This application ("OSS Browser for Android") is an open-source Aliyun OSS client.
The source code is released under the Apache License 2.0.

## 2. How we handle your information

This app **does not upload, collect or analyze** any personal data, usage
behavior or device identifier.

- **Access keys (AccessKey ID / Secret)**: stored **only on device** through
  Android Jetpack Security (`EncryptedSharedPreferences`) with AES-256
  encryption. They are never sent to any third-party server.
- **Network traffic**: all OSS requests go **directly** to the Aliyun OSS
  endpoints (`*.aliyuncs.com`) via the official SDK. What is sent between
  your device and Aliyun is governed by Aliyun's own privacy policy.
- **Preview cache**: image previews use Coil for local caching. You can
  clear it any time from *Settings → Clear preview cache*.
- **Analytics / crash reporting**: this app does **not** integrate any
  third-party analytics, crash reporting, advertising or tracking SDK.

## 3. Your control

- All keys and preferences live in the app's private directory. **Uninstalling**
  the app removes everything.
- You can also delete individual OSS profiles from the *Keys* page.

## 4. Permissions

- **Internet**: to reach Aliyun OSS over HTTPS
- **Read / write storage**: via the system Storage Access Framework (SAF)
  when you upload / download a file. The app never scans your filesystem.

## 5. Changes

Any changes will be published in the project repository and reflected in
this page.

## 6. Contact

For questions or compliance requests, please open an issue on the project
repository.
