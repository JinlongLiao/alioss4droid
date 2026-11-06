package io.github.jinlongliao.ossbrowser.s3

import io.github.jinlongliao.ossbrowser.data.OssProfile
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/** AWS 标准区域端点；中国区域使用独立分区域名。 */
fun awsS3Endpoint(region: String): String {
    require(region.matches(Regex("[a-z]{2}(?:-[a-z]+)+-[0-9]+"))) { "AWS Region 格式无效" }
    return "https://s3.$region.amazonaws.com" + if (region.startsWith("cn-")) ".cn" else ""
}

/** 从 Bucket 列表提供的标准端点读取签名区域，自定义地址则保留配置中的明确区域。 */
fun resolveS3Region(profile: OssProfile, endpoint: String?): String {
    val host = endpoint?.let { URI(it).host }.orEmpty()
    return Regex("s3\\.([a-z0-9-]+)\\.amazonaws\\.com(?:\\.cn)?").matchEntire(host)
        ?.groupValues?.get(1) ?: profile.region
}

/** 按路径段编码，保留目录分隔、重复斜杠和点路径的原始对象 key 语义。 */
fun encodeS3ObjectKey(key: String): String = key.split('/').joinToString("/") {
    URLEncoder.encode(it, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}

/** 公开 URL 只去掉签名，不改变端点已有路径；请求是否允许匿名读取由 S3 权限决定。 */
fun s3PublicObjectUrl(profile: OssProfile, endpoint: String?, bucket: String, key: String): String {
    val base = URI(endpoint?.takeIf { it.isNotBlank() } ?: profile.endpoint.takeIf { it.isNotBlank() }
        ?: awsS3Endpoint(profile.region))
    require(base.scheme == "https" && base.host != null && base.rawQuery == null && base.rawFragment == null) {
        "S3 Endpoint 必须是无查询参数的 HTTPS 地址"
    }
    val path = base.rawPath.orEmpty().trimEnd('/')
    return if (profile.forcePathStyle || base.host.contains(':') || bucket.contains('.')) {
        "${base.scheme}://${base.rawAuthority}$path/${encodeS3ObjectKey(bucket)}/${encodeS3ObjectKey(key)}"
    } else {
        "${base.scheme}://$bucket.${base.rawAuthority}$path/${encodeS3ObjectKey(key)}"
    }
}

/** 配置保存前验证区域格式和端点结构；自定义端点允许 path，但不接受隐含凭据、查询和片段。 */
fun isS3ConnectionValid(region: String, endpoint: String): Boolean = try {
    awsS3Endpoint(region.trim())
    if (endpoint.isBlank()) true else {
        val uri = URI(endpoint.trim())
        uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.port in -1..65535
    }
} catch (_: IllegalArgumentException) {
    false
} catch (_: URISyntaxException) {
    false
}
