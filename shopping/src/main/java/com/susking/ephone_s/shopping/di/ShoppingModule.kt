package com.susking.ephone_s.shopping.di

import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.data.repository.ShoppingCartRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ShoppingCategoryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ShoppingOrderRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ShoppingProductRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ShoppingAuthorizedAccountRepositoryImpl
import com.susking.ephone_s.aidata.domain.repository.ShoppingCartRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingCategoryRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingOrderRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Shopping模块的Hilt依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object ShoppingModule {
    
    /**
     * 提供ShoppingCategoryRepository
     */
    @Provides
    @Singleton
    fun provideShoppingCategoryRepository(
        database: ShoppingDatabase
    ): ShoppingCategoryRepository {
        return ShoppingCategoryRepositoryImpl(
            categoryDao = database.shoppingCategoryDao(),
            productDao = database.shoppingProductDao()
        )
    }
    
    /**
     * 提供ShoppingProductRepository
     */
    @Provides
    @Singleton
    fun provideShoppingProductRepository(
        database: ShoppingDatabase
    ): ShoppingProductRepository {
        return ShoppingProductRepositoryImpl(database.shoppingProductDao())
    }
    
    /**
     * 提供ShoppingCartRepository
     */
    @Provides
    @Singleton
    fun provideShoppingCartRepository(
        database: ShoppingDatabase
    ): ShoppingCartRepository {
        return ShoppingCartRepositoryImpl(
            cartDao = database.shoppingCartDao(),
            productDao = database.shoppingProductDao()
        )
    }
    
    /**
     * 提供ShoppingOrderRepository
     */
    @Provides
    @Singleton
    fun provideShoppingOrderRepository(
        database: ShoppingDatabase
    ): ShoppingOrderRepository {
        return ShoppingOrderRepositoryImpl(database.shoppingOrderDao())
    }
    
    /**
     * 提供ShoppingAuthorizedAccountRepository
     */
    @Provides
    @Singleton
    fun provideShoppingAuthorizedAccountRepository(
        database: ShoppingDatabase,
        productRepository: ShoppingProductRepository
    ): ShoppingAuthorizedAccountRepository {
        return ShoppingAuthorizedAccountRepositoryImpl(
            database.shoppingAuthorizedAccountDao(),
            productRepository
        )
    }
}