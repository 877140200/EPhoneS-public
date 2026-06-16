package com.susking.ephone_s.desktop.ui.drag

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.desktop.model.AppIcon
import com.susking.ephone_s.desktop.ui.DesktopViewModel

/**
 * 网格拖拽辅助类
 * 处理拖拽事件、坐标转换和拖拽状态管理
 */
class GridDragHelper(
    private val viewModel: DesktopViewModel
) {
    /**
     * 拖拽状态数据类
     */
    data class DragState(
        val fromPage: Int,
        val fromPosition: GridPosition,
        val icon: AppIcon,
        val isDragging: Boolean = false
    )
    
    // 当前拖拽状态
    private var currentDragState: DragState? = null
    
    /**
     * 开始拖拽
     * @param pageNumber 页面编号
     * @param icon 被拖拽的图标
     */
    fun startDrag(pageNumber: Int, icon: AppIcon) {
        // 如果图标没有网格坐标，尝试从页面中查找
        val position = if (icon.hasValidGridPosition()) {
            GridPosition(icon.gridRow, icon.gridColumn)
        } else {
            // 从页面列表中查找图标的索引位置
            val pageIcons = viewModel.pages.value?.getOrNull(pageNumber)
            val iconIndex = pageIcons?.indexOfFirst { it.name == icon.name && it.iconUri == icon.iconUri } ?: -1
            
            if (iconIndex >= 0) {
                // 根据线性索引计算网格位置
                GridPosition.fromLinearIndex(iconIndex)
            } else {
                Log.w("GridDragHelper", "Icon not found in page $pageNumber: ${icon.name}")
                GridPosition.invalid()
            }
        }
        
        currentDragState = DragState(
            fromPage = pageNumber,
            fromPosition = position,
            icon = icon,
            isDragging = true
        )
        
        Log.d("GridDragHelper", "Started drag: icon=${icon.name}, " +
                "from page=$pageNumber, position=(${position.row}, ${position.column})")
    }
    
    /**
     * 从Dock开始拖拽
     * @param dockPosition Dock中的位置索引
     * @param icon 被拖拽的图标
     */
    fun startDragFromDock(dockPosition: Int, icon: AppIcon) {
        currentDragState = DragState(
            fromPage = -1, // -1表示来自Dock
            fromPosition = GridPosition(0, dockPosition), // 存储Dock索引在column中
            icon = icon,
            isDragging = true
        )
        
        Log.d("GridDragHelper", "Started drag from dock: icon=${icon.name}, " +
                "dock position=$dockPosition")
    }
    
    /**
     * 将屏幕坐标转换为网格坐标
     * 
     * @param x 屏幕X坐标（相对于RecyclerView）
     * @param y 屏幕Y坐标（相对于RecyclerView）
     * @param recyclerView 桌面RecyclerView
     * @return 网格位置，如果无效则返回null
     */
    fun calculateDropPosition(
        x: Float,
        y: Float,
        recyclerView: RecyclerView
    ): GridPosition? {
        val cellWidth = recyclerView.width.toFloat() / GridPosition.COLUMNS
        val cellHeight = recyclerView.height.toFloat() / GridPosition.ROWS
        
        val column = (x / cellWidth).toInt().coerceIn(0, GridPosition.COLUMNS - 1)
        val row = (y / cellHeight).toInt().coerceIn(0, GridPosition.ROWS - 1)
        
        val position = GridPosition(row, column)
        
        Log.d("GridDragHelper", "Calculated drop position: " +
                "screen=(${x}, ${y}), grid=(${row}, ${column})")
        
        return if (position.isValid()) position else null
    }
    
    /**
     * 处理拖拽放置到桌面
     * 
     * @param toPage 目标页面
     * @param toPosition 目标网格位置
     * @return 是否成功放置
     */
    fun handleDropToDesktop(
        toPage: Int,
        toPosition: GridPosition
    ): Boolean {
        val dragState = currentDragState ?: return false
        
        Log.d("GridDragHelper", "Handling drop to desktop: " +
                "from page=${dragState.fromPage}, to page=$toPage, " +
                "target position=(${toPosition.row}, ${toPosition.column})")
        
        return try {
            if (dragState.fromPage == -1) {
                // 从Dock拖到桌面
                val dockPosition = dragState.fromPosition.column
                viewModel.moveIconFromDockToGrid(dockPosition, toPage, toPosition)
            } else {
                // 在桌面内移动
                viewModel.moveIconInGrid(
                    fromPage = dragState.fromPage,
                    fromPosition = dragState.fromPosition,
                    toPage = toPage,
                    toPosition = toPosition
                )
            }
            true
        } catch (e: Exception) {
            Log.e("GridDragHelper", "Failed to handle drop", e)
            false
        }
    }
    
    /**
     * 处理拖拽放置到Dock
     * 
     * @param dockPosition Dock中的目标位置
     * @return 是否成功放置
     */
    fun handleDropToDock(dockPosition: Int): Boolean {
        val dragState = currentDragState ?: return false
        
        Log.d("GridDragHelper", "Handling drop to dock: " +
                "from page=${dragState.fromPage}, dock position=$dockPosition")
        
        return try {
            if (dragState.fromPage == -1) {
                // 在Dock内移动
                val fromDockPosition = dragState.fromPosition.column
                viewModel.moveIconWithinDock(fromDockPosition, dockPosition)
            } else {
                // 从桌面拖到Dock
                viewModel.moveIconFromGridToDock(
                    fromPage = dragState.fromPage,
                    fromPosition = dragState.fromPosition,
                    toDockPosition = dockPosition
                )
            }
            true
        } catch (e: Exception) {
            Log.e("GridDragHelper", "Failed to handle drop to dock", e)
            false
        }
    }
    
    /**
     * 结束拖拽
     */
    fun endDrag() {
        Log.d("GridDragHelper", "Ended drag")
        currentDragState = null
    }
    
    /**
     * 获取当前拖拽状态
     */
    fun getCurrentDragState(): DragState? = currentDragState
    
    /**
     * 检查是否正在拖拽
     */
    fun isDragging(): Boolean = currentDragState?.isDragging == true
    
    /**
     * 检查拖拽是否来自Dock
     */
    fun isDraggingFromDock(): Boolean = currentDragState?.fromPage == -1
    
    /**
     * 检查是否可以放置到指定位置
     * 
     * @param pageNumber 页面编号
     * @param position 目标位置
     * @return 是否可以放置
     */
    fun canDropAt(pageNumber: Int, position: GridPosition): Boolean {
        if (!position.isValid()) return false
        
        val dragState = currentDragState ?: return false
        
        // 检查是否是拖拽回原位置
        if (dragState.fromPage == pageNumber && 
            dragState.fromPosition == position) {
            return true
        }
        
        // 检查目标位置是否为空
        val positionManager = viewModel.getPositionManager(pageNumber)
        return positionManager.isPositionEmpty(position)
    }
}