package io.github.jinlongliao.ossbrowser.s3

import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import java.io.File

/** 可重放的文件分片流；SDK 重试重新打开同一范围，不把大文件加载到内存。 */
internal class S3FileRangeStream(
    /** 任务持有的不可变快照。 */
    private val file: File,
    /** 分片起始位置，单位字节。 */
    private val start: Long,
    /** 分片长度，单位字节。 */
    override val contentLength: Long
) : ByteStream.SourceStream() {
    /** 相同请求重试可以重新读取固定快照。 */
    override val isOneShot: Boolean = false

    /** SDK 负责关闭每次打开的流，结束位置为包含边界。 */
    override fun readFrom(): SdkSource = file.source(start, start + contentLength - 1)
}
