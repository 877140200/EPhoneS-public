package com.susking.ephone_s.desktop.ui

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.tabs.TabLayoutMediator
import com.susking.ephone_s.desktop.ui.drag.GridDragHelper
import com.susking.ephone_s.desktop.ui.drag.ItemMoveCallback
import com.susking.ephone_s.desktop.api.DesktopNavigator
import com.susking.ephone_s.desktop.databinding.FragmentDesktopContainerBinding
import com.susking.ephone_s.desktop.model.AppIcon
import com.susking.ephone_s.desktop.ui.adapter.AppIconAdapter
import com.susking.ephone_s.desktop.ui.adapter.ViewPagerAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DesktopContainerFragment : Fragment() {
 
    @Inject
    lateinit var navigator: DesktopNavigator
 
    private var _binding: FragmentDesktopContainerBinding? = null
    private val binding get() = _binding!!
    private val desktopViewModel: DesktopViewModel by activityViewModels()
    private lateinit var dockAdapter: AppIconAdapter
    private lateinit var gridDragHelper: GridDragHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDesktopContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            applySystemBarInsets(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewPager()
        setupDock()
        setupOnBackPressed()
        observeViewModel()
        setupRootDragListener()
    }

    private fun observeViewModel() {
        desktopViewModel.dockItems.observe(viewLifecycleOwner) { newDockItems ->
            // 更新Adapter的数据源，Dock不需要null占位符，直接转换类型即可。
            dockAdapter.icons = newDockItems.map { it as AppIcon? }.toMutableList()
            // 通知Adapter刷新。
            dockAdapter.notifyDataSetChanged()
        }

        desktopViewModel.currentWallpaper.observe(viewLifecycleOwner) { wallpaperUri ->
            updateWallpaper(wallpaperUri)
        }

        desktopViewModel.dockBackgroundColor.observe(viewLifecycleOwner) {
            updateDockBackground()
        }

        desktopViewModel.dockBackgroundAlpha.observe(viewLifecycleOwner) {
            updateDockBackground()
        }

        desktopViewModel.dockCornerRadiusDp.observe(viewLifecycleOwner) {
            updateDockBackground()
        }

        desktopViewModel.appLabelColor.observe(viewLifecycleOwner) {
            updateDockLabelStyle()
        }

        desktopViewModel.appLabelShadowColor.observe(viewLifecycleOwner) {
            updateDockLabelStyle()
        }

        desktopViewModel.isAppLabelShadowEnabled.observe(viewLifecycleOwner) {
            updateDockLabelStyle()
        }
    }

    private fun updateWallpaper(wallpaperUri: String?): Unit {
        if (wallpaperUri.isNullOrBlank()) {
            binding.desktopWallpaperImage.setImageDrawable(null)
            return
        }

        binding.desktopWallpaperImage.load(wallpaperUri) {
            crossfade(true)
        }
    }

    private fun applySystemBarInsets(left: Int, top: Int, right: Int, bottom: Int): Unit {
        // 根布局不设置padding，确保主题壁纸能延伸到状态栏和导航栏下方。
        binding.viewPager.setPadding(left, top, right, 0)
        binding.viewPager.clipToPadding = false

        val dockLayoutParams: ViewGroup.MarginLayoutParams =
            binding.bottomDockRecyclerView.layoutParams as ViewGroup.MarginLayoutParams
        dockLayoutParams.bottomMargin = DEFAULT_DOCK_BOTTOM_MARGIN_DP.toPx() + bottom
        binding.bottomDockRecyclerView.layoutParams = dockLayoutParams
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun updateDockBackground(): Unit {
        val backgroundColor: Int = desktopViewModel.dockBackgroundColor.value ?: Color.WHITE
        val backgroundAlpha: Int = desktopViewModel.dockBackgroundAlpha.value ?: DEFAULT_DOCK_BACKGROUND_ALPHA
        val cornerRadiusDp: Float = desktopViewModel.dockCornerRadiusDp.value ?: DEFAULT_DOCK_CORNER_RADIUS_DP
        val cornerRadiusPx: Float = cornerRadiusDp * resources.displayMetrics.density
        val dockBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(backgroundColor)
            alpha = backgroundAlpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
        }

        binding.bottomDockRecyclerView.background = dockBackground
    }

    private fun updateDockLabelStyle(): Unit {
        val labelColor: Int = desktopViewModel.appLabelColor.value ?: Color.WHITE
        val labelShadowColor: Int = desktopViewModel.appLabelShadowColor.value ?: Color.BLACK
        val isLabelShadowEnabled: Boolean = desktopViewModel.isAppLabelShadowEnabled.value ?: true

        dockAdapter.updateLabelStyle(labelColor, labelShadowColor, isLabelShadowEnabled)
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.pageIndicator, binding.viewPager) { _, _ -> }.attach()
        binding.pageIndicator.post {
            val tabStrip = binding.pageIndicator.getChildAt(0) as ViewGroup
            for (i in 0 until tabStrip.childCount) {
                val tab = tabStrip.getChildAt(i)
                tab.setPadding(0, 0, 0, 0)
                val params = tab.layoutParams as ViewGroup.MarginLayoutParams
                val marginDp = 6
                val marginPx = (resources.displayMetrics.density * marginDp).toInt()
                params.leftMargin = marginPx
                params.rightMargin = marginPx
                tab.layoutParams = params
                tab.requestLayout()
            }
        }
    }

    private fun setupDock() {
        // 初始化GridDragHelper
        gridDragHelper = desktopViewModel.initGridDragHelper()
        
        // Dock不需要null占位符，但需要转换为nullable类型以匹配adapter签名
        val dockIcons = (desktopViewModel.dockItems.value ?: mutableListOf()).map { it as AppIcon? }.toMutableList()
        dockAdapter = AppIconAdapter(
            icons = dockIcons,
            onIconClick = { icon -> handleDockIconClick(icon) },
            onIconLongClick = { view, icon ->
                // 从Dock开始拖拽时通知GridDragHelper
                val dockPosition = dockAdapter.icons.indexOf(icon)
                gridDragHelper.startDragFromDock(dockPosition, icon)
                
                val item = ClipData.Item("-1,$dockPosition")
                val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                val dragData = ClipData("icon", mimeTypes, item)
                val shadowBuilder = View.DragShadowBuilder(view)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(dragData, shadowBuilder, view, 0)
                } else {
                    @Suppress("DEPRECATION")
                    view.startDrag(dragData, shadowBuilder, view, 0)
                }
                true
            },
            isDock = true // 标记这是Dock栏
        )
        binding.bottomDockRecyclerView.adapter = dockAdapter
        
        // 使用自定义的居中GridLayoutManager
        val layoutManager = object : GridLayoutManager(context, 4) {
            override fun canScrollHorizontally(): Boolean = false
            override fun canScrollVertically(): Boolean = false
            
            override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                super.onLayoutChildren(recycler, state)
                
                // 计算总宽度和items数量
                val itemCount = itemCount
                if (itemCount == 0) return
                
                val totalWidth = width - paddingLeft - paddingRight
                val itemWidth = totalWidth / spanCount
                
                // 计算需要的偏移量来居中显示
                val actualItemCount = minOf(itemCount, spanCount)
                val usedWidth = itemWidth * actualItemCount
                val offset = (totalWidth - usedWidth) / 2
                
                // 调整每个item的位置
                for (i in 0 until childCount) {
                    val child = getChildAt(i) ?: continue
                    val lp = child.layoutParams as LayoutParams
                    
                    // 只在第一行应用偏移
                    if (lp.spanIndex == 0) {
                        child.offsetLeftAndRight(offset)
                    } else {
                        child.offsetLeftAndRight(offset)
                    }
                }
            }
        }
        binding.bottomDockRecyclerView.layoutManager = layoutManager
        
        // 设置RecyclerView属性
        binding.bottomDockRecyclerView.clipToPadding = false

        val callback = ItemMoveCallback(dockAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.bottomDockRecyclerView)

        binding.bottomDockRecyclerView.setOnDragListener(createGridDockDragListener())
    }
    
    private fun setupRootDragListener() {
        // 在根布局上添加拖拽监听器，处理全局拖拽事件
        binding.root.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // 如果是从Dock开始拖拽，禁用ViewPager的用户交互
                    if (gridDragHelper.isDraggingFromDock()) {
                        binding.viewPager.isUserInputEnabled = false
                    }
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    true
                }
                DragEvent.ACTION_DROP -> {
                    // 在根布局上处理drop，作为兜底方案
                    try {
                        // 判断drop位置更接近Dock还是桌面
                        val dockBounds = intArrayOf(0, 0)
                        binding.bottomDockRecyclerView.getLocationOnScreen(dockBounds)
                        val dockTop = dockBounds[1]
                        
                        val rootBounds = intArrayOf(0, 0)
                        binding.root.getLocationOnScreen(rootBounds)
                        val dropY = rootBounds[1] + event.y
                        
                        // 如果drop位置在Dock区域附近（带容差）
                        val tolerance = 100f
                        if (dropY >= dockTop - tolerance) {
                            // 使用GridDragHelper处理drop到Dock
                            val targetPosition = getPositionFromDropInDock(event.x, event.y)
                            gridDragHelper.handleDropToDock(targetPosition)
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DesktopContainer", "Root drop handling failed", e)
                        false
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // 拖拽结束后恢复ViewPager的用户交互
                    binding.viewPager.isUserInputEnabled = true
                    gridDragHelper.endDrag()
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 在根布局的drop事件中计算Dock的目标位置
     */
    private fun getPositionFromDropInDock(x: Float, y: Float): Int {
        // 将根布局坐标转换为Dock RecyclerView的本地坐标
        val rootLocation = intArrayOf(0, 0)
        binding.root.getLocationOnScreen(rootLocation)
        
        val dockLocation = intArrayOf(0, 0)
        binding.bottomDockRecyclerView.getLocationOnScreen(dockLocation)
        
        val localX = rootLocation[0] + x - dockLocation[0]
        val localY = rootLocation[1] + y - dockLocation[1]
        
        val child = binding.bottomDockRecyclerView.findChildViewUnder(localX, localY)
        return child?.let {
            binding.bottomDockRecyclerView.getChildAdapterPosition(it)
        } ?: dockAdapter.itemCount.coerceAtMost(3) // Dock最多4个图标
    }

    /**
     * 创建Dock的网格拖拽监听器
     */
    private fun createGridDockDragListener(): View.OnDragListener {
        return View.OnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.8f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1.0f
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.alpha = 1.0f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    try {
                        val targetPosition = getPositionFromDrop(event.x, event.y)
                        val success = gridDragHelper.handleDropToDock(targetPosition)
                        
                        if (success) {
                            android.util.Log.d("DesktopContainer",
                                "Successfully dropped icon to dock position $targetPosition")
                        }
                        
                        view.alpha = 1.0f
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("DesktopContainer", "Dock drop failed", e)
                        view.alpha = 1.0f
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    /**
     * 保留旧方法用于兼容
     */
    @Deprecated("Use GridDragHelper instead")
    private fun createDragListener(): View.OnDragListener {
        return View.OnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> true
                DragEvent.ACTION_DROP -> {
                    val item: ClipData.Item = event.clipData.getItemAt(0)
                    val dragData = item.text.toString().split(",")
                    val fromPage = dragData[0].toInt()
                    val fromPosition = dragData[1].toInt()
                    val targetPosition = getPositionFromDrop(event.x, event.y)
                    desktopViewModel.moveIconToDock(fromPage, fromPosition, targetPosition)
                    true
                }
                else -> false
            }
        }
    }

    private fun getPositionFromDrop(x: Float, y: Float): Int {
        val child = binding.bottomDockRecyclerView.findChildViewUnder(x, y)
        return child?.let { binding.bottomDockRecyclerView.getChildAdapterPosition(it) } ?: dockAdapter.itemCount
    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 如果当前是在桌面，则显示退出确认对话框
                if (parentFragmentManager.backStackEntryCount == 0) {
                    showExitConfirmationDialog()
                } else {
                    // 如果不在桌面，则执行默认的返回操作
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("退出应用")
            .setMessage("您确定要退出吗？")
            .setPositiveButton("确认") { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DEFAULT_DOCK_BACKGROUND_ALPHA = 220
        private const val DEFAULT_DOCK_CORNER_RADIUS_DP = 28f
        private const val DEFAULT_DOCK_BOTTOM_MARGIN_DP = 16
        private const val MIN_ALPHA = 0
        private const val MAX_ALPHA = 255

        fun newInstance(): DesktopContainerFragment = DesktopContainerFragment()
    }
    private fun handleDockIconClick(icon: AppIcon) {
        when (icon.name) {
            "QQ" -> navigator.launchQq()
            "世界书集" -> navigator.launchWorldBook()
            "相册" -> navigator.launchAlbum()
            "主题" -> navigator.launchTheme()
            "设置" -> navigator.launchSettings()
            "酒馆记录" -> navigator.launchCloudDreams(requireContext())
            "CPhone" -> navigator.launchCPhone()
            "预设" -> navigator.launchPreset()
            "商城" -> navigator.launchShopping()
            "支付宝" -> navigator.launchAlipay()
            "关系图" -> navigator.launchEventGraph()
            "课程表" -> navigator.launchSchedule()
            "???" -> navigator.launchTavern() // 「???」即酒馆入口（SillyTavern WebView）
        }
    }
}