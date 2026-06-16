package com.susking.ephone_s.core.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * 设备指纹生成器。
 *
 * 基于 [Settings.Secure.ANDROID_ID] 派生出一个稳定的设备标识，用于一设备一码验证。
 * app 在激活时自动携带该指纹，网友无需手动复制粘贴，从而保持"一步输码"体验。
 *
 * 已知局限（已与产品确认接受）：
 *  - 恢复出厂设置、刷机后 ANDROID_ID 会变，导致已激活设备掉激活，需在 Worker 后台清除旧绑定。
 *  - Android 8 起 ANDROID_ID 与「应用签名密钥」绑定，更换签名密钥重新打包会使所有设备指纹变化。
 *
 * 模块位置：从 app 下沉到 core，供激活流程（app 模块）和意见反馈（settings 模块）共享。
 */
object DeviceFingerprint {

    /** SHA-256 摘要算法名称。 */
    private const val HASH_ALGORITHM: String = "SHA-256"

    /** 指纹截取长度（十六进制字符数），16 位足够区分内测设备且便于传输。 */
    private const val FINGERPRINT_LENGTH: Int = 16

    /**
     * 获取当前设备的稳定指纹。
     *
     * @param context 用于读取系统设置的上下文
     * @return 十六进制字符串形式的设备指纹
     */
    @SuppressLint("HardwareIds")
    fun getFingerprint(context: Context): String {
        val androidId: String = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
        return hashToHex(androidId).take(FINGERPRINT_LENGTH)
    }

    /**
     * 将输入字符串做 SHA-256 摘要并转为十六进制。
     *
     * @param input 原始字符串
     * @return 十六进制摘要字符串
     */
    private fun hashToHex(input: String): String {
        val digest: MessageDigest = MessageDigest.getInstance(HASH_ALGORITHM)
        val bytes: ByteArray = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
