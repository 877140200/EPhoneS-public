package com.susking.ephone_s.settings.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 下载与安装器。
 *
 * 用 [DownloadManager] 把 Gitee Releases 上的 APK 下载到 app 外部私有目录的 Download 下，
 * 下载完成后通过 [FileProvider] 暴露文件并调起系统安装器。
 *
 * 设计要点：
 *  - 下载目录对应 provider_paths.xml 中的 external-files-path「Download/」，无需额外存储权限。
 *  - FileProvider authority 必须与 app 模块 AndroidManifest 中声明的一致（[FILE_PROVIDER_AUTHORITY]）。
 *  - Android 8（O）起安装未知来源应用需用户授权，调用方应先检查 [canInstallPackages]。
 */
class ApkInstaller(context: Context) {

    private val appContext: Context = context.applicationContext

    private val downloadManager: DownloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** 本次下载任务的 ID，用于在完成广播中比对。 */
    private var currentDownloadId: Long = -1L

    /** 下载完成监听器，下载结束后回调（成功传安装用的 File，失败传 null）。 */
    private var onDownloadComplete: ((File?) -> Unit)? = null

    /** 监听 DownloadManager 完成广播的接收器。 */
    private val downloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiver: Context?, intent: Intent?) {
            val completedId: Long = intent?.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID,
                -1L
            ) ?: -1L
            if (completedId == currentDownloadId) {
                handleDownloadFinished(completedId)
            }
        }
    }

    /**
     * 开始下载 APK。
     *
     * @param downloadUrl APK 下载地址（Gitee Releases 附件）
     * @param versionName 版本名，用于命名下载文件
     * @param onComplete 下载完成回调：成功返回 APK File，失败返回 null
     */
    fun startDownload(
        downloadUrl: String,
        versionName: String,
        onComplete: (File?) -> Unit
    ) {
        onDownloadComplete = onComplete
        registerReceiver()

        val fileName: String = buildFileName(versionName)
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("小手机更新包 $versionName")
            setDescription("正在下载新版本…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            setMimeType(APK_MIME_TYPE)
        }
        currentDownloadId = downloadManager.enqueue(request)
    }

    /**
     * 处理下载完成事件：校验下载状态，成功则回调安装文件。
     *
     * @param downloadId 完成的下载任务 ID
     */
    private fun handleDownloadFinished(downloadId: Long) {
        unregisterReceiver()
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex: Int = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status: Int = cursor.getInt(statusIndex)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUriIndex: Int = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri: String? = cursor.getString(localUriIndex)
                    val file: File? = localUri?.let { File(Uri.parse(it).path ?: "") }
                    onDownloadComplete?.invoke(file)
                    return
                }
            }
        }
        onDownloadComplete?.invoke(null)
    }

    /**
     * 调起系统安装器安装指定 APK。
     *
     * @param apkFile 已下载完成的 APK 文件
     */
    fun installApk(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            appContext,
            FILE_PROVIDER_AUTHORITY,
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(installIntent)
    }

    /**
     * 判断当前是否已具备安装未知来源应用的权限。
     * Android 8（O）以下默认允许；O 及以上需用户在系统设置中授权本应用。
     *
     * @return 是否可直接安装
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * 构造跳转到「安装未知应用」系统设置页的 Intent，供调用方引导用户授权。
     *
     * @return 设置页 Intent
     */
    fun buildUnknownSourceSettingsIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    /** 注册下载完成广播接收器。Android 13+ 需显式声明导出标志。 */
    private fun registerReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            appContext,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /** 注销下载完成广播接收器，避免泄漏。 */
    private fun unregisterReceiver() {
        try {
            appContext.unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            // 接收器可能已注销，忽略即可
        }
    }

    /**
     * 生成下载文件名，避免特殊字符引发路径问题。
     *
     * @param versionName 版本名
     * @return 形如 ephone_s_1.1.apk 的文件名
     */
    private fun buildFileName(versionName: String): String {
        val safeVersion: String = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "ephone_s_$safeVersion.apk"
    }

    companion object {
        /**
         * FileProvider authority，必须与 app 模块 AndroidManifest 中
         * FileProvider 声明的 authorities 完全一致。
         */
        const val FILE_PROVIDER_AUTHORITY: String = "com.susking.ephone_s.provider"

        private const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"
    }
}
