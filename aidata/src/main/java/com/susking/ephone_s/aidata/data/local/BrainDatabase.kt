package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.susking.ephone_s.aidata.data.local.converters.StatusConverter
import com.susking.ephone_s.aidata.data.local.dao.AiActivityDao
import com.susking.ephone_s.aidata.data.local.entity.AiActivityEntity

/**
 * Brain模块的独立数据库。
 */
@Database(
    entities = [AiActivityEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(StatusConverter::class)
abstract class BrainDatabase : RoomDatabase() {
    
    abstract fun aiActivityDao(): AiActivityDao
    
    companion object {
        private const val DATABASE_NAME = "brain_database"
        
        @Volatile
        private var INSTANCE: BrainDatabase? = null
        
        /**
         * 从版本1到版本2的迁移:清空所有活动日志以解决base64数据过大问题
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 清空所有活动日志记录
                db.execSQL("DELETE FROM ai_activities")
            }
        }
        
        /**
         * 从版本2到版本3的迁移:添加isBackgroundTask字段
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加isBackgroundTask字段，默认值为0 (false)
                db.execSQL("ALTER TABLE ai_activities ADD COLUMN isBackgroundTask INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        /**
         * 从版本3到版本4的迁移:重建表以修复数据损坏和字段缺失问题
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建新表,确保所有字段都有正确的定义和默认值
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_activities_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        activityChainId TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        description TEXT NOT NULL DEFAULT '',
                        prompt TEXT NOT NULL DEFAULT '',
                        rawResponse TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'WAITING',
                        isRead INTEGER NOT NULL DEFAULT 0,
                        hasVibrated INTEGER NOT NULL DEFAULT 0,
                        isBackgroundTask INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // 安全地复制有效数据,过滤掉可能损坏的记录
                db.execSQL("""
                    INSERT INTO ai_activities_new
                    (id, activityChainId, timestamp, description, prompt, rawResponse,
                     status, isRead, hasVibrated, isBackgroundTask)
                    SELECT id, activityChainId, timestamp, description, prompt, rawResponse,
                           status, isRead, hasVibrated, isBackgroundTask
                    FROM ai_activities
                    WHERE id IS NOT NULL
                      AND activityChainId IS NOT NULL
                      AND timestamp IS NOT NULL
                """)
                
                // 删除旧表
                db.execSQL("DROP TABLE ai_activities")
                
                // 重命名新表
                db.execSQL("ALTER TABLE ai_activities_new RENAME TO ai_activities")
            }
        }
        
        fun getInstance(context: Context): BrainDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrainDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
        }
    }
}