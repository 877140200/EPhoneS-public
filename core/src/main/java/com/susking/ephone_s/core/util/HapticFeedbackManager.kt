package com.susking.ephone_s.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

object HapticFeedbackManager {

    private const val TAG = "HapticFeedbackManager"

    // 定义振动类型
    enum class VibrationType(val duration: Long) {
        SHORT(15L),
        LONG(400L)
    }

    /**
     * 根据指定的类型触发振动反馈。
     * 在支持的设备上使用现代的、预定义的触感效果,否则回退到基于时长的振动。
     *
     * @param context 上下文环境。
     * @param type 振动类型 (SHORT or LONG)。
     */
    fun performVibration(context: Context, type: VibrationType) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator hardware found on this device.")
            return
        }

        // 在较新的设备上,优先使用预定义的触感效果,以获得更佳的用户体验和可靠性。
        // 旧版振动(OneShot)可能会被系统的"触摸反馈"设置禁用。
        if (type == VibrationType.SHORT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 使用现代的、预定义的点击效果
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
            Log.d(TAG, "Performed modern vibration: EFFECT_CLICK")
        } else {
            // 对于长振动或旧设备,回退到基于时长的振动。
            performLegacyVibration(vibrator, type.duration)
        }
    }

    /**
     * 执行安卓原生振动。
     */
    private fun performLegacyVibration(vibrator: Vibrator, duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        Log.d(TAG, "Performed legacy vibration for ${duration}ms.")
    }
}