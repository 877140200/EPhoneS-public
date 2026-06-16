package com.susking.ephone_s.aidata.domain.model.import_export

/**
 * 导入结果三态模型。
 *
 * 取代原先笼统的 [Result]，让 UI 层能区分三种本质不同的失败语义，从而给出正确的善后提示：
 *
 * - [Success]：导入成功，快照已清理。
 * - [Failed]：导入「尚未开始」就失败（如快照创建失败、文件无法打开），用户原有数据未被触碰，
 *   无需回滚也无需重启。
 * - [RolledBack]：导入开始后中途失败，已用快照把数据回滚到导入前状态。由于内存中仍持有旧的
 *   数据库连接与缓存，必须重启 App 才能读到恢复后的数据。UI 层应弹框告知并在确认后调用
 *   [com.susking.ephone_s.aidata.util.BackupSnapshotHelper.restartApp]。
 *
 * @param T 成功时携带的载荷类型（完整备份导入为 String 提示语，交互式导入为 ImportResult）。
 */
sealed class ImportOutcome<out T> {

    /**
     * 导入成功。
     * @param data 成功载荷（提示语或导入统计）。
     */
    data class Success<out T>(val data: T) : ImportOutcome<T>()

    /**
     * 导入未开始即失败，原有数据完好无损。
     * @param error 失败原因。
     */
    data class Failed(val error: Throwable) : ImportOutcome<Nothing>()

    /**
     * 导入中途失败，数据已回滚，需要重启 App。
     * @param error 导致回滚的原始失败原因。
     * @param restoredCleanly 快照恢复是否全程无异常；false 表示恢复过程本身也出了错，
     *        数据可能仍不完整，提示语应更谨慎。
     */
    data class RolledBack(
        val error: Throwable,
        val restoredCleanly: Boolean
    ) : ImportOutcome<Nothing>()
}
