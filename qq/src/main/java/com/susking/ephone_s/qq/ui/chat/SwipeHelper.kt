package com.susking.ephone_s.qq.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.*

@SuppressLint("ClickableViewAccessibility")
abstract class SwipeHelper(
    context: Context,
    private val recyclerView: RecyclerView,
    internal var buttonWidth: Int,
    private val onRightSwipe: () -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var buttonsList: MutableList<UnderlayButton> = mutableListOf()
    private lateinit var gestureDetector: GestureDetector
    private var swipedPos = -1
    private var swipeThreshold = 0.5f
    private val buttonsBuffer: MutableMap<Int, MutableList<UnderlayButton>> = HashMap()
    private val recoverQueue: Queue<Int> = object : LinkedList<Int>() {
        override fun add(element: Int): Boolean {
            if (contains(element))
                return false
            else
                return super.add(element)
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            for (button in buttonsList) {
                if (button.onClick(e.x, e.y))
                    break
            }
            return true
        }
    }

    private val onTouchListener = View.OnTouchListener { _, motionEvent ->
        if (swipedPos < 0) return@OnTouchListener false
        val point = Point(motionEvent.rawX.toInt(), motionEvent.rawY.toInt())
        val swipedViewHolder = recyclerView.findViewHolderForAdapterPosition(swipedPos)
        val swipedItem = swipedViewHolder?.itemView
        val rect = Rect()
        swipedItem?.getGlobalVisibleRect(rect)

        if (motionEvent.action == MotionEvent.ACTION_DOWN || motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_MOVE) {
            if (rect.top < point.y && rect.bottom > point.y)
                gestureDetector.onTouchEvent(motionEvent)
            else {
                recoverQueue.add(swipedPos)
                swipedPos = -1
                recoverSwipedItem()
            }
        }
        false
    }

    init {
        this.gestureDetector = GestureDetector(context, gestureListener)
        this.recyclerView.setOnTouchListener(onTouchListener)
    }


    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.adapterPosition
        if (direction == ItemTouchHelper.LEFT) {
            if (swipedPos != pos)
                recoverQueue.add(swipedPos)
            swipedPos = pos
            if (buttonsBuffer.containsKey(swipedPos))
                buttonsList = buttonsBuffer[swipedPos]!!
            else
                buttonsList.clear()
            buttonsBuffer.clear()
            swipeThreshold = 0.5f * buttonsList.size * buttonWidth
            recoverSwipedItem()
        } else if (direction == ItemTouchHelper.RIGHT) {
            onRightSwipe()
            // 立即恢复item，因为我们不希望它保持滑动状态
            recyclerView.adapter?.notifyItemChanged(pos)
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return swipeThreshold
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return 0.1f * defaultValue
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return 5.0f * defaultValue
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val pos = viewHolder.adapterPosition
        var translationX = dX
        val itemView = viewHolder.itemView

        if (pos < 0) {
            swipedPos = pos
            return
        }

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (dX < 0) { // 向左滑动
                var buffer: MutableList<UnderlayButton> = ArrayList()
                if (!buttonsBuffer.containsKey(pos)) {
                    instantiateUnderlayButton(viewHolder, buffer)
                    buttonsBuffer[pos] = buffer
                } else {
                    buffer = buttonsBuffer[pos]!!
                }
                translationX = dX * buffer.size * buttonWidth / itemView.width
                drawButtons(c, itemView, buffer, pos, translationX)
            } else { // 向右滑动
                translationX = 0f // 不移动itemView
            }
        }

        super.onChildDraw(
            c,
            recyclerView,
            viewHolder,
            translationX,
            dY,
            actionState,
            isCurrentlyActive
        )
    }

    @Synchronized
    private fun recoverSwipedItem() {
        while (!recoverQueue.isEmpty()) {
            val pos = recoverQueue.poll()
            if (pos != null && pos > -1) {
                recyclerView.adapter?.notifyItemChanged(pos)
            }
        }
    }

    private fun drawButtons(
        c: Canvas,
        itemView: View,
        buffer: List<UnderlayButton>,
        pos: Int,
        dX: Float
    ) {
        var right = itemView.right.toFloat()
        val dButtonWidth = -1 * dX / buffer.size
        for (button in buffer) {
            val left = right - dButtonWidth
            button.onDraw(
                c,
                RectF(left, itemView.top.toFloat(), right, itemView.bottom.toFloat()),
                pos
            )
            right = left
        }
    }

    fun attachSwipe() {
        val itemTouchHelper = ItemTouchHelper(this)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    abstract fun instantiateUnderlayButton(
        viewHolder: RecyclerView.ViewHolder,
        underlayButtons: MutableList<UnderlayButton>
    )

    class UnderlayButton(
        private val text: String,
        private val color: Int,
        private val clickListener: UnderlayButtonClickListener
    ) {
        private var pos: Int = 0
        private var clickRegion: RectF? = null

        fun onClick(x: Float, y: Float): Boolean {
            if (clickRegion != null && clickRegion!!.contains(x, y)) {
                clickListener.onClick(pos)
                return true
            }
            return false
        }

        fun onDraw(c: Canvas, rect: RectF, pos: Int) {
            val p = Paint()
            p.color = color
            c.drawRect(rect, p)

            p.color = android.graphics.Color.WHITE
            p.textSize = 50f
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD

            val r = Rect()
            val cHeight = rect.height()
            val cWidth = rect.width()
            p.textAlign = Paint.Align.LEFT
            p.getTextBounds(text, 0, text.length, r)
            val x = cWidth / 2f - r.width() / 2f - r.left
            val y = cHeight / 2f + r.height() / 2f - r.bottom
            c.drawText(text, rect.left + x, rect.top + y, p)
            clickRegion = rect
            this.pos = pos
        }
    }

    interface UnderlayButtonClickListener {
        fun onClick(pos: Int)
    }
}