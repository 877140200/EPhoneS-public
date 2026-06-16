package com.susking.ephone_s.aidata.di

import javax.inject.Qualifier

/**
 * 用于标识应用级别的 CoroutineScope
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope