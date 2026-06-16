package com.susking.ephone_s.desktop.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.local.entity.ScheduleWidgetStateEntity
import com.susking.ephone_s.aidata.domain.model.WeatherInfo
import com.susking.ephone_s.aidata.domain.repository.ScheduleRepository
import com.susking.ephone_s.aidata.domain.repository.WeatherRepository
import com.susking.ephone_s.desktop.data.DesktopRepository
import com.susking.ephone_s.desktop.location.WeatherLocationHelper
import com.susking.ephone_s.core.widget.DesktopWidgetConfig
import com.susking.ephone_s.core.widget.DesktopWidgetType
import com.susking.ephone_s.desktop.ui.drag.GridDragHelper
import com.susking.ephone_s.desktop.ui.drag.ItemMoveCallback
import com.susking.ephone_s.desktop.api.DesktopNavigator
import com.susking.ephone_s.desktop.R
import com.susking.ephone_s.desktop.databinding.FragmentDesktopBinding
import com.susking.ephone_s.desktop.model.AppIcon
import com.susking.ephone_s.desktop.ui.adapter.AppIconAdapter
import com.susking.ephone_s.desktop.ui.drag.GridPosition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DesktopFragment : Fragment() {

    @Inject
    lateinit var navigator: DesktopNavigator

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var desktopRepository: DesktopRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    // 天气定位辅助（原生 last-known 定位），懒加载避免无谓创建
    private val weatherLocationHelper: WeatherLocationHelper by lazy {
        WeatherLocationHelper(requireContext().applicationContext)
    }

    // 是否正在刷新天气，避免点击/权限回调重复触发并发请求
    private var isRefreshingWeather: Boolean = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 用户授予了权限
                android.util.Log.d("DesktopFragment", "Notification permission granted.")
            } else {
                // 用户拒绝了权限
                android.util.Log.d("DesktopFragment", "Notification permission denied.")
            }
        }

    // 天气定位权限请求：授予后立即刷新一次天气，拒绝则走 IP 兜底
    private val weatherLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            android.util.Log.d("DesktopFragment", "Coarse location permission granted=$isGranted.")
            // 直接执行刷新、绕过权限门禁：授予用 GPS，拒绝走 IP 兜底。
            // 切勿回到 refreshWeather——权限被永久拒绝时系统会同步回调，会与门禁互相调用形成无限递归（StackOverflow）。
            executeWeatherRefresh(forceRefresh = true)
        }

    private var _binding: FragmentDesktopBinding? = null
    private val binding get() = _binding!!
    private var pageNumber: Int = 0
    private lateinit var appIconAdapter: AppIconAdapter
    private val desktopViewModel: DesktopViewModel by activityViewModels()
    private lateinit var gridDragHelper: GridDragHelper
    // 各卡片类型的当前配置缓存（按类型分键），拖拽与渲染时读取
    private val widgetConfigs: MutableMap<DesktopWidgetType, DesktopWidgetConfig> = mutableMapOf()
    // 当前正在拖拽的卡片类型，null 表示拖拽的是普通应用图标
    private var draggingWidgetType: DesktopWidgetType? = null
    // 时钟卡片的时间/日期格式化器，复用实例避免每次刷新重复创建
    private val clockTimeFormat: java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
    private val clockDateFormat: java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("M月d日 EEE", java.util.Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get page number
        arguments?.let {
            pageNumber = it.getInt(ARG_PAGE_NUMBER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDesktopBinding.inflate(inflater, container, false)
        setupRecyclerView()
        observeViewModel()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets()
        requestNotificationPermission()
        setupScheduleCard()
        setupClockCard()
        setupWeatherCard()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 权限已经被授予
                    android.util.Log.d("DesktopFragment", "Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 可以向用户解释为什么需要这个权限
                    // 在这里可以显示一个对话框
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // 直接请求权限
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding?.desktopRecyclerView?.setOnDragListener(null)
        super.onDestroyView()
        _binding = null
    }

    private fun observeViewModel() {
        desktopViewModel.pages.observe(viewLifecycleOwner) { pages ->
            pages.getOrNull(pageNumber)?.let { icons ->
                // 创建一个6x4=24个位置的完整网格数组，用null占位
                val gridSize = GridPosition.ROWS * GridPosition.COLUMNS // 6 * 4 = 24
                val gridArray = arrayOfNulls<AppIcon>(gridSize)
                
                // 将图标放到对应的网格位置
                icons.forEach { icon ->
                    if (icon.hasValidGridPosition()) {
                        val index = icon.gridRow * GridPosition.COLUMNS + icon.gridColumn
                        if (index in 0 until gridSize) {
                            gridArray[index] = icon
                        }
                    }
                }
                
                // 直接使用包含null的数组，RecyclerView会显示空白格子
                appIconAdapter.icons = gridArray.toMutableList()
                appIconAdapter.notifyDataSetChanged()
                
                android.util.Log.d("DesktopFragment", "Updated grid for page $pageNumber: " +
                        "${icons.size} icons in ${gridArray.count { it != null }} positions")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                scheduleRepository.observeWidgetState().collect { state: ScheduleWidgetStateEntity ->
                    renderScheduleCard(state)
                }
            }
        }

        // 为每种已在桌面上呈现的卡片类型订阅各自的位置配置，统一应用到对应卡片
        DesktopWidgetType.values().forEach { type: DesktopWidgetType ->
            if (getWidgetCard(type) == null) return@forEach
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    desktopRepository.getWidgetConfig(type).collect { config: DesktopWidgetConfig ->
                        widgetConfigs[type] = config
                        applyWidgetConfig(type, config)
                    }
                }
            }
        }

        desktopViewModel.appLabelColor.observe(viewLifecycleOwner) {
            updateAppLabelStyle()
        }

        desktopViewModel.appLabelShadowColor.observe(viewLifecycleOwner) {
            updateAppLabelStyle()
        }

        desktopViewModel.isAppLabelShadowEnabled.observe(viewLifecycleOwner) {
            updateAppLabelStyle()
        }
    }

    private fun applySystemBarInsets(): Unit {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.scheduleDesktopCard.setPadding(0, systemBars.top / SYSTEM_BAR_PADDING_DIVISOR, 0, 0)
            insets
        }
    }

    private fun setupScheduleCard(): Unit {
        binding.scheduleDesktopCard.setOnClickListener {
            navigator.launchSchedule()
        }
        binding.scheduleDesktopCard.setOnLongClickListener { view: View ->
            startWidgetDrag(DesktopWidgetType.SCHEDULE, view)
            true
        }
        binding.scheduleDesktopCard.visibility = if (pageNumber == HOME_PAGE_NUMBER) View.VISIBLE else View.GONE
    }

    /**
     * 初始化时钟卡片：点击进入闹钟（暂无入口则忽略）、长按拖拽、首页才显示。
     * 时钟数据纯本地，刷新逻辑在 [startClockTicking]。
     */
    private fun setupClockCard(): Unit {
        binding.clockWidgetCard.setOnLongClickListener { view: View ->
            startWidgetDrag(DesktopWidgetType.CLOCK, view)
            true
        }
        binding.clockWidgetCard.visibility = if (pageNumber == HOME_PAGE_NUMBER) View.VISIBLE else View.GONE
        if (pageNumber == HOME_PAGE_NUMBER) {
            startClockTicking()
        }
    }

    /**
     * 初始化天气卡片：点击触发一次刷新、长按拖拽、首页才显示。
     * 天气数据来自 [observeWeather] 与 [refreshWeather]。
     */
    private fun setupWeatherCard(): Unit {
        binding.weatherWidgetCard.setOnClickListener {
            refreshWeather(forceRefresh = true)
        }
        binding.weatherWidgetCard.setOnLongClickListener { view: View ->
            startWidgetDrag(DesktopWidgetType.WEATHER, view)
            true
        }
        binding.weatherWidgetCard.visibility = if (pageNumber == HOME_PAGE_NUMBER) View.VISIBLE else View.GONE
        // 仅首页承载天气卡片：观察缓存渲染 UI，并发起一次节流刷新
        if (pageNumber == HOME_PAGE_NUMBER) {
            observeWeather()
            refreshWeather(forceRefresh = false)
        }
    }

    /**
     * 观察天气缓存流，每次变化时重渲染天气卡片。
     * 借 repeatOnLifecycle(STARTED) 保证仅前台收集，离开自动停止。
     */
    private fun observeWeather(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                weatherRepository.observeWeather().collect { weatherInfo: WeatherInfo? ->
                    renderWeatherCard(weatherInfo)
                }
            }
        }
    }

    /**
     * 渲染天气卡片：位置、温度、天气文案与图标。
     * weatherInfo 为 null（无缓存）时展示占位提示，引导点击刷新。
     */
    private fun renderWeatherCard(weatherInfo: WeatherInfo?): Unit {
        if (weatherInfo == null) {
            binding.weatherCardLocation.text = getString(R.string.weather_card_no_data_location)
            binding.weatherCardTemperature.text = WEATHER_PLACEHOLDER_TEXT
            binding.weatherCardCondition.text = getString(R.string.weather_card_tap_to_refresh)
            binding.weatherCardIcon.setImageResource(R.drawable.ic_weather_cloudy)
            return
        }
        binding.weatherCardLocation.text = weatherInfo.locationName
        binding.weatherCardTemperature.text =
            getString(R.string.weather_card_temperature_format, weatherInfo.temperatureCelsius.toInt())
        binding.weatherCardCondition.text = mapWeatherCodeToText(weatherInfo.weatherCode)
        binding.weatherCardIcon.setImageResource(mapWeatherCodeToIcon(weatherInfo.weatherCode))
    }

    /**
     * 刷新天气数据的「权限门禁」入口。
     *
     * 仅负责定位权限判定：无权限且用户主动点击（forceRefresh）时请求一次权限后返回，
     * 请求结果由 [weatherLocationPermissionLauncher] 回调直接驱动 [executeWeatherRefresh]，
     * 不再回到本方法——否则权限被永久拒绝时系统的同步回调会与门禁互相调用形成无限递归。
     * 已有权限（或非强制刷新）时直接交给 [executeWeatherRefresh]。
     *
     * @param forceRefresh true 时忽略节流（用户主动点击）；false 时遵守 [WEATHER_REFRESH_THROTTLE_MS] 节流
     */
    private fun refreshWeather(forceRefresh: Boolean): Unit {
        // 无定位权限时先请求权限，授予/拒绝都由回调统一驱动刷新；仅用户主动点击才弹权限
        if (!weatherLocationHelper.hasLocationPermission() && forceRefresh) {
            weatherLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        executeWeatherRefresh(forceRefresh)
    }

    /**
     * 真正执行天气刷新，不涉及任何权限请求（故可被权限回调安全调用，不会递归）。
     *
     * 流程：定位（GPS last-known → IP 兜底）→ 通过 brain 悬浮窗链路查询 Open-Meteo → 落盘缓存。
     * 所有外部请求都经 [AiDataApi.getAiRequestService] 转发，显示在 brain 悬浮窗中。
     * 无定位权限时 [WeatherLocationHelper.getLastKnownLocation] 返回 null，自动走 IP 兜底。
     *
     * @param forceRefresh true 时忽略节流；false 时遵守 [WEATHER_REFRESH_THROTTLE_MS] 节流
     */
    private fun executeWeatherRefresh(forceRefresh: Boolean): Unit {
        // 防止点击/权限回调重复触发并发请求
        if (isRefreshingWeather) return
        isRefreshingWeather = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 非强制刷新时遵守节流：缓存未过期则跳过
                if (!forceRefresh && !isWeatherCacheStale()) {
                    return@launch
                }
                val locationPoint: WeatherLocationHelper.LocationPoint? = resolveLocation()
                if (locationPoint == null) {
                    android.util.Log.w("DesktopFragment", "天气刷新失败：无法获取位置")
                    return@launch
                }
                val locationName: String = resolveLocationName(locationPoint)
                val weatherResult = AiDataApi.getAiRequestService()
                    .fetchWeatherWithLogging(locationPoint.latitude, locationPoint.longitude)
                if (weatherResult == null) {
                    android.util.Log.w("DesktopFragment", "天气刷新失败：查询返回 null")
                    return@launch
                }
                weatherRepository.saveWeather(
                    WeatherInfo(
                        locationName = locationName,
                        temperatureCelsius = weatherResult.temperatureCelsius,
                        weatherText = mapWeatherCodeToText(weatherResult.weatherCode),
                        weatherCode = weatherResult.weatherCode,
                        updatedAt = System.currentTimeMillis(),
                        latitude = locationPoint.latitude,
                        longitude = locationPoint.longitude
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("DesktopFragment", "天气刷新异常", e)
            } finally {
                isRefreshingWeather = false
            }
        }
    }

    /**
     * 判断天气缓存是否已过期（超过节流时长或无缓存）。
     */
    private suspend fun isWeatherCacheStale(): Boolean {
        val cached: WeatherInfo? = weatherRepository.getWeatherSync()
        if (cached == null) {
            return true
        }
        val elapsed: Long = System.currentTimeMillis() - cached.updatedAt
        return elapsed >= WEATHER_REFRESH_THROTTLE_MS
    }

    /**
     * 解析定位坐标：优先原生 last-known，失败走 IP 定位兜底。
     * IP 定位通过 brain 悬浮窗链路转发。
     */
    private suspend fun resolveLocation(): WeatherLocationHelper.LocationPoint? {
        weatherLocationHelper.getLastKnownLocation()?.let { gpsPoint: WeatherLocationHelper.LocationPoint ->
            return gpsPoint
        }
        val ipResult = AiDataApi.getAiRequestService().fetchIpLocationWithLogging() ?: return null
        return WeatherLocationHelper.LocationPoint(
            latitude = ipResult.latitude,
            longitude = ipResult.longitude
        )
    }

    /**
     * 解析展示用位置名：GPS 定位无名称，故优先复用上次缓存的名称，否则走 IP 定位补名。
     */
    private suspend fun resolveLocationName(locationPoint: WeatherLocationHelper.LocationPoint): String {
        // GPS 路径拿不到地名，尝试沿用已有缓存名；缓存也没有时请求一次 IP 定位获取城市名
        val cachedName: String? = weatherRepository.getWeatherSync()?.locationName?.takeIf { it.isNotBlank() }
        if (cachedName != null) {
            return cachedName
        }
        val ipResult = AiDataApi.getAiRequestService().fetchIpLocationWithLogging()
        return ipResult?.locationName?.takeIf { it.isNotBlank() } ?: WEATHER_DEFAULT_LOCATION_NAME
    }

    /**
     * 将 WMO 天气代码映射为展示图标资源。
     * 参考 https://open-meteo.com/en/docs WMO Weather interpretation codes。
     */
    private fun mapWeatherCodeToIcon(weatherCode: Int): Int {
        return when (weatherCode) {
            in WMO_CLEAR_CODES -> R.drawable.ic_weather_clear
            in WMO_CLOUDY_CODES -> R.drawable.ic_weather_cloudy
            in WMO_FOG_CODES -> R.drawable.ic_weather_fog
            in WMO_RAIN_CODES -> R.drawable.ic_weather_rain
            in WMO_SNOW_CODES -> R.drawable.ic_weather_snow
            else -> R.drawable.ic_weather_cloudy
        }
    }

    /**
     * 将 WMO 天气代码映射为中文天气文案。
     */
    private fun mapWeatherCodeToText(weatherCode: Int): String {
        return when (weatherCode) {
            in WMO_CLEAR_CODES -> "晴"
            in WMO_CLOUDY_CODES -> "多云"
            in WMO_FOG_CODES -> "雾"
            in WMO_RAIN_CODES -> "雨"
            in WMO_SNOW_CODES -> "雪"
            else -> "多云"
        }
    }

    /**
     * 启动时钟每分钟刷新：先立即渲染一次，再对齐到下一整分循环刷新。
     * 借 repeatOnLifecycle(STARTED) 保证仅在前台运行，onStop 自动停止，避免后台空转。
     */
    private fun startClockTicking(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    renderClockCard()
                    kotlinx.coroutines.delay(calculateDelayToNextMinute())
                }
            }
        }
    }

    /**
     * 计算距离下一整分的毫秒数，使时钟刷新对齐整分跳变。
     */
    private fun calculateDelayToNextMinute(): Long {
        val now: java.util.Calendar = java.util.Calendar.getInstance()
        val secondsPart: Int = now.get(java.util.Calendar.SECOND)
        val millisPart: Int = now.get(java.util.Calendar.MILLISECOND)
        val remainingMillis: Long =
            (SECONDS_PER_MINUTE - secondsPart) * MILLIS_PER_SECOND - millisPart
        return remainingMillis.coerceAtLeast(MIN_CLOCK_TICK_DELAY_MS)
    }

    /**
     * 渲染时钟卡片：HH:mm 时间 + "M月d日 周X"日期。
     */
    private fun renderClockCard(): Unit {
        if (pageNumber != HOME_PAGE_NUMBER) return
        val now: java.util.Date = java.util.Date()
        binding.clockCardTime.text = clockTimeFormat.format(now)
        binding.clockCardDate.text = clockDateFormat.format(now)
    }

    /**
     * 返回指定类型卡片对应的 View，未在当前布局中呈现的类型返回 null。
     * 时钟、天气卡片接入后在此登记各自的 binding 即可参与拖拽与配置。
     */
    private fun getWidgetCard(type: DesktopWidgetType): View? {
        return when (type) {
            DesktopWidgetType.SCHEDULE -> binding.scheduleDesktopCard
            DesktopWidgetType.CLOCK -> binding.clockWidgetCard
            DesktopWidgetType.WEATHER -> binding.weatherWidgetCard
        }
    }

    /**
     * 读取指定类型卡片的当前配置，缓存缺失时回退到该类型默认值。
     */
    private fun resolveWidgetConfig(type: DesktopWidgetType): DesktopWidgetConfig {
        return widgetConfigs[type] ?: DesktopWidgetConfig.fromType(type)
    }

    /**
     * 发起卡片拖拽，记录当前拖拽的卡片类型。
     */
    private fun startWidgetDrag(type: DesktopWidgetType, view: View): Unit {
        if (pageNumber != HOME_PAGE_NUMBER) return
        draggingWidgetType = type
        val item: ClipData.Item = ClipData.Item(type.storageKey)
        val mimeTypes: Array<String> = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
        val dragData: ClipData = ClipData(WIDGET_DRAG_LABEL, mimeTypes, item)
        val shadowBuilder: View.DragShadowBuilder = View.DragShadowBuilder(view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(dragData, shadowBuilder, view, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(dragData, shadowBuilder, view, 0)
        }
    }

    /**
     * 将配置应用到指定类型的卡片：计算宽高、吸附网格、约束边界。
     */
    private fun applyWidgetConfig(type: DesktopWidgetType, config: DesktopWidgetConfig): Unit {
        val card: View = getWidgetCard(type) ?: return
        if (binding.desktopRecyclerView.width == 0 || binding.desktopRecyclerView.height == 0) {
            binding.desktopRecyclerView.post { applyWidgetConfig(type, config) }
            return
        }
        val layoutParams: ViewGroup.MarginLayoutParams = card.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = calculateWidgetWidth(type, config.widthMode)
        layoutParams.height = calculateWidgetHeight(type, config.heightMode)
        layoutParams.leftMargin = snapCoordinateToGrid(config.leftMarginDp.toPx(), calculateDesktopGridCellWidth())
        layoutParams.topMargin = snapCoordinateToGrid(config.topMarginDp.toPx(), calculateDesktopGridCellHeight())
        constrainWidgetToGrid(layoutParams)
        card.minimumHeight = 0
        card.layoutParams = layoutParams
    }

    private fun constrainWidgetToGrid(layoutParams: ViewGroup.MarginLayoutParams): Unit {
        val maxLeftMargin: Int = (binding.desktopRecyclerView.width - layoutParams.width).coerceAt(0)
        val maxTopMargin: Int = (binding.desktopRecyclerView.height - layoutParams.height).coerceAt(0)
        layoutParams.leftMargin = layoutParams.leftMargin.coerceIn(0, maxLeftMargin)
        layoutParams.topMargin = layoutParams.topMargin.coerceIn(0, maxTopMargin)
    }

    private fun snapCoordinateToGrid(coordinate: Int, cellSize: Int): Int {
        if (cellSize <= 0) return coordinate.coerceAt(0)
        return ((coordinate + cellSize / GRID_SNAP_HALF_DIVISOR) / cellSize * cellSize).coerceAt(0)
    }

    private fun calculateWidgetWidth(type: DesktopWidgetType, widthMode: String): Int {
        return calculateDesktopGridCellWidth() * type.resolveColumnSpan(widthMode)
    }

    private fun calculateWidgetHeight(type: DesktopWidgetType, heightMode: String): Int {
        return calculateDesktopGridCellHeight() * type.resolveRowSpan(heightMode)
    }

    private fun calculateDesktopGridCellWidth(): Int {
        return (binding.desktopRecyclerView.width / GridPosition.COLUMNS).coerceAtLeast(MIN_WIDGET_GRID_CELL_SIZE_PX)
    }

    private fun calculateDesktopGridCellHeight(): Int {
        return (binding.desktopRecyclerView.height / GridPosition.ROWS).coerceAtLeast(MIN_WIDGET_GRID_CELL_SIZE_PX)
    }

    private fun saveWidgetPosition(type: DesktopWidgetType, layoutParams: ViewGroup.MarginLayoutParams): Unit {
        val nextConfig: DesktopWidgetConfig = resolveWidgetConfig(type).copy(
            leftMarginDp = layoutParams.leftMargin.toDp(),
            topMarginDp = layoutParams.topMargin.toDp()
        )
        viewLifecycleOwner.lifecycleScope.launch { desktopRepository.saveWidgetConfig(type, nextConfig) }
    }

    private fun renderScheduleCard(state: ScheduleWidgetStateEntity): Unit {
        if (pageNumber != HOME_PAGE_NUMBER) return
        binding.scheduleCardNextCourse.text = state.nextCourseText.ifBlank { "暂无下一节课" }
        binding.scheduleCardExam.text = state.examCountdownText.ifBlank { "暂无考试倒计时" }
        binding.scheduleCardAssignment.text = state.pendingAssignmentText.ifBlank { "未完成作业：0" }
        runCatching { Color.parseColor(state.accentColor) }
            .getOrNull()
            ?.let { color: Int -> binding.scheduleDesktopCard.strokeColor = color }
        binding.scheduleDesktopCard.visibility = View.VISIBLE
    }

    private fun updateAppLabelStyle(): Unit {
        val labelColor: Int = desktopViewModel.appLabelColor.value ?: android.graphics.Color.WHITE
        val labelShadowColor: Int = desktopViewModel.appLabelShadowColor.value ?: android.graphics.Color.BLACK
        val isLabelShadowEnabled: Boolean = desktopViewModel.isAppLabelShadowEnabled.value ?: true

        appIconAdapter.updateLabelStyle(labelColor, labelShadowColor, isLabelShadowEnabled)
    }

    private fun setupRecyclerView() {
        // 初始化GridDragHelper
        gridDragHelper = desktopViewModel.initGridDragHelper()
        
        // 关联GridOverlayView到RecyclerView，用于获取实际item尺寸
        binding.gridOverlay.setRecyclerView(binding.desktopRecyclerView)
        
        // 初始化为空的24格网格
        val emptyGrid = MutableList<AppIcon?>(GridPosition.ROWS * GridPosition.COLUMNS) { null }
        appIconAdapter = AppIconAdapter(emptyGrid,
            { icon -> handleIconClick(icon) },
            { view, icon ->
                // 开始拖拽时通知GridDragHelper
                gridDragHelper.startDrag(pageNumber, icon)
                
                val item = ClipData.Item("$pageNumber,${appIconAdapter.icons.indexOf(icon)}")
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
            })

        binding.desktopRecyclerView.adapter = appIconAdapter
        val layoutManager = object : GridLayoutManager(context, 4) {
            override fun canScrollVertically(): Boolean = false
        }
        binding.desktopRecyclerView.layoutManager = layoutManager

        val callback = ItemMoveCallback(appIconAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.desktopRecyclerView)

        binding.desktopRecyclerView.setOnDragListener(createGridDragListener())
    }

    /**
     * 创建网格拖拽监听器
     */
    private fun createGridDragListener(): View.OnDragListener {
        return View.OnDragListener { view, event ->
            val currentBinding: FragmentDesktopBinding = _binding ?: return@OnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // 显示网格覆盖层
                    currentBinding.gridOverlay.visibility = View.VISIBLE
                    currentBinding.gridOverlay.setHighlightSpan(calculateCurrentDragColumnSpan(), calculateCurrentDragRowSpan())
                    currentBinding.gridOverlay.showGridWithHighlight(null)
                    // 接受所有拖拽事件
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // 进入区域时高亮显示
                    view.alpha = 0.8f
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    // 拖拽位置更新时计算并高亮目标位置
                    val targetPosition = gridDragHelper.calculateDropPosition(
                        event.x,
                        event.y,
                        currentBinding.desktopRecyclerView
                    )
                    currentBinding.gridOverlay.updateHighlight(targetPosition)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // 离开区域时恢复
                    view.alpha = 1.0f
                    currentBinding.gridOverlay.updateHighlight(null)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // 拖拽结束时恢复并隐藏网格
                    view.alpha = 1.0f
                    currentBinding.gridOverlay.hideGrid()
                    currentBinding.gridOverlay.visibility = View.GONE
                    draggingWidgetType = null
                    gridDragHelper.endDrag()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    try {
                        // 使用GridDragHelper计算目标网格位置
                        val targetPosition = gridDragHelper.calculateDropPosition(
                            event.x,
                            event.y,
                            currentBinding.desktopRecyclerView
                        )
                        
                        if (targetPosition != null) {
                            val draggingType: DesktopWidgetType? = draggingWidgetType
                            if (draggingType != null) {
                                moveWidgetToGridPosition(draggingType, targetPosition)
                            } else {
                                // 处理drop到桌面
                                val success: Boolean = gridDragHelper.handleDropToDesktop(pageNumber, targetPosition)
                                
                                if (success) {
                                    android.util.Log.d("DesktopFragment",
                                        "Successfully dropped icon at grid position (${targetPosition.row}, ${targetPosition.column})")
                                } else {
                                    android.util.Log.w("DesktopFragment", "Failed to drop icon")
                                }
                            }
                        }
                        
                        view.alpha = 1.0f
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("DesktopFragment", "Drop failed", e)
                        view.alpha = 1.0f
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun calculateCurrentDragColumnSpan(): Int {
        val type: DesktopWidgetType = draggingWidgetType ?: return ICON_GRID_SPAN
        return type.resolveColumnSpan(resolveWidgetConfig(type).widthMode)
    }

    private fun calculateCurrentDragRowSpan(): Int {
        val type: DesktopWidgetType = draggingWidgetType ?: return ICON_GRID_SPAN
        return type.resolveRowSpan(resolveWidgetConfig(type).heightMode)
    }

    /**
     * 将指定类型卡片移动到目标网格位置并持久化。
     */
    private fun moveWidgetToGridPosition(type: DesktopWidgetType, position: GridPosition): Unit {
        val card: View = getWidgetCard(type) ?: return
        val config: DesktopWidgetConfig = resolveWidgetConfig(type)
        val layoutParams: ViewGroup.MarginLayoutParams = card.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = calculateWidgetWidth(type, config.widthMode)
        layoutParams.height = calculateWidgetHeight(type, config.heightMode)
        layoutParams.leftMargin = position.column * calculateDesktopGridCellWidth()
        layoutParams.topMargin = position.row * calculateDesktopGridCellHeight()
        constrainWidgetToGrid(layoutParams)
        card.layoutParams = layoutParams
        saveWidgetPosition(type, layoutParams)
    }
    
    /**
     * 保留旧的方法用于兼容（已废弃，使用GridDragHelper代替）
     */
    @Deprecated("Use GridDragHelper instead")
    private fun createDragListener(): View.OnDragListener {
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
                        val item: ClipData.Item = event.clipData.getItemAt(0)
                        val dragData = item.text.toString().split(",")
                        val fromPage = dragData[0].toInt()
                        val fromPosition = dragData[1].toInt()

                        val targetPosition = getPositionFromDrop(event.x, event.y)

                        if (fromPage == -1) {
                            desktopViewModel.moveIconFromDock(fromPosition, pageNumber, targetPosition)
                        } else {
                            desktopViewModel.moveIcon(fromPage, fromPosition, pageNumber, targetPosition)
                        }
                        view.alpha = 1.0f
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("DesktopFragment", "Drop failed", e)
                        view.alpha = 1.0f
                        false
                    }
                }
                else -> false
            }
        }
    }

    @Deprecated("Use GridDragHelper.calculateDropPosition instead")
    private fun getPositionFromDrop(x: Float, y: Float): Int {
        val child = binding.desktopRecyclerView.findChildViewUnder(x, y)
        return child?.let { binding.desktopRecyclerView.getChildAdapterPosition(it) } ?: appIconAdapter.itemCount
    }

    private fun handleIconClick(icon: AppIcon) {
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
            "健康" -> navigator.launchHealth() // 健康应用（AI 健康关怀）
        }
    }

    private fun Int.coerceAt(minValue: Int): Int {
        return coerceAtLeast(minValue)
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun Int.toDp(): Int {
        return (this / resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ARG_PAGE_NUMBER = "page_number"
        private const val HOME_PAGE_NUMBER: Int = 0
        private const val SYSTEM_BAR_PADDING_DIVISOR: Int = 2
        private const val GRID_SNAP_HALF_DIVISOR: Int = 2
        private const val MIN_WIDGET_GRID_CELL_SIZE_PX: Int = 1
        private const val ICON_GRID_SPAN: Int = 1
        // 时钟刷新相关常量
        private const val SECONDS_PER_MINUTE: Int = 60
        private const val MILLIS_PER_SECOND: Long = 1000L
        // 时钟刷新最小间隔，避免恰好整分时 delay 为 0 造成空转
        private const val MIN_CLOCK_TICK_DELAY_MS: Long = 1000L
        // 卡片拖拽 ClipData 标签，区分卡片拖拽与普通图标拖拽
        private const val WIDGET_DRAG_LABEL: String = "desktop_widget"

        // ==================== 天气相关常量 ====================
        // 天气刷新节流时长：30 分钟内不重复自动刷新，避免频繁外部请求
        private const val WEATHER_REFRESH_THROTTLE_MS: Long = 30L * 60L * 1000L
        // 无温度数据时的占位文案
        private const val WEATHER_PLACEHOLDER_TEXT: String = "--°"
        // 无法获取地名时的兜底位置名
        private const val WEATHER_DEFAULT_LOCATION_NAME: String = "当前位置"
        // WMO 天气代码分组（参考 Open-Meteo 文档），用于映射图标与文案
        // 0 晴；1 多云间晴
        private val WMO_CLEAR_CODES: Set<Int> = setOf(0, 1)
        // 2 多云；3 阴
        private val WMO_CLOUDY_CODES: Set<Int> = setOf(2, 3)
        // 45/48 雾
        private val WMO_FOG_CODES: Set<Int> = setOf(45, 48)
        // 51-67 毛毛雨/雨；80-82 阵雨；95-99 雷雨
        private val WMO_RAIN_CODES: Set<Int> =
            setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        // 71-77 雪；85/86 阵雪
        private val WMO_SNOW_CODES: Set<Int> = setOf(71, 73, 75, 77, 85, 86)

        @JvmStatic
        fun newInstance(pageNumber: Int): DesktopFragment {
            // Use a factory method to pass arguments, which is safer and more standard.
            return DesktopFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PAGE_NUMBER, pageNumber)
                }
            }
        }
    }
}