package io.github.jinlongliao.ossbrowser.s3

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** S3 分片会话只存 ID 和目标摘要，不存凭据；已完成分片以服务端 ListParts 为准。 */
@Serializable
data class S3UploadCheckpoint(
    /** uploadId 绑定目标账号、Bucket 和 key。 */
    val uploadId: String,
    /** 固定文件长度，单位字节。 */
    val size: Long,
    /** 分片大小，单位字节，最后一片可小于此值。 */
    val partSize: Long,
    /** 区域、endpoint、账号配置 ID、Bucket 与 key 的目标摘要。 */
    val target: String
)

/** 原子发布断点；只有完整 JSON 能用于重试。原子移动失败时向上抛出，禁止假装保存成功。 */
fun saveS3UploadCheckpoint(file: File, checkpoint: S3UploadCheckpoint) {
    check(file.parentFile?.let { it.mkdirs() || it.isDirectory } == true) { "无法创建 S3 断点目录" }
    val partial = File(file.parentFile, file.name + ".partial")
    try {
        partial.outputStream().use { output ->
            output.write(Json.encodeToString(checkpoint).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(partial.renameTo(file)) { "无法保存 S3 上传断点" }
    } finally {
        partial.delete()
    }
}

/** S3 每片至少 5 MiB，最多 10000 片；采用至少 8 MiB 的流式分片，不整片加载进内存。 */
fun s3UploadPartSize(size: Long): Long {
    require(size in 0..5L * 1024 * 1024 * 1024 * 1024) { "S3 文件大小超过支持范围" }
    return maxOf(8L * 1024 * 1024, (size + 9999) / 10000)
}
