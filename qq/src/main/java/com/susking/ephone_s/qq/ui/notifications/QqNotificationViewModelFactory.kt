package com.susking.ephone_s.qq.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class QqNotificationViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QqNotificationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QqNotificationViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}