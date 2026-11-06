package io.github.jinlongliao.ossbrowser.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CompleteMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.CompletedMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedPart
import aws.sdk.kotlin.services.s3.model.CreateMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.ListPartsRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.model.UploadPartRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import java.io.File
import java.net.URLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

/**
 * S3 分片续传：复用会话并分页核对远端已完成分片，仅上传缺少的分片，最后原子合并对象。
 * 失败和取消保留会话；取消由 SDK 的挂起 HTTP 请求传播，不自动 Abort 以便继续。
 * @param client 由调用方持有的 SDK 客户端。
 * @param bucket 固定目标 Bucket。
 * @param key 固定对象路径。
 * @param source 不可变本地快照。
 * @param records 此任务专属断点目录。
 * @param target 不含凭据的目标摘要，用于拒绝账号、区域和路径变化后复用旧断点。
 * @param onProgress 已确认发送的字节数与总长度；网络失败时不能表示远端对象已完成。
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html">S3 multipart upload</a>
 */
suspend fun uploadS3Multipart(
    client: S3Client, bucket: String, key: String, source: File, records: File,
    target: String, onProgress: (Long, Long) -> Unit
): String {
    check(source.isFile) { "上传文件快照不存在，请重新选择文件" }
    val size = source.length()
    val partSize = s3UploadPartSize(size)
    onProgress(0, size)
    if (size < 8L * 1024 * 1024) {
        client.putObject(PutObjectRequest {
            this.bucket = bucket
            this.key = key
            body = if (size == 0L) ByteStream.fromBytes(byteArrayOf()) else ByteStream.fromFile(source)
            contentType = URLConnection.guessContentTypeFromName(key) ?: "application/octet-stream"
        })
        onProgress(size, size)
        return key
    }
    val checkpointFile = File(records, "s3.json")
    var checkpoint = if (checkpointFile.exists()) {
        Json.decodeFromString<S3UploadCheckpoint>(checkpointFile.readText()).also {
            check(it.size == size && it.partSize == partSize && it.target == target) {
                "上传目标或文件快照已变更，请清理此任务后重新选择文件"
            }
        }
    } else null
    val parts = HashMap<Int, CompletedPart>()
    if (checkpoint != null) {
        try {
            var marker: String? = null
            do {
                val response = client.listParts(ListPartsRequest {
                    this.bucket = bucket
                    this.key = key
                    uploadId = checkpoint!!.uploadId
                    partNumberMarker = marker
                    maxParts = 1000
                })
                response.parts.orEmpty().forEach { part ->
                    val number = requireNotNull(part.partNumber) { "S3 分片缺少编号" }
                    val expected = minOf(partSize, size - (number - 1) * partSize)
                    check(number in 1..10000 && expected > 0 && part.size == expected && part.eTag != null) {
                        "S3 断点分片与文件快照不一致"
                    }
                    // 合并请求必须携带服务端返回的分片校验和，不能只保留 ETag。
                    parts[number] = CompletedPart {
                        partNumber = number
                        eTag = part.eTag
                        checksumCrc32 = part.checksumCrc32
                        checksumCrc32C = part.checksumCrc32C
                        checksumCrc64Nvme = part.checksumCrc64Nvme
                        checksumMd5 = part.checksumMd5
                        checksumSha1 = part.checksumSha1
                        checksumSha256 = part.checksumSha256
                        checksumSha512 = part.checksumSha512
                        checksumXxhash128 = part.checksumXxhash128
                        checksumXxhash3 = part.checksumXxhash3
                        checksumXxhash64 = part.checksumXxhash64
                    }
                }
                val next = response.nextPartNumberMarker.takeIf { response.isTruncated == true }
                check(next == null || next != marker) { "S3 分片列表返回了重复分页令牌" }
                marker = next
            } while (marker != null)
        } catch (exception: S3Exception) {
            if (exception.sdkErrorMetadata.errorCode != "NoSuchUpload") throw exception
            // 会话可能已合并或过期；固定快照可以安全重新创建会话，不复用旧分片。
            checkpointFile.delete()
            checkpoint = null
            parts.clear()
        }
    }
    if (checkpoint == null) {
        val response = client.createMultipartUpload(CreateMultipartUploadRequest {
            this.bucket = bucket
            this.key = key
            contentType = URLConnection.guessContentTypeFromName(key) ?: "application/octet-stream"
        })
        checkpoint = S3UploadCheckpoint(requireNotNull(response.uploadId), size, partSize, target)
        saveS3UploadCheckpoint(checkpointFile, checkpoint!!)
    }
    val uploadId = checkpoint!!.uploadId
    var confirmed = parts.keys.sumOf { minOf(partSize, size - (it - 1) * partSize) }
    onProgress(confirmed, size)
    val count = ((size + partSize - 1) / partSize).toInt()
    for (number in 1..count) {
        currentCoroutineContext().ensureActive()
        if (parts.containsKey(number)) continue
        val start = (number - 1) * partSize
        val length = minOf(partSize, size - start)
        val response = client.uploadPart(UploadPartRequest {
            this.bucket = bucket
            this.key = key
            this.uploadId = uploadId
            partNumber = number
            body = S3FileRangeStream(source, start, length)
        })
        parts[number] = CompletedPart {
            partNumber = number
            eTag = requireNotNull(response.eTag)
            checksumCrc32 = response.checksumCrc32
            checksumCrc32C = response.checksumCrc32C
            checksumCrc64Nvme = response.checksumCrc64Nvme
            checksumMd5 = response.checksumMd5
            checksumSha1 = response.checksumSha1
            checksumSha256 = response.checksumSha256
            checksumSha512 = response.checksumSha512
            checksumXxhash128 = response.checksumXxhash128
            checksumXxhash3 = response.checksumXxhash3
            checksumXxhash64 = response.checksumXxhash64
        }
        confirmed += length
        onProgress(confirmed, size)
    }
    client.completeMultipartUpload(CompleteMultipartUploadRequest {
        this.bucket = bucket
        this.key = key
        this.uploadId = uploadId
        multipartUpload = CompletedMultipartUpload { this.parts = parts.toSortedMap().values.toList() }
    })
    checkpointFile.delete()
    return key
}
