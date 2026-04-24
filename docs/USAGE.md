# 使用手册

> 文档版本：v1.0 · 最后更新：2026-04-24

## 1. 安装

### 1.1 下载 Release
在 GitHub Releases 页下载最新 `app-debug.apk` 或 `app-release.apk`，拷贝到手机安装。

### 1.2 自行构建
前置：JDK 17、Android SDK（Platform 34+）、或使用 Android Studio Koala 及以上。

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

### 1.3 小米 / 红米等 MIUI / HyperOS 设备

如果安装提示 `INSTALL_FAILED_USER_RESTRICTED`：打开 *设置 → 更多设置 → 开发者选项 → USB 安装*，开启该开关。

## 2. 首次启动

1. 阅读并**同意**隐私政策。不同意则无法进入主界面。
2. 主界面下方共三个 Tab：**文件 / 密钥 / 设置**。

## 3. 添加 OSS 配置（密钥）

1. 切到 **密钥** Tab → 点击右下角 `+`。
2. 填写：
   - **配置名称**：自定义，如 `生产`、`测试-cn-hangzhou`。
   - **Endpoint**：如 `https://oss-cn-hangzhou.aliyuncs.com`。
   - **Bucket**：*可选*。留空则登录后可列出账号下所有 Bucket。
   - **AccessKey ID / Secret**：建议使用 **RAM 子账号** 的最小权限 AK/SK。
3. 点击 **保存并激活**。
4. 回到 **文件** Tab，自动刷新为当前账号的 Bucket 列表或指定 Bucket 的根目录。

> 切换已有配置：在 **密钥** 页点任意配置即切换激活。

## 4. 浏览文件

- **点目录**：进入下一级。
- **面包屑**：顶部路径中任一段可跳回。
- **返回手势 / 返回键**：
  - 目录下：返回父目录。
  - Bucket 列表下：**连按两次**返回键退出应用。
- **输入路径跳转**：点顶部 🔍 图标，输入相对 key。例：
  - `img/2024/` 直接跳入该目录。
  - `img/2024/a.jpg` 跳到 `img/2024/` 并**高亮** `a.jpg`。
- **下拉 / 刷新**：顶栏右侧的刷新按钮。
- **分页**：默认每页 10，滚动到底自动加载下一页。页大小可在**设置**中改到 20 / 50 / 100。
- **排序**：目录在前，目录与文件各自按名称升序。

## 5. 文件操作

| 操作 | 入口 | 说明 |
| --- | --- | --- |
| 上传 | 右下「+ 操作」→ 上传文件 | 系统文件选择器，单文件 |
| 新建目录 | 右下「+ 操作」→ 新建目录 | 以 `foo/` 空对象实现 |
| 下载 | 行右侧 ⬇ | 系统创建文档弹窗，选保存位置 |
| 预览 | 行右侧 👁 或点击文件行 | 见下节 |
| 生成链接 | 行右侧 🔗 | 填写有效期（小时），可复制 / 分享 |
| 删除 | 行右侧 🗑 | 默认需要二次确认；目录会**递归**删除 |
| 移动 / 重命名 | 行右侧 ↗ | 填目标父前缀（留空为根目录）和新名称，目录会**递归**搬运 |

> 💡 破坏性操作的二次确认可在 **设置 → 安全 → 破坏性操作二次确认** 中关闭。

## 6. 预览支持的格式

| 类型 | 扩展名（举例） | 行为 |
| --- | --- | --- |
| 文本 / 源码 | `txt log md json xml yaml toml ini conf properties` `kt kts java groovy gradle py js ts c cpp go rs swift sh sql` ... | 下载到内存展示，受设置中"文本预览大小上限"限制 |
| 图片 | `jpg png gif webp bmp heic svg` ... | Coil 加载，支持 GIF 动图 |
| 音频 | `mp3 m4a aac wav flac ogg opus ...` | 内嵌播放，支持暂停 / 快进 / 进度条 |
| 视频 | `mp4 mkv webm mov avi ...` | 内嵌 ExoPlayer |
| PDF | `pdf` | 下载到本地后按屏幕宽度分页渲染 |
| 其他 | - | 提示不支持，可用「下载」或「生成链接」 |

## 7. 设置详解

- **外观**
  - *主题模式*：跟随系统 / 浅色 / 深色
  - *语言*：跟随系统 / English / 简体中文 / 繁體中文（切换立即生效）
- **浏览**
  - *每页加载数量*：10 / 20 / 50 / 100
- **预览**
  - *文本预览大小上限*：512 KB / 2 MB / 5 MB / 10 MB
  - *自动播放音视频*：开 / 关
- **安全**
  - *破坏性操作二次确认*：开 / 关
- **存储**
  - *清除预览缓存*：删除 `cacheDir` 下的临时文件，不影响密钥和偏好
- **关于**
  - 版本号 / 项目主页 / 开源许可 / 隐私政策

## 8. 常见问题

**Q1 提示 "Service haven't been set"**
使用了带 endpoint 的 OSSClient 调用 `listBuckets`，本项目已修复；更新到最新版本即可。

**Q2 提示 "The bucket you are attempting to access must be addressed using the specified endpoint"**
说明目标 Bucket 不在你 profile 的默认 endpoint。留空 Bucket 字段后，应用会按 `ListBuckets` 返回的 `extranetEndpoint` 正确访问。

**Q3 "CLEARTEXT communication not permitted"**
已在 `network_security_config.xml` 放行 `*.aliyuncs.com`。如果你使用自定义域名的 OSS，需要把域名加入该白名单或在 Endpoint 中使用 `https`。

**Q4 空目录一直显示"加载中…"**
通常是网络不通或 endpoint 错误。现支持点击"取消"主动结束加载并查看错误；也可在 **密钥** 页核对 Endpoint / AK/SK 是否正确。

**Q5 权限不足 `AccessDenied`**
检查子账号 RAM 策略，建议最少授予：`oss:ListBuckets`、`oss:ListObjects`、`oss:GetObject`、`oss:PutObject`、`oss:DeleteObject`、`oss:GetObjectAcl`（可选）。

## 9. 隐私

- 本应用不收集任何个人数据、不包含第三方统计 / 崩溃 / 广告 SDK。
- 所有 OSS AK/SK 仅加密保存在手机私有目录（`EncryptedSharedPreferences`，基于 Android Keystore）。
- 详情见 **设置 → 隐私政策**。
