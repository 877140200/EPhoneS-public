package com.susking.ephone_s.album.api

import com.susking.ephone_s.aidata.data.local.dao.AlbumDao
import com.susking.ephone_s.aidata.data.local.dao.PhotoDao

/**
 * 数据库提供者接口
 * 由app模块实现，为album模块提供数据库访问能力
 */
interface AlbumDatabaseProvider {
    fun getAlbumDao(): AlbumDao
    fun getPhotoDao(): PhotoDao
}