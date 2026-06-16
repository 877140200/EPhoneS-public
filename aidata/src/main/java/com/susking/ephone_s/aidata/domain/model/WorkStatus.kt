package com.susking.ephone_s.aidata.domain.model

data class WorkStatus(
    val isWorking: Boolean,
    val canFinishWork: Boolean,
    val hasWorkedToday: Boolean,
    val workStartTime: Long,
    val workEndTime: Long = 0
)