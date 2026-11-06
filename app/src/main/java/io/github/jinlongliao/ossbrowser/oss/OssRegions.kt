package io.github.jinlongliao.ossbrowser.oss

import io.github.jinlongliao.ossbrowser.R

/** OSS 公网地域选择项；仅用于表单，不改变旧配置的数据格式或 S3 签名地域。 */
data class OssRegionOption(
    /** 官方地域代码；不得与 AWS 同名地域混用。 */
    val code: String,
    /** 该地域的标准 HTTPS 公网 Endpoint，不含 Bucket 或对象路径。 */
    val endpoint: String,
    /** 本地化地域名称资源。 */
    val nameResource: Int
)

/**
 * 常规 OSS 公网地域，依据 2026-11-07 核对的官方列表；账号可用地域以控制台为准。
 * 金融云、内网、专有云和自定义域名继续通过 Endpoint 输入框填写。
 * @see <a href="https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints">OSS 地域与 Endpoint</a>
 */
val ossRegionOptions: List<OssRegionOption> = listOf(
    OssRegionOption("cn-hangzhou", "https://oss-cn-hangzhou.aliyuncs.com", R.string.oss_region_cn_hangzhou),
    OssRegionOption("cn-shanghai", "https://oss-cn-shanghai.aliyuncs.com", R.string.oss_region_cn_shanghai),
    OssRegionOption("cn-qingdao", "https://oss-cn-qingdao.aliyuncs.com", R.string.oss_region_cn_qingdao),
    OssRegionOption("cn-beijing", "https://oss-cn-beijing.aliyuncs.com", R.string.oss_region_cn_beijing),
    OssRegionOption("cn-zhangjiakou", "https://oss-cn-zhangjiakou.aliyuncs.com", R.string.oss_region_cn_zhangjiakou),
    OssRegionOption("cn-huhehaote", "https://oss-cn-huhehaote.aliyuncs.com", R.string.oss_region_cn_huhehaote),
    OssRegionOption("cn-wulanchabu", "https://oss-cn-wulanchabu.aliyuncs.com", R.string.oss_region_cn_wulanchabu),
    OssRegionOption("cn-shenzhen", "https://oss-cn-shenzhen.aliyuncs.com", R.string.oss_region_cn_shenzhen),
    OssRegionOption("cn-heyuan", "https://oss-cn-heyuan.aliyuncs.com", R.string.oss_region_cn_heyuan),
    OssRegionOption("cn-guangzhou", "https://oss-cn-guangzhou.aliyuncs.com", R.string.oss_region_cn_guangzhou),
    OssRegionOption("cn-chengdu", "https://oss-cn-chengdu.aliyuncs.com", R.string.oss_region_cn_chengdu),
    OssRegionOption("cn-wuhan-lr", "https://oss-cn-wuhan-lr.aliyuncs.com", R.string.oss_region_cn_wuhan_lr),
    OssRegionOption("cn-zhongwei", "https://oss-cn-zhongwei.aliyuncs.com", R.string.oss_region_cn_zhongwei),
    OssRegionOption("cn-hongkong", "https://oss-cn-hongkong.aliyuncs.com", R.string.oss_region_cn_hongkong),
    OssRegionOption("ap-northeast-1", "https://oss-ap-northeast-1.aliyuncs.com", R.string.oss_region_ap_northeast_1),
    OssRegionOption("ap-northeast-2", "https://oss-ap-northeast-2.aliyuncs.com", R.string.oss_region_ap_northeast_2),
    OssRegionOption("ap-southeast-1", "https://oss-ap-southeast-1.aliyuncs.com", R.string.oss_region_ap_southeast_1),
    OssRegionOption("ap-southeast-3", "https://oss-ap-southeast-3.aliyuncs.com", R.string.oss_region_ap_southeast_3),
    OssRegionOption("ap-southeast-5", "https://oss-ap-southeast-5.aliyuncs.com", R.string.oss_region_ap_southeast_5),
    OssRegionOption("ap-southeast-6", "https://oss-ap-southeast-6.aliyuncs.com", R.string.oss_region_ap_southeast_6),
    OssRegionOption("ap-southeast-7", "https://oss-ap-southeast-7.aliyuncs.com", R.string.oss_region_ap_southeast_7),
    OssRegionOption("ap-southeast-8", "https://oss-ap-southeast-8.aliyuncs.com", R.string.oss_region_ap_southeast_8),
    OssRegionOption("eu-central-1", "https://oss-eu-central-1.aliyuncs.com", R.string.oss_region_eu_central_1),
    OssRegionOption("eu-west-1", "https://oss-eu-west-1.aliyuncs.com", R.string.oss_region_eu_west_1),
    OssRegionOption("eu-west-2", "https://oss-eu-west-2.aliyuncs.com", R.string.oss_region_eu_west_2),
    OssRegionOption("us-west-1", "https://oss-us-west-1.aliyuncs.com", R.string.oss_region_us_west_1),
    OssRegionOption("us-east-1", "https://oss-us-east-1.aliyuncs.com", R.string.oss_region_us_east_1),
    OssRegionOption("na-south-1", "https://oss-na-south-1.aliyuncs.com", R.string.oss_region_na_south_1),
    OssRegionOption("sa-east-1", "https://oss-sa-east-1.aliyuncs.com", R.string.oss_region_sa_east_1),
    OssRegionOption("me-east-1", "https://oss-me-east-1.aliyuncs.com", R.string.oss_region_me_east_1),
)

/**
 * 仅识别完整标准公网地址。允许旧配置省略协议或使用 HTTP、末尾斜杠，函数不修改原值。
 * 含路径、端口、查询参数、Bucket 前缀或内网域名的地址保持自定义状态，避免误替换部署信息。
 * @param endpoint 表单当前地址。
 * @return 对应的预置地域；非标准地址返回 null。
 */
fun findOssRegionByEndpoint(endpoint: String): OssRegionOption? {
    val address = endpoint.trim().removeSuffix("/")
    return ossRegionOptions.firstOrNull { option ->
        val host = option.endpoint.removePrefix("https://")
        address.equals(host, ignoreCase = true) ||
            address.equals(option.endpoint, ignoreCase = true) ||
            address.equals("http://$host", ignoreCase = true)
    }
}
