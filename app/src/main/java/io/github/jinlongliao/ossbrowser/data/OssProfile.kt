/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jinlongliao.ossbrowser.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 对象存储连接配置，沿用旧类型名和加密存储格式以兼容现有配置及备份。
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
    val note: String = "",
    /** 存储协议，旧记录默认为 OSS。 */
    val storageType: StorageType = StorageType.OSS,
    /** AWS 签名区域；S3 必填，OSS 不使用。 */
    val region: String = "",
    /** AWS 临时凭证的会话令牌；为空时使用长期密钥，沿用加密存储和加密备份。 */
    val sessionToken: String = "",
    /** S3 是否使用 endpoint/bucket/key 形式；默认使用 SDK 的标准域名寻址。 */
    val forcePathStyle: Boolean = false
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
