package com.susking.ephone_s.core.util

import androidx.lifecycle.Observer

/**
 * 一个用于 [Event] 的 [Observer]，简化了检查事件内容是否已被处理的过程。
 *
 * [onEventUnhandledContent] 仅在 [Event] 的内容未被处理时才会被调用。
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        value.getContentIfNotHandled()?.let {
            onEventUnhandledContent(it)
        }
    }
}