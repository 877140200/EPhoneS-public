package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预发送祝福实体
 * 用于存储AI预生成的节日祝福，在指定时间自动发送
 */
@Entity(
    tableName = "scheduled_greetings",
    indices = [Index(value = ["contactId"]), Index(value = ["scheduledTime"])]
)
data class ScheduledGreetingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 关联的联系人ID
    val contactId: String,
    
    // 祝福类型：new_year（元旦）、spring_festival（春节）
    val greetingType: String,
    
    // AI生成的祝福内容
    val greetingContent: String,
    
    // 预定发送时间（时间戳）
    val scheduledTime: Long,
    
    // 生成时间
    val createdTime: Long = System.currentTimeMillis(),
    
    // 发送状态：pending（待发送）、sent（已发送）、cancelled（已取消）
    val status: String = "pending",
    
    // 实际发送时间（如果已发送）
    val sentTime: Long? = null,
    
    // 节日的年份
    val festivalYear: Int,
    
    // 节日的月份
    val festivalMonth: Int,
    
    // 节日的日期
    val festivalDay: Int
)