package com.ossbrowser.app.oss

import android.content.Context
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.ossbrowser.app.data.OssProfile
import com.ossbrowser.app.data.OssProfileRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * 按当前激活 profile 懒加载 / 缓存 OSSClient。
 *
 * 因 OSS Android SDK 区分两类请求：
 *   1. Bucket 级（listObjects / put / get / delete / copy）：**必须** 用带 endpoint 的 client，
 *      且该 endpoint 必须与 bucket 所在 region 匹配，否则会返回
 *      "The bucket you are attempting to access must be addressed using the specified endpoint"。
 *   2. Service 级（listBuckets）：**必须** 用不带 endpoint 的 client，否则会抛
 *      "Service haven't been set !"。
 *
 * 因此本类：
 *   - [current] 按 endpoint 缓存多个 bucket 级 client（支持一个 AK 跨 region 访问）；
 *   - [service] 缓存一个 service 级 client。
 */
class OssClientProvider(
    private val context: Context,
    private val repository: OssProfileRepository
) {
    /** key = profileId + "|" + normalizedEndpoint */
    private val bucketClients = ConcurrentHashMap<String, OSSClient>()

    @Volatile private var serviceClient: OSSClient? = null
    @Volatile private var serviceClientProfileId: String? = null

    /**
     * 返回用于 bucket 级操作的 client。
     * @param endpoint 如果为空则使用 profile 自身的 endpoint；
     *                 否则按该 endpoint（通常是目标 bucket 的 `extranetEndpoint`）创建 / 取缓存。
     */
    fun current(endpoint: String? = null): Pair<OSSClient, OssProfile>? {
        val profile = repository.active() ?: return null
        val ep = normalizeEndpoint(
            endpoint?.takeIf { it.isNotBlank() } ?: profile.endpoint
        )
        val key = profile.id + "|" + ep
        val client = bucketClients.getOrPut(key) {
            OSSClient(
                context.applicationContext,
                ep,
                credentials(profile),
                clientConfig()
            )
        }
        return client to profile
    }

    /** 用于 Service 级操作（listBuckets）。endpoint 必须为 null。 */
    fun service(): Pair<OSSClient, OssProfile>? {
        val profile = repository.active() ?: return null
        val client = synchronized(this) {
            if (serviceClientProfileId != profile.id || serviceClient == null) {
                serviceClient = OSSClient(
                    context.applicationContext,
                    credentials(profile),
                    clientConfig()
                )
                serviceClientProfileId = profile.id
            }
            serviceClient!!
        }
        return client to profile
    }

    fun invalidate() {
        synchronized(this) {
            bucketClients.clear()
            serviceClient = null
            serviceClientProfileId = null
        }
    }

    private fun clientConfig() = ClientConfiguration().apply {
        connectionTimeout = 30_000
        socketTimeout = 30_000
        maxConcurrentRequest = 5
        maxErrorRetry = 2
    }

    private fun credentials(profile: OssProfile) =
        OSSPlainTextAKSKCredentialProvider(profile.accessKeyId, profile.accessKeySecret)

    /** `oss-cn-hangzhou.aliyuncs.com` -> `https://oss-cn-hangzhou.aliyuncs.com`。 */
    private fun normalizeEndpoint(raw: String): String {
        val s = raw.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            else -> "https://$s"
        }
    }
}
