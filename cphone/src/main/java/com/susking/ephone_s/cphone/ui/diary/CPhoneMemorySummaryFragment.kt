package com.susking.ephone_s.cphone.ui.diary

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneMemorySummaryBinding
import com.susking.ephone_s.core.ui.dialog.ConfirmAiPromptDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * CPhone分层摘要可视化页面。
 */
@AndroidEntryPoint
class CPhoneMemorySummaryFragment : Fragment() {
    private var _binding: FragmentCphoneMemorySummaryBinding? = null
    private val binding: FragmentCphoneMemorySummaryBinding get() = _binding!!
    private val viewModel: CPhoneMemorySummaryViewModel by viewModels()
    private lateinit var adapter: CPhoneMemorySummaryAdapter
    private var contactId: String = ""
    private var currentLevel: SummaryLevel = SummaryLevel.DAILY
    private var allSummaries: List<CPhoneMemorySummaryItem> = emptyList()
    private var selectedTimestamp: Long? = System.currentTimeMillis()
    private var pendingSummaryWindow: SummaryWindow? = null
    private var pendingSummaryLevel: SummaryLevel = SummaryLevel.DAILY
    private var calendarTouchStartX: Float = 0F
    private var calendarTouchStartY: Float = 0F
    private var calendarLastDragX: Float = 0F
    private var isCalendarDragging: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCphoneMemorySummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAppBar()
        setupPromptConfirmationResultListener()
        setupRecyclerView()
        setupLevelFilter()
        setupCalendar()
        setupGenerateButton()
        observeSummaries()
        updateSelectedWindowText()
    }

    private fun setupAppBar() {
        val toolbar: MaterialToolbar? = binding.root.findViewById(R.id.toolbar)
        toolbar?.apply {
            title = "分层摘要"
            setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            menu.clear()
            inflateMenu(R.menu.menu_cphone_memory_summary)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_repair_default_importance -> {
                        showRepairDefaultImportanceDialog()
                        true
                    }
                    R.id.action_delete_all_summaries -> {
                        showDeleteAllSummariesDialog()
                        true
                    }
                    else -> false
                }
            }
        }
        binding.root.findViewById<View>(R.id.btn_refresh)?.visibility = View.GONE
        binding.root.findViewById<View>(R.id.btn_generate_images)?.visibility = View.GONE
        binding.root.findViewById<View>(R.id.btn_memory_summary)?.visibility = View.GONE
    }

    private fun setupPromptConfirmationResultListener() {
        childFragmentManager.setFragmentResultListener(
            ConfirmAiPromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle: Bundle ->
            val confirmed: Boolean = bundle.getBoolean(ConfirmAiPromptDialogFragment.RESULT_CONFIRMED)
            if (confirmed) {
                executePendingSummaryGeneration()
            } else {
                clearPendingSummaryGeneration()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CPhoneMemorySummaryAdapter(
            onEditClick = { summary: MemorySummary -> showEditSummaryDialog(summary) },
            onDeleteClick = { summary: MemorySummary -> showDeleteSummaryDialog(summary) },
            onRegenerateClick = { summary: MemorySummary -> showRegenerateSummaryDialog(summary) },
            onRevectorizeClick = { summary: MemorySummary -> revectorizeSummary(summary) }
        )
        binding.rvSummaries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSummaries.adapter = adapter
    }

    private fun setupLevelFilter() {
        binding.chipGroupLevels.check(R.id.chip_daily)
        binding.chipGroupLevels.setOnCheckedStateChangeListener { _, checkedIds: List<Int> ->
            currentLevel = when (checkedIds.firstOrNull()) {
                R.id.chip_weekly -> SummaryLevel.WEEKLY
                R.id.chip_monthly -> SummaryLevel.MONTHLY
                R.id.chip_yearly -> SummaryLevel.YEARLY
                else -> SummaryLevel.DAILY
            }
            updateSummaryCalendar()
            updateSelectedWindowText()
            submitFilteredList()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCalendar() {
        binding.btnCalendarPrevious.setOnClickListener { moveVisibleCalendarWindow(-1) }
        binding.btnCalendarNext.setOnClickListener { moveVisibleCalendarWindow(1) }
        binding.btnCalendarToday.setOnClickListener { selectCurrentCalendarWindow() }
        binding.layoutSummaryCalendar.setOnTouchListener { view: View, event: MotionEvent ->
            handleCalendarTouchEvent(view, event)
        }
        binding.gridSummaryCalendar.setOnTouchListener { view: View, event: MotionEvent ->
            handleCalendarTouchEvent(view, event)
            true
        }
        updateSummaryCalendar()
    }

    private fun handleCalendarTouchEvent(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleCalendarTouchDown(view, event)
                false
            }
            MotionEvent.ACTION_MOVE -> {
                handleCalendarTouchMove(view, event)
                isCalendarDragging
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging: Boolean = isCalendarDragging
                handleCalendarTouchEnd()
                wasDragging
            }
            MotionEvent.ACTION_CANCEL -> {
                handleCalendarTouchEnd()
                true
            }
            else -> false
        }
    }

    private fun handleCalendarTouchDown(view: View, event: MotionEvent) {
        view.parent?.requestDisallowInterceptTouchEvent(true)
        binding.gridSummaryCalendar.animate().cancel()
        calendarTouchStartX = event.rawX
        calendarTouchStartY = event.rawY
        calendarLastDragX = 0F
        isCalendarDragging = false
    }

    private fun handleCalendarTouchMove(view: View, event: MotionEvent) {
        val dragX: Float = event.rawX - calendarTouchStartX
        val dragY: Float = event.rawY - calendarTouchStartY
        if (!isCalendarDragging && kotlin.math.abs(dragX) > CALENDAR_DRAG_START_DISTANCE && kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) {
            isCalendarDragging = true
        }
        if (!isCalendarDragging) return
        view.parent?.requestDisallowInterceptTouchEvent(true)
        calendarLastDragX = dragX
        val canDragToFuture: Boolean = dragX >= 0F || canMoveToFutureWindow()
        binding.gridSummaryCalendar.translationX = if (canDragToFuture) dragX else dragX * CALENDAR_BLOCKED_DRAG_RESISTANCE
    }

    private fun handleCalendarTouchEnd() {
        val dragX: Float = calendarLastDragX
        val shouldMovePrevious: Boolean = dragX > CALENDAR_SWIPE_MIN_DISTANCE
        val shouldMoveNext: Boolean = dragX < -CALENDAR_SWIPE_MIN_DISTANCE && canMoveToFutureWindow()
        when {
            shouldMovePrevious -> animateCalendarWindowChange(-1, dragX)
            shouldMoveNext -> animateCalendarWindowChange(1, dragX)
            else -> animateCalendarBackToOrigin()
        }
        calendarTouchStartX = 0F
        calendarTouchStartY = 0F
        calendarLastDragX = 0F
        isCalendarDragging = false
    }

    private fun animateCalendarWindowChange(offset: Int, currentTranslationX: Float) {
        val targetTranslationX: Float = if (offset > 0) -binding.gridSummaryCalendar.width.toFloat() else binding.gridSummaryCalendar.width.toFloat()
        binding.gridSummaryCalendar.translationX = currentTranslationX
        binding.gridSummaryCalendar.animate()
            .translationX(targetTranslationX)
            .setDuration(CALENDAR_SWIPE_ANIMATION_DURATION_MS)
            .withEndAction {
                binding.gridSummaryCalendar.translationX = 0F
                moveVisibleCalendarWindow(offset)
            }
            .start()
    }

    private fun animateCalendarBackToOrigin() {
        binding.gridSummaryCalendar.animate()
            .translationX(0F)
            .setDuration(CALENDAR_SWIPE_ANIMATION_DURATION_MS)
            .start()
    }

    private fun selectCurrentCalendarWindow() {
        selectedTimestamp = System.currentTimeMillis()
        updateSummaryCalendar()
        updateSelectedWindowText()
        submitFilteredList()
    }

    private fun moveVisibleCalendarWindow(offset: Int) {
        val calendar: Calendar = Calendar.getInstance(Locale.CHINA)
        calendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        when (currentLevel) {
            SummaryLevel.DAILY, SummaryLevel.WEEKLY -> calendar.add(Calendar.MONTH, offset)
            SummaryLevel.MONTHLY -> calendar.add(Calendar.YEAR, offset)
            SummaryLevel.YEARLY -> calendar.add(Calendar.YEAR, offset * YEAR_GRID_SIZE)
        }
        selectedTimestamp = calendar.timeInMillis
        updateSummaryCalendar()
        updateSelectedWindowText()
        submitFilteredList()
    }

    private fun updateSummaryCalendar() {
        binding.gridSummaryCalendar.removeAllViews()
        binding.tvCalendarTitle.text = getCalendarTitle()
        binding.btnCalendarNext.isEnabled = canMoveToFutureWindow()
        when (currentLevel) {
            SummaryLevel.DAILY -> bindDailyCalendar()
            SummaryLevel.WEEKLY -> bindWeeklyCalendar()
            SummaryLevel.MONTHLY -> bindMonthlyCalendar()
            SummaryLevel.YEARLY -> bindYearlyCalendar()
        }
    }

    private fun getCalendarTitle(): String {
        val calendar: Calendar = Calendar.getInstance(Locale.CHINA)
        calendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        return when (currentLevel) {
            SummaryLevel.DAILY -> String.format(Locale.CHINA, "%d年%02d月", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            SummaryLevel.WEEKLY -> String.format(Locale.CHINA, "%d年%02d月周历", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            SummaryLevel.MONTHLY -> String.format(Locale.CHINA, "%d年月历", calendar.get(Calendar.YEAR))
            SummaryLevel.YEARLY -> String.format(Locale.CHINA, "%d-%d年年历", getVisibleYearStart(calendar), getVisibleYearStart(calendar) + YEAR_GRID_SIZE - 1)
        }
    }

    private fun canMoveToFutureWindow(): Boolean {
        val nextCalendar: Calendar = Calendar.getInstance(Locale.CHINA)
        nextCalendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        when (currentLevel) {
            SummaryLevel.DAILY, SummaryLevel.WEEKLY -> nextCalendar.add(Calendar.MONTH, 1)
            SummaryLevel.MONTHLY -> nextCalendar.add(Calendar.YEAR, 1)
            SummaryLevel.YEARLY -> nextCalendar.add(Calendar.YEAR, YEAR_GRID_SIZE)
        }
        return getCandidateWindowStart(nextCalendar.timeInMillis) <= getCurrentWindowStart()
    }

    private fun bindDailyCalendar() {
        binding.gridSummaryCalendar.columnCount = DAYS_IN_WEEK
        WEEKDAY_TITLES.forEach { title: String -> addCalendarHeaderCell(title) }
        val monthStartCalendar: Calendar = getVisibleMonthStartCalendar()
        val leadingBlankCount: Int = (monthStartCalendar.get(Calendar.DAY_OF_WEEK) + DAYS_IN_WEEK - Calendar.MONDAY) % DAYS_IN_WEEK
        repeat(leadingBlankCount) { addBlankCalendarCell() }
        val dayCount: Int = monthStartCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(dayCount) { index: Int ->
            val dayCalendar: Calendar = monthStartCalendar.clone() as Calendar
            dayCalendar.add(Calendar.DAY_OF_MONTH, index)
            addSelectableCalendarCell(dayCalendar.get(Calendar.DAY_OF_MONTH).toString(), dayCalendar.timeInMillis)
        }
    }

    private fun bindWeeklyCalendar() {
        binding.gridSummaryCalendar.columnCount = WEEKLY_COLUMN_COUNT
        val monthStartCalendar: Calendar = getVisibleMonthStartCalendar()
        val monthEndCalendar: Calendar = monthStartCalendar.clone() as Calendar
        monthEndCalendar.set(Calendar.DAY_OF_MONTH, monthEndCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        var weekStartCalendar: Calendar = monthStartCalendar.clone() as Calendar
        while (weekStartCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            weekStartCalendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        var weekIndex = 1
        while (weekStartCalendar.timeInMillis <= monthEndCalendar.timeInMillis) {
            val weekEndCalendar: Calendar = weekStartCalendar.clone() as Calendar
            weekEndCalendar.add(Calendar.DAY_OF_MONTH, WEEKLY_WINDOW_DAYS - 1)
            val label = "第${weekIndex}周\n${SHORT_DATE_FORMAT.format(weekStartCalendar.time)} - ${SHORT_DATE_FORMAT.format(weekEndCalendar.time)}"
            addSelectableCalendarCell(label, weekStartCalendar.timeInMillis)
            weekStartCalendar.add(Calendar.DAY_OF_MONTH, WEEKLY_WINDOW_DAYS)
            weekIndex++
        }
    }

    private fun bindMonthlyCalendar() {
        binding.gridSummaryCalendar.columnCount = MONTHLY_COLUMN_COUNT
        val yearCalendar: Calendar = Calendar.getInstance(Locale.CHINA)
        yearCalendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        repeat(MONTHS_IN_YEAR) { index: Int ->
            val monthCalendar: Calendar = Calendar.getInstance(Locale.CHINA).apply {
                set(Calendar.YEAR, yearCalendar.get(Calendar.YEAR))
                set(Calendar.MONTH, index)
                set(Calendar.DAY_OF_MONTH, 1)
                setSummaryStartTime(this)
            }
            addSelectableCalendarCell("${index + 1}月", monthCalendar.timeInMillis)
        }
    }

    private fun bindYearlyCalendar() {
        binding.gridSummaryCalendar.columnCount = YEARLY_COLUMN_COUNT
        val selectedCalendar: Calendar = Calendar.getInstance(Locale.CHINA)
        selectedCalendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        val yearStart: Int = getVisibleYearStart(selectedCalendar)
        repeat(YEAR_GRID_SIZE) { index: Int ->
            val yearCalendar: Calendar = Calendar.getInstance(Locale.CHINA).apply {
                set(Calendar.YEAR, yearStart + index)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                setSummaryStartTime(this)
            }
            addSelectableCalendarCell("${yearStart + index}年", yearCalendar.timeInMillis)
        }
    }

    private fun addCalendarHeaderCell(title: String) {
        val textView: TextView = createCalendarCell(title, false, false, false)
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        binding.gridSummaryCalendar.addView(textView)
    }

    private fun addBlankCalendarCell() {
        binding.gridSummaryCalendar.addView(createCalendarCell("", false, false, false))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addSelectableCalendarCell(label: String, timestamp: Long) {
        val windowStart: Long = getCandidateWindowStart(timestamp)
        val isFutureWindow: Boolean = windowStart > getCurrentWindowStart()
        val hasSummary: Boolean = hasSummaryForWindow(windowStart)
        val isSelected: Boolean = selectedTimestamp?.let { selected: Long -> getCandidateWindowStart(selected) == windowStart } ?: false
        val textView: TextView = createCalendarCell(label, hasSummary, isSelected, isFutureWindow)
        textView.isEnabled = true
        textView.setOnClickListener {
            if (!isFutureWindow) selectCalendarTimestamp(timestamp)
        }
        textView.setOnTouchListener { view: View, event: MotionEvent ->
            val hasDragged: Boolean = handleCalendarTouchEvent(view, event)
            if (!hasDragged && event.actionMasked == MotionEvent.ACTION_UP && !isFutureWindow) view.performClick()
            true
        }
        binding.gridSummaryCalendar.addView(textView)
    }

    private fun createCalendarCell(label: String, hasSummary: Boolean, isSelected: Boolean, isFutureWindow: Boolean): TextView {
        val colorPack: CalendarColorPack = getCalendarColorPack()
        val textColor: Int = if (isFutureWindow) colorPack.disabled else colorPack.onSurface
        val backgroundDrawable: GradientDrawable = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.cphone_summary_calendar_cell_radius)
            if (isSelected) {
                setStroke(resources.getDimensionPixelSize(R.dimen.cphone_summary_calendar_cell_stroke_width), colorPack.primary)
                setColor(colorPack.selectedBackground)
            } else {
                setColor(colorPack.surface)
            }
        }
        return TextView(requireContext()).apply {
            text = buildCalendarCellText(label, hasSummary, colorPack.primary)
            gravity = Gravity.CENTER
            minHeight = resources.getDimensionPixelSize(R.dimen.cphone_summary_calendar_cell_min_height)
            setTextColor(textColor)
            textSize = SUMMARY_CALENDAR_TEXT_SIZE_SP
            background = backgroundDrawable
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1F)
                setMargins(CALENDAR_CELL_MARGIN, CALENDAR_CELL_MARGIN, CALENDAR_CELL_MARGIN, CALENDAR_CELL_MARGIN)
            }
        }
    }

    private fun buildCalendarCellText(label: String, hasSummary: Boolean, dotColor: Int): CharSequence {
        if (label.isBlank()) return label
        val dotText: String = if (hasSummary) SUMMARY_DOT_TEXT else SUMMARY_EMPTY_DOT_TEXT
        val dotStart: Int = label.length + 1
        val text = "$label\n$dotText"
        val visibleDotColor: Int = if (hasSummary) dotColor else android.graphics.Color.TRANSPARENT
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(visibleDotColor), dotStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(SUMMARY_DOT_RELATIVE_SIZE), dotStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun selectCalendarTimestamp(timestamp: Long) {
        val currentSelectedTimestamp: Long? = selectedTimestamp
        selectedTimestamp = if (currentSelectedTimestamp != null && getCandidateWindowStart(currentSelectedTimestamp) == getCandidateWindowStart(timestamp)) {
            null
        } else {
            timestamp
        }
        updateSummaryCalendar()
        updateSelectedWindowText()
        submitFilteredList()
    }

    private fun setupGenerateButton() {
        binding.btnGenerateSelectedSummary.setOnClickListener {
            val window: SummaryWindow = getSelectedWindow() ?: return@setOnClickListener
            if (isCurrentSummaryWindow(window)) {
                showOperationToast(false, "", "当前${getLevelName(currentLevel)}尚未结束，不能手动生成摘要")
                return@setOnClickListener
            }
            val level: SummaryLevel = currentLevel
            viewModel.buildSummaryPrompt(contactId, level, window.startTimestamp, window.endTimestamp) { promptRequest: AiPromptRequest? ->
                if (promptRequest == null) {
                    showOperationToast(false, "", "生成失败：所选范围材料不足，无法构建提示词")
                    return@buildSummaryPrompt
                }
                pendingSummaryWindow = window
                pendingSummaryLevel = level
                showPromptConfirmationDialog(promptRequest)
            }
        }
    }

    private fun showPromptConfirmationDialog(promptRequest: AiPromptRequest) {
        ConfirmAiPromptDialogFragment.newInstance(
            promptJson = promptRequest.displayPromptJson,
            url = promptRequest.url,
            model = promptRequest.request.model,
            timestamp = promptRequest.timestamp
        ).show(childFragmentManager, ConfirmAiPromptDialogFragment.TAG)
    }

    private fun executePendingSummaryGeneration() {
        val window: SummaryWindow = pendingSummaryWindow ?: return
        val level: SummaryLevel = pendingSummaryLevel
        clearPendingSummaryGeneration()
        viewModel.generateSummary(contactId, level, window.startTimestamp, window.endTimestamp) { isSuccess: Boolean ->
            showOperationToast(isSuccess, "已生成${getLevelName(level)}摘要", "生成失败：所选范围材料不足或AI请求失败")
            updateSelectedWindowText()
        }
    }

    private fun clearPendingSummaryGeneration() {
        pendingSummaryWindow = null
        pendingSummaryLevel = SummaryLevel.DAILY
    }

    private fun observeSummaries() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getSummaryItems(contactId).collectLatest { summaries: List<CPhoneMemorySummaryItem> ->
                allSummaries = summaries
                updateSummaryCalendar()
                updateSelectedWindowText()
                submitFilteredList()
            }
        }
    }

    private fun submitFilteredList() {
        val window: SummaryWindow? = getSelectedWindow()
        val filteredSummaries: List<CPhoneMemorySummaryItem> = if (window == null) {
            allSummaries
        } else {
            allSummaries.filter { item: CPhoneMemorySummaryItem ->
                val summary: MemorySummary = item.summary
                summary.summaryLevel == currentLevel && summary.startTimestamp == window.startTimestamp && summary.endTimestamp == window.endTimestamp
            }
        }
        adapter.submitList(filteredSummaries)
        binding.rvSummaries.visibility = if (filteredSummaries.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmptySummary.visibility = if (filteredSummaries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSelectedWindowText() {
        val window: SummaryWindow? = getSelectedWindow()
        if (window == null) {
            binding.tvSelectedWindow.text = "未选择日期，显示该角色全部分层摘要"
            binding.btnGenerateSelectedSummary.text = "选择日历日期后可生成摘要"
            binding.btnGenerateSelectedSummary.isEnabled = false
            return
        }
        binding.tvSelectedWindow.text = "${getLevelName(currentLevel)}范围：${formatWindowTime(window.startTimestamp)} 至 ${formatWindowTime(window.endTimestamp)}"
        val hasSummary: Boolean = allSummaries.any { item: CPhoneMemorySummaryItem ->
            val summary: MemorySummary = item.summary
            summary.summaryLevel == currentLevel && summary.startTimestamp == window.startTimestamp && summary.endTimestamp == window.endTimestamp
        }
        val isCurrentWindow: Boolean = isCurrentSummaryWindow(window)
        binding.btnGenerateSelectedSummary.text = when {
            isCurrentWindow -> "当前${getLevelName(currentLevel)}尚未结束，不能生成摘要"
            hasSummary -> "已存在，重新生成请点列表内按钮"
            else -> "生成所选${getLevelName(currentLevel)}摘要"
        }
        binding.btnGenerateSelectedSummary.isEnabled = !hasSummary && !isCurrentWindow
    }

    private fun getSelectedWindow(): SummaryWindow? {
        val timestamp: Long = selectedTimestamp ?: return null
        val windowStart: Long = when (currentLevel) {
            SummaryLevel.DAILY -> getStartOfSummaryDay(timestamp)
            SummaryLevel.WEEKLY -> getStartOfSelectedWeek(timestamp)
            SummaryLevel.MONTHLY -> getStartOfSelectedMonth(timestamp)
            SummaryLevel.YEARLY -> getStartOfSelectedYear(timestamp)
        }
        return SummaryWindow(windowStart, addDays(windowStart, getWindowSizeDays(currentLevel)))
    }

    private fun getStartOfSummaryDay(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.get(Calendar.HOUR_OF_DAY) < SUMMARY_DAY_START_HOUR) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, SUMMARY_DAY_START_HOUR)
        return calendar.timeInMillis
    }

    private fun getStartOfSelectedWeek(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance(Locale.CHINA)
        calendar.timeInMillis = getStartOfSummaryDay(timestamp)
        calendar.firstDayOfWeek = Calendar.MONDAY
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return calendar.timeInMillis
    }

    private fun getStartOfSelectedMonth(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = getStartOfSummaryDay(timestamp)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    private fun getStartOfSelectedYear(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = getStartOfSummaryDay(timestamp)
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun addDays(timestamp: Long, days: Int): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.timeInMillis
    }

    private fun getVisibleMonthStartCalendar(): Calendar {
        val calendar: Calendar = Calendar.getInstance(Locale.CHINA)
        calendar.timeInMillis = selectedTimestamp ?: System.currentTimeMillis()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        setSummaryStartTime(calendar)
        return calendar
    }

    private fun setSummaryStartTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, SUMMARY_DAY_START_HOUR)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun getCandidateWindowStart(timestamp: Long): Long {
        return when (currentLevel) {
            SummaryLevel.DAILY -> getStartOfSummaryDay(timestamp)
            SummaryLevel.WEEKLY -> getStartOfSelectedWeek(timestamp)
            SummaryLevel.MONTHLY -> getStartOfSelectedMonth(timestamp)
            SummaryLevel.YEARLY -> getStartOfSelectedYear(timestamp)
        }
    }

    private fun getCurrentWindowStart(): Long {
        return getCandidateWindowStart(System.currentTimeMillis())
    }

    private fun hasSummaryForWindow(windowStart: Long): Boolean {
        val windowEnd: Long = addDays(windowStart, getWindowSizeDays(currentLevel))
        return allSummaries.any { item: CPhoneMemorySummaryItem ->
            val summary: MemorySummary = item.summary
            summary.summaryLevel == currentLevel && summary.startTimestamp == windowStart && summary.endTimestamp == windowEnd
        }
    }

    private fun isCurrentSummaryWindow(window: SummaryWindow): Boolean {
        return window.startTimestamp == getCurrentWindowStart()
    }

    private fun getVisibleYearStart(calendar: Calendar): Int {
        val selectedYear: Int = calendar.get(Calendar.YEAR)
        return selectedYear - selectedYear % YEAR_GRID_SIZE
    }

    private fun getCalendarColorPack(): CalendarColorPack {
        return CalendarColorPack(
            primary = resolveThemeColor(com.google.android.material.R.attr.colorPrimary),
            onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface),
            disabled = resolveThemeColor(com.google.android.material.R.attr.colorOutline),
            surface = resolveThemeColor(com.google.android.material.R.attr.colorSurface),
            selectedBackground = resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        )
    }

    private fun resolveThemeColor(attributeId: Int): Int {
        val typedValue: android.util.TypedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attributeId, typedValue, true)
        return typedValue.data
    }

    private fun getWindowSizeDays(level: SummaryLevel): Int {
        return when (level) {
            SummaryLevel.DAILY -> DAILY_WINDOW_DAYS
            SummaryLevel.WEEKLY -> WEEKLY_WINDOW_DAYS
            SummaryLevel.MONTHLY -> MONTHLY_WINDOW_DAYS
            SummaryLevel.YEARLY -> YEARLY_WINDOW_DAYS
        }
    }

    private fun formatWindowTime(timestamp: Long): String {
        return WINDOW_TIME_FORMAT.format(timestamp)
    }

    private fun showEditSummaryDialog(summary: MemorySummary) {
        val summaryTextEditText: EditText = createSummaryEditField(summary.summaryText, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = SUMMARY_EDIT_MIN_LINES
            maxLines = SUMMARY_EDIT_MAX_LINES
            setSelection(text.length)
            isVerticalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
            setOnTouchListener { v, event ->
                // 当EditText可以滚动时，请求父视图(ScrollView)不要拦截触摸事件
                v.parent.requestDisallowInterceptTouchEvent(true)
                // 当触摸事件结束时，恢复父视图的拦截
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
                // 返回false，表示我们没有完全处理这个事件，允许其他监听器（如滚动）继续处理
                false
            }
        }
        val importanceScoreEditText: EditText = createSummaryEditField(summary.importanceScore.toString(), InputType.TYPE_CLASS_NUMBER)
        val sourceMemoryCountEditText: EditText = createSummaryEditField(summary.sourceMemoryCount.toString(), InputType.TYPE_CLASS_NUMBER)
        val editorLayout: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SUMMARY_EDIT_PADDING, SUMMARY_EDIT_PADDING, SUMMARY_EDIT_PADDING, SUMMARY_EDIT_PADDING)
            addView(createSummaryEditLabel("摘要文本", "这段时间窗口保存下来的分层摘要正文，修改后会影响之后的召回和向量化内容。"))
            addView(summaryTextEditText)
            addView(createSummaryEditLabel("重要度", "范围为1-10，数字越大代表这条摘要越重要，召回时会更容易被选中。"))
            addView(importanceScoreEditText)
            addView(createSummaryEditLabel("来源数量", "生成这条摘要时参考的原始记忆数量或下级摘要数量，只用于调试和展示。"))
            addView(sourceMemoryCountEditText)
            addView(createSummaryEditLabel("模型版本", "当前记录为：${summary.modelVersion}。模型字段只展示，不在这里编辑。"))
        }
        val scrollView: ScrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                SUMMARY_EDIT_CONTENT_WEIGHT
            )
            addView(editorLayout)
        }
        var editDialog: androidx.appcompat.app.AlertDialog? = null
        val dialogContentLayout: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * SUMMARY_EDIT_DIALOG_MAX_HEIGHT_RATIO).toInt()
            )
            addView(scrollView)
            addView(LinearLayout(requireContext()).apply {
                gravity = Gravity.END
                orientation = LinearLayout.HORIZONTAL
                setPadding(SUMMARY_EDIT_PADDING, SUMMARY_EDIT_LABEL_BOTTOM_MARGIN, SUMMARY_EDIT_PADDING, SUMMARY_EDIT_PADDING)
                addView(Button(requireContext()).apply {
                    text = "取消"
                    setOnClickListener { editDialog?.dismiss() }
                })
                addView(Button(requireContext()).apply {
                    text = "保存"
                    setOnClickListener {
                        val editInput: CPhoneMemorySummaryEditInput? = buildSummaryEditInput(
                            summaryTextEditText = summaryTextEditText,
                            importanceScoreEditText = importanceScoreEditText,
                            sourceMemoryCountEditText = sourceMemoryCountEditText
                        )
                        if (editInput == null) {
                            showOperationToast(false, "", "摘要不能为空，重要度必须为1-10，来源数量不能小于0")
                            return@setOnClickListener
                        }
                        viewModel.updateSummary(summary, editInput) { isSuccess: Boolean ->
                            showOperationToast(isSuccess, "已保存分层摘要调试字段", "摘要不能为空，重要度必须为1-10，来源数量不能小于0")
                        }
                        editDialog?.dismiss()
                    }
                })
            })
        }
        editDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑分层摘要调试字段")
            .setMessage("可编辑字段会立即写回保存的数据，并停用旧摘要向量，便于重新向量化调试。")
            .setView(dialogContentLayout)
            .show()
    }

    private fun createSummaryEditField(valueText: String, inputTypeValue: Int): EditText {
        return EditText(requireContext()).apply {
            setText(valueText)
            inputType = inputTypeValue
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = SUMMARY_EDIT_FIELD_BOTTOM_MARGIN
            }
        }
    }

    private fun createSummaryEditLabel(titleText: String, descriptionText: String): TextView {
        return TextView(requireContext()).apply {
            text = "$titleText\n$descriptionText"
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            textSize = SUMMARY_EDIT_LABEL_TEXT_SIZE_SP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = SUMMARY_EDIT_LABEL_TOP_MARGIN
                bottomMargin = SUMMARY_EDIT_LABEL_BOTTOM_MARGIN
            }
        }
    }

    private fun buildSummaryEditInput(
        summaryTextEditText: EditText,
        importanceScoreEditText: EditText,
        sourceMemoryCountEditText: EditText
    ): CPhoneMemorySummaryEditInput? {
        val summaryText: String = summaryTextEditText.text.toString().trim()
        val importanceScore: Int = importanceScoreEditText.text.toString().trim().toIntOrNull() ?: return null
        val sourceMemoryCount: Int = sourceMemoryCountEditText.text.toString().trim().toIntOrNull() ?: return null
        if (summaryText.isBlank() || importanceScore !in MIN_IMPORTANCE_SCORE..MAX_IMPORTANCE_SCORE || sourceMemoryCount < MIN_SOURCE_MEMORY_COUNT) {
            return null
        }
        return CPhoneMemorySummaryEditInput(
            summaryText = summaryText,
            sourceMemoryCount = sourceMemoryCount,
            importanceScore = importanceScore
        )
    }

    private fun showDeleteSummaryDialog(summary: MemorySummary) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分层摘要")
            .setMessage("确定删除这条${getLevelName(summary.summaryLevel)}分层摘要吗？原始记忆不会被删除。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteSummary(summary) { isSuccess: Boolean ->
                    showOperationToast(isSuccess, "已删除分层摘要", "删除分层摘要失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteAllSummariesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除全部分层摘要")
            .setMessage("确定删除当前角色的全部分层摘要吗？原始聊天记录和原始记忆不会被删除。")
            .setPositiveButton("全部删除") { _, _ ->
                viewModel.deleteAllSummaries(contactId) { isSuccess: Boolean ->
                    showOperationToast(isSuccess, "已删除该角色全部分层摘要", "删除全部分层摘要失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRepairDefaultImportanceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修复默认重要度")
            .setMessage("将重新计算当前角色所有重要度为3的分层摘要，并停用已变化摘要的旧向量，便于你重新向量化调试。")
            .setPositiveButton("开始修复") { _, _ ->
                viewModel.repairDefaultImportanceSummaries(contactId) { repairedCount: Int? ->
                    if (repairedCount == null) {
                        showOperationToast(false, "", "修复默认重要度失败")
                        return@repairDefaultImportanceSummaries
                    }
                    Toast.makeText(requireContext(), "已修复${repairedCount}条默认重要度摘要", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRegenerateSummaryDialog(summary: MemorySummary) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重新生成分层摘要")
            .setMessage("将根据这段时间窗口内的下级记忆材料重新请求AI生成${getLevelName(summary.summaryLevel)}摘要。")
            .setPositiveButton("重新生成") { _, _ ->
                viewModel.regenerateSummary(summary) { isSuccess: Boolean ->
                    showOperationToast(isSuccess, "已重新生成分层摘要", "生成失败：当前时间窗口的材料不足或AI请求失败")
                    submitFilteredList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun revectorizeSummary(summary: MemorySummary): Unit {
        viewModel.revectorizeSummary(summary) { isSuccess: Boolean ->
            showOperationToast(isSuccess, "已重新向量化分层摘要", "重新向量化失败：没有可关联的原子事件或向量服务失败")
        }
    }

    private fun showOperationToast(isSuccess: Boolean, successText: String, failureText: String) {
        val message: String = if (isSuccess) successText else failureText
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun getLevelName(level: SummaryLevel): String {
        return when (level) {
            SummaryLevel.DAILY -> "每日"
            SummaryLevel.WEEKLY -> "每周"
            SummaryLevel.MONTHLY -> "每月"
            SummaryLevel.YEARLY -> "每年"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class SummaryWindow(
        val startTimestamp: Long,
        val endTimestamp: Long
    )

    private data class CalendarColorPack(
        val primary: Int,
        val onSurface: Int,
        val disabled: Int,
        val surface: Int,
        val selectedBackground: Int
    )

    companion object {
        private const val ARG_CONTACT_ID: String = "contact_id"
        private const val SUMMARY_EDIT_MIN_LINES: Int = 4
        private const val SUMMARY_EDIT_MAX_LINES: Int = 8
        private const val SUMMARY_EDIT_PADDING: Int = 24
        private const val SUMMARY_EDIT_DIALOG_MAX_HEIGHT_RATIO: Float = 0.58F
        private const val SUMMARY_EDIT_CONTENT_WEIGHT: Float = 1F
        private const val SUMMARY_EDIT_FIELD_BOTTOM_MARGIN: Int = 12
        private const val SUMMARY_EDIT_LABEL_TOP_MARGIN: Int = 6
        private const val SUMMARY_EDIT_LABEL_BOTTOM_MARGIN: Int = 4
        private const val SUMMARY_EDIT_LABEL_TEXT_SIZE_SP: Float = 13F
        private const val MIN_SOURCE_MEMORY_COUNT: Int = 0
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val SUMMARY_DAY_START_HOUR: Int = 5
        private const val DAILY_WINDOW_DAYS: Int = 1
        private const val WEEKLY_WINDOW_DAYS: Int = 7
        private const val MONTHLY_WINDOW_DAYS: Int = 30
        private const val YEARLY_WINDOW_DAYS: Int = 365
        private const val DAYS_IN_WEEK: Int = 7
        private const val WEEKLY_COLUMN_COUNT: Int = 1
        private const val MONTHLY_COLUMN_COUNT: Int = 3
        private const val YEARLY_COLUMN_COUNT: Int = 3
        private const val MONTHS_IN_YEAR: Int = 12
        private const val YEAR_GRID_SIZE: Int = 12
        private const val CALENDAR_CELL_MARGIN: Int = 4
        private const val SUMMARY_CALENDAR_TEXT_SIZE_SP: Float = 14F
        private const val SUMMARY_DOT_TEXT: String = "•"
        private const val SUMMARY_EMPTY_DOT_TEXT: String = "•"
        private const val SUMMARY_DOT_RELATIVE_SIZE: Float = 0.6F
        private const val CALENDAR_DRAG_START_DISTANCE: Float = 12F
        private const val CALENDAR_SWIPE_MIN_DISTANCE: Float = 80F
        private const val CALENDAR_BLOCKED_DRAG_RESISTANCE: Float = 0.25F
        private const val CALENDAR_SWIPE_ANIMATION_DURATION_MS: Long = 180L
        private val WEEKDAY_TITLES: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")
        private val WINDOW_TIME_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        private val SHORT_DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("MM-dd", Locale.CHINA)
 
        fun newInstance(contactId: String): CPhoneMemorySummaryFragment {
            return CPhoneMemorySummaryFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
        }
    }
}
