package com.susking.ephone_s.settings.update

import com.google.gson.annotations.SerializedName

/**
 * 版本信息数据类。
 *
 * 对应 Cloudflare Worker /version 接口返回的 JSON，描述线上最新版本。
 * APK 实际文件托管在 Gitee Releases，这里只携带其下载地址。
 *
 * 约定 JSON 格式：
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1",
 *   "changelog": "修复若干问题\n新增意见反馈",
 *   "downloadUrl": "https://gitee.com/xxx/releases/download/v1.1/app-release.apk",
 *   "forceUpdate": false
 * }
 */
data class UpdateInfo(
    /** 线上最新版本号，与本地 PackageInfo.versionCode 比对，更大则有新版。 */
    @SerializedName("versionCode")
    val versionCode: Long,

    /** 线上最新版本名称，用于展示，如 "1.1"。 */
    @SerializedName("versionName")
    val versionName: String,

    /** 更新日志，多行用 \n 分隔，展示在更新弹窗中。 */
    @SerializedName("changelog")
    val changelog: String,

    /** APK 下载地址，指向 Gitee Releases 附件。 */
    @SerializedName("downloadUrl")
    val downloadUrl: String,

    /** 是否强制更新（预留字段，当前仅作提示，不强制阻断）。 */
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = false
)

/**
 * 检查更新的结果。
 */
sealed class UpdateCheckResult {

    /** 发现新版本，携带版本信息。 */
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()

    /** 当前已是最新版本。 */
    data object NoUpdate : UpdateCheckResult()

    /** 检查失败（网络异常、解析失败），[message] 为简要原因。 */
    data class Error(val message: String) : UpdateCheckResult()
}
