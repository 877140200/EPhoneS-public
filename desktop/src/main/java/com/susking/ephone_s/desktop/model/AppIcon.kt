package com.susking.ephone_s.desktop.model

/**
 * 应用图标数据模型
 *
 * @property name 应用名称
 * @property iconUri 图标URI
 * @property gridRow 网格行坐标 (0-5)，-1表示未分配或在Dock中
 * @property gridColumn 网格列坐标 (0-3)，-1表示未分配或在Dock中
 */
data class AppIcon(
    val name: String,
    val iconUri: String,
    val gridRow: Int = -1,
    val gridColumn: Int = -1
) {
    /**
     * 检查图标是否有有效的网格位置
     */
    fun hasValidGridPosition(): Boolean {
        return gridRow >= 0 && gridColumn >= 0
    }
    
    /**
     * 创建一个新的AppIcon实例，更新网格位置
     */
    fun withGridPosition(row: Int, column: Int): AppIcon {
        return copy(gridRow = row, gridColumn = column)
    }
    
    /**
     * 清除网格位置（设置为-1）
     */
    fun clearGridPosition(): AppIcon {
        return copy(gridRow = -1, gridColumn = -1)
    }
}