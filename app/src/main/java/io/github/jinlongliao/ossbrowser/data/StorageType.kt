package io.github.jinlongliao.ossbrowser.data

import kotlinx.serialization.Serializable

/** 连接协议；旧配置没有此字段时按阿里云 OSS 读取。 */
@Serializable
enum class StorageType {
    /** 阿里云 OSS 原生 SDK。 */
    OSS,
    /** AWS S3 标准区域端点。 */
    AWS_S3
}
