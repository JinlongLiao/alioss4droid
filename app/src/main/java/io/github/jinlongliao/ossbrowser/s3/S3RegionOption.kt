package io.github.jinlongliao.ossbrowser.s3

import io.github.jinlongliao.ossbrowser.R

/** 区域选择项；展示名称本地化，保存和签名始终使用原始 AWS 区域代码。 */
data class S3RegionOption(
    /** AWS 标准区域代码，不含 endpoint 或 Bucket 名。 */
    val code: String,
    /** 三语言资源中的地区名称。 */
    val nameResource: Int
)

/**
 * 商业、中国与 GovCloud 分区的常规 S3 区域。选择项不代表账号已启用该区域。
 * 常用区域排在前面，新区域仍允许手动输入，不依赖启动时下载网络数据。
 * @see <a href="https://docs.aws.amazon.com/general/latest/gr/s3.html">AWS S3 区域端点</a>
 * @see <a href="https://docs.amazonaws.cn/general/latest/gr/s3.html">AWS 中国区域端点</a>
 */
val s3RegionOptions: List<S3RegionOption> = listOf(
    S3RegionOption("ap-northeast-1", R.string.s3_region_ap_northeast_1),
    S3RegionOption("ap-southeast-1", R.string.s3_region_ap_southeast_1),
    S3RegionOption("ap-east-1", R.string.s3_region_ap_east_1),
    S3RegionOption("us-east-1", R.string.s3_region_us_east_1),
    S3RegionOption("us-west-2", R.string.s3_region_us_west_2),
    S3RegionOption("af-south-1", R.string.s3_region_af_south_1),
    S3RegionOption("ap-east-2", R.string.s3_region_ap_east_2),
    S3RegionOption("ap-northeast-2", R.string.s3_region_ap_northeast_2),
    S3RegionOption("ap-northeast-3", R.string.s3_region_ap_northeast_3),
    S3RegionOption("ap-south-1", R.string.s3_region_ap_south_1),
    S3RegionOption("ap-south-2", R.string.s3_region_ap_south_2),
    S3RegionOption("ap-southeast-2", R.string.s3_region_ap_southeast_2),
    S3RegionOption("ap-southeast-3", R.string.s3_region_ap_southeast_3),
    S3RegionOption("ap-southeast-4", R.string.s3_region_ap_southeast_4),
    S3RegionOption("ap-southeast-5", R.string.s3_region_ap_southeast_5),
    S3RegionOption("ap-southeast-6", R.string.s3_region_ap_southeast_6),
    S3RegionOption("ap-southeast-7", R.string.s3_region_ap_southeast_7),
    S3RegionOption("ca-central-1", R.string.s3_region_ca_central_1),
    S3RegionOption("ca-west-1", R.string.s3_region_ca_west_1),
    S3RegionOption("cn-north-1", R.string.s3_region_cn_north_1),
    S3RegionOption("cn-northwest-1", R.string.s3_region_cn_northwest_1),
    S3RegionOption("eu-central-1", R.string.s3_region_eu_central_1),
    S3RegionOption("eu-central-2", R.string.s3_region_eu_central_2),
    S3RegionOption("eu-north-1", R.string.s3_region_eu_north_1),
    S3RegionOption("eu-south-1", R.string.s3_region_eu_south_1),
    S3RegionOption("eu-south-2", R.string.s3_region_eu_south_2),
    S3RegionOption("eu-west-1", R.string.s3_region_eu_west_1),
    S3RegionOption("eu-west-2", R.string.s3_region_eu_west_2),
    S3RegionOption("eu-west-3", R.string.s3_region_eu_west_3),
    S3RegionOption("il-central-1", R.string.s3_region_il_central_1),
    S3RegionOption("me-central-1", R.string.s3_region_me_central_1),
    S3RegionOption("me-south-1", R.string.s3_region_me_south_1),
    S3RegionOption("mx-central-1", R.string.s3_region_mx_central_1),
    S3RegionOption("sa-east-1", R.string.s3_region_sa_east_1),
    S3RegionOption("us-east-2", R.string.s3_region_us_east_2),
    S3RegionOption("us-west-1", R.string.s3_region_us_west_1),
    S3RegionOption("us-gov-east-1", R.string.s3_region_us_gov_east_1),
    S3RegionOption("us-gov-west-1", R.string.s3_region_us_gov_west_1),
)
