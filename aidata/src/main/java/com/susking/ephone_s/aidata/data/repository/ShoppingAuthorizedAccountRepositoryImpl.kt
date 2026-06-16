package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ShoppingAuthorizedAccountDao
import com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity
import com.susking.ephone_s.aidata.domain.model.AuthorizedAccount
import com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 购物app已授权账号仓库实现
 */
class ShoppingAuthorizedAccountRepositoryImpl @Inject constructor(
    private val authorizedAccountDao: ShoppingAuthorizedAccountDao,
    private val productRepository: ShoppingProductRepository
) : ShoppingAuthorizedAccountRepository {
    
    override fun getAllAuthorizedAccounts(): Flow<List<AuthorizedAccount>> {
        return authorizedAccountDao.getAllAuthorizedAccounts().map { entities ->
            entities.map { entity ->
                AuthorizedAccount(
                    contactId = entity.contactId,
                    authorizedTimestamp = entity.authorizedTimestamp,
                    note = entity.note
                )
            }
        }
    }
    
    override suspend fun isContactAuthorized(contactId: String): Boolean {
        return authorizedAccountDao.getAuthorizedAccount(contactId) != null
    }
    
    override suspend fun addAuthorizedAccount(contactId: String, note: String?) {
        val entity = ShoppingAuthorizedAccountEntity(
            contactId = contactId,
            authorizedTimestamp = System.currentTimeMillis(),
            note = note
        )
        authorizedAccountDao.insertAuthorizedAccount(entity)
    }
    
    override suspend fun removeAuthorizedAccount(contactId: String) {
        authorizedAccountDao.deleteAuthorizedAccount(contactId)
    }
    
    override suspend fun removeAuthorizedAccount(contactId: String, keepProducts: Boolean) {
        // 先删除授权账号
        authorizedAccountDao.deleteAuthorizedAccount(contactId)
        // 如果不保留商品,则删除该账号的所有商品和分类
        if (!keepProducts) {
            productRepository.deleteProductsByContactId(contactId)
        }
    }
    
    override suspend fun clearAllAuthorizedAccounts() {
        authorizedAccountDao.deleteAllAuthorizedAccounts()
    }
}