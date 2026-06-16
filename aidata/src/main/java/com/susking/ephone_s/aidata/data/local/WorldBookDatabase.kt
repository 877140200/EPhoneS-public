package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.susking.ephone_s.aidata.data.local.dao.WorldBookDao
import com.susking.ephone_s.aidata.data.local.dao.WorldBookEntryDao
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity

/**
 * 世界书独立数据库
 * 存储世界观设定相关数据
 * 
 * 从AiDataDatabase拆分出来,实现模块化管理
 * 便于未来独立使用或移植到其他项目
 */
@Database(
    entities = [
        WorldBookEntity::class,
        WorldBookEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WorldBookDatabase : RoomDatabase() {
    
    /**
     * 获取世界书Dao
     */
    abstract fun worldBookDao(): WorldBookDao
    
    /**
     * 获取世界书条目Dao
     */
    abstract fun worldBookEntryDao(): WorldBookEntryDao
    
    companion object {
        @Volatile
        private var INSTANCE: WorldBookDatabase? = null
        
        /**
         * 获取数据库实例(单例模式)
         * 
         * @param context 应用上下文
         * @return WorldBookDatabase实例
         */
        fun getDatabase(context: Context): WorldBookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorldBookDatabase::class.java,
                    "worldbook_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 清理数据库实例
         * 用于测试或重置
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}