package com.susking.ephone_s.album.api

import android.content.Context
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.domain.repository.AlbumRepository

/**
 * Album模块对外API
 * 提供模块初始化和获取核心组件的能力
 */
object AlbumApi {
    
    private var databaseProvider: AlbumDatabaseProvider? = null
    private var navigator: AlbumNavigator? = null
    private var imageSelectionCallback: ImageSelectionCallback? = null
    
    /**
     * 初始化Album模块
     */
    fun init(
        databaseProvider: AlbumDatabaseProvider,
        navigator: AlbumNavigator
    ) {
        this.databaseProvider = databaseProvider
        this.navigator = navigator
    }
    
    /**
     * 设置图片选择回调
     */
    fun setImageSelectionCallback(callback: ImageSelectionCallback?) {
        this.imageSelectionCallback = callback
    }
    
    /**
     * 获取数据库提供者
     */
    fun getDatabaseProvider(): AlbumDatabaseProvider {
        return databaseProvider ?: throw IllegalStateException("AlbumApi not initialized. Call init() first.")
    }
    
    /**
     * 获取导航器
     */
    fun getNavigator(): AlbumNavigator {
        return navigator ?: throw IllegalStateException("AlbumApi not initialized. Call init() first.")
    }
    
    /**
     * 获取图片选择回调
     */
    fun getImageSelectionCallback(): ImageSelectionCallback? = imageSelectionCallback
    
    /**
     * 创建AlbumRepository实例
     */
    fun createRepository(): AlbumRepository {
        val provider = getDatabaseProvider()
        return AlbumRepositoryImpl(provider.getAlbumDao(), provider.getPhotoDao())
    }
}