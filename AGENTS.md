# Repository Guidelines

## 项目结构与模块组织

本仓库是单模块 Android 应用。`app/src/main/java/io/github/jinlongliao/ossbrowser/` 存放 Kotlin 代码，按 `data`、`oss`、`ui` 和 `util` 分层；`MainActivity.kt` 与 `OssBrowserApp.kt` 是主要入口。资源位于 `app/src/main/res/`，本地化文案分别维护在 `values`、`values-zh-rCN` 和 `values-zh-rTW`。JVM 单元测试位于 `app/src/test/java/`，包结构应与生产代码一致。设计、需求和使用说明集中在 `docs/`，签名辅助脚本位于 `scripts/`。

## 构建、测试与本地开发

项目要求 JDK 17、Android SDK 34，并使用仓库内 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug   # 构建调试 APK
.\gradlew.bat :app:testDebugUnitTest # 运行 JVM 单元测试
.\gradlew.bat :app:lintDebug       # 执行 Android Lint
.\install.ps1 -Launch              # 构建、安装并启动应用
```

macOS/Linux 使用对应的 `./gradlew` 与 `./install.sh --launch`。发布构建前复制 `keystore.properties.sample`，不要提交真实密钥或密码。

## 编码风格与命名约定

遵循 `.editorconfig` 与 `.gitattributes`：UTF-8、通常使用 LF、四空格缩进，Kotlin/Java 行宽不超过 120 字符；Markdown、XML、JSON 使用两空格。类和 Composable 使用 `PascalCase`，函数与属性使用 `camelCase`，资源名使用 `snake_case`。优先沿用现有 Compose、ViewModel、StateFlow 分层，不为局部需求引入额外抽象。

新增或修改的类型、字段和方法必须具有准确的 KDoc 或注释；状态流转、协程、缓存、重试、回调及跨系统调用需说明顺序、前置条件和失败边界，避免逐行复述。修改逻辑时同步更新注释。Java/Groovy 新代码优先使用 `Objects.isNull/nonNull`；无顺序或并发要求的 Map 使用 `HashMap`，可预估元素数量时指定容量。具有独立业务含义的 DTO、枚举或策略应使用顶层类型。

## 测试指南

单元测试使用 JUnit 4（`testImplementation(libs.junit)`）。测试文件命名为 `<Subject>Test.kt`，测试方法应表达场景和预期。修复缺陷时先添加可复现测试；涉及 OSS 签名、预览类型、主题或 Web 控制台策略时，扩展相邻测试类。提交前至少运行 `:app:testDebugUnitTest`、`:app:lintDebug` 和受影响构建。

## 提交与 Pull Request

历史提交主要采用 Conventional Commits，如 `feat(browser): ...`、`fix(build): ...`、`refactor(nav): ...`；旧历史中的 `[add] - ...` 不作为新提交模板。请使用简短祈使句并保持单一主题。未经用户明确授权，Agent 不得执行 `git add`、提交、推送、拉取、合并、变基、切换分支或清理工作区；允许使用 `git status`、`git diff`、`git log` 等只读命令。

PR 应说明动机、实现范围、验证命令和结果，并关联相关 issue。UI 改动附前后截图或录屏；配置、安全或签名改动需说明敏感信息处理及回滚方式。不要混入无关格式化或生成产物。

## 安全与配置

不得提交 AccessKey、Secret、Token、Cookie、真实 `keystore.properties` 或新增签名文件。日志需包含可定位的阶段、业务标识、外部资源、异常 message 与完整堆栈，同时对鉴权信息脱敏。拼接 OSS 或其他服务地址时保留配置中已有 path，仅规范化末尾斜杠后追加业务路径。涉及凭据存储、备份策略、网络安全配置或签名的改动必须补充针对性测试并说明兼容与回滚风险。

## Agent 执行要求

修改前先检查工作区状态、相关配置、相邻实现和测试；仓库存在 `.codegraph/` 时，定位代码和调用链优先使用 `codegraph explore`。只修改需求直接涉及的内容，保留用户已有改动，不顺手重构或清理无关代码。先定义可验证的成功标准：缺陷修复先复现，功能修改补充测试，文档修改执行格式与 diff 检查。格式化仅限本次涉及文件，并在格式化后重新运行受影响测试；至少执行 `git diff --check`，未运行或受环境阻塞的检查必须明确报告。
