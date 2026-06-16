package com.susking.ephone_s.settings.update

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新流程协调器（面向 UI）。
 *
 * 把 [UpdateChecker]（检查）与 [ApkInstaller]（下载安装）串联成完整的用户流程，
 * 对外只暴露一个入口 [checkForUpdate]，由它负责弹窗、权限引导与提示。
 *
 * 两种模式：
 *  - 静默（isSilent = true）：app 启动时自动检查，仅在「发现新版」时弹窗，
 *    无新版或检查失败都安静忽略，不打扰用户。
 *  - 手动（isSilent = false）：用户在「关于」页主动点检查，全程给反馈
 *    （检查中 / 已是最新 / 失败均有提示）。
 */
class UpdateManager(private val activity: AppCompatActivity) {

    private val updateChecker: UpdateChecker = UpdateChecker()
    private val apkInstaller: ApkInstaller = ApkInstaller(activity)

    /**
     * 发起更新检查。
     *
     * @param isSilent 是否静默模式：true 仅在有新版时弹窗，false 全程提示
     */
    fun checkForUpdate(isSilent: Boolean) {
        if (!isSilent) {
            Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        }
        activity.lifecycleScope.launch {
            val result: UpdateCheckResult = updateChecker.checkForUpdate(activity)
            handleCheckResult(result, isSilent)
        }
    }

    /**
     * 处理检查结果。
     *
     * @param result 检查结果
     * @param isSilent 是否静默模式，决定无新版/失败时是否提示
     */
    private fun handleCheckResult(result: UpdateCheckResult, isSilent: Boolean) {
        when (result) {
            is UpdateCheckResult.UpdateAvailable -> showUpdateDialog(result.info)
            is UpdateCheckResult.NoUpdate -> {
                if (!isSilent) {
                    Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show()
                }
            }
            is UpdateCheckResult.Error -> {
                if (!isSilent) {
                    Toast.makeText(activity, "检查更新失败：${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 弹出新版本提示对话框，展示更新日志。
     *
     * @param info 线上版本信息
     */
    private fun showUpdateDialog(info: UpdateInfo) {
        if (activity.isFinishing) {
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${info.versionName}")
            .setMessage(info.changelog.ifBlank { "建议更新到最新版本" })
            .setPositiveButton("立即更新") { dialog, _ ->
                dialog.dismiss()
                startUpdateFlow(info)
            }
            .setNegativeButton("以后再说", null)
            .setCancelable(!info.forceUpdate)
            .show()
    }

    /**
     * 开始更新流程：先确保具备安装权限，再下载。
     *
     * @param info 线上版本信息
     */
    private fun startUpdateFlow(info: UpdateInfo) {
        if (!apkInstaller.canInstallPackages()) {
            promptInstallPermission()
            return
        }
        Toast.makeText(activity, "开始下载，完成后将自动弹出安装", Toast.LENGTH_LONG).show()
        apkInstaller.startDownload(
            downloadUrl = info.downloadUrl,
            versionName = info.versionName,
            onComplete = { apkFile -> onDownloadComplete(apkFile) }
        )
    }

    /**
     * 下载完成回调：成功则调起安装，失败则提示。
     *
     * @param apkFile 下载得到的 APK 文件，失败为 null
     */
    private fun onDownloadComplete(apkFile: File?) {
        if (apkFile != null && apkFile.exists()) {
            apkInstaller.installApk(apkFile)
        } else {
            Toast.makeText(activity, "更新包下载失败，请稍后重试", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 引导用户去系统设置开启「安装未知应用」权限。
     */
    private fun promptInstallPermission() {
        AlertDialog.Builder(activity)
            .setTitle("需要安装权限")
            .setMessage("为了安装更新包，请在接下来的系统设置中允许本应用安装未知应用，开启后再次点击检查更新即可。")
            .setPositiveButton("去开启") { dialog, _ ->
                dialog.dismiss()
                val intent: Intent = apkInstaller.buildUnknownSourceSettingsIntent()
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
