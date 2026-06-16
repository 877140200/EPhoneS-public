package com.susking.ephone_s.album

import android.content.Context
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.aidata.data.local.AlbumDatabase
import com.susking.ephone_s.aidata.data.local.dao.AlbumDao
import com.susking.ephone_s.aidata.data.local.dao.PhotoDao

/**
 * AlbumDatabaseProvider接口的实现类
 * 在Album模块中实现，提供数据库访问能力
 */
class AlbumDatabaseProviderImpl(private val context: Context) : AlbumDatabaseProvider {
    
    private val database by lazy { AlbumDatabase.getDatabase(context) }
    
    override fun getAlbumDao(): AlbumDao = database.albumDao()
    
    override fun getPhotoDao(): PhotoDao = database.photoDao()
}