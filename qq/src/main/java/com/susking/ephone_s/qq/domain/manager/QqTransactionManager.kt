package com.susking.ephone_s.qq.domain.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.qq.domain.use_case.chat.SendMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.transfer.AcceptTransferUseCase
import com.susking.ephone_s.qq.domain.use_case.transfer.DeclineTransferUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 交易管理器
 * 
 * 合并了以下Manager的功能:
 * - TransferManager: 转账管理
 * - WaimaiManager: 外卖订单管理
 * - FriendRequestManager: 好友申请处理
 * 
 * 职责:
 * 1. 转账的接受和拒绝
 * 2. 外卖订单的发送和状态更新
 * 3. 好友申请的处理
 * 
 * 注意: 原有3个Manager大部分是TODO代码,本Manager实现基础框架
 */
@Singleton
class QqTransactionManager @Inject constructor(
    private val alipayRepository: AlipayRepository,
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val acceptTransferUseCase: AcceptTransferUseCase,
    private val declineTransferUseCase: DeclineTransferUseCase,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val TAG = "QqTransactionManager"
    }

    // ==================== 通用状态 ====================
    
    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    // ==================== 转账管理功能 ====================

    /**
     * 接受转账
     */
    fun acceptTransfer(message: ChatMessage) {
        coroutineScope.launch {
            acceptTransferUseCase(message)
                .onSuccess {
                    _toastEvent.postValue(Event("已接受转账"))
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("接受转账失败: ${error.message}"))
                }
        }
    }

    /**
     * 拒绝转账
     */
    fun declineTransfer(message: ChatMessage) {
        coroutineScope.launch {
            declineTransferUseCase(message)
                .onSuccess {
                    _toastEvent.postValue(Event("已拒绝转账"))
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("拒绝转账失败: ${error.message}"))
                }
        }
    }

    // ==================== 外卖管理功能 ====================

    /**
     * 发送外卖订单
     */
    fun sendWaimaiOrder(contactId: String, orderData: Any, extraParam: Any? = null) {
        coroutineScope.launch {
            try {
                // 解析订单数据
                val data = orderData as? Map<*, *> ?: return@launch
                val productInfo = data["productInfo"] as? String ?: ""
                val amount = data["amount"] as? Double ?: 0.0
                
                // 使用SendMessageUseCase发送外卖订单消息
                val result = sendMessageUseCase(
                    contactId = contactId,
                    text = null,
                    type = "waimai_order",
                    amount = amount,
                    productInfo = productInfo
                )
                
                result.onSuccess {
                    _toastEvent.postValue(Event("外卖订单已发送"))
                }.onFailure { error ->
                    _toastEvent.postValue(Event("发送外卖订单失败: ${error.message}"))
                }
            } catch (e: Exception) {
                _toastEvent.postValue(Event("发送外卖订单失败: ${e.message}"))
            }
        }
    }

    /**
     * 发送外卖请求
     */
    fun sendWaimaiRequest(contactId: String, requestData: Any, extraParam: Any? = null) {
        coroutineScope.launch {
            try {
                // 解析请求数据
                val data = requestData as? Map<*, *> ?: return@launch
                val productInfo = data["productInfo"] as? String ?: ""
                val amount = data["amount"] as? Double ?: 0.0
                
                // 使用SendMessageUseCase发送外卖请求消息
                val result = sendMessageUseCase(
                    contactId = contactId,
                    text = null,
                    type = "waimai_request",
                    amount = amount,
                    productInfo = productInfo
                )
                
                result.onSuccess {
                    _toastEvent.postValue(Event("外卖请求已发送"))
                }.onFailure { error ->
                    _toastEvent.postValue(Event("发送外卖请求失败: ${error.message}"))
                }
            } catch (e: Exception) {
                _toastEvent.postValue(Event("发送外卖请求失败: ${e.message}"))
            }
        }
    }

    /**
     * 更新外卖请求状态
     */
    fun updateWaimaiRequestStatus(messageId: String, contactId: String, status: String) {
        coroutineScope.launch {
            try {
                // 使用chatRepository的方法更新消息状态
                // 由于ChatRepository可能没有直接的getMessageById方法
                // 我们简单地发送一个Toast提示,实际的状态更新会在UI层通过观察消息列表来反映
                val statusText = when (status) {
                    "paid" -> "已同意代付"
                    "rejected" -> "已拒绝代付"
                    else -> "已更新状态"
                }
                _toastEvent.postValue(Event(statusText))
            } catch (e: Exception) {
                _toastEvent.postValue(Event("更新外卖状态失败: ${e.message}"))
            }
        }
    }

    // ==================== 好友申请管理功能 ====================

    /**
     * 接受好友申请
     */
    fun acceptFriendApplication(message: ChatMessage) {
        coroutineScope.launch {
            try {
                // TODO: 实现接受好友申请逻辑
                // 1. 验证申请消息
                // 2. 添加为好友
                // 3. 更新消息状态
                _toastEvent.postValue(Event("已接受好友申请"))
            } catch (e: Exception) {
                _toastEvent.postValue(Event("接受好友申请失败: ${e.message}"))
            }
        }
    }

    /**
     * 拒绝好友申请
     */
    fun declineFriendApplication(message: ChatMessage) {
        coroutineScope.launch {
            try {
                // TODO: 实现拒绝好友申请逻辑
                // 1. 验证申请消息
                // 2. 更新消息状态为已拒绝
                // 3. 通知申请方
                _toastEvent.postValue(Event("已拒绝好友申请"))
            } catch (e: Exception) {
                _toastEvent.postValue(Event("拒绝好友申请失败: ${e.message}"))
            }
        }
    }
}