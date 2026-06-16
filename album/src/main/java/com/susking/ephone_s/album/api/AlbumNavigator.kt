package com.susking.ephone_s.album.api

import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.album.domain.model.Photo

/**
 * 导航接口
 * 由app模块实现，为album模块提供导航能力
 */
interface AlbumNavigator {
    /**
     * 获取Fragment容器ID
     */
    fun getContainerId(): Int
    
    /**
     * 导航到照片查看器
     */
    fun navigateToPhotoViewer(fragmentManager: FragmentManager, photos: ArrayList<Photo>, position: Int)
    
    /**
     * 导航到照片网格
     */
    fun navigateToPhotoGrid(fragmentManager: FragmentManager, albumId: Long, albumName: String, isFavorites: Boolean)
    
    /**
     * 返回上一页
     */
    fun navigateBack()
}