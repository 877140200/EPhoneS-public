package com.susking.ephone_s.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局"通话忙线"状态。
 *
 * 记录当前正在通话的联系人 ID（null 表示当前无人通话）。用途：让所有"自动发起的文字回复"
 * （静默追问、自动回复、后台独立行动）在 AI 正与某联系人通话时主动跳过，
 * 避免出现"一边视频通话、一边在聊天框冒出文字追问"这类出戏场景。
 *
 * 设计为 core 层零依赖 object（沿用 [EventBus] 的全局通道模式）：
 * - QqChatManager 无法注入 VideoCallManager（会与 VideoCallManager → QqChatManager
 *   的依赖形成 Hilt 循环依赖），因此借助这一全局通道解耦。
 * - core 模块不依赖 qq 模块，故这里只暴露原始类型 String，由 VideoCallManager（qq 侧）
 *   负责把 VideoCallState 翻译成 setBusy/clearBusy 调用，保持 core 纯净。
 *
 * 线程安全：写入发生在主线程（VideoCallManager 的 LiveData 观察者回调），
 * 读取可发生在任意协程/后台线程（StateFlow.value 跨线程读取安全）。
 */
object CallBusyState {

    // 当前正在通话的联系人 ID；null 表示当前无通话。
    private val _busyContactId = MutableStateFlow<String?>(null)

    /** 当前正在通话的联系人 ID 流，供需要响应式观察的场景使用。 */
    val busyContactId: StateFlow<String?> = _busyContactId.asStateFlow()

    /** 当前正在通话的联系人 ID；null 表示当前无通话。 */
    val currentBusyContactId: String?
        get() = _busyContactId.value

    /**
     * 是否正在与指定联系人通话。
     * @param contactId 待判断的联系人 ID
     * @return true=正在与该联系人通话
     */
    fun isBusyWith(contactId: String): Boolean = _busyContactId.value == contactId

    /**
     * 标记正在与指定联系人通话。进入任一通话态（来电/呼出/接通中/通话中/最小化）时调用。
     * @param contactId 正在通话的联系人 ID
     */
    fun setBusy(contactId: String) {
        _busyContactId.value = contactId
    }

    /**
     * 清除通话忙线状态。回到空闲态或通话结束态（Idle/Terminated/TerminatedByAi）时调用。
     */
    fun clearBusy() {
        _busyContactId.value = null
    }
}
