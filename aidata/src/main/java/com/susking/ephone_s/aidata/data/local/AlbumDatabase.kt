package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.susking.ephone_s.aidata.data.local.dao.AlbumDao
import com.susking.ephone_s.aidata.data.local.dao.PhotoDao
import com.susking.ephone_s.aidata.data.local.entity.AlbumEntity
import com.susking.ephone_s.aidata.data.local.entity.PhotoEntity

/**
 * album 模块的独立数据库
 * 管理相册和照片数据
 */
@Database(
    entities = [
        AlbumEntity::class,
        PhotoEntity::class
    ],
    version = 2, // 清理包含base64的旧数据
    exportSchema = false
)
abstract class AlbumDatabase : RoomDatabase() {
    
    // DAO 抽象方法
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AlbumDatabase? = null
        
        /**
         * 获取数据库实例（单例模式）
         */
        fun getDatabase(context: Context): AlbumDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlbumDatabase::class.java,
                    "album_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}