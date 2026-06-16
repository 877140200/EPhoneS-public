package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.susking.ephone_s.aidata.data.local.dao.AlipayDao
import com.susking.ephone_s.aidata.data.local.entity.AlipayBillEntity
import com.susking.ephone_s.aidata.data.local.entity.AlipayWalletEntity
import com.susking.ephone_s.aidata.data.local.entity.BigDecimalConverter
import com.susking.ephone_s.aidata.data.local.entity.WorkStatusEntity

/**
 * 支付宝数据库
 * 包含钱包、账单和工作状态
 */
@Database(
    entities = [
        AlipayWalletEntity::class,
        AlipayBillEntity::class,
        WorkStatusEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(BigDecimalConverter::class)
abstract class AlipayDatabase : RoomDatabase() {
    
    abstract fun alipayDao(): AlipayDao
    
    companion object {
        @Volatile
        private var INSTANCE: AlipayDatabase? = null
        
        fun getDatabase(context: Context): AlipayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlipayDatabase::class.java,
                    "alipay_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}