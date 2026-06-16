package com.susking.ephone_s.aidata.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.AlbumDatabase
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.aidata.data.local.BrainDatabase
import com.susking.ephone_s.aidata.data.local.CPhoneDatabase
import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.data.local.WorldBookDatabase
import java.io.File

/**
 * 导入回滚快照助手
 *
 * 设计目标：在任何导入开始前，把当前所有数据库文件 + SharedPreferences 复制成一份快照；
 * 导入任一环节失败时，用快照覆盖回原位，使数据回到导入前的状态。
 *
 * 可靠性边界（必须诚实记录，没有任何方案是绝对的）：
 * 1. 创建快照本身失败（磁盘空间不足 / IO 错误）→ 导入直接中止、根本不开始，旧数据未被触碰，最安全。
 * 2. 恢复覆盖到一半进程被系统杀死 → 用「先复制到临时文件，再原子 rename」把危险窗口压到文件系统单次
 *    rename 的级别（同分区 rename 是原子的，要么完整旧文件要么完整新文件，不存在覆盖一半的中间态）。
 * 3. WAL 日志未完全落盘 → 创建快照前对所有数据库执行 wal_checkpoint(TRUNCATE)，确保主 .db 文件完整、
 *    wal 为空；恢复时直接删除 -wal/-shm，让 SQLite 基于完整主库重建。
 *
 * 由于数据库连接与 SharedPreferences 在内存中均有缓存，恢复文件后必须重启进程才能让缓存失效、
 * 读到恢复后的旧数据。故恢复与重启拆分为两步：[restoreSnapshot] 仅覆盖文件，[restartApp] 由 UI 层
 * 在向用户弹出「数据已恢复，即将重启」提示并得到确认后调用。这样用户对重启有预期，且能避免在
 * 恢复完成到重启之间误触其它功能（此空档内旧连接仍指向已被覆盖的文件）。
 */
class BackupSnapshotHelper(private val context: Context) {

    companion object {
        private const val TAG = "BackupSnapshotHelper"

        /** 快照目录前缀，位于 cacheDir 下，用完即删。 */
        private const val SNAPSHOT_DIR_PREFIX = "import_snapshot_"

        /** 恢复时临时文件后缀，复制完成后原子 rename 去掉它。 */
        private const val RESTORE_TMP_SUFFIX = ".restore_tmp"

        /** SQLite WAL / SHM 伴随文件后缀。 */
        private val DB_SIDECAR_SUFFIXES: List<String> = listOf("-wal", "-shm")

        /** 参与备份的所有数据库主文件名（与各 Room databaseBuilder 中的名称一致）。 */
        private val DATABASE_NAMES: List<String> = listOf(
            "aidata_database",
            "alipay_database",
            "album_database",
            "cphone_database",
            "worldbook_database",
            "shopping_database",
            "brain_database"
        )

        /** 重启延迟（毫秒），留出极短时间让 System.exit 完成进程清理。 */
        private const val RESTART_DELAY_MILLIS: Long = 150L
    }

    /**
     * 创建快照：checkpoint 所有数据库 → 复制 databases/ 与 shared_prefs/ 到快照目录。
     *
     * @return 成功返回快照目录；失败抛异常（调用方应据此中止导入，不要继续）。
     */
    fun createSnapshot(): File {
        val snapshotDir = File(context.cacheDir, "$SNAPSHOT_DIR_PREFIX${System.currentTimeMillis()}")
        if (!snapshotDir.mkdirs()) {
            throw IllegalStateException("无法创建快照目录: ${snapshotDir.absolutePath}")
        }

        // 1. checkpoint 所有数据库，把 WAL 中的数据落盘到主 .db 文件
        checkpointAllDatabases()

        // 2. 复制数据库文件
        val databasesDir: File = resolveDatabasesDir()
        val snapshotDatabasesDir = File(snapshotDir, "databases").apply { mkdirs() }
        copyDatabaseFiles(databasesDir, snapshotDatabasesDir)

        // 3. 复制 SharedPreferences 目录
        val sharedPrefsDir: File = resolveSharedPrefsDir()
        if (sharedPrefsDir.exists()) {
            val snapshotPrefsDir = File(snapshotDir, "shared_prefs").apply { mkdirs() }
            sharedPrefsDir.listFiles()?.forEach { prefFile ->
                if (prefFile.isFile) {
                    prefFile.copyTo(File(snapshotPrefsDir, prefFile.name), overwrite = true)
                }
            }
        }

        Log.d(TAG, "快照创建完成: ${snapshotDir.absolutePath}")
        return snapshotDir
    }

