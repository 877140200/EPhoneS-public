package com.susking.ephone_s.qq.di

import com.susking.ephone_s.qq.data.repository.QqChatRepositoryImpl
import com.susking.ephone_s.qq.data.repository.QqContactRepositoryImpl
import com.susking.ephone_s.qq.domain.repository.QqChatRepository
import com.susking.ephone_s.qq.domain.repository.QqContactRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * QQ数据层Hilt绑定模块
 * 
 * 绑定Repository接口与实现
 * 实现依赖倒置:domain层依赖接口,data层提供实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QqDataModule {
    
    /**
     * 绑定QqChatRepository接口到实现
     */
    @Binds
    @Singleton
    abstract fun bindQqChatRepository(
        impl: QqChatRepositoryImpl
    ): QqChatRepository
    
    /**
     * 绑定QqContactRepository接口到实现
     */
    @Binds
    @Singleton
    abstract fun bindQqContactRepository(
        impl: QqContactRepositoryImpl
    ): QqContactRepository
}