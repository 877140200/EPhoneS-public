package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 时间敏感度配置
 * 根据角色性格定制时间感知行为
 * 
 * @property needsSleep 是否需要睡觉(机器人/神仙可设为false)
 * @property longTimeNoContactThreshold 多少小时算"很久没联系"
 * @property responseUrgencyLevel 回复紧迫性: 1=慢热 2=正常 3=秒回
 */
@Parcelize
data class TimeSensitivityConfig(
    val needsSleep: Boolean = true,                    // 是否需要睡觉(机器人/神仙可设为false)
    val longTimeNoContactThreshold: Int = 24,          // 多少小时算"很久没联系"
    val responseUrgencyLevel: Int = 2                  // 回复紧迫性: 1=慢热 2=正常 3=秒回
) : Parcelable