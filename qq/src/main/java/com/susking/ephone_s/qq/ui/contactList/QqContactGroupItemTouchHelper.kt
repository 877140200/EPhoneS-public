package com.susking.ephone_s.qq.ui.contactList

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import kotlin.math.abs
import kotlin.math.max

class QqContactGroupItemTouchHelper(
    private val context: Context,
    private val adapter: QqContactGroupAdapter,
    private val onPinClicked: (PersonProfile) -> Unit,
    private val onDeleteClicked: (PersonProfile) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val singleButtonWidth = 90f.dpToPx()
    private val buttonsTotalWidth = singleButtonWidth * 2
    private val paint = Paint()
    internal var swipedViewHolder: RecyclerView.ViewHolder? = null
    private val gestureDetector: GestureDetector

    init {
        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    swipedViewHolder?.let { viewHolder ->
                        val itemView = viewHolder.itemView
                        // 修正：按钮的矩形区域是静态的，与 item view 的原始位置相关，不应受 translationX 影响
                        val pinButtonRect = RectF(
                            (itemView.right - singleButtonWidth),
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat()
                        )
                        val deleteButtonRect = RectF(
                            (itemView.right - buttonsTotalWidth),
                            itemView.top.toFloat(),
                            (itemView.right - singleButtonWidth),
                            itemView.bottom.toFloat()
                        )

                        Log.d("SwipeDebug", "onSingleTapUp: Touch at (${e.x}, ${e.y})")
                        Log.d("SwipeDebug", "onSingleTapUp: Pin button rect: $pinButtonRect")
                        Log.d("SwipeDebug", "onSingleTapUp: Delete button rect: $deleteButtonRect")

                        if (viewHolder.adapterPosition < 0 || viewHolder.adapterPosition >= adapter.currentList.size) {
                            return false
                        }
                        val item = adapter.currentList[viewHolder.adapterPosition]
                        if (item is ContactListItem.ContactItem) {
                            if (pinButtonRect.contains(e.x, e.y)) {
                                Log.d("SwipeDebug", "onSingleTapUp: Pin button clicked.")
                                onPinClicked(item.contact)
                                closeOpenItem()
                                return true
                            }
                            if (deleteButtonRect.contains(e.x, e.y)) {
                                Log.d("SwipeDebug", "onSingleTapUp: Delete button clicked.")
                                onDeleteClicked(item.contact)
                                closeOpenItem()
                                return true
                            }
                        }
                    }
                    Log.d("SwipeDebug", "onSingleTapUp: No button clicked or no item swiped.")
                    return false
                }
            })
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // 只允许联系人项左滑，不允许标题项滑动
        return if (viewHolder is QqContactGroupAdapter.ContactViewHolder) {
            makeMovementFlags(0, 0)
        } else {
            makeMovementFlags(0, 0)
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // 当一个项目被完全滑动时，我们在这里记录它
        // 尽管我们的动画是自定义的，但与ItemTouchHelper的状态机同步是最佳实践
        swipedViewHolder = viewHolder
    }

    // 禁用默认的滑动完成机制，完全由 clearView 控制
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = Float.MAX_VALUE

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // 当用户开始拖动时，立即停止RecyclerView的所有滚动
            if (isCurrentlyActive) {
                recyclerView.stopScroll()
            }
            val itemView = viewHolder.itemView
            val translationX: Float

            // 核心逻辑：区分用户拖动和程序动画
            if (isCurrentlyActive) {
                // 用户正在拖动：实时更新位置
                translationX = max(-buttonsTotalWidth, dX)
            } else if (swipedViewHolder == viewHolder) {
                 // 用户已松手，且此项是已打开项：强制保持在打开位置
                translationX = -buttonsTotalWidth
            } else {
                 // 用户已松手，且此项是已关闭项：强制保持在关闭位置
                translationX = 0f
            }
            itemView.translationX = translationX

            // 绘制背景按钮...
            // 修正：按钮背景的坐标应该是静态的，不随 translationX 变化
            val deleteButtonBackground = RectF(
                itemView.right - buttonsTotalWidth,
                itemView.top.toFloat(),
                itemView.right - singleButtonWidth,
                itemView.bottom.toFloat()
            )
            val errorColor = TypedValue()
            context.theme.resolveAttribute(R.attr.colorError, errorColor, true)
            paint.color = errorColor.data
            c.drawRect(deleteButtonBackground, paint)
            val pinButtonBackground = RectF(
                itemView.right - singleButtonWidth,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            val primaryColor = TypedValue()
            context.theme.resolveAttribute(R.attr.colorPrimary, primaryColor, true)
            paint.color = primaryColor.data
            c.drawRect(pinButtonBackground, paint)
            val textColorOnError = TypedValue()
            context.theme.resolveAttribute(R.attr.colorOnError, textColorOnError, true)
            paint.color = textColorOnError.data
            paint.textSize = 16f.dpToPx()
            paint.textAlign = Paint.Align.CENTER
            val itemHeight = itemView.bottom - itemView.top
            val textY = itemView.top + itemHeight / 2 - (paint.descent() + paint.ascent()) / 2
            c.drawText("删除", itemView.right - singleButtonWidth - (singleButtonWidth / 2), textY, paint)
            val textColorOnPrimary = TypedValue()
            context.theme.resolveAttribute(R.attr.colorOnPrimary, textColorOnPrimary, true)
            paint.color = textColorOnPrimary.data
            c.drawText("置顶", itemView.right - (singleButtonWidth / 2), textY, paint)
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private fun Float.dpToPx(): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics)

    fun isAnyItemOpen(): Boolean = swipedViewHolder != null

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        // 当用户手势结束时，此方法被调用
        val itemView = viewHolder.itemView
        val translationX = itemView.translationX
        val openThreshold = buttonsTotalWidth / 2

        if (viewHolder.adapterPosition == -1) return

        if (abs(translationX) > openThreshold) {
            // 用户想要打开它
            Log.d("SwipeDebug", "clearView: Threshold crossed. Opening item.")
            // 关键修复：立即更新状态
            swipedViewHolder = viewHolder
            itemView.animate().translationX(-buttonsTotalWidth).withEndAction {
                if (viewHolder.adapterPosition != -1) {
                    Log.d("SwipeDebug", "Item at position ${viewHolder.adapterPosition} successfully expanded.")
                }
            }.setDuration(200).start()
        } else {
            // 用户想要关闭它
            Log.d("SwipeDebug", "clearView: Threshold not crossed. Closing item.")
            // 关键修复：立即更新状态
            if (swipedViewHolder == viewHolder) {
                swipedViewHolder = null
            }
            itemView.animate().translationX(0f).withEndAction {
                if (viewHolder.adapterPosition != -1) {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                }
            }.setDuration(200).start()
        }
    }

    fun closeOpenItem() {
        val viewHolderToClose = swipedViewHolder ?: return
        val position = viewHolderToClose.adapterPosition
        if (position == -1) return

        // 立即清除状态，这是防止竞态条件的关键
        swipedViewHolder = null

        Log.d("SwipeDebug", "closeOpenItem: Closing item at position $position")

        viewHolderToClose.itemView.animate().translationX(0f).withEndAction {
            Log.d("SwipeDebug", "Item at position $position successfully collapsed.")
            if (viewHolderToClose.adapterPosition != -1) {
                adapter.notifyItemChanged(position)
            }
        }.setDuration(200).start()
    }

    fun onTouch(event: MotionEvent): Boolean {
        if (swipedViewHolder != null) {
            return gestureDetector.onTouchEvent(event)
        }
        return false
    }
}