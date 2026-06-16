package com.susking.ephone_s.desktop.ui.drag

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 网格覆盖层View
 * 在拖拽时显示网格线和目标位置预览，带有流畅的动画效果
 */
public class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var recyclerView: RecyclerView? = null

    // 网格线画笔
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = ContextCompat.getColor(context, android.R.color.white)
        alpha = 120
        isAntiAlias = true
    }

    // 高亮填充画笔（带渐变）
    private val highlightPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // 高亮边框画笔
    private val highlightStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ContextCompat.getColor(context, android.R.color.holo_blue_bright)
        isAntiAlias = true
    }
    
    // 阴影画笔
    private val shadowPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 40
        isAntiAlias = true
    }

    private var showGrid = false
    private var highlightPosition: GridPosition? = null
    private var previousHighlightPosition: GridPosition? = null
    private val cellRects = mutableListOf<RectF>()
    private var highlightColumnSpan: Int = 1
    private var highlightRowSpan: Int = 1
    
    // 动画相关属性
    private var gridAlpha: Float = 0f // 网格透明度 (0-1)
    private var highlightScale: Float = 0f // 高亮缩放 (0-1)
    private var highlightAlpha: Float = 0f // 高亮透明度 (0-1)
    private var transitionProgress: Float = 1f // 位置切换进度 (0-1)
    
    // 动画器
    private var gridAnimator: ValueAnimator? = null
    private var highlightAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null
    
    // 圆角半径
    private val cornerRadius = 12f
    
    // 阴影偏移
    private val shadowOffset = 4f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateCellRects()
    }

    private fun calculateCellRects() {
        cellRects.clear()
        
        // 考虑padding，计算实际可用区域
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        
        val cellWidth = availableWidth / GridPosition.Companion.COLUMNS
        val cellHeight = availableHeight / GridPosition.Companion.ROWS

        for (row in 0 until GridPosition.Companion.ROWS) {
            for (col in 0 until GridPosition.Companion.COLUMNS) {
                val left = paddingLeft + col * cellWidth
                val top = paddingTop + row * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight
                cellRects.add(RectF(left, top, right, bottom))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showGrid && gridAlpha <= 0f) return

        // 应用网格透明度
        val currentGridAlpha = (gridAlpha * 120).toInt()
        gridPaint.alpha = currentGridAlpha

        // 绘制网格线
        if (gridAlpha > 0f) {
            drawGridLines(canvas)
        }

        // 绘制高亮位置（带动画）
        if (highlightAlpha > 0f) {
            drawHighlightWithAnimation(canvas)
        }
    }
    
    /**
     * 绘制网格线
     */
    private fun drawGridLines(canvas: Canvas) {
        if (cellRects.isNotEmpty()) {
            // 绘制垂直线（基于实际cell位置）
            for (col in 0..GridPosition.Companion.COLUMNS) {
                val x = if (col == 0) {
                    cellRects[0].left
                } else if (col == GridPosition.Companion.COLUMNS) {
                    cellRects[cellRects.size - 1].right
                } else {
                    cellRects[col].left
                }
                val top = cellRects[0].top
                val bottom = cellRects[minOf(cellRects.size - 1, GridPosition.Companion.COLUMNS * (GridPosition.Companion.ROWS - 1))].bottom
                canvas.drawLine(x, top, x, bottom, gridPaint)
            }

            // 绘制水平线（基于实际cell位置）
            for (row in 0..GridPosition.Companion.ROWS) {
                val index = minOf(row * GridPosition.Companion.COLUMNS, cellRects.size - 1)
                if (index >= 0 && index < cellRects.size) {
                    val y = if (row == GridPosition.Companion.ROWS) {
                        cellRects[cellRects.size - 1].bottom
                    } else {
                        cellRects[index].top
                    }
                    val left = cellRects[0].left
                    val right = cellRects[minOf(GridPosition.Companion.COLUMNS - 1, cellRects.size - 1)].right
                    canvas.drawLine(left, y, right, y, gridPaint)
                }
            }
        } else {
            // 回退到均分计算
            val availableWidth = (width - paddingLeft - paddingRight).toFloat()
            val availableHeight = (height - paddingTop - paddingBottom).toFloat()
            
            val cellWidth = availableWidth / GridPosition.Companion.COLUMNS
            val cellHeight = availableHeight / GridPosition.Companion.ROWS

            for (col in 0..GridPosition.Companion.COLUMNS) {
                val x = paddingLeft + col * cellWidth
                canvas.drawLine(x, paddingTop.toFloat(), x, (height - paddingBottom).toFloat(), gridPaint)
            }

            for (row in 0..GridPosition.Companion.ROWS) {
                val y = paddingTop + row * cellHeight
                canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, gridPaint)
            }
        }
    }
    
    /**
     * 绘制带动画的高亮效果
     */
    private fun drawHighlightWithAnimation(canvas: Canvas) {
        val currentPosition = highlightPosition ?: return
        if (!currentPosition.isValid()) return
        
        val index = currentPosition.row * GridPosition.Companion.COLUMNS + currentPosition.column
        if (index !in cellRects.indices) return
        
        val targetRect = buildHighlightTargetRect(currentPosition, index)
        
        // 如果正在进行位置切换动画
        val drawRect = if (transitionProgress < 1f && previousHighlightPosition != null) {
            val prevPosition = previousHighlightPosition!!
            val prevIndex = prevPosition.row * GridPosition.Companion.COLUMNS + prevPosition.column
            if (prevIndex in cellRects.indices) {
                val prevRect = buildHighlightTargetRect(prevPosition, prevIndex)
                // 插值计算过渡矩形
                RectF(
                    prevRect.left + (targetRect.left - prevRect.left) * transitionProgress,
                    prevRect.top + (targetRect.top - prevRect.top) * transitionProgress,
                    prevRect.right + (targetRect.right - prevRect.right) * transitionProgress,
                    prevRect.bottom + (targetRect.bottom - prevRect.bottom) * transitionProgress
                )
            } else {
                targetRect
            }
        } else {
            targetRect
        }
        
        // 应用缩放效果（从中心缩放）
        val scaledRect = RectF(drawRect)
        if (highlightScale < 1f) {
            val centerX = drawRect.centerX()
            val centerY = drawRect.centerY()
            val scaleWidth = drawRect.width() * highlightScale
            val scaleHeight = drawRect.height() * highlightScale
            
            scaledRect.left = centerX - scaleWidth / 2
            scaledRect.top = centerY - scaleHeight / 2
            scaledRect.right = centerX + scaleWidth / 2
            scaledRect.bottom = centerY + scaleHeight / 2
        }
        
        // 绘制阴影
        val shadowRect = RectF(
            scaledRect.left + shadowOffset,
            scaledRect.top + shadowOffset,
            scaledRect.right + shadowOffset,
            scaledRect.bottom + shadowOffset
        )
        shadowPaint.alpha = (40 * highlightAlpha).toInt()
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
        
        // 设置渐变填充
        val gradient = LinearGradient(
            scaledRect.left, scaledRect.top,
            scaledRect.right, scaledRect.bottom,
            intArrayOf(
                Color.argb((100 * highlightAlpha).toInt(), 100, 181, 246),
                Color.argb((60 * highlightAlpha).toInt(), 33, 150, 243)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        highlightPaint.shader = gradient
        highlightPaint.alpha = (255 * highlightAlpha).toInt()
        
        // 绘制填充
        canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, highlightPaint)
        
        // 绘制边框
        highlightStrokePaint.alpha = (200 * highlightAlpha).toInt()
        canvas.drawRoundRect(scaledRect, cornerRadius, cornerRadius, highlightStrokePaint)
    }

    private fun buildHighlightTargetRect(position: GridPosition, index: Int): RectF {
        val baseRect: RectF = cellRects[index]
        val endColumn: Int = (position.column + highlightColumnSpan - 1).coerceAtMost(GridPosition.Companion.COLUMNS - 1)
        val endRow: Int = (position.row + highlightRowSpan - 1).coerceAtMost(GridPosition.Companion.ROWS - 1)
        val endIndex: Int = endRow * GridPosition.Companion.COLUMNS + endColumn
        if (endIndex !in cellRects.indices) return baseRect
        val endRect: RectF = cellRects[endIndex]
        return RectF(baseRect.left, baseRect.top, endRect.right, endRect.bottom)
    }

    /**
     * 显示网格和目标位置预览（带淡入动画）
     */
    public fun showGridWithHighlight(position: GridPosition?) {
        showGrid = true
        highlightPosition = position
        previousHighlightPosition = null
        
        // 启动网格淡入动画
        gridAnimator?.cancel()
        gridAnimator = ValueAnimator.ofFloat(gridAlpha, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                gridAlpha = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // 启动高亮显示动画
        if (position != null && position.isValid()) {
            startHighlightAnimation()
        }
    }

    /**
     * 隐藏网格（带淡出动画）
     */
    public fun hideGrid() {
        showGrid = false
        
        // 启动网格淡出动画
        gridAnimator?.cancel()
        gridAnimator = ValueAnimator.ofFloat(gridAlpha, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                gridAlpha = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // 启动高亮隐藏动画
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(highlightAlpha, 0f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                highlightAlpha = animation.animatedValue as Float
                highlightScale = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        highlightPosition = null
        previousHighlightPosition = null
    }

    public fun setHighlightSpan(columnSpan: Int, rowSpan: Int): Unit {
        highlightColumnSpan = columnSpan.coerceIn(MIN_HIGHLIGHT_SPAN, GridPosition.Companion.COLUMNS)
        highlightRowSpan = rowSpan.coerceIn(MIN_HIGHLIGHT_SPAN, GridPosition.Companion.ROWS)
        invalidate()
    }

    /**
     * 更新高亮位置（带平滑过渡动画）
     */
    public fun updateHighlight(position: GridPosition?) {
        if (!showGrid) return
        
        val oldPosition = highlightPosition
        
        // 如果位置没有变化，不需要动画
        if (oldPosition == position) return
        
        highlightPosition = position
        
        if (position != null && position.isValid()) {
            // 如果之前有高亮位置，启动位置切换动画
            if (oldPosition != null && oldPosition.isValid()) {
                startTransitionAnimation(oldPosition, position)
            } else {
                // 否则直接显示高亮
                startHighlightAnimation()
            }
        } else {
            // 隐藏高亮
            hideHighlight()
        }
    }
    
    /**
     * 启动高亮显示动画（缩放+淡入）
     */
    private fun startHighlightAnimation() {
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                highlightScale = value
                highlightAlpha = value
                invalidate()
            }
            start()
        }
    }
    
    /**
     * 隐藏高亮动画
     */
    private fun hideHighlight() {
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(highlightAlpha, 0f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                highlightAlpha = animation.animatedValue as Float
                highlightScale = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    /**
     * 启动位置切换过渡动画
     */
    private fun startTransitionAnimation(from: GridPosition, to: GridPosition) {
        previousHighlightPosition = from
        transitionProgress = 0f
        
        transitionAnimator?.cancel()
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                transitionProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 设置关联的RecyclerView，用于获取实际item尺寸
     */
    public fun setRecyclerView(rv: RecyclerView) {
        recyclerView = rv
        // 监听RecyclerView布局变化，重新计算网格
        rv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            calculateCellRectsFromRecyclerView()
        }
    }

    /**
     * 从RecyclerView获取实际item位置来计算网格
     */
    private fun calculateCellRectsFromRecyclerView() {
        recyclerView?.let { rv ->
            cellRects.clear()
            
            // 获取RecyclerView中实际的item位置
            for (i in 0 until minOf(24, rv.childCount)) {
                val child = rv.getChildAt(i)
                if (child != null) {
                    val left = child.left.toFloat()
                    val top = child.top.toFloat()
                    val right = child.right.toFloat()
                    val bottom = child.bottom.toFloat()
                    cellRects.add(RectF(left, top, right, bottom))
                }
            }
            
            // 如果RecyclerView还没有24个child，用计算的方式补充
            if (cellRects.size < 24 && rv.childCount > 0) {
                val firstChild = rv.getChildAt(0)
                val cellWidth = firstChild.width.toFloat()
                val cellHeight = firstChild.height.toFloat()
                
                for (i in cellRects.size until 24) {
                    val row = i / GridPosition.Companion.COLUMNS
                    val col = i % GridPosition.Companion.COLUMNS
                    val left = paddingLeft + col * cellWidth
                    val top = paddingTop + row * cellHeight
                    cellRects.add(RectF(left, top, left + cellWidth, top + cellHeight))
                }
            }
            
            invalidate()
        }
    }
    
    /**
     * 清理资源
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gridAnimator?.cancel()
        highlightAnimator?.cancel()
        transitionAnimator?.cancel()
    }
    private companion object {
        private const val MIN_HIGHLIGHT_SPAN: Int = 1
    }
}