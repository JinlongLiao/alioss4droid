# 设计文档

> Language / 语言：**简体中文** · [English](DESIGN.md)

> **应用名**: OSS Browser for Android  ·  **作者**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **仓库**: <https://github.com/JinlongLiao/alioss4droid>  ·  **许可证**: [Apache License 2.0](../LICENSE)  ·  **包名**: `io.github.jinlongliao.ossbrowser`
>
> 文档版本：v1.4 · 最后更新：2026-04-24

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
io.github.jinlongliao.ossbrowser
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
│   └── OssPreviewKind          # TEXT/IMAGE/AUDIO/VIDEO/PDF/DOCX/UNSUPPORTED 及扩展名集合
├── ui/
│   ├── OssBrowserRoot          # NavHost + BottomNav + 隐私同意拦截
│   ├── theme/Theme.kt          # M3 主题（跟随系统 / 浅 / 深）
│   ├── profile/                # 密钥页
│   ├── browser/                # 浏览页 + ViewModel + 各类 Dialog / Preview
│   └── settings/               # 设置 / 隐私 / 开源许可
└── util/
    ├── FileUtils               # SAF 复制 / 人类可读大小
    ├── DocxText                # DOCX（ZIP + word/document.xml）零依赖纯文本抽取
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
PreviewState ::= Loading(totalBytes, downloadedBytes)
              | Text(isMarkdown) | Docx | Image | Media(audio/video) | Pdf
              | TooLarge(sizeBytes, limitBytes)
              | Error(message)
```

- **TEXT**：`OssService.downloadBytes(maxBytes)`；若对象 `size > textPreviewMaxBytes`，直接落到
  `TooLarge`，不再发起请求。`.md / .markdown / .mdown / .mkd / .mkdn` 命中 `isMarkdown`，顶栏多一
  个"源码 / 富文本"切换按钮。
- **DOCX**：对象是 ZIP；`util/DocxText.kt` 用 `ZipInputStream` 定位 `word/document.xml`（限
  32 MB），再用 `XmlPullParser` 提取 `<w:p>` 段落，识别 `<w:t>` / `<w:tab>` / `<w:br>`。UI 以
  `LazyColumn` 逐段渲染并支持全文复制。不支持老版 `.doc` 二进制格式。
- **IMAGE / AUDIO / VIDEO**：预签名 URL（1h 过期），UI 用 Coil / ExoPlayer 播放。
- **PDF**：下载到 `cacheDir/pdf/<hash>_<name>`；`PdfRenderer` 按屏幕宽度等比渲染每页 Bitmap。
  下载过程通过 `downloadToFile` 的 `onProgress` 回写到 `Loading` 状态，前台展示真实进度条。
- **UNSUPPORTED**：直接设为 `TaskState.Failed`。

**友好加载 / 错误态。** 以下三种情况过去会被 snackbar 一闪而过，现在作为预览弹窗的一级状态呈现：

- *Loading*（`LoadingPreviewBody`）：文件图标 + 文件名 + `OssObjectItem.size` 估算大小；PDF 下
  载阶段 `LinearProgressIndicator` 显示精确进度，预签名 / 内存下载阶段展示 indeterminate 进度 +
  "正在拉取数据…"。
- *TooLarge*（`TooLargePreviewBody`）：`BrowserViewModel.preview()` 在发起下载前，先用
  `item.size` 与 `textPreviewMaxBytes` 做前置比较，命中则直接落到该状态。引导页展示当前大小 /
  上限，并提供「下载到本地」（复用 SAF `ACTION_CREATE_DOCUMENT`）与「生成临时链接」（跳到既
  有 Presign 弹窗）两个按钮；弹窗内部依赖 `currentPreviewItem()` 回取源文件引用。
- *Error*（`ErrorPreviewBody`）：预览流程中的任何异常都会走到这一状态，保留原始错误信息并给
  出「重试」（`retryPreview()` 重放 `preview(item)`）、「下载」、「关闭」三个动作。

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

## 10. 内建 Markdown 渲染器

隐私政策 / 开源声明随包以 Markdown 形式放在 `res/raw[-zh-rCN|-zh-rTW]/*.md`；浏览器页对
`.md` 对象的预览也复用同一渲染器。为避免引入 Markdown 解析库，
`ui/settings/MarkdownView.kt` 提供了单文件自研 Compose 渲染器（约 600 行）：

- **块级语法**
  - ATX 标题 `#` … `######`（自动去除行尾 `#`），以及 Setext 下划线式
    （`====` → H1，`----` → H2）
  - 段落：换行自动拼接；行尾双空格触发硬换行
  - 列表：`-` / `*` / `+` 无序、`1.` / `42.` 有序，按缩进嵌套（2 空格 / 级，最多 5 级），
    支持多行续写
  - 任务列表 `- [ ]` / `- [x]` 渲染为 ☐ / ☑
  - 引用块：连续多行 `>` 合并为一个多行引用
  - 围栏代码块 \`\`\` / `~~~`，可附语言标签
  - 分隔线 `---` / `***` / `___`（与 Setext 通过上下文消歧）
  - GFM 表格：表头行 + `|:--|:--:|--:|` 对齐行 + 数据行，支持 `\|` 转义
  - 整段 `![alt](url)` 提升为块级图片（Coil `AsyncImage`）
- **行内语法**：`**粗体**`、`*斜体*` / `_斜体_`、`***粗斜体***`、`~~删除线~~`、
  `` `行内代码` ``、`[文案](url)`、`![alt](url)`（渲染为🖼占位 + 链接）、
  `<http…>` 尖括号 autolink、裸 `https?://` URL、`\X` 反斜杠转义
- **参考式链接**：预处理 `[id]: url "title"` 定义；行内 `[text][id]` 与省略式
  `[id]` 会解析成链接
- 样式跟随 `MaterialTheme.colorScheme` / `typography`，自动适配明暗主题
- 链接使用 Compose 1.7 `LinkAnnotation.Url` + `TextLinkStyles`，不依赖已 deprecated 的
  `ClickableText`

调用方：`PrivacyScreen`、`LicensesScreen`（项目许可卡片）、`PrivacyConsentDialog`（首启弹窗），
以及 `BrowserScreen` 对 `.md` / `.markdown` / `.mdown` / `.mkd` / `.mkdn` 对象的预览
（可一键切换到等宽源码视图）。

## 11. 源码编码与行尾

- 仓库统一使用 **LF**，由 `.gitattributes` 与 `.editorconfig` 强制
- 唯一例外：`scripts/gen-release-keystore.ps1` 固定为 **CRLF**，
  以保证在不同 Windows 语言环境下 `powershell.exe` 双击执行行为一致
- Kotlin 源文件头部带 Apache 2.0 协议：`Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)`
- KDoc `@author` / `@since` 字段统一 `yyyy-MM-dd` 格式
