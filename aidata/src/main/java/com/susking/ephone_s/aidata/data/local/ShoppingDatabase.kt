package com.susking.ephone_s.aidata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.susking.ephone_s.aidata.data.local.dao.ShoppingAuthorizedAccountDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingCartDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingCategoryDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingOrderDao
import com.susking.ephone_s.aidata.data.local.dao.ShoppingProductDao
import com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingConverters
import com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity
import com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity

/**
 * 虚拟购物系统专用数据库
 * 
 * 独立管理商城、商品、购物车、订单相关数据
 */
@Database(
    entities = [
        ShoppingCategoryEntity::class,
        ShoppingProductEntity::class,
        ShoppingCartItemEntity::class,
        ShoppingOrderEntity::class,
        ShoppingAuthorizedAccountEntity::class,
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(ShoppingConverters::class)
abstract class ShoppingDatabase : RoomDatabase() {
    
    /**
     * 商品分类数据访问对象
     */
    abstract fun shoppingCategoryDao(): ShoppingCategoryDao
    
    /**
     * 商品数据访问对象
     */
    abstract fun shoppingProductDao(): ShoppingProductDao
    
    /**
     * 购物车数据访问对象
     */
    abstract fun shoppingCartDao(): ShoppingCartDao
    
    /**
     * 订单数据访问对象
     */
    abstract fun shoppingOrderDao(): ShoppingOrderDao
    
    /**
     * 已授权账号数据访问对象
     */
    abstract fun shoppingAuthorizedAccountDao(): ShoppingAuthorizedAccountDao
    
    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null
        
        /**
         * 数据库迁移: 版本3 -> 版本4
         * 添加shopping_authorized_accounts表
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建已授权账号表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_authorized_accounts` (
                        `contactId` TEXT NOT NULL,
                        `authorizedTimestamp` INTEGER NOT NULL,
                        `note` TEXT,
                        PRIMARY KEY(`contactId`)
                    )
                    """.trimIndent()
                )
            }
        }
        
        /**
         * 获取数据库实例（单例模式）
         */
        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}