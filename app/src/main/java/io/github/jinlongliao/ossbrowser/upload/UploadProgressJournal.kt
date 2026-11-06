package io.github.jinlongliao.ossbrowser.upload

import kotlinx.serialization.Serializable

/** 单任务进度绑定具体已提交主快照，避免同次执行内主快照重新基线后套用未删除的旧 journal。 */
@Serializable
internal data class UploadProgressJournal(
    /** 主快照中该任务完整 JSON 的 SHA-256，不含额外凭据。 */
    val baseFingerprint: String,
    /** 同代次 RUNNING 的最新字节进度。 */
    val task: UploadTask
)
