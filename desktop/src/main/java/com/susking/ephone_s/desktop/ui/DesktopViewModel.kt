package com.susking.ephone_s.desktop.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.desktop.ui.drag.GridDragHelper
import com.susking.ephone_s.desktop.api.ThemeProvider
import com.susking.ephone_s.desktop.data.DesktopRepository
import com.susking.ephone_s.desktop.model.AppIcon
import com.susking.ephone_s.desktop.ui.drag.GridPosition
import com.susking.ephone_s.desktop.ui.drag.GridPositionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DesktopViewModel @Inject constructor(
    private val themeProvider: ThemeProvider,
    private val desktopRepository: DesktopRepository
) : ViewModel() {

    private val _pages = MutableLiveData<MutableList<MutableList<AppIcon>>>()
    val pages: LiveData<MutableList<MutableList<AppIcon>>> = _pages

    private val _dockItems = MutableLiveData<MutableList<AppIcon>>()
    val dockItems: LiveData<MutableList<AppIcon>> = _dockItems
    
    // 当前壁纸URI (通过ThemeProvider获取)
    val currentWallpaper: LiveData<String?> = themeProvider.getWallpaperUri().asLiveData()

    // Dock背景颜色 (通过ThemeProvider获取)
    val dockBackgroundColor: LiveData<Int> = themeProvider.getDockBackgroundColor().asLiveData()

    // Dock背景透明度 (通过ThemeProvider获取)
    val dockBackgroundAlpha: LiveData<Int> = themeProvider.getDockBackgroundAlpha().asLiveData()

    // Dock圆角半径 (通过ThemeProvider获取)
    val dockCornerRadiusDp: LiveData<Float> = themeProvider.getDockCornerRadiusDp().asLiveData()

    // 应用名称文字颜色 (通过ThemeProvider获取)
    val appLabelColor: LiveData<Int> = themeProvider.getAppLabelColor().asLiveData()

    // 应用名称文字阴影颜色 (通过ThemeProvider获取)
    val appLabelShadowColor: LiveData<Int> = themeProvider.getAppLabelShadowColor().asLiveData()

    // 应用名称文字阴影开关 (通过ThemeProvider获取)
    val isAppLabelShadowEnabled: LiveData<Boolean> = themeProvider.isAppLabelShadowEnabled().asLiveData()
    
    // 网格位置管理器，每个页面一个
    private val positionManagers = mutableMapOf<Int, GridPositionManager>()
    
    // GridDragHelper实例
    private var gridDragHelper: GridDragHelper? = null
 
    init {
        viewModelScope.launch {
            themeProvider.getIconPaths().collect { iconPaths ->
                // 先设置默认值，确保界面有数据
                _pages.value = mutableListOf(
                    mutableListOf(
                        AppIcon("QQ", iconPaths["QQ"] ?: ""),
                        AppIcon("世界书集", iconPaths["世界书集"] ?: ""),
                        AppIcon("主题", iconPaths["主题"] ?: ""),
                        AppIcon("相册", iconPaths["相册"] ?: ""),
                        AppIcon("商城", iconPaths["商城"] ?: ""),
                        AppIcon("支付宝", iconPaths["支付宝"] ?: ""),
                        AppIcon("关系图", iconPaths["关系图"] ?: ""),
                        AppIcon("课程表", iconPaths["课程表"] ?: ""),
                        AppIcon("健康", iconPaths["健康"] ?: "")
                    ),
                    mutableListOf(
                        AppIcon("预设", iconPaths["预设"] ?: ""),
                        AppIcon("X", iconPaths["X"] ?: "")
                    )
                )
                _dockItems.value = mutableListOf(
                    AppIcon("设置", iconPaths["设置"] ?: ""),
                    AppIcon("???", iconPaths["???"] ?: ""),
                    AppIcon("CPhone", iconPaths["CPhone"] ?: ""),
                    AppIcon("酒馆记录", iconPaths["酒馆记录"] ?: "")
                )
                
                // 然后尝试加载持久化的数据并覆盖
                try {
                    val savedPages = desktopRepository.getPages().first()
                    val savedDock = desktopRepository.getDockItems().first()
                    
                    if (savedPages != null) {
                        _pages.value = savedPages.map { it.toMutableList() }.toMutableList()
                        // 更新已存在应用的图标URI
                        updateIconUris(iconPaths)
                        // 检查并添加新应用
                        addMissingAppsToLayout(iconPaths)
                    }
                    if (savedDock != null) {
                        _dockItems.value = savedDock.toMutableList()
                        // 更新Dock中的图标URI
                        updateDockIconUris(iconPaths)
                    }
                } catch (e: Exception) {
                    // 如果读取失败，使用默认值即可
                    android.util.Log.e("DesktopViewModel", "Failed to load saved layout", e)
                }
                
                // 自动为所有图标分配网格坐标（数据迁移）
                autoAssignGridPositions()
                
                android.util.Log.d("DesktopViewModel", "Desktop initialization complete with grid positions")
            }
        }
    }

    fun moveIcon(fromPage: Int, fromPosition: Int, toPage: Int, toPosition: Int) {
        val pageFrom = _pages.value?.getOrNull(fromPage) ?: return
        val pageTo = _pages.value?.getOrNull(toPage) ?: return

        val icon = pageFrom.removeAt(fromPosition)
        // Clamp toPosition to be within the valid range for 'add' after removal
        val finalToPosition = toPosition.coerceAtMost(pageTo.size)
        pageTo.add(finalToPosition, icon)

        _pages.value = _pages.value // Trigger update
        savePages() // 保存到持久化存储
    }

    fun moveIconToDock(fromPage: Int, fromPosition: Int, toPosition: Int) {
        if (fromPage == -1) { // Move within the dock
            moveIconWithinDock(fromPosition, toPosition)
            return
        }

        val pageFrom = _pages.value?.getOrNull(fromPage) ?: return
        val dock = _dockItems.value ?: return

        if (dock.size < 4) {
            val icon = pageFrom.removeAt(fromPosition)
            dock.add(toPosition, icon)
            _pages.value = _pages.value // Trigger update
            _dockItems.value = _dockItems.value // Trigger update
            savePages() // 保存页面
            saveDock() // 保存Dock
        }
    }

    fun moveIconFromDock(fromPosition: Int, toPage: Int, toPosition: Int) {
        val dock = _dockItems.value ?: return
        val pageTo = _pages.value?.getOrNull(toPage) ?: return

        // 边界检查，防止索引越界
        if (fromPosition < 0 || fromPosition >= dock.size) {
            android.util.Log.e("DesktopViewModel", "Invalid fromPosition: $fromPosition, dock size: ${dock.size}")
            return
        }

        val icon = dock.removeAt(fromPosition)
        val finalToPosition = toPosition.coerceAtMost(pageTo.size)
        pageTo.add(finalToPosition, icon)

        _pages.value = _pages.value // Trigger update
        _dockItems.value = _dockItems.value // Trigger update
        savePages() // 保存页面
        saveDock() // 保存Dock
    }
    
    // 持久化保存方法
    private fun savePages() {
        viewModelScope.launch {
            try {
                _pages.value?.let { pages ->
                    desktopRepository.savePages(pages)
                }
            } catch (e: Exception) {
                android.util.Log.e("DesktopViewModel", "Failed to save pages", e)
            }
        }
    }
    
    private fun saveDock() {
        viewModelScope.launch {
            try {
                _dockItems.value?.let { dock ->
                    desktopRepository.saveDockItems(dock)
                }
            } catch (e: Exception) {
                android.util.Log.e("DesktopViewModel", "Failed to save dock", e)
            }
        }
    }
    
    // ========== 新增：网格拖拽相关方法 ==========
    
    /**
     * 获取指定页面的GridPositionManager
     */
    fun getPositionManager(pageNumber: Int): GridPositionManager {
        return positionManagers.getOrPut(pageNumber) {
            GridPositionManager().apply {
                _pages.value?.getOrNull(pageNumber)?.let { icons ->
                    initializeGrid(icons)
                }
            }
        }
    }
    
    /**
     * 初始化GridDragHelper
     */
    fun initGridDragHelper(): GridDragHelper {
        if (gridDragHelper == null) {
            gridDragHelper = GridDragHelper(this)
        }
        return gridDragHelper!!
    }
    
    /**
     * 获取GridDragHelper
     */
    fun getGridDragHelper(): GridDragHelper? = gridDragHelper
    
    /**
     * 在网格中移动图标（支持单页面和跨页面）
     */
    fun moveIconInGrid(
        fromPage: Int,
        fromPosition: GridPosition,
        toPage: Int,
        toPosition: GridPosition
    ) {
        val pageFrom = _pages.value?.getOrNull(fromPage) ?: return
        val pageTo = _pages.value?.getOrNull(toPage) ?: return
        
        // 查找源图标 - 先尝试通过网格坐标查找，如果找不到则通过索引查找
        val icon = pageFrom.find {
            it.gridRow == fromPosition.row && it.gridColumn == fromPosition.column
        } ?: run {
            // 如果通过网格坐标找不到，尝试通过线性索引查找
            val linearIndex = fromPosition.toLinearIndex()
            pageFrom.getOrNull(linearIndex)
        } ?: run {
            android.util.Log.w("DesktopViewModel", "Icon not found at position (${fromPosition.row}, ${fromPosition.column})")
            return
        }
        
        val fromManager = getPositionManager(fromPage)
        val toManager = getPositionManager(toPage)
        
        // 处理碰撞检测
        val finalPosition = if (toManager.isPositionEmpty(toPosition)) {
            toPosition
        } else {
            // 如果目标位置被占用，查找最近的空位
            toManager.findNearestEmptyPosition(toPosition.row, toPosition.column)
                ?: run {
                    android.util.Log.w("DesktopViewModel", "No empty position found on page $toPage")
                    return
                }
        }
        
        // 释放源位置（如果图标有有效的网格位置）
        if (icon.hasValidGridPosition()) {
            fromManager.releasePosition(fromPosition)
        }
        pageFrom.remove(icon)
        
        // 占用目标位置
        toManager.occupyPosition(finalPosition)
        val updatedIcon = icon.withGridPosition(finalPosition.row, finalPosition.column)
        pageTo.add(updatedIcon)
        
        // 触发UI更新
        _pages.value = _pages.value
        savePages()
        
        android.util.Log.d("DesktopViewModel", "Moved icon ${icon.name} from " +
                "page $fromPage (${fromPosition.row},${fromPosition.column}) to " +
                "page $toPage (${finalPosition.row},${finalPosition.column})")
    }
    
    /**
     * 从Dock移动图标到网格
     */
    fun moveIconFromDockToGrid(
        fromDockPosition: Int,
        toPage: Int,
        toPosition: GridPosition
    ) {
        val dock = _dockItems.value ?: return
        val pageTo = _pages.value?.getOrNull(toPage) ?: return
        
        if (fromDockPosition !in dock.indices) return
        
        val icon = dock[fromDockPosition]
        val toManager = getPositionManager(toPage)
        
        // 处理碰撞检测
        val finalPosition = if (toManager.isPositionEmpty(toPosition)) {
            toPosition
        } else {
            toManager.findNearestEmptyPosition(toPosition.row, toPosition.column)
                ?: run {
                    android.util.Log.w("DesktopViewModel", "No empty position on page $toPage")
                    return
                }
        }
        
        // 从Dock移除
        dock.removeAt(fromDockPosition)
        
        // 添加到桌面
        toManager.occupyPosition(finalPosition)
        val updatedIcon = icon.withGridPosition(finalPosition.row, finalPosition.column)
        pageTo.add(updatedIcon)
        
        // 触发UI更新
        _dockItems.value = _dockItems.value
        _pages.value = _pages.value
        saveDock()
        savePages()
        
        android.util.Log.d("DesktopViewModel", "Moved icon ${icon.name} from " +
                "dock position $fromDockPosition to page $toPage (${finalPosition.row},${finalPosition.column})")
    }
    
    /**
     * 从网格移动图标到Dock
     */
    fun moveIconFromGridToDock(
        fromPage: Int,
        fromPosition: GridPosition,
        toDockPosition: Int
    ) {
        val pageFrom = _pages.value?.getOrNull(fromPage) ?: return
        val dock = _dockItems.value ?: return
        
        // Dock最多4个图标
        if (dock.size >= 4) {
            android.util.Log.w("DesktopViewModel", "Dock is full")
            return
        }
        
        // 查找源图标
        val icon = pageFrom.find {
            it.gridRow == fromPosition.row && it.gridColumn == fromPosition.column
        } ?: return
        
        val fromManager = getPositionManager(fromPage)
        
        // 从桌面移除
        fromManager.releasePosition(fromPosition)
        pageFrom.remove(icon)
        
        // 添加到Dock（清除网格坐标）
        val dockIcon = icon.clearGridPosition()
        val finalDockPosition = toDockPosition.coerceIn(0, dock.size)
        dock.add(finalDockPosition, dockIcon)
        
        // 触发UI更新
        _pages.value = _pages.value
        _dockItems.value = _dockItems.value
        savePages()
        saveDock()
        
        android.util.Log.d("DesktopViewModel", "Moved icon ${icon.name} from " +
                "page $fromPage (${fromPosition.row},${fromPosition.column}) to dock position $finalDockPosition")
    }
    
    /**
     * 在Dock内移动图标
     */
    fun moveIconWithinDock(fromPosition: Int, toPosition: Int) {
        val dock = _dockItems.value ?: return
        
        if (fromPosition !in dock.indices || toPosition < 0) return
        
        val icon = dock.removeAt(fromPosition)
        val finalToPosition = toPosition.coerceIn(0, dock.size)
        dock.add(finalToPosition, icon)
        
        _dockItems.value = _dockItems.value
        saveDock()
        
        android.util.Log.d("DesktopViewModel", "Moved icon ${icon.name} within dock " +
                "from $fromPosition to $finalToPosition")
    }
    
    /**
     * 自动分配网格坐标给所有图标（用于初始化和迁移）
     */
    fun autoAssignGridPositions() {
        val pages = _pages.value ?: return
        
        pages.forEachIndexed { pageIndex, icons ->
            val manager = getPositionManager(pageIndex)
            manager.clearGrid()
            
            val updatedIcons = mutableListOf<AppIcon>()
            
            icons.forEachIndexed { index, icon ->
                val updatedIcon = if (!icon.hasValidGridPosition()) {
                    // 为没有坐标的图标分配位置
                    val position = GridPosition.fromLinearIndex(index)
                    if (position.isValid()) {
                        manager.occupyPosition(position)
                        icon.withGridPosition(position.row, position.column)
                    } else {
                        icon
                    }
                } else {
                    // 已有坐标的图标，更新管理器状态
                    manager.occupyPosition(icon.gridRow, icon.gridColumn)
                    icon
                }
                updatedIcons.add(updatedIcon)
            }
            
            // 替换整个图标列表
            icons.clear()
            icons.addAll(updatedIcons)
            
            // 打印调试信息
            manager.printGrid()
            android.util.Log.d("DesktopViewModel", "Page $pageIndex: ${icons.size} icons, " +
                    "${manager.getEmptyCount()} empty positions")
        }
        
        _pages.value = _pages.value
        savePages()
        
        android.util.Log.d("DesktopViewModel", "Auto-assigned grid positions for all icons")
    }
    
    /**
     * 更新桌面页面中已存在图标的URI
     * 用于修复旧布局中图标URI过时的问题
     */
    private fun updateIconUris(iconPaths: Map<String, String>) {
        val pages = _pages.value ?: return
        var hasUpdates = false
        
        pages.forEach { page ->
            val updatedIcons = mutableListOf<AppIcon>()
            page.forEach { icon ->
                val newUri = iconPaths[icon.name]
                if (newUri != null && newUri != icon.iconUri) {
                    val updatedIcon = icon.copy(iconUri = newUri)
                    updatedIcons.add(updatedIcon)
                    hasUpdates = true
                } else {
                    updatedIcons.add(icon)
                }
            }
            page.clear()
            page.addAll(updatedIcons)
        }
        
        if (hasUpdates) {
            _pages.value = _pages.value
            savePages()
        }
    }
    
    /**
     * 更新Dock中已存在图标的URI
     */
    private fun updateDockIconUris(iconPaths: Map<String, String>) {
        val dock = _dockItems.value ?: return
        var hasUpdates = false
        
        val updatedDock = mutableListOf<AppIcon>()
        dock.forEach { icon ->
            val newUri = iconPaths[icon.name]
            if (newUri != null && newUri != icon.iconUri) {
                val updatedIcon = icon.copy(iconUri = newUri)
                updatedDock.add(updatedIcon)
                hasUpdates = true
            } else {
                updatedDock.add(icon)
            }
        }
        
        if (hasUpdates) {
            _dockItems.value = updatedDock.toMutableList()
            saveDock()
        }
    }
    
    /**
     * 检查并添加缺失的应用到布局中
     * 用于兼容用户的旧布局,自动添加新应用
     */
    private fun addMissingAppsToLayout(iconPaths: Map<String, String>) {
        val pages = _pages.value ?: return
        
        // 定义所有应该存在的应用列表
        val allApps = listOf("QQ", "世界书集", "主题", "相册", "商城", "支付宝", "预设", "X", "关系图", "课程表", "健康")
        
        // 获取当前布局中已有的应用
        val existingApps = pages.flatten().map { it.name }.toSet()
        
        // 找出缺失的应用
        val missingApps = allApps.filter { it !in existingApps }
        
        if (missingApps.isEmpty()) {
            android.util.Log.d("DesktopViewModel", "No missing apps found")
            return
        }
        
        android.util.Log.d("DesktopViewModel", "Found missing apps: $missingApps")
        
        // 将缺失的应用添加到第一页
        if (pages.isNotEmpty()) {
            val firstPage = pages[0]
            missingApps.forEach { appName ->
                val icon = AppIcon(appName, iconPaths[appName] ?: "")
                firstPage.add(icon)
                android.util.Log.d("DesktopViewModel", "Added missing app: $appName")
            }
            
            // 触发更新并保存
            _pages.value = _pages.value
            savePages()
        }
    }
}