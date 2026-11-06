# 浏览布局与 OSS 地域选择

本轮为文件浏览补充持久化布局选择，并改善对象存储配置的辨识与填写。未修改凭据存储、配置备份格式或 OSS/S3 请求协议。

## 浏览布局

- 文件页右上角「更多 → 视图设置」中并排显示「列表」「双列瀑布流」，不再占用文件区的一整行；首次安装及旧版本缺少配置项时默认列表。
- 选择保存到应用偏好 `browser_layout_mode`，Bucket、文件夹和文件页使用同一偏好；切换账号、重建页面及再次启动后继续生效。
- 瀑布流固定两列，卡片依内容高度排布；卡片沿用当前主题材质，文件类型图标跨主题保持一致。
- 两种布局使用同一套文件操作回调，分页提示在瀑布流中跨两列展示；接近底部加载与路径跳转高亮支持两种滚动容器。
- 排序字段与方向独立于布局，切换排序或布局后回到顶部。

## 配置类型

每张配置卡片在名称下明确显示「阿里云 OSS」或「AWS S3」，标签直接使用保存的 `storageType`，不依据配置名称或 Endpoint 推测。

## OSS 地域选择

- OSS 配置新增可搜索的地域选择框，预置 30 个常规公网地域；支持按本地化名称或地域代码搜索。
- 仅明确选中某一地域时自动填入对应的 HTTPS 公网 Endpoint。打开、搜索、取消均不改写原地址。
- 原 Endpoint 输入框继续可编辑，用于金融云、内网、专有云、自定义域名及带路径的部署。
- 标准公网地址可识别原有无协议、HTTP 及末尾斜杠形式，但不自动规范化保存值。包含路径、端口、查询参数、Bucket 前缀或内网域名的地址展示为自定义。
- OSS 地域列表与 S3 完全独立。例如 OSS 的 `ap-southeast-3` 是吉隆坡，不能复用 AWS 同码地域的雅加达名称。
- 地域数据依据 [阿里云 OSS 地域与 Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints) 整理，核对日期为 2026-11-07。账号是否启用某地域仍以控制台为准。

## 验证记录

执行仓库 Gradle Wrapper，使用独立构建目录避免覆盖并行会话的报告：

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline \
  --init-script /private/tmp/alioss-visual-isolated.init.gradle \
  --project-cache-dir /private/tmp/alioss-visual-project-cache --console=plain
```

- 构建成功。
- 单元测试共 120 项：119 项通过、0 失败、1 项跳过。跳过项为未提供真实账号时的 S3 连接测试。
- 本轮新增 7 项测试全部通过，覆盖布局跨仓库重建保存、默认与未知值兼容、其他设置保留、地域唯一性、标准 Endpoint 识别及自定义地址边界。
- Android Lint：0 错误、70 警告；本轮新增文件无 Lint 提示。
- 三语言资源 XML 合法且无重复键；新增文件核对 UTF-8、LF 与文件末尾换行，`git diff --check` 通过。
- APK：`app/build/outputs/apk/visual-review/app-visual-debug.apk`。
- 本轮未进行真机界面交互验收；避免打断设备上正在进行的配置编辑。上述测试不等于真实云账号连接或两种布局的完整真机验收。

回滚时可撤去本轮 UI 入口和独立布局字段；偏好中遗留的布局键及配置地址不影响旧版本读取。没有执行 Git 提交或推送。
