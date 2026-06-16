package com.susking.ephone_s.desktop.ui.drag

import android.util.Log
import com.susking.ephone_s.desktop.model.AppIcon

/**
 * 网格位置管理器
 * 管理单个桌面页面的4×6网格占用状态
 */
class GridPositionManager {
    // 网格占用状态，true表示已占用，false表示空闲
    private val grid: Array<BooleanArray> = Array(GridPosition.ROWS) {
        BooleanArray(GridPosition.COLUMNS) { false }
    }
    
    /**
     * 使用图标列表初始化网格状态
     */
    fun initializeGrid(icons: List<AppIcon>) {
        clearGrid()
        icons.forEach { icon ->
            if (icon.hasValidGridPosition()) {
                occupyPosition(icon.gridRow, icon.gridColumn)
            }
        }
    }
    
    /**
     * 清空整个网格
     */
    fun clearGrid() {
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                grid[row][col] = false
            }
        }
    }
    
    /**
     * 检查指定位置是否为空
     */
    fun isPositionEmpty(row: Int, column: Int): Boolean {
        if (row !in 0 until GridPosition.ROWS || column !in 0 until GridPosition.COLUMNS) {
            return false
        }
        return !grid[row][column]
    }
    
    /**
     * 检查GridPosition是否为空
     */
    fun isPositionEmpty(position: GridPosition): Boolean {
        return isPositionEmpty(position.row, position.column)
    }
    
    /**
     * 占用一个位置
     */
    fun occupyPosition(row: Int, column: Int) {
        if (row in 0 until GridPosition.ROWS && column in 0 until GridPosition.COLUMNS) {
            grid[row][column] = true
        }
    }
    
    /**
     * 占用GridPosition
     */
    fun occupyPosition(position: GridPosition) {
        occupyPosition(position.row, position.column)
    }
    
    /**
     * 释放一个位置
     */
    fun releasePosition(row: Int, column: Int) {
        if (row in 0 until GridPosition.ROWS && column in 0 until GridPosition.COLUMNS) {
            grid[row][column] = false
        }
    }
    
    /**
     * 释放GridPosition
     */
    fun releasePosition(position: GridPosition) {
        releasePosition(position.row, position.column)
    }
    
    /**
     * 查找最近的空白位置
     * 使用螺旋搜索算法从目标位置向外扩展
     * 
     * @param targetRow 目标行
     * @param targetColumn 目标列
     * @return 最近的空位置，如果没有则返回null
     */
    fun findNearestEmptyPosition(targetRow: Int, targetColumn: Int): GridPosition? {
        val target = GridPosition(targetRow, targetColumn)
        
        // 如果目标位置本身为空，直接返回
        if (isPositionEmpty(target)) {
            return target
        }
        
        // 使用优先队列进行螺旋搜索
        val visited = mutableSetOf<GridPosition>()
        val queue = mutableListOf(target)
        visited.add(target)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            
            // 检查所有相邻位置
            for (adjacent in current.getAdjacentPositions()) {
                if (adjacent in visited) continue
                
                visited.add(adjacent)
                
                // 找到空位置
                if (isPositionEmpty(adjacent)) {
                    return adjacent
                }
                
                queue.add(adjacent)
            }
        }
        
        // 如果螺旋搜索失败，尝试线性搜索整个网格
        return findFirstEmptyPosition()
    }
    
    /**
     * 查找第一个空位置（从左到右，从上到下）
     */
    fun findFirstEmptyPosition(): GridPosition? {
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                if (isPositionEmpty(row, col)) {
                    return GridPosition(row, col)
                }
            }
        }
        return null
    }
    
    /**
     * 获取所有空位置
     */
    fun getAllEmptyPositions(): List<GridPosition> {
        val emptyPositions = mutableListOf<GridPosition>()
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                if (isPositionEmpty(row, col)) {
                    emptyPositions.add(GridPosition(row, col))
                }
            }
        }
        return emptyPositions
    }
    
    /**
     * 获取所有已占用的位置
     */
    fun getAllOccupiedPositions(): List<GridPosition> {
        val occupiedPositions = mutableListOf<GridPosition>()
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                if (!isPositionEmpty(row, col)) {
                    occupiedPositions.add(GridPosition(row, col))
                }
            }
        }
        return occupiedPositions
    }
    
    /**
     * 检查网格是否已满
     */
    fun isFull(): Boolean {
        return findFirstEmptyPosition() == null
    }
    
    /**
     * 获取空位置数量
     */
    fun getEmptyCount(): Int {
        var count = 0
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                if (isPositionEmpty(row, col)) {
                    count++
                }
            }
        }
        return count
    }
    
    /**
     * 获取已占用位置数量
     */
    fun getOccupiedCount(): Int {
        return GridPosition.MAX_POSITIONS - getEmptyCount()
    }
    
    /**
     * 打印网格状态（用于调试）
     */
    fun printGrid() {
        val sb = StringBuilder()
        sb.append("Grid Status (X=occupied, .=empty):\n")
        for (row in 0 until GridPosition.ROWS) {
            for (col in 0 until GridPosition.COLUMNS) {
                sb.append(if (grid[row][col]) "X " else ". ")
            }
            sb.append("\n")
        }
        Log.d("GridPositionManager", sb.toString())
    }
}