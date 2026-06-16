package com.susking.ephone_s.core.license

import android.content.Context

/**
 * 激活状态只读读取器。
 *
 * 职责：跨模块只读访问激活码，供意见反馈等功能自动携带激活信息。
 * 不提供写入能力（写入由 app 模块的 LicenseManager 独占），避免外围模块误操作。
 *
 * 设计要点：
 *  - 统一数据源：读取的 SharedPreferences 文件名与 app/LicenseManager 一致（PREFS_NAME）。
 *  - 单一常量：PREFS_NAME 由此处维护，app/LicenseManager 引用此常量，保证始终同步。
 *  - 只读语义：外部无法通过此类修改激活状态，写入权限由 LicenseManager 封装。
 */
object LicenseReader {

    /**
     * 激活状态专用的 SharedPreferences 文件名。
     * 此常量是激活数据的唯一数据源标识，app/LicenseManager 也引用此值。
     */
    const val PREFS_NAME: String = "license_prefs"

    private const val KEY_ACTIVATION_CODE: String = "activation_code"

    /**
     * 读取当前设备已保存的激活码。
     *
     * @param context 用于访问 SharedPreferences 的上下文
     * @return 激活码字符串，若未激活或无记录则返回空字符串
     */
    fun getActivationCode(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVATION_CODE, null).orEmpty()
    }
}
