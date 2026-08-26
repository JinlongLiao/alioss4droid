# OSS Browser for Android · 阿里云 OSS 安卓客户端

> Language / 语言：**简体中文** · [English](README.md)

> 仓库 slug：`alioss4droid`；应用对外显示名称为 **OSS Browser for Android**。

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![targetSdk](https://img.shields.io/badge/targetSdk-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7f52ff)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BoM%202024.09-4285f4)

> 一款参考 [aliyun/oss-browser](https://github.com/aliyun/oss-browser) 设计思路实现的阿里云 OSS **Android 原生客户端**。
> 采用 **Kotlin + Jetpack Compose + Material 3**，可作为阿里云 OSS 移动端管理、文件浏览与轻量预览的日常工具。

- 应用名：**OSS Browser for Android**
- 作者：**jinlongliao** · <https://github.com/JinlongLiao/>
- 仓库地址：<https://github.com/JinlongLiao/alioss4droid>
- 应用包名：`io.github.jinlongliao.ossbrowser`
- 许可证：[Apache License 2.0](LICENSE)

---

## ✨ 功能特性

### 账号与安全

- 多套 OSS 账号（AK/SK + Endpoint）管理：新增 / 编辑 / 删除 / 切换激活
- `EncryptedSharedPreferences`（AES‑256‑GCM，Android Keystore 派生密钥）加密落盘
- 按 profile + endpoint 缓存 OSSClient，支持跨 region 的 bucket 访问
- 隐私协议首次启动强制同意；设置中提供隐私政策 / 开源协议 / 第三方许可查看

### 浏览与操作

- Bucket 列表与 bucket 内对象 **懒加载分页**（默认每页 10 条，下滑自动续拉）
- 文件夹优先 + 名称升序排序
- 面包屑 / 返回上一级 / 路径直跳（命中文件则跳到父目录并高亮该文件）
- 系统返回手势适配：目录 → 上级；bucket 列表 → 两次快速点击退出
- **本地缓存**（内存 + 磁盘，TTL 30 分钟）：导航类操作秒开，刷新按钮强制拉网络
- 上传（SAF 选择文件，带进度）、下载（`CREATE_DOCUMENT` 指定保存位置）、删除、移动 / 重命名（支持递归目录）
- 危险操作二次确认（删除 / 移动），可在设置中关闭
- 生成临时签名链接（1~168 小时）或无签名公开链接；公开链接可选择 OSS 返回的已启用
  Bucket 自定义域名，附复制 / 分享入口

### 文件预览

| 类型 | 实现 |
| --- | --- |
| TEXT（txt / json / xml / yaml / md / groovy / gradle / kts / plist / dart / scala / hcl…） | 流式下载到内存（大小上限可配），`SelectionContainer` 支持复制 |
| HTML（html / htm / xhtml） | WebView 安全静态渲染；可在当前会话确认后开启 JavaScript 与 HTTPS 外部资源 |
| IMAGE（jpg / png / webp / gif / bmp…） | Coil + `coil-gif` 异步加载预签名 URL |
| AUDIO / VIDEO | Media3 ExoPlayer 内嵌播放，可暂停 / 快进 / 拖动进度 |
| PDF | `PdfRenderer` 分页位图渲染 |

### 国际化 & 外观

- 简体中文 / English / 繁體中文，可在设置页切换或跟随系统
- 配色与明暗模式独立选择：默认 / Gemini / 夏日晴空配色，以及明亮 / 深色 / 跟随系统模式
- 夏日晴空壁纸首次远程加载并写入应用私有缓存，离线时优先使用缓存或本地渐变兜底
- 文件类型图标按扩展名分色映射，列表采用 Material 3 色调容器与圆角卡片；未知类型走通用 icon

---

## 🧱 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose（Compose BoM 2024.09）+ Material 3 |
| 导航 | Navigation Compose |
| 状态管理 | ViewModel + StateFlow + Kotlin Coroutines |
| 本地安全存储 | `androidx.security:security-crypto`（EncryptedSharedPreferences）|
| 序列化 | `kotlinx.serialization` JSON |
| 网络 / OSS | `com.aliyun.dpa:oss-android-sdk` 2.9.13 |
| 图片 | Coil 2.7.0 + coil-gif |
| 音视频 | Media3 ExoPlayer 1.4.1 |
| 构建 | AGP 8.5.2 + Gradle 8.9 + Version Catalog |

---

## 🧩 项目结构

```
android-oss/
├── build.gradle.kts              # 根构建脚本
├── settings.gradle.kts           # 模块 / 仓库配置（含国内镜像）
├── gradle/libs.versions.toml     # 版本目录
├── gradle/wrapper/               # Gradle Wrapper (8.9)
├── LICENSE                       # Apache 2.0 全文
├── docs/
│   ├── REQUIREMENTS.md           # 需求文档
│   ├── DESIGN.md                 # 设计文档
│   └── USAGE.md                  # 用户手册
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/io/github/jinlongliao/ossbrowser/
        │   ├── MainActivity.kt
        │   ├── OssBrowserApp.kt
        │   ├── data/               # 设置、账号持久化
        │   │   ├── AppSettings.kt
        │   │   ├── SettingsRepository.kt
        │   │   ├── OssProfile.kt
        │   │   └── OssProfileRepository.kt
        │   ├── oss/                # OSS 业务封装
        │   │   ├── OssClientProvider.kt
        │   │   ├── OssService.kt
        │   │   ├── OssListCache.kt
        │   │   ├── OssObjectItem.kt
        │   │   ├── OssPreviewKind.kt
        │   │   └── OssFileIcon.kt
        │   ├── ui/
        │   │   ├── OssBrowserRoot.kt
        │   │   ├── theme/
        │   │   ├── browser/
        │   │   ├── profile/
        │   │   └── settings/
        │   └── util/
        │       ├── FileUtils.kt
        │       └── LocaleHelper.kt
        └── res/                    # strings (en / zh-CN / zh-TW), icons, xml
```

---

## 🛠️ 构建环境

| 依赖 | 版本 / 要求 |
| --- | --- |
| JDK | **17**（推荐使用 Temurin 17 LTS） |
| Android SDK | `compileSdk = 34`，`minSdk = 26`，`targetSdk = 34` |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.9（Wrapper 已提交） |
| Kotlin | 2.0.21 |
| Android Studio | **Koala Feature Drop (2024.1.2) 或更高**，或 **Android Studio Jellyfish/Iguana 24.x+** |

> 首次构建会从 Maven / 阿里云镜像拉依赖；`settings.gradle.kts` 已内置 Aliyun Maven 镜像以加速国内下载。

---

## 🚀 快速开始

### 方式一：Android Studio

1. `File ▸ Open` 选择本仓库根目录。
2. 等待 Gradle Sync 完成（约 1~3 分钟，视网络）。
3. 连接真机（开启 USB 调试）或启动模拟器，点击 ▶ 运行 `app`。

> **小米 / Redmi 设备**：首次 `adb install` 可能报 `INSTALL_FAILED_USER_RESTRICTED`；需在 *开发者选项* 中打开 "USB 安装"。

### 方式二：命令行

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 方式三：一键脚本（推荐，可绕过小米 HyperOS 安装限制）

小米 / Redmi HyperOS 对 `adb install` 有额外的 "USB 安装" 校验，
常见报错：`INSTALL_FAILED_USER_RESTRICTED`。
仓库根目录提供了 `install.ps1`（Windows）和 `install.sh`（macOS / Linux），
改走 `adb push /data/local/tmp + adb shell pm install` 的稳定路径。

```powershell
# Windows（PowerShell）
.\install.ps1 -Launch             # 构建 + 安装 + 启动
.\install.ps1 -NoBuild -Launch    # 仅安装现有 APK + 启动
.\install.ps1 -Clean -Launch      # clean 构建 + 安装 + 启动
```

```bash
# macOS / Linux
./install.sh --launch             # 构建 + 安装 + 启动
./install.sh --no-build --launch  # 仅安装现有 APK + 启动
./install.sh --clean --launch     # clean 构建 + 安装 + 启动
```

---

## 📱 使用说明（速览）

1. **首次启动**：阅读并同意隐私协议。
2. **添加账号**：`设置` 顶部「密钥管理」卡片 → `+` 新建；填写 AccessKey ID/Secret、Endpoint（如 `https://oss-cn-hangzhou.aliyuncs.com`）、可选默认 bucket → **保存并激活**。
3. **浏览**：底部 Tab `文件` 即进入激活账号下的 bucket 列表；点击 bucket 进入其根目录。
4. **常用动作**：长按 / 点击操作图标完成上传、下载、预览、删除、生成链接、移动 / 重命名。
5. **刷新 vs 缓存**：默认走 30 分钟缓存；点顶部刷新图标强制走网络。
6. **清缓存**：`设置 ▸ 清理缓存` 一键清除所有本地缓存。

完整图文手册见 [docs/USAGE.zh-CN.md](docs/USAGE.zh-CN.md)。

---

## 🔐 安全与合规

- AK/SK 仅保存在本机 `EncryptedSharedPreferences`，Key 由 Android Keystore 派生，不上传任何第三方。
- 备份策略 (`fullBackupContent` / `dataExtractionRules`) 排除 `oss_profiles.xml`，防止云备份泄露密钥。
- 网络安全配置 `network-security-config.xml` 仅在必要的 `*.aliyuncs.com` 子域启用 cleartext，其他域名默认禁用明文。
- RAM 子账号最小权限建议：仅授予对应 Bucket 的 `ListObjects` / `GetObject` / `PutObject` / `DeleteObject`；
  仅在需要自动获取 Bucket 自定义域名时增加 `oss:ListCname`。未授予时，公开链接会回退到 OSS 默认公网域名。

---

## 📚 详细文档

- [需求文档 (PRD)](docs/REQUIREMENTS.zh-CN.md)
- [设计文档](docs/DESIGN.zh-CN.md)
- [使用手册](docs/USAGE.zh-CN.md)
- [版本变更（Changelog）](CHANGELOG.md)
- 英文版请查看 [docs/](docs/) 同名文件（默认英文，中文文件以 `.zh-CN.md` 后缀）。

---

## 🧭 未来规划

- [ ] 断点续传 / 分片上传（`ResumableUploadRequest`）
- [ ] 多选 & 批量操作（删除 / 移动 / 下载）
- [ ] 对象 ACL / Storage Class 修改
- [ ] STS 临时凭证支持
- [ ] 分享链接带二维码 / 批量导出

---

## 🤝 贡献

欢迎 issue / PR。请保证：

1. 代码遵循仓库内 `.editorconfig` 的缩进与换行规则；
2. 新增源文件保留 Apache 2.0 头注释；
3. 用户可见字符串统一通过 `res/values*/strings.xml` 管理，避免硬编码中文。

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

本项目使用到的主要第三方开源组件及其协议见应用内 **设置 ▸ 开源协议**，或参考 `app/src/main/java/io/github/jinlongliao/ossbrowser/ui/settings/SettingsContent.kt`。
