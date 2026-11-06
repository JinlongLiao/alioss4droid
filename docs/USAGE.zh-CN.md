# 使用手册

> Language / 语言：**简体中文** · [English](USAGE.md)

> **应用名**: OSS Browser for Android  ·  **作者**: [jinlongliao](https://github.com/JinlongLiao/)  ·  **仓库**: <https://github.com/JinlongLiao/alioss4droid>  ·  **许可证**: [Apache License 2.0](../LICENSE)  ·  **包名**: `io.github.jinlongliao.ossbrowser`

> 文档版本：1.0.5 · 最后更新：2026-11-07；当前交付与验证边界见[文档索引](文档索引.md)

## 1. 安装

### 1.1 下载 Release
正式安装使用经发布密钥签名的 `app-release.apk`。本地路径为 `app/build/outputs/apk/release/app-release.apk`；当前操作不代表已发布到 GitHub Releases 或应用商店。

### 1.2 自行构建
前置：JDK 17、Android SDK Platform 37；应用最低 Android 12（API 31），targetSdk 34。签名从仓库本地 `keystore.properties` 读取，不将密钥提交 Git。

```bash
# Windows
gradlew.bat :app:assembleRelease

# macOS / Linux
bash gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。正式包名为 `io.github.jinlongliao.ossbrowser`；调试包追加 `.debug`，两者的账号、任务和设置相互独立。跨包迁移使用设置中的加密导出 / 导入，不能直接复制加密偏好文件。

`install.sh` / `install.ps1` 当前默认安装调试包。正式版可手动安装：

```bash
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/oss-browser-release.apk
adb shell pm install -r /data/local/tmp/oss-browser-release.apk
adb shell rm -f /data/local/tmp/oss-browser-release.apk
```

### 1.3 小米 / 红米等 MIUI / HyperOS 设备

如果安装提示 `INSTALL_FAILED_USER_RESTRICTED`：打开 *设置 → 更多设置 → 开发者选项 → USB 安装*，开启该开关。

## 2. 首次启动

1. 阅读并**同意**隐私政策。不同意则无法进入主界面。
2. 主界面下方为 **文件 / 设置**。文件页右上角「更多」按文件与传输、浏览视图、存储配置分组。

## 3. 添加 OSS / S3 配置

1. 文件页右上角 **更多 → 对象存储配置**，再点击配置页的 `+`。
2. 填写：
   - **配置名称**：自定义，如 `生产`、`测试-cn-hangzhou`。
   - **存储类型**：阿里云 OSS 或 AWS S3。
   - **Endpoint**：OSS 如 `https://oss-cn-hangzhou.aliyuncs.com`。
   - **Bucket**：*可选*。留空则登录后可列出账号下所有 Bucket。
   - **AccessKey ID / Secret**：填写相应服务的访问凭证。
   - **S3 Region**：搜索地区名称或区域代码，也可手动输入有效区域代码。S3 Endpoint 可留空使用 SDK 区域端点；SessionToken、路径寻址按实际服务配置。
3. 点击 **保存并激活**。
4. 回到 **文件** Tab，自动刷新为当前账号的 Bucket 列表或指定 Bucket 的根目录。

> 切换已有配置：在 **对象存储配置** 页点任意配置即切换激活。

S3 与 OSS 共用文件操作页和上传任务入口。S3 公开链接不查询 OSS 自定义域名；手动填写的 SessionToken 到期后需更新，应用不自动获取或刷新 STS。

## 4. 浏览文件

- **点目录**：进入下一级。
- **面包屑**：顶部路径中任一段可跳回。
- **返回手势 / 返回键**：
  - 目录下：返回父目录。
  - Bucket 列表下：**连按两次**返回键退出应用。
- **输入路径跳转**：右上角 **更多 → 跳转到路径**，输入相对 key。例：
  - `img/2024/` 直接跳入该目录。
  - `img/2024/a.jpg` 跳到 `img/2024/` 并**高亮** `a.jpg`。
- **刷新**：顶栏右侧的刷新按钮。
- **显示方式**：**更多 → 视图设置** 中选择列表或双列瀑布流；选择后持续生效。
- **分页**：默认每页 10，滚动到底自动加载下一页。页大小可在**设置**中改到 20 / 50 / 100。
- **排序**：进入 Bucket 后，在 **更多 → 视图设置** 中按名称或修改时间选择正序/倒序，目录仍优先。
- **目录规模**：名称正序按需分页；时间和名称倒序读取完整当前目录元数据，最多 10000 条，超限会提示。切换排序立即生效，上传完成刷新对应目录。

## 5. 文件操作

| 操作 | 入口 | 说明 |
| --- | --- | --- |
| 上传 | 进入 Bucket 后，右上角「更多 → 上传文件」 | 系统文件选择器，支持多选 |
| 上传任务 | 右上角「更多 → 上传任务」 | 查看任务；正在上传时更多按钮显示数量角标 |
| 新建目录 | 右上角「更多 → 新建目录」 | 以 `foo/` 空对象实现 |
| 下载 | 行右侧 ⬇ | 系统创建文档弹窗，选保存位置 |
| 预览 | 行右侧 👁 或点击文件行 | 见下节 |
| 生成链接 | 行右侧 🔗 | 填写有效期（小时），可复制 / 分享 |
| 删除 | 行右侧 🗑 | 默认需要二次确认；目录会**递归**删除 |
| 移动 / 重命名 | 行右侧 ↗ | 填目标父前缀（留空为根目录）和新名称，目录会**递归**搬运 |

> 💡 破坏性操作的二次确认可在 **设置 → 安全 → 破坏性操作二次确认** 中关闭。

### 5.1 上传任务

从右上「更多 → 上传任务」查看排队、准备、上传、失败、取消及完成任务；列表刷新和切目录不会清空任务。支持逐任务取消、继续 / 重试、清理已结束历史。应用退到后台由前台服务继续执行；需允许通知权限才能在通知栏查看进度，系统强制停止仍可能中断任务。

大文件使用分片与本地不可变快照；S3 从 8 MiB 起启用分片，重试会查询服务端已上传分片后续传。取消后保留断点便于继续，历史清理移除本地任务、快照与断点，不删除已完成的云端文件，也不清理远端未完成分片。

## 6. 预览支持的格式

| 类型 | 扩展名（举例） | 行为 |
| --- | --- | --- |
| 文本 / 源码 | `txt log md json xml yaml toml ini conf properties` `kt kts java groovy gradle py js ts c cpp go rs swift sh sql` ... | 下载到内存展示，受设置中"文本预览大小上限"限制 |
| 图片 | `jpg png gif webp bmp heic svg` ... | Coil 加载，支持 GIF 动图 |
| 音频 | `mp3 m4a aac wav flac ogg opus ...` | 内嵌播放，支持暂停 / 快进 / 进度条 |
| 视频 | `mp4 mkv webm mov avi ...` | 内嵌 ExoPlayer |
| PDF | `pdf` | 下载后按可见页后台渲染，限制单页像素预算并保留完整比例 |
| Office | `docx xlsx pptx` | 本地解析文字 / 表格内容，受读取限额约束，不承诺原版式完整还原 |
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
- **备份与分享**
  - *导出 OSS 配置*：一键加密导出所有账号为 `.aloss` 文件（AES‑256‑GCM，可选口令）
  - *导入 OSS 配置*：选择 `.aloss` 文件合并到本机，支持 OSS / S3 混合配置；按 id 及协议、区域、连接标识匹配
- **关于**
  - 版本号 / **作者**（点击跳转 GitHub 主页）/ 项目主页 / 开源许可 / 隐私政策

> 💡 "开源许可" 与 "隐私政策" 页会把随包的 Markdown（`res/raw*/*.md`）直接渲染为标题、
> 列表、粗体、行内代码和可点击链接，无需外部浏览器。

## 8. 常见问题

**Q1 提示 "Service haven't been set"**
使用了带 endpoint 的 OSSClient 调用 `listBuckets`，本项目已修复；更新到最新版本即可。

**Q2 提示 "The bucket you are attempting to access must be addressed using the specified endpoint"**
说明目标 Bucket 不在你 profile 的默认 endpoint。留空 Bucket 字段后，应用会按 `ListBuckets` 返回的 `extranetEndpoint` 正确访问。

**Q3 "CLEARTEXT communication not permitted"**
已在 `network_security_config.xml` 放行 `*.aliyuncs.com`。如果你使用自定义域名的 OSS，需要把域名加入该白名单或在 Endpoint 中使用 `https`。

**Q4 空目录一直显示"加载中…"**
通常是网络不通或 endpoint 错误。现支持点击"取消"主动结束加载并查看错误；也可在 **更多 → 对象存储配置** 中核对 Endpoint / AK/SK 是否正确。

**Q5 权限不足 `AccessDenied`**
检查子账号 RAM 策略，建议最少授予：`oss:ListBuckets`、`oss:ListObjects`、`oss:GetObject`、`oss:PutObject`、`oss:DeleteObject`、`oss:GetObjectAcl`（可选）。

## 9. 隐私

- 本应用不收集任何个人数据、不包含第三方统计 / 崩溃 / 广告 SDK。
- 所有 OSS / S3 AK/SK 仅加密保存在手机私有目录（`EncryptedSharedPreferences`，基于 Android Keystore）。
- 详情见 **设置 → 隐私政策**。

## 10. 验证范围

本轮真实 S3 只验证 `ListBuckets`，未执行真实文件列举、读取、上传、复制、删除等操作。SDK 文件协议回归使用本地模拟服务。完整 Region 手机交互、弱网及后台长稳尚未覆盖，详见[发布与清理记录](1.0.5发布与清理记录.md)。
