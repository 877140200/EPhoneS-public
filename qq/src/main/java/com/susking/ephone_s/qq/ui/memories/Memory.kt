package com.susking.ephone_s.qq.ui.memories

import java.util.Date

sealed class Memory {
    data class GeneralMemory(
        val id: String,
        val contactId: String,
        val date: Date,
        val title: String,
        val content: String,
        val isLongTermMemory: Boolean,
        val isVectorized: Boolean
    ) : Memory()

    data class Appointment(
        val id: Long,
        val contactId: String,
        val title: String,
        val appointmentDate: Long
    ) : Memory()
}