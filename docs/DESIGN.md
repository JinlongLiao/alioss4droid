# 设计文档

> 文档版本：v1.0 · 最后更新：2026-04-24

## 1. 技术总览

| 维度 | 方案 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Navigation Compose，层次：UI (Composable) ↔ ViewModel (StateFlow) ↔ Repository / Service |
| 异步 | Kotlin Coroutines + `Dispatchers.IO` |
| 持久化 | `EncryptedSharedPreferences` 保存 OSS 密钥 / 普通 `SharedPreferences` 保存偏好 |
| 序列化 | `kotlinx.serialization` JSON |
| 图片 / GIF | Coil (`coil-compose` + `coil-gif`) |
| 音视频 | Media3 ExoPlayer |
| PDF | Android 原生 `PdfRenderer` |
| SDK | Aliyun `oss-android-sdk` |

## 2. 模块与职责

```
com.ossbrowser.app
├── OssBrowserApp.kt           # Application，统一装配 Repository / Provider / Locale
├── MainActivity.kt            # 单 Activity；attachBaseContext 负责应用语言
├── data/
│   ├── OssProfile              # 密钥 / 配置 数据类
│   ├── OssProfileRepository    # 多配置 CRUD + 激活项
│   ├── AppSettings             # 偏好设置
│   └── SettingsRepository      # 偏好读写 + StateFlow
├── oss/
│   ├── OssClientProvider       # 多 endpoint 缓存 OSSClient；service-level client
│   ├── OssService              # 业务 API：list/upload/download/delete/move/presign/exists/downloadBytes/downloadToFile
│   ├── OssObjectItem           # Folder / File / BucketInfo
│   ├── OssFileIcon             # 扩展名 → 图标 / 颜色
│   └── OssPreviewKind          # TEXT/IMAGE/AUDIO/VIDEO/PDF/UNSUPPORTED 及扩展名集合
├── ui/
│   ├── OssBrowserRoot          # NavHost + BottomNav + 隐私同意拦截
│   ├── theme/Theme.kt          # M3 主题（跟随系统 / 浅 / 深）
│   ├── profile/                # 密钥页
│   ├── browser/                # 浏览页 + ViewModel + 各类 Dialog / Preview
│   └── settings/               # 设置 / 隐私 / 开源许可
└── util/
    ├── FileUtils               # SAF 复制 / 人类可读大小
    └── LocaleHelper            # 语言解析 + Context 包装
```

## 3. 关键状态机

### 3.1 BrowserUiState

```
BrowserUiState(
  activeProfile, profiles, currentBucket, currentBucketEndpoint,
  prefix,                 // 当前目录（以 / 结尾）
  items, nextMarker,      // 分页列表
  loading, loadingMore,   // 两级加载态
  highlightKey,           // 路径跳转后要高亮的 key
  buckets,                // Bucket 列表视图下
  errorMessage, task,     // 全局错误 / 进度
  previewState            // 预览态
)
```

进入新目录：`loadJob?.cancel()` → `_state = state.copy(loading=true, items=[], nextMarker=null)` → `service.list(..., pageSize)` → 成功：`loading=false, items, nextMarker`；失败：`loading=false, errorMessage`。

### 3.2 预览状态

```
PreviewState ::= Loading | Text | Image | Media(audio/video) | Pdf
```

- **TEXT**：`OssService.downloadBytes(maxBytes)`，超限抛错。
- **IMAGE / AUDIO / VIDEO**：预签名 URL（1h 过期），UI 用 Coil / ExoPlayer 播放。
- **PDF**：下载到 `cacheDir/pdf/<hash>_<name>`；`PdfRenderer` 按屏幕宽度等比渲染每页 Bitmap。
- **UNSUPPORTED**：直接设为 `TaskState.Failed`。

## 4. OSS Client 选型

Aliyun SDK 的 `OSSClient` 同时需要 endpoint：

- `listBuckets()` **只能** 用**不带 endpoint 的 service client**
- 其它对象操作必须使用**对应 Bucket 所在 region 的 endpoint**

设计：
- `OssClientProvider.service()` → 进程级唯一 service client（按激活 profile 缓存）
- `OssClientProvider.current(endpoint)` → 按 `profileId|endpoint` 缓存；切换激活配置时整体失效

## 5. 排序策略

```kotlin
val FolderFirstByName: Comparator<OssObjectItem> = Comparator { a, b ->
    val folderA = a is Folder; val folderB = b is Folder
    when {
        folderA && !folderB -> -1
        !folderA && folderB -> 1
        else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
    }
}
```

- `list()` 首屏即应用排序
- `loadMore()` 追加后**再次全量排序**，保证分页叠加后依然"文件夹优先 + 名称有序"

## 6. 国际化方案

- `res/values/strings.xml`：英文（缺省 / fallback）
- `res/values-zh-rCN/`、`res/values-zh-rTW/`：简 / 繁
- `AppSettings.languageTag`：`""` = 跟随系统 / `en` / `zh-CN` / `zh-TW`
- `LocaleHelper.resolve(tag)`：把系统非支持语种映射到英文；`zh`（不带 script/country）默认简体
- `Application.attachBaseContext` + `MainActivity.attachBaseContext` 均调用 `LocaleHelper.wrap(...)`
- 切换语言时 `(context as Activity).recreate()`，无需重启进程

## 7. 隐私 / 安全

- 密钥：`EncryptedSharedPreferences`（Android Keystore 主密钥）
- 备份排除：`backup_rules.xml` + `data_extraction_rules.xml` 排除 `oss_profiles.xml`
- 首启隐私拦截：`PrivacyConsentDialog`，`dismissOnBackPress=false` & `dismissOnClickOutside=false`
- 网络安全：`network_security_config.xml` 仅对 `*.aliyuncs.com` 域开放 cleartext，用于部分老 endpoint

## 8. 可配置项默认值

| Key | 默认 | 可选 |
| --- | --- | --- |
| `themeMode` | SYSTEM | SYSTEM / LIGHT / DARK |
| `languageTag` | "" | "" / en / zh-CN / zh-TW |
| `pageSize` | 10 | 10 / 20 / 50 / 100 |
| `textPreviewMaxBytes` | 2 MB | 512KB / 2MB / 5MB / 10MB |
| `autoPlayMedia` | true | bool |
| `confirmDestructiveOps` | true | bool |

## 9. 已知限制

- PDF 采用一次性全量渲染，超大 PDF 可能瞬时占用较多内存；后续可改为按可见页懒渲染
- 目录 move / delete 为客户端递归；在超大目录下建议配合服务端批量接口
- 未做自动刷新 Token / STS；AK/SK 需用户自行管理
