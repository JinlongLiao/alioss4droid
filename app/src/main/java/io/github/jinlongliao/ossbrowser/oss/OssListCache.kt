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
package io.github.jinlongliao.ossbrowser.oss

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * OSS 列表的本地缓存（内存 + 磁盘）。
 *
 * 目的：进入 bucket / 目录时秒开，避免每次都等网络。只有用户主动点"刷新"
 * 或执行写操作（上传 / 删除 / 移动 / 新建）后才强制访问网络。
 *
 * 设计要点：
 * - TTL 30 分钟。过期则视为未命中，需要重新拉取（用户可接受的数据陈旧度）。
 * - 磁盘文件存于 `cacheDir/oss-list/<md5(key)>.json`，随"设置 → 清理缓存"一并清除。
 * - 使用扁平 DTO 规避 sealed class 多态序列化的配置成本。
 */
class OssListCache(context: Context) {

    private val dir: File = File(context.cacheDir, "oss-list").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, Entry>()

    // ---------- Bucket 列表 ----------

    fun readBuckets(profileId: String): List<OssBucketInfo>? {
        val entry = get(bucketsKey(profileId)) ?: return null
        val raw = entry.buckets ?: return null
        return raw.map { it.toDomain() }
    }

    fun writeBuckets(profileId: String, list: List<OssBucketInfo>) {
        put(
            bucketsKey(profileId),
            Entry(
                savedAt = System.currentTimeMillis(),
                buckets = list.map { BucketDto.from(it) }
            )
        )
    }

    fun invalidateBuckets(profileId: String) = remove(bucketsKey(profileId))

    // ---------- 对象列表 ----------

    data class Cached(val items: List<OssObjectItem>, val nextMarker: String?)

    fun readList(profileId: String, bucket: String, prefix: String): Cached? {
        val entry = get(listKey(profileId, bucket, prefix)) ?: return null
        val items = entry.items ?: return null
        return Cached(items.map { it.toDomain() }, entry.nextMarker)
    }

    fun writeList(
        profileId: String,
        bucket: String,
        prefix: String,
        items: List<OssObjectItem>,
        nextMarker: String?
    ) {
        put(
            listKey(profileId, bucket, prefix),
            Entry(
                savedAt = System.currentTimeMillis(),
                items = items.map { ItemDto.from(it) },
                nextMarker = nextMarker
            )
        )
    }

    fun invalidateList(profileId: String, bucket: String, prefix: String) =
        remove(listKey(profileId, bucket, prefix))

    // ---------- 全局清理 ----------

    /**
     * 清空所有缓存（内存 + 磁盘）。
     *
     * 没有按 profileId 精细清理：文件名是 md5 后的 hash，无法反查前缀；
     * 场景多在"改密钥 / 删除账号"触发，整体清更安全也足够简单。
     */
    fun clear() {
        memory.clear()
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    // ---------- 内部 ----------

    private fun bucketsKey(profileId: String) = "buckets|$profileId"
    private fun listKey(profileId: String, bucket: String, prefix: String) =
        "list|$profileId|$bucket|$prefix"

    private fun get(key: String): Entry? {
        val now = System.currentTimeMillis()
        memory[key]?.let {
            return if (now - it.savedAt > TTL_MS) {
                remove(key); null
            } else it
        }
        val file = fileFor(key)
        if (!file.exists()) return null
        val entry = runCatching {
            json.decodeFromString<Entry>(file.readText())
        }.getOrNull() ?: run {
            file.delete()
            return null
        }
        if (now - entry.savedAt > TTL_MS) {
            file.delete()
            return null
        }
        memory[key] = entry
        return entry
    }

    private fun put(key: String, entry: Entry) {
        memory[key] = entry
        runCatching { fileFor(key).writeText(json.encodeToString(entry)) }
    }

    private fun remove(key: String) {
        memory.remove(key)
        runCatching { fileFor(key).delete() }
    }

    private fun fileFor(key: String): File = File(dir, md5(key) + ".json")

    private fun md5(input: String): String {
        val d = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    // ---------- 序列化 DTO ----------

    @Serializable
    private data class Entry(
        val savedAt: Long,
        val buckets: List<BucketDto>? = null,
        val items: List<ItemDto>? = null,
        val nextMarker: String? = null
    )

    @Serializable
    private data class BucketDto(
        val name: String,
        val location: String? = null,
        val createDateMillis: Long? = null,
        val extranetEndpoint: String? = null,
        val storageClass: String? = null
    ) {
        fun toDomain() = OssBucketInfo(
            name = name,
            location = location,
            createDate = createDateMillis?.let { Date(it) },
            extranetEndpoint = extranetEndpoint,
            storageClass = storageClass
        )

        companion object {
            fun from(b: OssBucketInfo) = BucketDto(
                name = b.name,
                location = b.location,
                createDateMillis = b.createDate?.time,
                extranetEndpoint = b.extranetEndpoint,
                storageClass = b.storageClass
            )
        }
    }

    @Serializable
    private data class ItemDto(
        val kind: String,
        val key: String,
        val size: Long = 0L,
        val lastModifiedMillis: Long? = null,
        val etag: String? = null
    ) {
        fun toDomain(): OssObjectItem = when (kind) {
            KIND_FOLDER -> OssObjectItem.Folder(key = key)
            else -> OssObjectItem.File(
                key = key,
                size = size,
                lastModified = lastModifiedMillis?.let { Date(it) },
                etag = etag
            )
        }

        companion object {
            fun from(item: OssObjectItem): ItemDto = when (item) {
                is OssObjectItem.Folder -> ItemDto(kind = KIND_FOLDER, key = item.key)
                is OssObjectItem.File -> ItemDto(
                    kind = KIND_FILE,
                    key = item.key,
                    size = item.size,
                    lastModifiedMillis = item.lastModified?.time,
                    etag = item.etag
                )
            }
        }
    }

    companion object {
        private const val TTL_MS = 30L * 60L * 1000L
        private const val KIND_FOLDER = "folder"
        private const val KIND_FILE = "file"
    }
}
