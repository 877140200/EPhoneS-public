package com.susking.ephone_s.desktop.ui.drag

import kotlin.math.abs

/**
 * 网格位置数据类
 * 表示桌面4×6网格中的一个位置
 * 
 * @property row 行坐标 (0-5)
 * @property column 列坐标 (0-3)
 */
data class GridPosition(
    val row: Int,
    val column: Int
) {
    companion object {
        const val ROWS = 6
        const val COLUMNS = 4
        const val MAX_POSITIONS = ROWS * COLUMNS // 24
        
        /**
         * 从线性索引创建GridPosition
         * @param index 线性索引 (0-23)
         */
        fun fromLinearIndex(index: Int): GridPosition {
            return GridPosition(
                row = index / COLUMNS,
                column = index % COLUMNS
            )
        }
        
        /**
         * 创建一个无效的位置（用于表示未分配）
         */
        fun invalid(): GridPosition = GridPosition(-1, -1)
    }
    
    /**
     * 检查位置是否有效（在网格范围内）
     */
    fun isValid(): Boolean {
        return row in 0 until ROWS && column in 0 until COLUMNS
    }
    
    /**
     * 转换为线性索引
     */
    fun toLinearIndex(): Int {
        return row * COLUMNS + column
    }
    
    /**
     * 计算与另一个位置的曼哈顿距离
     */
    fun distanceTo(other: GridPosition): Int {
        return abs(row - other.row) + abs(column - other.column)
    }
    
    /**
     * 获取相邻位置列表（上下左右）
     */
    fun getAdjacentPositions(): List<GridPosition> {
        return listOf(
            GridPosition(row - 1, column), // 上
            GridPosition(row + 1, column), // 下
            GridPosition(row, column - 1), // 左
            GridPosition(row, column + 1)  // 右
        ).filter { it.isValid() }
    }
}