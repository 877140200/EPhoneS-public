package com.susking.ephone_s.core.util

/**
 * 用作 LiveData 所公开数据的包装器，代表一个事件。
 * 确保事件仅被消费一次。
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // 允许外部读取，但不允许写入

    /**
     * 返回内容并阻止其再次使用。
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * 返回内容，即时它已经被处理过。
     */
    fun peekContent(): T = content
}