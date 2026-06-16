package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.susking.ephone_s.aidata.data.local.dao.CPhoneDao
import com.susking.ephone_s.aidata.data.local.entity.CPhoneDataEntity

/**
 * CPhone Room数据库配置
 * 
 * 数据库版本：1
 * 实体类：CPhoneDataEntity
 * 
 * 用于存储所有角色的"查手机"数据
 */
@Database(
    entities = [CPhoneDataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CPhoneDatabase : RoomDatabase() {
    
    /**
     * 获取CPhoneDao实例
     */
    abstract fun cphoneDao(): CPhoneDao
    
    companion object {
        private const val DATABASE_NAME = "cphone_database"
        
        @Volatile
        private var INSTANCE: CPhoneDatabase? = null
        
        /**
         * 获取数据库单例实例
         * 使用双重检查锁定确保线程安全
         * 
         * @param context 应用上下文
         * @return CPhoneDatabase实例
         */
        fun getDatabase(context: Context): CPhoneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        /**
         * 构建数据库实例
         * 
         * @param context 应用上下文
         * @return CPhoneDatabase实例
         */
        private fun buildDatabase(context: Context): CPhoneDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CPhoneDatabase::class.java,
                DATABASE_NAME
            )
                // 允许在主线程查询（仅用于调试，生产环境应移除）
                // .allowMainThreadQueries()
                
                // 数据库迁移策略：销毁并重建（开发阶段使用）
                .fallbackToDestructiveMigration()
                
                .build()
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