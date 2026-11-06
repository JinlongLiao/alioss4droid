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
    /** 按访问顺序淘汰内存快照；磁盘TTL和飞行请求代次不随内存淘汰改变。 */
    private val memory = LinkedHashMap<String, Entry>(MAX_MEMORY_SNAPSHOTS, 0.75f, true)

    /** 内存元数据总条目数，仅在本实例锁内更新。 */
    internal var memoryMetadataItemCount = 0
        private set

    /** 供本地回归检查内存上限，不读取或修改磁盘。 */
    internal val memorySnapshotCount: Int
        @Synchronized get() = memory.size

    /** 目录失效代次只用于进程内飞行请求，不写入磁盘；单调增长，清缓存也不会复用旧值。 */
    private var nextListGeneration = 0L

    /** 全量清理后的默认代次，使未单独登记的目录也能拒绝清理前的旧响应。 */
    private var clearedListGeneration = 0L

    /** 各目录最近一次写操作/刷新导致的失效代次，由本实例锁保护。 */
    private val listGenerations = HashMap<String, Long>()

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

    @Synchronized
    fun readList(profileId: String, bucket: String, prefix: String): Cached? {
        val entry = get(listKey(profileId, bucket, prefix)) ?: return null
        val items = entry.items ?: return null
        return Cached(items.map { it.toDomain() }, entry.nextMarker)
    }

    /** 读取发起目录请求时的代次，供网络响应写回前核对。 */
    @Synchronized
    fun listGeneration(profileId: String, bucket: String, prefix: String): Long =
        listGenerations[listKey(profileId, bucket, prefix)] ?: clearedListGeneration

    /**
     * 缓存目录快照；上传、刷新或清理之后才返回的旧响应不能覆盖最新失效状态。
     * @param expectedGeneration 请求发起时的目录代次；为空时用于没有异步请求的直接写入。
     * @return 响应仍有效并已缓存时为 true；代次过期时不写入并返回 false。
     */
    @Synchronized
    fun writeList(
        profileId: String,
        bucket: String,
        prefix: String,
        items: List<OssObjectItem>,
        nextMarker: String?,
        expectedGeneration: Long? = null
    ): Boolean {
        if (expectedGeneration != null && expectedGeneration != listGeneration(profileId, bucket, prefix)) {
            return false
        }
        put(
            listKey(profileId, bucket, prefix),
            Entry(
                savedAt = System.currentTimeMillis(),
                items = items.map { ItemDto.from(it) },
                nextMarker = nextMarker
            )
        )
        return true
    }

    /** 使指定目录失效，同时拒绝失效之前发起、之后才返回的网络结果。 */
    @Synchronized
    fun invalidateList(profileId: String, bucket: String, prefix: String) {
        val key = listKey(profileId, bucket, prefix)
        listGenerations[key] = ++nextListGeneration
        remove(key)
    }

    // ---------- 全局清理 ----------

    /**
     * 清空所有缓存（内存 + 磁盘）。
     *
     * 没有按 profileId 精细清理：文件名是 md5 后的 hash，无法反查前缀；
     * 场景多在"改密钥 / 删除账号"触发，整体清更安全也足够简单。
     */
    @Synchronized
    fun clear() {
        clearedListGeneration = ++nextListGeneration
        listGenerations.clear()
        memory.clear()
        memoryMetadataItemCount = 0
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    // ---------- 内部 ----------

    private fun bucketsKey(profileId: String) = "buckets|$profileId"
    private fun listKey(profileId: String, bucket: String, prefix: String) =
        "list|$profileId|$bucket|$prefix"

    @Synchronized
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
        retainMemoryEntry(key, entry)
        return entry
    }

    @Synchronized
    private fun put(key: String, entry: Entry) {
        retainMemoryEntry(key, entry)
        runCatching { fileFor(key).writeText(json.encodeToString(entry)) }
    }

    @Synchronized
    private fun remove(key: String) {
        memory.remove(key)?.let { memoryMetadataItemCount -= entryMetadataCount(it) }
        runCatching { fileFor(key).delete() }
    }

    /**
     * 保留近期快照，先清理所有已过期内存项，再按总元数据数/快照数淘汰最久未访问项。
     * 淘汰只释放DTO引用；后续读取仍可命中磁盘，不能删除目录失效代次或磁盘缓存。
     */
    private fun retainMemoryEntry(key: String, entry: Entry) {
        memory.remove(key)?.let { memoryMetadataItemCount -= entryMetadataCount(it) }
        val now = System.currentTimeMillis()
        val expired = memory.entries.iterator()
        while (expired.hasNext()) {
            val cached = expired.next()
            if (now - cached.value.savedAt > TTL_MS) {
                memoryMetadataItemCount -= entryMetadataCount(cached.value)
                expired.remove()
            }
        }
        memory[key] = entry
        memoryMetadataItemCount += entryMetadataCount(entry)
        val oldest = memory.entries.iterator()
        while (oldest.hasNext() &&
            (memory.size > MAX_MEMORY_SNAPSHOTS || memoryMetadataItemCount > MAX_MEMORY_METADATA_ITEMS)
        ) {
            memoryMetadataItemCount -= entryMetadataCount(oldest.next().value)
            oldest.remove()
        }
    }

    /** 空目录也计为一个快照条目，避免大量空目录绕过内存上限。 */
    private fun entryMetadataCount(entry: Entry): Int =
        ((entry.items?.size ?: 0) + (entry.buckets?.size ?: 0)).coerceAtLeast(1)

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
        /** 仅限制内存DTO；超过预算的完整列表仍写磁盘，返回结果不截断。 */
        private const val MAX_MEMORY_METADATA_ITEMS = 20_000
        /** 同时约束大量小目录的快照及索引开销，不影响磁盘缓存命中。 */
        private const val MAX_MEMORY_SNAPSHOTS = 32
        private const val TTL_MS = 30L * 60L * 1000L
        private const val KIND_FOLDER = "folder"
        private const val KIND_FILE = "file"
    }
}
