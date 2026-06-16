package com.susking.ephone_s.core.util

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 一个简单的全局事件总线，用于在应用的不同组件之间传递事件。
 * 主要用于解决后台任务完成后，需要通知前台UI刷新的问题。
 *
 * 支持两种事件机制:
 * 1. LiveData事件(旧API,保持兼容性)
 * 2. Flow事件(新API,用于Manager间通信)
 */
object EventBus {

    // ==================== LiveData事件(旧API) ====================
    
    // AI活动事件
    private val _newAiActivityEvent = MutableLiveData<Event<Unit>>()
    val newAiActivityEvent: LiveData<Event<Unit>> = _newAiActivityEvent

    // 图片选择事件
    private val _imageSelectedEvent = MutableLiveData<Event<ImageSelectedData>>()
    val imageSelectedEvent: LiveData<Event<ImageSelectedData>> = _imageSelectedEvent

    /**
     * 发送一个新的AI活动事件。
     */
    fun postNewAiActivityEvent() {
        _newAiActivityEvent.postValue(Event(Unit))
    }

    /**
     * 发送图片选择事件。
     * @param requestKey 请求标识，用于区分不同的图片选择请求
     * @param imagePath 选中的图片路径
     * @param cropOptions 裁剪选项
     */
    fun postImageSelectedEvent(requestKey: String, imagePath: String, cropOptions: CropImageOptions?) {
        Log.d("EventBus", "发送图片选择事件: requestKey=$requestKey, imagePath=$imagePath")
        _imageSelectedEvent.value = Event(ImageSelectedData(requestKey, imagePath, cropOptions))
        Log.d("EventBus", "图片选择事件已发送")
    }

    // ==================== Flow事件(新API) ====================
    
    private val _events = MutableSharedFlow<Any>(replay = 0, extraBufferCapacity = 10)
    val events: SharedFlow<Any> = _events.asSharedFlow()

    /**
     * 发送通用事件(新API)
     * 用于Manager间解耦通信
     */
    fun post(event: Any) {
        _events.tryEmit(event)
    }
}

/**
 * 图片选择事件数据
 */
data class ImageSelectedData(
    val requestKey: String,
    val imagePath: String,
    val cropOptions: CropImageOptions?
)

/**
 * 支付请求事件
 * 用于请求显示支付确认对话框
 */
data class ShowPaymentDialogEvent(
    val orderAmount: Double,
    val onConfirm: () -> Unit
)

/**
 * 添加物品到背包事件
 * 当用户在购物商城支付成功后发送此事件
 */
data class AddToBackpackEvent(
    val productName: String,
    val imageUrl: String,
    val price: Double,
    val orderId: Long,
    val source: String = "购物商城"
)

/**
 * AI发起视频来电事件（全局）。
 * 用途：AI在任意时机（聊天页/桌面/其他界面）发起视频通话时，
 * 通过此全局事件通知 MainActivity 拉起来电界面。
 *
 * 设计原因：QqChatManager 无法注入 VideoCallManager（会与 VideoCallManager → QqChatManager
 * 的依赖形成 Hilt 循环依赖），因此借助 EventBus 这一全局通道解耦，
 * 由 Activity 级（而非聊天页 viewLifecycleOwner）的监听者消费，确保任何界面都能弹出来电。
 *
 * @param contactId 发起来电的AI联系人ID
 */
data class IncomingCallEvent(
    val contactId: String
)

/**
 * 酒馆（「???」）前台状态事件。
 *
 * 用途：进入酒馆页面时需要隐藏 brain 大脑伪悬浮窗、改用酒馆专属的「锦囊」悬浮窗，
 * 离开时恢复 brain。TavernFragment 在 onResume/onPause 发此事件，
 * 由 Activity 级监听者（MainActivity）消费控制 brain 容器可见性。
 *
 * @param isForeground true 表示酒馆进入前台（应隐藏 brain），false 表示离开（恢复 brain）
 */
data class TavernForegroundEvent(
    val isForeground: Boolean
)
