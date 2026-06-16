package com.susking.ephone_s.schedule.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.schedule.databinding.FragmentScheduleCourseEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 独立课程编辑页。
 * 用选择器和推荐时长减少用户输入，保存后复用首页 ViewModel 的写入逻辑。
 */
@AndroidEntryPoint
class ScheduleCourseEditorFragment : Fragment() {

    private var _binding: FragmentScheduleCourseEditorBinding? = null
    private val binding: FragmentScheduleCourseEditorBinding
        get() = _binding ?: throw IllegalStateException("Schedule course editor binding is not available")

    private val viewModel: ScheduleViewModel by activityViewModels()
    private val courseId: String?
        get() = arguments?.getString(ARG_COURSE_ID)
    private val initialDayOfWeek: Int
        get() = arguments?.getInt(ARG_DAY_OF_WEEK, DEFAULT_DAY_OF_WEEK) ?: DEFAULT_DAY_OF_WEEK
    private val initialStartSection: Int
        get() = arguments?.getInt(ARG_START_SECTION, DEFAULT_START_SECTION) ?: DEFAULT_START_SECTION
    private val initialWeek: Int
        get() = arguments?.getInt(ARG_WEEK, DEFAULT_WEEK) ?: DEFAULT_WEEK
    private var currentMaxSection: Int = DEFAULT_MAX_SECTION
    private var currentTotalWeeks: Int = DEFAULT_TOTAL_WEEKS
    private var selectedWeeks: Set<Int> = emptySet()
    private var disabledConflictWeeks: Set<Int> = emptySet()
    private var hasAppliedInitialWeeks: Boolean = false
    private var pendingTouchWeek: Int? = null
    private var isWeekSwipeSelecting: Boolean = false
    private var isWeekSwipeAdding: Boolean = true
    private var weekTouchStartX: Float = 0f
    private var weekTouchStartY: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleCourseEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        setupToolbar()
        setupPickers()
        setupActions()
        fillInitialValues()
        observeEditorTarget()
        if (courseId == null) observeSectionTemplates()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(): Unit {
        binding.courseEditorToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupPickers(): Unit {
        binding.dayOfWeekInput.setAdapter(createTextAdapter(WEEKDAY_OPTIONS))
        binding.startSectionInput.setAdapter(createTextAdapter(createSectionOptions(currentMaxSection)))
        binding.endSectionInput.setAdapter(createTextAdapter(createSectionOptions(currentMaxSection)))
        binding.oneSectionChip.setOnClickListener { updateEndSectionByDuration(ONE_SECTION_DURATION) }
        binding.twoSectionChip.setOnClickListener { updateEndSectionByDuration(TWO_SECTION_DURATION) }
        binding.fullWeekChip.setOnClickListener { selectAvailableWeekRange(DEFAULT_WEEK, currentTotalWeeks) }
        binding.currentWeekChip.setOnClickListener { selectAvailableWeekRange(initialWeek, initialWeek) }
        binding.dayOfWeekInput.setOnItemClickListener { _, _, _, _ -> refreshWeekChips(viewModel.uiState.value) }
        binding.startSectionInput.setOnItemClickListener { _, _, _, _ -> refreshWeekChips(viewModel.uiState.value) }
        binding.endSectionInput.setOnItemClickListener { _, _, _, _ -> refreshWeekChips(viewModel.uiState.value) }
        binding.weekChipGroup.setOnTouchListener { _, event: MotionEvent -> handleWeekChipGroupTouch(event) }
    }

    private fun setupActions(): Unit {
        binding.saveCourseButton.setOnClickListener {
            val weekRange: IntRange = getSelectedContinuousWeekRange() ?: run {
                Snackbar.make(binding.root, "请至少选择一个可用周次", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveCourseFromEditor(
                courseName = binding.courseNameInput.text.toString(),
                teacherName = binding.teacherNameInput.text.toString(),
                classroom = binding.classroomInput.text.toString(),
                dayOfWeekText = parseOptionNumber(binding.dayOfWeekInput.text.toString()).toString(),
                startSectionText = parseOptionNumber(binding.startSectionInput.text.toString()).toString(),
                endSectionText = parseOptionNumber(binding.endSectionInput.text.toString()).toString(),
                courseId = courseId,
                startWeekText = weekRange.first.toString(),
                endWeekText = weekRange.last.toString()
            ) { hasSaved: Boolean ->
                if (!isAdded) return@saveCourseFromEditor
                if (hasSaved) {
                    parentFragmentManager.popBackStack()
                    return@saveCourseFromEditor
                }
                Snackbar.make(binding.root, "该时间段已被其它课程占用，请选择可用周次", Snackbar.LENGTH_SHORT).show()
                refreshWeekChips(viewModel.uiState.value)
            }
        }
    }

    private fun fillInitialValues(): Unit {
        binding.dayOfWeekInput.setText(formatWeekdayOption(initialDayOfWeek), false)
        binding.startSectionInput.setText(formatSectionOption(initialStartSection), false)
        binding.endSectionInput.setText(formatSectionOption((initialStartSection + 1).coerceAtMost(currentMaxSection)), false)
        selectedWeeks = setOf(initialWeek)
        renderWeekChips()
    }

    private fun observeEditorTarget(): Unit {
        val targetCourseId: String = courseId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    val course: ScheduleCourseEntity = state.allCourses.firstOrNull { item: ScheduleCourseEntity -> item.courseId == targetCourseId } ?: return@collect
                    val rule: ScheduleCourseRuleEntity? = state.allCourseRules.firstOrNull { item: ScheduleCourseRuleEntity -> item.courseId == targetCourseId }
                    updateSectionPickers(maxOf(getMaxSectionFromState(state), rule?.endSection ?: DEFAULT_START_SECTION))
                    binding.courseNameInput.setTextIfDifferent(course.courseName)
                    binding.teacherNameInput.setTextIfDifferent(course.teacherName)
                    binding.classroomInput.setTextIfDifferent(course.classroom)
                    binding.dayOfWeekInput.setTextIfDifferent(formatWeekdayOption(rule?.dayOfWeek ?: initialDayOfWeek))
                    binding.startSectionInput.setTextIfDifferent(formatSectionOption(rule?.startSection ?: initialStartSection))
                    binding.endSectionInput.setTextIfDifferent(formatSectionOption(rule?.endSection ?: initialStartSection))
                    if (!hasAppliedInitialWeeks) {
                        selectWeekRange(rule?.startWeek ?: initialWeek, rule?.endWeek ?: initialWeek)
                        hasAppliedInitialWeeks = true
                    }
                    refreshWeekChips(state)
                }
            }
        }
    }