    /**
     * 恢复快照（仅覆盖文件，不重启）。
     *
     * 用「复制到临时文件 → 原子 rename」覆盖回数据库与 SharedPreferences。覆盖完成后，内存中的
     * 旧连接 / 旧缓存仍然有效且已与磁盘不一致，调用方必须随后引导用户重启进程（见 [restartApp]）。
     *
     * @param snapshotDir [createSnapshot] 返回的快照目录。
     * @return 恢复全程无异常返回 true；中途出错返回 false（调用方据此仍应重启，但可提示用户数据可能不完整）。
     */
    fun restoreSnapshot(snapshotDir: File): Boolean {
        return try {
            // 1. 恢复数据库文件
            val snapshotDatabasesDir = File(snapshotDir, "databases")
            if (snapshotDatabasesDir.exists()) {
                val databasesDir: File = resolveDatabasesDir()
                restoreDatabaseFiles(snapshotDatabasesDir, databasesDir)
            }

            // 2. 恢复 SharedPreferences
            val snapshotPrefsDir = File(snapshotDir, "shared_prefs")
            if (snapshotPrefsDir.exists()) {
                val sharedPrefsDir: File = resolveSharedPrefsDir().apply { mkdirs() }
                restorePrefsFiles(snapshotPrefsDir, sharedPrefsDir)
            }

            Log.d(TAG, "快照恢复完成")
            true
        } catch (e: Exception) {
            // 恢复阶段出错是最危险的情形，记入日志便于排查，返回 false 让调用方据实告知用户。
            Log.e(TAG, "快照恢复过程中出错", e)
            false
        }
    }

    /**
     * 丢弃快照：导入成功后清理快照目录，释放空间。
     */
    fun discardSnapshot(snapshotDir: File) {
        if (snapshotDir.exists()) {
            val deleted: Boolean = snapshotDir.deleteRecursively()
            Log.d(TAG, "快照清理${if (deleted) "成功" else "失败"}: ${snapshotDir.absolutePath}")
        }
    }

