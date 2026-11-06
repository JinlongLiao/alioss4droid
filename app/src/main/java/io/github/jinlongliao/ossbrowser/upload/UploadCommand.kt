package io.github.jinlongliao.ossbrowser.upload

/** 服务内部的串行命令，不含账号凭据；生命周期由已启动的前台服务持有。 */
internal sealed interface UploadCommand {
    /** 记录提交时固定的任务参数，先落盘再执行。 */
    data class Enqueue(
        /** 提交时捕获的任务参数，不含账号凭据。 */
        val task: UploadTask
    ) : UploadCommand
    /** 仅标识待取消任务，不修改任务目标。 */
    data class Cancel(
        /** 待取消的唯一任务 ID。 */
        val taskId: String
    ) : UploadCommand
}
