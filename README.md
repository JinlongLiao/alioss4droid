# OSS Browser (Android)

一款参考 [aliyun/oss-browser](https://github.com/aliyun/oss-browser) 设计思路的 Android 客户端，用于在手机上管理阿里云 OSS Bucket 中的对象。

## ✨ 核心功能

- **多套 OSS 密钥管理**
  - 新建 / 编辑 / 删除 / 一键切换当前 Bucket 配置
  - AccessKey 采用 `EncryptedSharedPreferences`（AES‑256‑GCM）加密落盘
- **文件浏览**
  - 以目录（CommonPrefix）方式展现对象层级
  - 面包屑跳转、返回根目录
  - 文件大小、修改时间展示
- **上传**
  - 通过系统文件选择器（SAF）选择任意本地文件上传到当前目录
  - 带进度条
- **下载**
  - 通过 `CREATE_DOCUMENT` 将对象保存到用户选定的位置
  - 带进度条
- **删除**
  - 支持删除文件，也可递归删除“目录”前缀下的所有对象
- **移动 / 重命名**
  - 文件或目录均支持，通过 `copyObject + deleteObject` 实现
  - 目录会递归搬运其下所有对象

## 🧱 技术栈

| 模块 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 状态管理 | ViewModel + StateFlow |
| 本地安全存储 | `androidx.security:security-crypto` (`EncryptedSharedPreferences`) |
| 序列化 | `kotlinx.serialization` JSON |
| 协程 | `kotlinx.coroutines` |
| OSS SDK | `com.aliyun.dpa:oss-android-sdk` |

## 📁 项目结构

```
android-oss/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ossbrowser/app/
│       │   ├── MainActivity.kt
│       │   ├── OssBrowserApp.kt
│       │   ├── data/
│       │   │   ├── OssProfile.kt
│       │   │   └── OssProfileRepository.kt
│       │   ├── oss/
│       │   │   ├── OssClientProvider.kt
│       │   │   ├── OssObjectItem.kt
│       │   │   └── OssService.kt
│       │   ├── ui/
│       │   │   ├── OssBrowserRoot.kt
│       │   │   ├── theme/Theme.kt
│       │   │   ├── profile/
│       │   │   │   ├── ProfileListScreen.kt
│       │   │   │   ├── ProfileEditScreen.kt
│       │   │   │   └── ProfileViewModel.kt
│       │   │   └── browser/
│       │   │       ├── BrowserScreen.kt
│       │   │       └── BrowserViewModel.kt
│       │   └── util/FileUtils.kt
│       └── res/...
└── README.md
```

## 🚀 构建与运行

推荐 **Android Studio Koala Feature Drop (2024.1.2)** 或更高，JDK 17。

### 方式一：Android Studio（推荐）

1. `File ▸ Open` 选中本目录。
2. 首次打开时会自动下载 Gradle 8.x 和依赖；如果弹出 `gradle-wrapper.jar missing` 提示，点击 `Sync Project with Gradle Files`，Studio 会自动补齐 wrapper。
3. 连接真机或启动模拟器，点击 ▶ 运行 `app`。

### 方式二：命令行

本仓库未提交 `gradle-wrapper.jar`（二进制）。如果本机已装 Gradle 8.9+，在项目根目录执行：

```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug    # macOS / Linux
gradlew.bat :app:assembleDebug  # Windows
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 📱 使用说明

首次启动会停留在 **文件** 页，并提示 “请先在「密钥」页新增并激活一个 OSS 配置”。

### 1. 添加密钥

1. 切到 **密钥** 页 → 点击右下角 `+`。
2. 填写：
   - **配置名称**：自定义，如 `生产`、`测试`。
   - **Endpoint**：如 `https://oss-cn-hangzhou.aliyuncs.com`。
   - **Bucket**：目标 bucket 名。
   - **AccessKey ID / Secret**：建议使用子账号 RAM 最小权限密钥。
3. 点击 **保存并激活**。

### 2. 切换配置

回到 **密钥** 页，点击任意一条配置即切换激活（左侧圆点变成√）。切换后 **文件** 页会自动刷新为新 bucket 的根目录。

### 3. 文件操作

- **进入目录**：点击列表中的目录项。
- **返回 / 跳转**：点顶部面包屑中的任一段。
- **上传**：右下 `操作` ▸ `上传文件`，选取任意本地文件。
- **新建目录**：右下 `操作` ▸ `新建目录`。
- **下载**：文件行右侧下载图标 → 系统弹窗选择保存位置。
- **删除**：文件行右侧删除图标；若选中目录将递归删除其下所有对象。
- **移动 / 重命名**：文件行右侧移动图标，填写目标父前缀（留空即根目录）和新名称。

## 🔐 安全说明

- AK/SK 仅保存在本机 `EncryptedSharedPreferences` 中，Key 基于 Android Keystore 生成，未上传任何第三方服务。
- `fullBackupContent` 和 `dataExtractionRules` 已配置排除 `oss_profiles.xml`，避免密钥被系统自动备份至云端。
- 生产环境建议为该应用的 RAM 用户仅授予所需 Bucket 的最小权限（`ListObjects` / `GetObject` / `PutObject` / `DeleteObject`）。

## 📚 详细文档

- [需求文档 (PRD)](docs/REQUIREMENTS.md)
- [设计文档](docs/DESIGN.md)
- [使用手册](docs/USAGE.md)

## 🛠️ 可拓展方向

- 多 Bucket 搜索、批量选择与批量操作
- 断点续传 / 分片上传（`ResumableUploadRequest`）
- 对象 ACL / Storage Class 管理
- HTTPS 证书自校验、STS 临时凭证接入

## 📝 License

Apache License 2.0

