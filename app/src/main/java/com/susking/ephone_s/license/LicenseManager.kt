package com.susking.ephone_s.license

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.susking.ephone_s.core.license.LicenseReader

/**
 * 本地激活状态管理器。
 *
 * 职责：把"这台设备是否已激活"这一状态持久化到一个**独立**的 SharedPreferences
 * 文件（[LicenseReader.PREFS_NAME]）中，并对外提供简单的读写判断。
 *
 * 设计要点：
 *  - 激活状态故意存在独立的 prefs 文件里，且**不**纳入数据导出名单
 *    （见 ExportCompleteAppDataUseCase）。否则网友导出数据、换设备导入后
 *    就等于复制了激活，绕过"一设备一码"的限制。
 *  - 这里只记录"本地已通过验证"的结果；真正的"一码绑一台设备"账本在
 *    Cloudflare Worker 的 KV 中，本地仅作为激活成功后的缓存，避免每次启动都联网。
 *  - 数据源常量由 core/LicenseReader 统一维护，此处引用保证同步。
 */
class LicenseManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(LicenseReader.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 当前是否为可调试构建（debug 包）。
     * 由构建类型自动决定：debug 包为 true，release 包为 false，无需任何 gradle 配置。
     * 用于让本机调试设备跳过联网激活，正式发布给网友的 release 包不受影响。
     */
    private val isDebuggable: Boolean =
        (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * 判断当前设备是否已激活。
     * 已激活后平时启动 app 不再需要联网验证。
     *
     * debug 包直接放行：方便本机调试设备在无网络环境下使用，
     * 该分支在 release 包中恒为 false，不影响正式激活逻辑。
     */
    fun isActivated(): Boolean {
        if (isDebuggable) return true
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
    }

    /**
     * 保存激活成功的结果。
     * @param activationCode 验证通过的激活码，留存以便排查问题
     */
    fun saveActivated(activationCode: String) {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVATED, true)
            .putString(KEY_ACTIVATION_CODE, activationCode)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * 清除本地激活状态。
     * 仅用于调试或后续"强制重新激活"需求，正常流程不调用。
     */
    fun clearActivation() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_IS_ACTIVATED: String = "is_activated"
        private const val KEY_ACTIVATION_CODE: String = "activation_code"
        private const val KEY_ACTIVATED_AT: String = "activated_at"
    }
}
