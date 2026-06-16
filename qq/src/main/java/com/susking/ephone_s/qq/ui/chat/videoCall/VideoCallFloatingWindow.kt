package com.susking.ephone_s.qq.ui.chat.videoCall

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.R
import kotlin.math.abs

/**
 * 视频通话悬浮窗管理器
 * 负责显示系统级视频通话悬浮窗，可以在任何应用上层显示
 * 注意:这是系统级悬浮窗,需要SYSTEM_ALERT_WINDOW权限
 */
class VideoCallFloatingWindow(
    private val context: Context,
    private val onClickListener: () -> Unit
) {
    
    companion object {
        private const val TAG = "VideoCallFloatingWindow"
        private const val DRAG_THRESHOLD = 10f // 拖动阈值,小于这个值视为点击
        private const val EDGE_MARGIN = 16f // 边缘磁吸边距
    }
    
    // WindowManager用于管理系统级悬浮窗
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private var floatingView: View? = null
    private var contactAvatarImageView: ImageView? = null
    private var callDurationTextView: TextView? = null
    
    // 拖动相关变量
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    
    // 屏幕尺寸
    private val screenWidth: Int
    private val screenHeight: Int
    
    init {
        // 获取屏幕真实尺寸 - 使用WindowManager而不依赖Context.display
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d(TAG, "【悬浮窗】初始化完成, 屏幕尺寸: ${screenWidth}x${screenHeight}")
    }
    
    /**
     * 显示悬浮窗
     * @param contact 联系人信息
     * @param duration 通话时长(可选)
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(contact: PersonProfile, duration: String? = null) {
        Log.d(TAG, "【悬浮窗】show() 方法被调用, 联系人: ${contact.remarkName}, 时长文本: $duration")
        
        // Android 6.0+ 需要检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(context)
            Log.d(TAG, "【悬浮窗】权限检查: $hasPermission")
            if (!hasPermission) {
                Log.e(TAG, "【悬浮窗】没有悬浮窗权限，无法显示系统级悬浮窗")
                return
            }
        } else {
            Log.d(TAG, "【悬浮窗】Android 6.0以下,无需权限检查")
        }
        
        // 如果已经显示,先隐藏
        Log.d(TAG, "【悬浮窗】准备隐藏旧的悬浮窗(如果存在)")
        hide()
        
        // 加载悬浮窗布局
        Log.d(TAG, "【悬浮窗】开始加载悬浮窗布局")
        floatingView = LayoutInflater.from(context)
            .inflate(R.layout.layout_video_call_floating_window, null)
        Log.d(TAG, "【悬浮窗】布局加载完成: ${floatingView != null}")
        
        // 获取控件引用
        contactAvatarImageView = floatingView?.findViewById(R.id.contact_avatar_floating)
        callDurationTextView = floatingView?.findViewById(R.id.call_duration_floating)
        Log.d(TAG, "【悬浮窗】控件引用获取完成, avatar: ${contactAvatarImageView != null}, duration: ${callDurationTextView != null}")
        
        // 加载联系人头像
        Log.d(TAG, "【悬浮窗】开始加载联系人头像: ${contact.avatarUri}")
        contactAvatarImageView?.let { imageView ->
            Glide.with(context)
                .load(contact.avatarUri)
                .placeholder(com.susking.ephone_s.core.R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(imageView)
            Log.d(TAG, "【悬浮窗】头像加载完成")
        }
        
        // 设置通话时长(如果提供)
        if (duration != null) {
            Log.d(TAG, "【悬浮窗】设置时长文本: $duration")
            callDurationTextView?.visibility = View.VISIBLE
            callDurationTextView?.text = duration
        } else {
            Log.d(TAG, "【悬浮窗】时长为null,隐藏时长控件")
            callDurationTextView?.visibility = View.GONE
        }
        
        // 设置系统级悬浮窗布局参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // Android 8.0+
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE // Android 8.0以下
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 初始位置在右上角
            x = screenWidth - 80 - EDGE_MARGIN.toInt() // 80是悬浮窗宽度
            y = 100 // 距离顶部100px
        }
        
        // 设置触摸监听器(拖动和点击)
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    
                    // 拖动时放大10%
                    view.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(100)
                        .start()
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    // 判断是否为拖动
                    if (!isDragging && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        val params = view.layoutParams as WindowManager.LayoutParams
                        
                        // 计算新位置
                        var newX = (initialX + dx).toInt()
                        var newY = (initialY + dy).toInt()
                        
                        // 限制在屏幕范围内
                        val maxX = screenWidth - view.width
                        val maxY = screenHeight - view.height
                        
                        newX = when {
                            maxX < 0 -> 0
                            newX < 0 -> 0
                            newX > maxX -> maxX
                            else -> newX
                        }
                        
                        newY = when {
                            maxY < 0 -> 0
                            newY < 0 -> 0
                            newY > maxY -> maxY
                            else -> newY
                        }
                        
                        params.x = newX
                        params.y = newY
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    // 恢复大小
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                    
                    if (isDragging) {
                        // 拖动结束,吸附到最近的边缘
                        snapToEdge(view)
                    } else {
                        // 点击事件,启动应用并恢复通话界面
                        bringAppToForeground()
                        onClickListener.invoke()
                    }
                    true
                }
                
                else -> false
            }
        }
        
        // 添加到WindowManager（系统级悬浮窗）
        Log.d(TAG, "【悬浮窗】准备添加View到WindowManager")
        try {
            windowManager.addView(floatingView, params)
            Log.d(TAG, "【悬浮窗】✓✓✓ 系统级悬浮窗已成功添加到WindowManager ✓✓✓")
            Log.d(TAG, "【悬浮窗】悬浮窗位置: x=${params.x}, y=${params.y}")
            Log.d(TAG, "【悬浮窗】悬浮窗是否显示: ${isShowing()}")
        } catch (e: Exception) {
            Log.e(TAG, "【悬浮窗】✗✗✗ 显示悬浮窗失败 ✗✗✗", e)
            Log.e(TAG, "【悬浮窗】异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "【悬浮窗】异常信息: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        Log.d(TAG, "【悬浮窗】hide() 方法被调用")
        try {
            floatingView?.let { view ->
                Log.d(TAG, "【悬浮窗】floatingView存在, windowToken: ${view.windowToken != null}, parent: ${view.parent != null}")
                if (view.windowToken != null && view.parent != null) {
                    windowManager.removeView(view)
                    Log.d(TAG, "【悬浮窗】系统级悬浮窗已从WindowManager移除")
                } else {
                    Log.d(TAG, "【悬浮窗】floatingView未添加到WindowManager,无需移除")
                }
            } ?: Log.d(TAG, "【悬浮窗】floatingView为null,无需操作")
        } catch (e: Exception) {
            Log.e(TAG, "【悬浮窗】隐藏悬浮窗失败", e)
        }
        floatingView = null
        contactAvatarImageView = null
        callDurationTextView = null
        Log.d(TAG, "【悬浮窗】清理完成")
    }
    
    /**
     * 更新通话时长
     * @param duration 通话时长文本
     */
    fun updateDuration(duration: String) {
        callDurationTextView?.visibility = View.VISIBLE
        callDurationTextView?.text = duration
    }
    
    /**
     * 将悬浮窗吸附到最近的边缘
     */
    private fun snapToEdge(view: View) {
        val params = view.layoutParams as WindowManager.LayoutParams
        val currentX = params.x + view.width / 2
        
        // 判断最近的边缘(只考虑左右边缘,保持Y坐标不变)
        val distanceToLeft = currentX
        val distanceToRight = screenWidth - currentX
        
        // 吸附到最近的左右边缘
        if (distanceToLeft < distanceToRight) {
            // 吸附到左边
            params.x = EDGE_MARGIN.toInt()
        } else {
            // 吸附到右边
            val rightX = screenWidth - view.width - EDGE_MARGIN.toInt()
            params.x = if (rightX < 0) 0 else rightX
        }
        
        // 保持Y坐标不变,但确保在屏幕范围内
        val maxY = screenHeight - view.height
        val minY = EDGE_MARGIN.toInt()
        val maxYWithMargin = maxY - EDGE_MARGIN.toInt()
        
        params.y = when {
            maxYWithMargin < minY -> {
                if (maxY < 0) 0 else if (params.y > maxY) maxY else params.y
            }
            params.y < minY -> minY
            params.y > maxYWithMargin -> maxYWithMargin
            else -> params.y
        }
        
        // 更新悬浮窗位置
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "更新悬浮窗位置失败", e)
        }
    }
    
    /**
     * 判断悬浮窗是否正在显示
     */
    fun isShowing(): Boolean {
        return floatingView != null && floatingView?.parent != null
    }
    
    /**
     * 将应用带到前台
     * 当用户在应用外部点击悬浮窗时，需要先启动应用
     */
    private fun bringAppToForeground() {
        try {
            // 获取应用的启动Intent
            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)
            
            if (launchIntent != null) {
                // 设置Intent标志，将应用带到前台
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                
                // 启动应用
                context.startActivity(launchIntent)
                Log.d(TAG, "应用已启动到前台")
            } else {
                Log.e(TAG, "无法获取应用启动Intent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败", e)
        }
    }
}