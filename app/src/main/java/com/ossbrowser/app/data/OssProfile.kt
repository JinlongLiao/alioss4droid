package com.ossbrowser.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一套 OSS 连接配置。
 */
@Serializable
data class OssProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpoint: String,
    val accessKeyId: String,
    val accessKeySecret: String,
    /** 默认 bucket，可选；未填时进入后先展示该账号所有 bucket 列表。 */
    val bucket: String = "",
    val note: String = ""
) {
    companion object {
        fun empty() = OssProfile(
            name = "",
            endpoint = "https://oss-cn-hangzhou.aliyuncs.com",
            accessKeyId = "",
            accessKeySecret = "",
            bucket = "",
            note = ""
        )
    }
}

@Serializable
internal data class ProfileStore(
    val profiles: List<OssProfile> = emptyList(),
    val activeId: String? = null
)
