package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String, // 关联的联系人ID
    val title: String,
    val appointmentDate: Long
)