    private fun observeSectionTemplates(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    updateSectionPickers(getMaxSectionFromState(state))
                    if (!hasAppliedInitialWeeks) {
                        selectWeekRange(initialWeek, initialWeek)
                        hasAppliedInitialWeeks = true
                    }
                    refreshWeekChips(state)
                }
            }
        }
    }

    private fun updateSectionPickers(maxSection: Int): Unit {
        val nextMaxSection: Int = maxSection.coerceAtLeast(DEFAULT_START_SECTION)
        if (currentMaxSection == nextMaxSection) return
        currentMaxSection = nextMaxSection
        val sectionOptions: List<String> = createSectionOptions(currentMaxSection)
        binding.startSectionInput.setAdapter(createTextAdapter(sectionOptions))
        binding.endSectionInput.setAdapter(createTextAdapter(sectionOptions))
    }

    private fun getMaxSectionFromState(state: ScheduleUiState): Int {
        return state.sectionTemplates.maxOfOrNull { template -> template.sectionIndex } ?: DEFAULT_MAX_SECTION
    }

    private fun createSectionOptions(maxSection: Int): List<String> {
        return (DEFAULT_START_SECTION..maxSection).map { section: Int -> formatSectionOption(section) }
    }

    private fun createTextAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, values)
    }

    private fun updateEndSectionByDuration(duration: Int): Unit {
        val startSection: Int = parseOptionNumber(binding.startSectionInput.text.toString()).coerceIn(DEFAULT_START_SECTION, currentMaxSection)
        binding.endSectionInput.setText(formatSectionOption((startSection + duration - 1).coerceAtMost(currentMaxSection)), false)
    }

    private fun refreshWeekChips(state: ScheduleUiState): Unit {
        currentTotalWeeks = getTotalWeeksFromState(state)
        disabledConflictWeeks = calculateDisabledConflictWeeks(state)
        selectedWeeks = selectedWeeks.filter { week: Int -> week in DEFAULT_WEEK..currentTotalWeeks && week !in disabledConflictWeeks }.toSet()
        if (selectedWeeks.isEmpty()) selectedWeeks = findFirstAvailableWeek()?.let { week: Int -> setOf(week) }.orEmpty()
        renderWeekChips()
    }

    private fun calculateDisabledConflictWeeks(state: ScheduleUiState): Set<Int> {
        val dayOfWeek: Int = parseOptionNumber(binding.dayOfWeekInput.text.toString())
        val startSection: Int = parseOptionNumber(binding.startSectionInput.text.toString())
        val endSection: Int = parseOptionNumber(binding.endSectionInput.text.toString()).coerceAtLeast(startSection)
        val currentCourseId: String? = courseId
        return state.allCourseRules
            .filter { rule: ScheduleCourseRuleEntity -> rule.isEnabled && rule.courseId != currentCourseId && rule.dayOfWeek == dayOfWeek && hasSectionOverlap(startSection, endSection, rule.startSection, rule.endSection) }
            .flatMap { rule: ScheduleCourseRuleEntity -> (rule.startWeek..rule.endWeek).toList() }
            .filter { week: Int -> week in DEFAULT_WEEK..currentTotalWeeks }
            .toSet()
    }

    private fun renderWeekChips(): Unit {
        binding.weekChipGroup.removeAllViews()
        (DEFAULT_WEEK..currentTotalWeeks).forEach { week: Int ->
            binding.weekChipGroup.addView(createWeekChip(week))
        }
        binding.weekRangeSummaryText.text = buildWeekRangeSummaryText()
    }

    private fun createWeekChip(week: Int): Chip {
        val isDisabled: Boolean = week in disabledConflictWeeks
        return Chip(requireContext()).apply {
            text = week.toString()
            tag = week
            isCheckable = true
            isChecked = week in selectedWeeks
            isEnabled = !isDisabled
            alpha = if (isDisabled) DISABLED_WEEK_ALPHA else ENABLED_WEEK_ALPHA
            setTextColor(if (isDisabled) Color.GRAY else Color.rgb(35, 35, 35))
            setOnTouchListener { _, event: MotionEvent -> handleWeekChipTouch(week, event) }
        }
    }

    private fun handleWeekChipTouch(week: Int, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startWeekTouch(week, event)
                true
            }
            MotionEvent.ACTION_MOVE -> updateWeekTouch(event)
            MotionEvent.ACTION_UP -> {
                finishWeekTouch(week)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearWeekTouch()
                true
            }
            else -> false
        }
    }

    private fun handleWeekChipGroupTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        return updateWeekTouch(event)
    }

    private fun startWeekTouch(week: Int, event: MotionEvent): Unit {
        pendingTouchWeek = week
        isWeekSwipeSelecting = false
        isWeekSwipeAdding = week !in selectedWeeks
        weekTouchStartX = event.rawX
        weekTouchStartY = event.rawY
        binding.weekChipGroup.parent.requestDisallowInterceptTouchEvent(true)
    }

    private fun updateWeekTouch(event: MotionEvent): Boolean {
        if (pendingTouchWeek == null) return false
        val distanceX: Float = kotlin.math.abs(event.rawX - weekTouchStartX)
        val distanceY: Float = kotlin.math.abs(event.rawY - weekTouchStartY)
        val touchSlopPx: Float = WEEK_SWIPE_TOUCH_SLOP_DP.toPx().toFloat()
        if (!isWeekSwipeSelecting && distanceX < touchSlopPx && distanceY < touchSlopPx) return true
        if (!isWeekSwipeSelecting && shouldAllowPageScroll(distanceX, distanceY)) {
            clearWeekTouch()
            return false
        }
        binding.weekChipGroup.parent.requestDisallowInterceptTouchEvent(true)
        isWeekSwipeSelecting = true
        pendingTouchWeek?.let { week: Int -> applyWeekSwipeSelection(week) }
        findTouchedWeek(event.rawX.toInt(), event.rawY.toInt())?.let { week: Int -> applyWeekSwipeSelection(week) }
        return true
    }

    private fun shouldAllowPageScroll(distanceX: Float, distanceY: Float): Boolean {
        return distanceY > distanceX * PAGE_SCROLL_DIRECTION_RATIO
    }

    private fun finishWeekTouch(week: Int): Unit {
        if (!isWeekSwipeSelecting) toggleWeekSelection(week)
        clearWeekTouch()
    }

    private fun clearWeekTouch(): Unit {
        binding.weekChipGroup.parent.requestDisallowInterceptTouchEvent(false)
        pendingTouchWeek = null
        isWeekSwipeSelecting = false
        isWeekSwipeAdding = true
    }

    private fun findTouchedWeek(rawX: Int, rawY: Int): Int? {
        val hitRect: Rect = Rect()
        for (index: Int in 0 until binding.weekChipGroup.childCount) {
            val childView: View = binding.weekChipGroup.getChildAt(index)
            childView.getGlobalVisibleRect(hitRect)
            if (!hitRect.contains(rawX, rawY)) continue
            return childView.tag as? Int
        }
        return null
    }

    private fun applyWeekSwipeSelection(week: Int): Unit {
        if (isWeekSwipeAdding) {
            selectWeek(week)
            return
        }
        deselectWeek(week)
    }

    private fun selectWeek(week: Int): Unit {
        if (week in disabledConflictWeeks || week in selectedWeeks) return
        selectedWeeks = selectedWeeks + week
        updateWeekChipChecked(week, true)
        binding.weekRangeSummaryText.text = buildWeekRangeSummaryText()
    }

    private fun deselectWeek(week: Int): Unit {
        if (week in disabledConflictWeeks || week !in selectedWeeks) return
        selectedWeeks = selectedWeeks - week
        updateWeekChipChecked(week, false)
        binding.weekRangeSummaryText.text = buildWeekRangeSummaryText()
    }

    private fun updateWeekChipChecked(week: Int, isChecked: Boolean): Unit {
        for (index: Int in 0 until binding.weekChipGroup.childCount) {
            val childChip: Chip = binding.weekChipGroup.getChildAt(index) as? Chip ?: continue
            if (childChip.tag != week) continue
            childChip.isChecked = isChecked
            return
        }
    }

    private fun toggleWeekSelection(week: Int): Unit {
        if (week in disabledConflictWeeks) return
        selectedWeeks = if (week in selectedWeeks) selectedWeeks - week else selectedWeeks + week
        renderWeekChips()
    }

    private fun selectWeekRange(startWeek: Int, endWeek: Int): Unit {
        selectedWeeks = (startWeek.coerceAtLeast(DEFAULT_WEEK)..endWeek.coerceAtMost(currentTotalWeeks)).toSet()
        renderWeekChips()
    }

    private fun selectAvailableWeekRange(startWeek: Int, endWeek: Int): Unit {
        selectedWeeks = (startWeek.coerceAtLeast(DEFAULT_WEEK)..endWeek.coerceAtMost(currentTotalWeeks))
            .filter { week: Int -> week !in disabledConflictWeeks }
            .toSet()
        renderWeekChips()
    }

    private fun getSelectedContinuousWeekRange(): IntRange? {
        val sortedWeeks: List<Int> = selectedWeeks.sorted()
        val firstWeek: Int = sortedWeeks.firstOrNull() ?: return null
        val lastWeek: Int = sortedWeeks.last()
        return firstWeek..lastWeek
    }

    private fun findFirstAvailableWeek(): Int? {
        return (DEFAULT_WEEK..currentTotalWeeks).firstOrNull { week: Int -> week !in disabledConflictWeeks }
    }

    private fun getTotalWeeksFromState(state: ScheduleUiState): Int {
        return state.semesters.firstOrNull { semester -> semester.isActive }?.totalWeeks
            ?: state.semesters.firstOrNull()?.totalWeeks
            ?: DEFAULT_TOTAL_WEEKS
    }

    private fun hasSectionOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean {
        return firstStart <= secondEnd && secondStart <= firstEnd
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun buildWeekRangeSummaryText(): String {
        val range: IntRange = getSelectedContinuousWeekRange() ?: return "未选择周次"
        val disabledText: String = if (disabledConflictWeeks.isEmpty()) "" else "，灰色周次已有冲突不可选"
        return "已选择第 ${range.first}-${range.last} 周$disabledText"
    }

    private fun parseOptionNumber(value: String): Int {
        return value.filter { char: Char -> char.isDigit() }.toIntOrNull() ?: DEFAULT_OPTION_VALUE
    }

    private fun formatWeekdayOption(dayOfWeek: Int): String {
        val label: String = WEEKDAY_LABELS.getOrNull(dayOfWeek - 1) ?: WEEKDAY_LABELS.first()
        return "$dayOfWeek $label"
    }

    private fun formatSectionOption(section: Int): String {
        return "第 $section 节"
    }

    private fun android.widget.EditText.setTextIfDifferent(value: String): Unit {
        if (text.toString() != value) setText(value)
    }

    companion object {
        private const val ARG_COURSE_ID: String = "course_id"
        private const val ARG_DAY_OF_WEEK: String = "day_of_week"
        private const val ARG_START_SECTION: String = "start_section"
        private const val ARG_WEEK: String = "week"
        private const val DEFAULT_DAY_OF_WEEK: Int = 1
        private const val DEFAULT_START_SECTION: Int = 1
        private const val DEFAULT_WEEK: Int = 1
        private const val DEFAULT_TOTAL_WEEKS: Int = 20
        private const val DEFAULT_MAX_SECTION: Int = 12
        private const val DEFAULT_OPTION_VALUE: Int = 1
        private const val ONE_SECTION_DURATION: Int = 1
        private const val TWO_SECTION_DURATION: Int = 2
        private val WEEKDAY_LABELS: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        private val WEEKDAY_OPTIONS: List<String> = WEEKDAY_LABELS.mapIndexed { index: Int, label: String -> "${index + 1} $label" }
        private const val DISABLED_WEEK_ALPHA: Float = 0.38f
        private const val ENABLED_WEEK_ALPHA: Float = 1f
        private const val WEEK_SWIPE_TOUCH_SLOP_DP: Int = 6
        private const val PAGE_SCROLL_DIRECTION_RATIO: Float = 1.2f

        fun newInstance(
            courseId: String? = null,
            dayOfWeek: Int = DEFAULT_DAY_OF_WEEK,
            startSection: Int = DEFAULT_START_SECTION,
            week: Int = DEFAULT_WEEK
        ): ScheduleCourseEditorFragment {
            return ScheduleCourseEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_COURSE_ID, courseId)
                    putInt(ARG_DAY_OF_WEEK, dayOfWeek)
                    putInt(ARG_START_SECTION, startSection)
                    putInt(ARG_WEEK, week)
                }
            }
        }
    }
}
