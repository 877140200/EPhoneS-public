package com.susking.ephone_s.qq.ui.chat

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class GestureInterceptingDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    private val gestureDetector: GestureDetector
    var isGestureEnabled: Boolean = true
 
     init {
         val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // 确认是横向滑动
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // 确认是向右滑动，并且速度和距离都足够
                    if (diffX > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        }
        gestureDetector = GestureDetector(context, gestureListener)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (!isGestureEnabled) {
            return super.onInterceptTouchEvent(ev)
        }
        // 将触摸事件传递给GestureDetector
        if (ev != null && gestureDetector.onTouchEvent(ev)) {
            // 如果GestureDetector识别了我们的手势（右滑），就拦截事件
            return true
        }
        // 否则，执行默认的拦截逻辑，让子视图（如ViewPager）可以正常处理事件
        return super.onInterceptTouchEvent(ev)
    }
}