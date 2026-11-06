# AWS S3 适配与验收

## 配置和文件界面

在现有账号配置页选择“阿里云 OSS”或“AWS S3”。Region 提供可搜索的下拉框，展示本地化地区名称和 AWS 区域代码，保留手动输入新区域；选择区域本身不发起网络请求，也不自动启用 AWS 区域。

S3 配置包括 AccessKey、SecretKey、Region、可选 SessionToken、可选默认 Bucket、可选 HTTPS Endpoint 及路径寻址开关。Endpoint 留空使用 SDK 标准区域端点；中国区域使用相应分区域名。自定义基础地址中的路径保留，不擅自移除 context-path。

文件模块继续使用现有界面和任务入口，支持 Bucket 列表、目录和文件分页、排序、预览、下载、上传、新建目录、删除、移动/重命名、公开链接和签名链接。没有新增一套 S3 专用文件页。

旧配置没有存储类型字段时仍按 OSS 加载。新增字段复用现有加密配置仓库和加密备份，混合导入时按协议、区域和连接标识区分配置。S3 使用 AWS 官方 Kotlin SDK；没有将服务端 Spring/JVM Helper JAR 引入 Android。

## 上传与协议差异

- S3 上传使用现有后台服务、每任务进度、取消、继续/重试和历史清理。
- 8 MiB 起使用 S3 Multipart Upload。失败或取消保留会话，重试先分页查询已传分片，只发送缺少的分片；合并时传递 ETag 和服务端分片校验和。
- 分片从不可变本地快照按范围流式读取，不将整个大文件加载进内存。S3 进度以已确认的分片推进，小文件在 PUT 成功后完成。
- 上传任务固定提交时的协议、区域、寻址方式和 endpoint；配置协议改变后，旧任务报错，不自动更换协议。
- S3 不提供 OSS ListCname 接口，公开链接仍使用同一分享弹窗的默认域名选项，不查询阿里云自定义域名。链接是否公开可读由权限决定，应用不更改 ACL 或 Bucket Policy。
- S3 签名链接最长 7 天；SessionToken 提前过期时链接也会提前失效。应用不自动刷新用户手工录入的临时凭据。
- 移动是复制成功后删除源对象，不具备原子重命名保证，不支持跨 Bucket 移动；单次复制受 S3 CopyObject 的服务限制。版本化 Bucket 的删除遵循 S3 删除标记语义。
- 历史清理只移除本地任务、快照和断点，不清理远端未完成分片，也不删除已完成对象。

## 本地验证

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
git diff --check
```

测试使用真实 AWS SDK 连接本机 MockWebServer，并以 Robolectric 提供 Android 平台环境。覆盖 SigV4 与 SessionToken、分页令牌、下载限额、零字节 PUT、第二片失败后的断点恢复、分片校验和合并、取消保留断点、旧配置兼容、混合加密备份、区域与 URL 编码、分片数量边界。另通过原 OSS SDK 的实际列表、上传、下载和删除请求验证共享 HTTP 依赖兼容性；这些文件请求都只发送到本地模拟服务。

真实账号测试默认跳过，只有显式提供仓库外私有配置文件才会运行：

```bash
bash gradlew :app:testDebugUnitTest --tests '*S3ReadOnlyConnectionTest' \
  -Ps3ConnectionConfig=/path/to/private-connection.json --console=plain
```

该测试仅调用 `ListBuckets`，不会执行 ListObjects、GetObject、HeadObject、PutObject、CopyObject、DeleteObject 或 Multipart 接口。凭据不放在代码、资源、文档或命令行中。

## 用户授权的账号测试

依据用户指定的 `BeanCommonConfiguration#awsS3Helper` JAR 注解默认配置，核对 `wlzn.aws.region`、`wlzn.aws.key`、`wlzn.aws.secret` 和 `wlzn.aws.role`。Helper 的普通 S3 客户端直接使用 AK/SK，Role 用于另行获取 STS 凭据；本次连接按普通 S3 客户端配置，不调用 AssumeRole。

真实账号 `ListBuckets` 验证通过：区域 `ap-northeast-1`，返回 4 个 Bucket。未访问或操作任何 S3 文件，未创建 Bucket 或修改账号配置。连接参数只经仓库外私有临时文件传入，并导出为应用既有加密备份格式，用于设备配置。

手机端已通过临时独立配置测试组件调用应用既有加密导入逻辑保存并激活，真实 Android `ListBuckets` 同样返回 4 个 Bucket。重启应用后配置仍激活、Bucket 列表保持可见。临时组件已卸载，本次明文凭据文件和中间配置已清理；凭据仅保留在应用既有加密仓库。

此结果证明账号鉴权和 Bucket 列表权限可用，不代表对象读写权限、真实文件操作或后台续传已经实测。用户当前明确禁止真实 S3 文件操作，后续不得据此自动开展对象测试。

## 权限说明

不配置默认 Bucket 时需要 `s3:ListAllMyBuckets` 才能展示账号 Bucket 列表；填写默认 Bucket 可以直接进入该 Bucket，但访问文件仍需其相应权限。

文件功能通常需要目标范围内的 `s3:ListBucket`、`s3:GetObject`、`s3:PutObject`、`s3:DeleteObject` 和 `s3:ListMultipartUploadParts`。应用不会自动授予这些权限。使用 KMS 等加密机制时还需相应的服务权限，以实际账号策略为准。

## 官方依据

- [AWS SDK for Kotlin 的 Android 支持](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/home.html)
- [AWS Kotlin S3 接口](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/use-services-s3.html)
- [S3 预签名请求](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/presign-requests.html)
- [S3 分片上传与校验和](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)
