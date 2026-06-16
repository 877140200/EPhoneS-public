package com.susking.ephone_s.settings.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.susking.ephone_s.core.config.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 更新检查器。
 *
 * 从 Cloudflare Worker 的 /version 接口拉取线上最新版本信息，与本地当前
 * versionCode 比对，判断是否有新版本可供更新。
 *
 * 设计要点：
 *  - 当前版本号通过 PackageManager 读取，不依赖 BuildConfig，避免模块间耦合。
 *  - 免费 workers.dev 国内访问不稳，检查失败时归入 [UpdateCheckResult.Error]，
 *    由调用方决定静默忽略（自动检查）还是提示（手动检查）。
 */
class UpdateChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    private val gson: Gson = Gson()

    /**
     * 执行更新检查。
     *
     * @param context 用于读取当前应用版本号
     * @return 检查结果：有新版 / 已最新 / 失败
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersionCode: Long = getCurrentVersionCode(context)
        val request: Request = Request.Builder()
            .url(RemoteConfig.versionUrl)
            .get()
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                val rawBody: String? = response.body?.string()
                if (rawBody.isNullOrBlank()) {
                    return@use UpdateCheckResult.Error("服务器无响应（HTTP ${response.code}）")
                }
                parseAndCompare(rawBody, currentVersionCode)
            }
        } catch (e: IOException) {
            UpdateCheckResult.Error(e.message ?: "网络连接失败")
        }
    }

    /**
     * 解析版本信息并与当前版本比对。
     *
     * @param rawBody 接口返回的 JSON 文本
     * @param currentVersionCode 本地当前版本号
     * @return 比对后的更新结果
     */
    private fun parseAndCompare(rawBody: String, currentVersionCode: Long): UpdateCheckResult {
        return try {
            val info: UpdateInfo = gson.fromJson(rawBody, UpdateInfo::class.java)
            if (info.versionCode > currentVersionCode) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.NoUpdate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("版本信息解析失败：${e.message}")
        }
    }

    /**
     * 读取当前应用的 versionCode。
     *
     * @param context 上下文
     * @return 当前版本号，读取失败时返回 0（视为有新版，倾向提示更新）
     */
    private fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }
    }
}
