package com.susking.ephone_s.cphone.domain.model

/**
 * CPhone中的App类型枚举
 */
enum class CPhoneAppType {
    ALBUM,      // 相册
    BROWSER,    // 浏览器
    TAOBAO,     // 淘宝
    MEMO,       // 备忘录
    DIARY,      // 日记
    AMAP,       // 高德地图
    USAGE,      // App使用记录
    MUSIC,      // 音乐播放器
    QQ          // 模拟QQ
}

/**
 * CPhone中的App数据模型
 */
data class CPhoneApp(
    val type: CPhoneAppType,
    val name: String,
    val iconResId: Int
)