    /**
     * 对所有数据库执行 wal_checkpoint(TRUNCATE)，确保主 .db 文件包含全部已提交数据。
     *
     * 调用各单例 getter 会强制初始化尚未创建的数据库；导入流程本就会用到它们，故可接受。
     * 单个数据库 checkpoint 失败不应阻断整个快照，仅记录日志。
     */
    private fun checkpointAllDatabases() {
        val databases = listOf(
            runCatching { AiDataDatabase.getDatabase(context) }.getOrNull(),
            runCatching { AlipayDatabase.getDatabase(context) }.getOrNull(),
            runCatching { AlbumDatabase.getDatabase(context) }.getOrNull(),
            runCatching { CPhoneDatabase.getDatabase(context) }.getOrNull(),
            runCatching { WorldBookDatabase.getDatabase(context) }.getOrNull(),
            runCatching { ShoppingDatabase.getDatabase(context) }.getOrNull(),
            runCatching { BrainDatabase.getInstance(context) }.getOrNull()
        )
        databases.forEach { database ->
            if (database == null) return@forEach
            runCatching {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    cursor.moveToFirst()
                }
            }.onFailure { error ->
                Log.w(TAG, "数据库 checkpoint 失败，继续快照其余部分", error)
            }
        }
    }

    /**
     * 复制 databases/ 下所有相关数据库文件（含 -wal/-shm）到快照目录。
     */
    private fun copyDatabaseFiles(databasesDir: File, snapshotDatabasesDir: File) {
        DATABASE_NAMES.forEach { dbName ->
            collectDatabaseFileNames(dbName).forEach { fileName ->
                val sourceFile = File(databasesDir, fileName)
                if (sourceFile.exists() && sourceFile.isFile) {
                    sourceFile.copyTo(File(snapshotDatabasesDir, fileName), overwrite = true)
                }
            }
        }
    }

    /**
     * 用快照中的数据库文件覆盖回原位：先复制到临时文件，再原子 rename。
     * 恢复主 .db 后删除原 -wal/-shm，避免旧日志污染恢复后的库。
     */
    private fun restoreDatabaseFiles(snapshotDatabasesDir: File, databasesDir: File) {
        databasesDir.mkdirs()
        DATABASE_NAMES.forEach { dbName ->
            val snapshotMainDb = File(snapshotDatabasesDir, dbName)
            if (!snapshotMainDb.exists()) return@forEach

            // 先恢复主 .db（原子 rename）
            replaceFileAtomically(snapshotMainDb, File(databasesDir, dbName))

            // 删除原 -wal/-shm，让 SQLite 基于恢复后的完整主库重建
            DB_SIDECAR_SUFFIXES.forEach { suffix ->
                val sidecar = File(databasesDir, "$dbName$suffix")
                if (sidecar.exists()) {
                    sidecar.delete()
                }
            }
        }
    }

    /**
     * 恢复 SharedPreferences 文件：先删除当前 prefs，再用快照原子 rename 回去。
     */
    private fun restorePrefsFiles(snapshotPrefsDir: File, sharedPrefsDir: File) {
        // 删除当前所有 prefs，避免快照中已删除的 prefs 残留
        sharedPrefsDir.listFiles()?.forEach { current ->
            if (current.isFile) current.delete()
        }
        snapshotPrefsDir.listFiles()?.forEach { snapshotPref ->
            if (snapshotPref.isFile) {
                replaceFileAtomically(snapshotPref, File(sharedPrefsDir, snapshotPref.name))
            }
        }
    }

    /**
     * 原子替换：把 [source] 复制成 [target] 同目录下的临时文件，再 rename 覆盖 [target]。
     * 同分区 rename 是原子操作，杜绝「覆盖一半」的中间态。
     */
    private fun replaceFileAtomically(source: File, target: File) {
        val tmpFile = File(target.parentFile, "${target.name}$RESTORE_TMP_SUFFIX")
        source.copyTo(tmpFile, overwrite = true)
        if (!tmpFile.renameTo(target)) {
            // 极少数文件系统 rename 失败时退化为直接复制，仍尽力恢复
            tmpFile.copyTo(target, overwrite = true)
            tmpFile.delete()
            Log.w(TAG, "原子 rename 失败，已退化为直接复制: ${target.name}")
        }
    }

    /**
     * 返回某数据库主文件及其 WAL/SHM 伴随文件名列表。
     */
    private fun collectDatabaseFileNames(dbName: String): List<String> {
        return listOf(dbName) + DB_SIDECAR_SUFFIXES.map { "$dbName$it" }
    }

    /**
     * 解析 databases/ 目录。借助 getDatabasePath 取任一数据库路径，其父目录即 databases/。
     */
    private fun resolveDatabasesDir(): File {
        return context.getDatabasePath(DATABASE_NAMES.first()).parentFile
            ?: File(context.applicationInfo.dataDir, "databases")
    }

    /**
     * 解析 shared_prefs/ 目录（位于 dataDir 下）。
     */
    private fun resolveSharedPrefsDir(): File {
        return File(context.applicationInfo.dataDir, "shared_prefs")
    }

    /**
     * 重启 App：通过 AlarmManager 调度启动 Intent，随后结束当前进程。
     *
     * 由 UI 层在「数据已恢复」提示框确认后调用，确保内存中的旧数据库连接 / 旧缓存随进程一并失效。
     */
    fun restartApp() {
        val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent == null) {
            Log.e(TAG, "无法获取启动 Intent，直接退出进程")
            System.exit(0)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + RESTART_DELAY_MILLIS,
            pendingIntent
        )
        System.exit(0)
    }
}
