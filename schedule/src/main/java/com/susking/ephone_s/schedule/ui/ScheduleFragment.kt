package com.susking.ephone_s.schedule.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseRuleEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity
import com.susking.ephone_s.aidata.data.local.entity.isActiveInWeek
import com.susking.ephone_s.schedule.databinding.FragmentScheduleBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/**
 * 课程表与校园动态首页。
 * 首期以信息卡片形式展示课程、作业、考试和校园动态，后续再扩展周视图和编辑页。
 */
@AndroidEntryPoint
class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding: FragmentScheduleBinding
        get() = _binding ?: throw IllegalStateException("Schedule binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    private var savedScrollY: Int = 0
    private var hasPendingScrollRestore: Boolean = false
    private var selectedHomeWeek: Int = HOME_MIN_WEEK
    private var pendingHomeBlankCell: HomeBlankCellSelection? = null
    private var homeSwipeStartX: Float = 0f
    private var homeSwipeStartY: Float = 0f
    private var isHomeHorizontalSwipe: Boolean = false
    private var isHomePageAnimating: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        setupToolbar()
        setupCategoryBar()
        setupHomeWeekSwitcher()
        setupActions()
        viewModel.ensureAcademicTemplatesInitialized()
        observeUiState()
    }

    override fun onPause() {
        savedScrollY = binding.scheduleScrollView.scrollY
        super.onPause()
    }

    override fun onDestroyView() {
        savedScrollY = binding.scheduleScrollView.scrollY
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(): Unit {
        binding.scheduleToolbar.title = ""
        binding.semesterNameBadgeText.background = createSemesterBadgeBackground()
        binding.semesterNameBadgeText.setOnClickListener { showSemesterSwitcher() }
        binding.scheduleToolbar.menu.add("课表设置").apply {
            setIcon(android.R.drawable.ic_menu_manage)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                openChildPage(ScheduleSettingsFragment.newInstance())
                true
            }
        }
    }

    private fun setupCategoryBar(): Unit {
        binding.scheduleCategoryGroup.check(binding.scheduleCourseCategoryButton.id)
        updateCategoryVisibility(binding.scheduleCourseCategoryButton.id)
        binding.scheduleCategoryGroup.addOnButtonCheckedListener { _, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener
            updateCategoryVisibility(checkedId)
        }
    }

    private fun updateCategoryVisibility(checkedId: Int): Unit {
        val isCourseCategory: Boolean = checkedId == binding.scheduleCourseCategoryButton.id
        binding.scheduleCourseCategoryContainer.visibility = if (isCourseCategory) View.VISIBLE else View.GONE
        binding.scheduleOtherCategoryContainer.visibility = if (isCourseCategory) View.GONE else View.VISIBLE
    }

    private fun setupHomeWeekSwitcher(): Unit {
        binding.homePreviousWeekButton.setOnClickListener { changeSelectedHomeWeekDirectly(HOME_WEEK_STEP_BACKWARD) }
        binding.homeNextWeekButton.setOnClickListener { changeSelectedHomeWeekDirectly(HOME_WEEK_STEP_FORWARD) }
        binding.homeWeekTitleText.setOnClickListener { showHomeWeekPicker() }
        binding.homeWeekGrid.setOnTouchListener { view: View, event: MotionEvent -> handleHomeGridTouch(view, event) }
        updateHomeWeekTitle()
    }

    private fun setupActions(): Unit {
        binding.addAssignmentTitleButton.setOnClickListener { openChildPage(ScheduleAssignmentEditorFragment.newInstance()) }
        binding.addExamTitleButton.setOnClickListener { openChildPage(ScheduleExamEditorFragment.newInstance()) }
        binding.openTimelineButton.setOnClickListener { openChildPage(ScheduleTimelineFragment.newInstance()) }
        binding.toggleAiVisibleButton.setOnClickListener { viewModel.toggleAiVisibility() }
        binding.toggleAiCareButton.setOnClickListener { viewModel.toggleAiProactiveCare() }
        binding.cycleAiCareIntensityButton.setOnClickListener { viewModel.cycleAiCareIntensity() }
    }

    private fun openChildPage(fragment: Fragment): Unit {
        savedScrollY = binding.scheduleScrollView.scrollY
        hasPendingScrollRestore = true
        parentFragmentManager.beginTransaction()
            .replace(requireParentContainerId(), fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun restoreScrollPosition(): Unit {
        if (!hasPendingScrollRestore && savedScrollY <= 0) return
        val currentBinding: FragmentScheduleBinding = _binding ?: return
        currentBinding.scheduleScrollView.post {
            val postedBinding: FragmentScheduleBinding = _binding ?: return@post
            postedBinding.scheduleScrollView.scrollTo(0, savedScrollY)
            hasPendingScrollRestore = false
        }
    }

    private fun requireParentContainerId(): Int {
        val containerId: Int = (view?.parent as? View)?.id ?: View.NO_ID
        if (containerId != View.NO_ID) return containerId
        return requireView().id
    }

    private fun showCourseEditorDialog(): Unit {
        val courseNameInput: EditText = createTextInput("课程名，例如 高等数学")
        val teacherNameInput: EditText = createTextInput("老师，可留空")
        val classroomInput: EditText = createTextInput("教室，例如 A203")
        val dayOfWeekInput: EditText = createNumberInput("星期几，1-7")
        val startSectionInput: EditText = createNumberInput("开始节次")
        val endSectionInput: EditText = createNumberInput("结束节次")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增课程")
            .setView(createEditorContainer(courseNameInput, teacherNameInput, classroomInput, dayOfWeekInput, startSectionInput, endSectionInput))
            .setNegativeButton("取消", null)
            .setNeutralButton("快速新增") { _, _ -> viewModel.addQuickCourse() }
            .setPositiveButton("保存") { _, _ ->
                viewModel.saveCourseFromEditor(
                    courseName = courseNameInput.text.toString(),
                    teacherName = teacherNameInput.text.toString(),
                    classroom = classroomInput.text.toString(),
                    dayOfWeekText = dayOfWeekInput.text.toString(),
                    startSectionText = startSectionInput.text.toString(),
                    endSectionText = endSectionInput.text.toString()
                )
            }
            .show()
    }

    private fun showAssignmentEditorDialog(): Unit {
        val titleInput: EditText = createTextInput("作业标题")
        val contentInput: EditText = createTextInput("作业内容，可留空")
        val priorityInput: EditText = createNumberInput("优先级，1-5")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增作业")
            .setView(createEditorContainer(titleInput, contentInput, priorityInput))
            .setNegativeButton("取消", null)
            .setNeutralButton("快速新增") { _, _ -> viewModel.addQuickAssignment() }
            .setPositiveButton("保存") { _, _ ->
                viewModel.saveAssignmentFromEditor(
                    title = titleInput.text.toString(),
                    content = contentInput.text.toString(),
                    priorityText = priorityInput.text.toString()
                )
            }
            .show()
    }

    private fun showExamEditorDialog(): Unit {
        val examNameInput: EditText = createTextInput("考试名，例如 高数期中")
        val classroomInput: EditText = createTextInput("考场，可留空")
        val scopeInput: EditText = createTextInput("考试范围，可留空")
        val importanceInput: EditText = createNumberInput("重要程度，1-5")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增考试")
            .setView(createEditorContainer(examNameInput, classroomInput, scopeInput, importanceInput))
            .setNegativeButton("取消", null)
            .setNeutralButton("快速新增") { _, _ -> viewModel.addQuickExam() }
            .setPositiveButton("保存") { _, _ ->
                viewModel.saveExamFromEditor(
                    examName = examNameInput.text.toString(),
                    classroom = classroomInput.text.toString(),
                    scopeText = scopeInput.text.toString(),
                    importanceText = importanceInput.text.toString()
                )
            }
            .show()
    }

    private fun createEditorContainer(vararg inputs: EditText): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DIALOG_PADDING, DIALOG_PADDING_HALF, DIALOG_PADDING, 0)
            inputs.forEach { input: EditText -> addView(input) }
        }
    }

    private fun createTextInput(hintText: String): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = TEXT_INPUT_MAX_LINES
        }
    }

    private fun createNumberInput(hintText: String): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = NUMBER_INPUT_MAX_LINES
        }
    }

    private fun observeUiState(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ScheduleUiState): Unit {
        if (!state.canRenderAcademicContent()) {
            clearLoadingTexts()
            return
        }
        showLoadedTexts()
        binding.assignmentsText.text = state.pendingAssignments
            .takeIf { assignments -> assignments.isNotEmpty() }
            ?.joinToString(separator = "\n") { assignment -> "• ${assignment.title}  状态：${assignment.status}" }
            ?: "当前没有未完成作业。"
        binding.assignmentsText.setOnClickListener {
            state.pendingAssignments.firstOrNull()?.let { assignment -> openChildPage(ScheduleAssignmentEditorFragment.newInstance(assignment.assignmentId)) }
        }

        binding.examsText.text = state.upcomingExams
            .takeIf { exams -> exams.isNotEmpty() }
            ?.joinToString(separator = "\n") { exam -> "• ${exam.examName}  复习：${exam.reviewStatus}" }
            ?: "近期没有考试安排。"
        binding.examsText.setOnClickListener {
            state.upcomingExams.firstOrNull()?.let { exam -> openChildPage(ScheduleExamEditorFragment.newInstance(exam.examId)) }
        }

        coerceSelectedHomeWeek(state)
        updateSemesterBadge(state)
        renderHomeWeekGrid(state)
        restoreScrollPosition()

        binding.timelineText.text = state.timelineLines
            .takeIf { lines: List<String> -> lines.isNotEmpty() }
            ?.joinToString(separator = "\n") { line: String -> "• $line" }
            ?: "暂无日程流事项。"

        binding.aiPolicyText.text = if (state.aiPolicy.isAiVisible) {
            "AI 可见：已开启\n主动关心：${if (state.aiPolicy.canAiProactivelyCare) "允许" else "关闭"}\n关心强度：${state.aiPolicy.careIntensity}"
        } else {
            "AI 可见：已关闭\n主动关心：${if (state.aiPolicy.canAiProactivelyCare) "允许" else "关闭"}\n关心强度：${state.aiPolicy.careIntensity}"
        }
        binding.toggleAiVisibleButton.text = if (state.aiPolicy.isAiVisible) "关闭 AI 可见" else "开启 AI 可见"
        binding.toggleAiCareButton.text = if (state.aiPolicy.canAiProactivelyCare) "关闭主动关心" else "开启主动关心"
        binding.cycleAiCareIntensityButton.text = "调整关心强度：${state.aiPolicy.careIntensity}"
    }

    private fun clearLoadingTexts(): Unit {
        binding.scheduleScrollView.visibility = View.INVISIBLE
        binding.scheduleCategoryGroup.visibility = View.INVISIBLE
        binding.assignmentsText.text = ""
        binding.examsText.text = ""
        binding.timelineText.text = ""
        binding.aiPolicyText.text = ""
        binding.assignmentsText.visibility = View.INVISIBLE
        binding.examsText.visibility = View.INVISIBLE
        binding.timelineText.visibility = View.INVISIBLE
        binding.aiPolicyText.visibility = View.INVISIBLE
        binding.toggleAiVisibleButton.visibility = View.INVISIBLE
        binding.toggleAiCareButton.visibility = View.INVISIBLE
        binding.cycleAiCareIntensityButton.visibility = View.INVISIBLE
        binding.homeWeekTitleText.visibility = View.INVISIBLE
        binding.homePreviousWeekButton.visibility = View.INVISIBLE
        binding.homeNextWeekButton.visibility = View.INVISIBLE
        binding.homeWeekGrid.visibility = View.INVISIBLE
        updateSemesterBadge(viewModel.uiState.value)
    }

    private fun showLoadedTexts(): Unit {
        binding.scheduleScrollView.visibility = View.VISIBLE
        binding.scheduleCategoryGroup.visibility = View.VISIBLE
        binding.assignmentsText.visibility = View.VISIBLE
        binding.examsText.visibility = View.VISIBLE
        binding.timelineText.visibility = View.VISIBLE
        binding.aiPolicyText.visibility = View.VISIBLE
        binding.toggleAiVisibleButton.visibility = View.VISIBLE
        binding.toggleAiCareButton.visibility = View.VISIBLE
        binding.cycleAiCareIntensityButton.visibility = View.VISIBLE
        binding.homeWeekTitleText.visibility = View.VISIBLE
        binding.homePreviousWeekButton.visibility = View.VISIBLE
        binding.homeNextWeekButton.visibility = View.VISIBLE
        binding.homeWeekGrid.visibility = View.VISIBLE
    }

    private fun renderHomeWeekGrid(state: ScheduleUiState): Unit {
        val currentBinding: FragmentScheduleBinding = _binding ?: return
        if (currentBinding.homeWeekGrid.width == 0) {
            currentBinding.homeWeekGrid.post {
                if (_binding == null) return@post
                renderHomeWeekGrid(state)
            }
            return
        }
        val lastSection: Int = findHomeLastSection(state)
        currentBinding.homeWeekGrid.removeAllViews()
        currentBinding.homeWeekGrid.rowCount = calculateHomeTotalRows(lastSection)
        addHomeHeaderCells()
        for (sectionIndex: Int in HOME_FIRST_SECTION..lastSection) {
            val rowIndex: Int = sectionIndex - HOME_FIRST_SECTION + HOME_HEADER_ROW_COUNT
            addHomeGridCell(buildHomeSectionText(sectionIndex, state), true, HOME_SECTION_COLOR, null, true, rowIndex = rowIndex, columnIndex = HOME_SECTION_COLUMN_INDEX)
            for (dayOfWeek: Int in HOME_FIRST_DAY..HOME_LAST_DAY) {
                addHomeCourseCell(dayOfWeek, sectionIndex, state, rowIndex, dayOfWeek)
            }
        }
    }

    private fun addHomeHeaderCells(): Unit {
        addHomeGridCell("节次", true, HOME_HEADER_COLOR, null, true, rowIndex = HOME_HEADER_ROW_INDEX, columnIndex = HOME_SECTION_COLUMN_INDEX)
        for (dayOfWeek: Int in HOME_FIRST_DAY..HOME_LAST_DAY) {
            addHomeGridCell(buildHomeWeekdayHeaderText(dayOfWeek), true, HOME_HEADER_COLOR, null, false, rowIndex = HOME_HEADER_ROW_INDEX, columnIndex = dayOfWeek)
        }
    }

    private fun addHomeCourseCell(dayOfWeek: Int, sectionIndex: Int, state: ScheduleUiState, rowIndex: Int, columnIndex: Int): Unit {
        val courseMap: Map<String, ScheduleCourseEntity> = state.allCourses.associateBy { course: ScheduleCourseEntity -> course.courseId }
        // 用 isActiveInWeek 统一判定本周是否上课(含单双周)，并按起始节排序，
        // 保证跨节合并时 firstRule 取到的是起始节最早的规则，rowSpan 计算正确。
        val matchedRules: List<ScheduleCourseRuleEntity> = state.allCourseRules.filter { rule: ScheduleCourseRuleEntity ->
            rule.isEnabled && rule.dayOfWeek == dayOfWeek && sectionIndex in rule.startSection..rule.endSection && rule.isActiveInWeek(selectedHomeWeek)
        }.sortedBy { rule: ScheduleCourseRuleEntity -> rule.startSection }
        val firstRule: ScheduleCourseRuleEntity? = matchedRules.firstOrNull()
        val firstCourse: ScheduleCourseEntity? = firstRule?.let { rule: ScheduleCourseRuleEntity -> courseMap[rule.courseId] }
        if (firstRule == null || firstCourse == null) {
            addHomeBlankCourseCell(dayOfWeek, sectionIndex, rowIndex, columnIndex)
            return
        }
        if (sectionIndex > firstRule.startSection) return
        val text: String = matchedRules.joinToString(separator = "\n\n") { rule: ScheduleCourseRuleEntity ->
            val course: ScheduleCourseEntity? = courseMap[rule.courseId]
            buildHomeCourseCellText(course, rule)
        }
        val colorText: String = firstCourse.courseColor
        val rowSpan: Int = (firstRule.endSection - firstRule.startSection + HOME_ROW_COUNT_STEP).coerceAtLeast(HOME_ROW_COUNT_STEP)
        addHomeGridCell(text.ifBlank { " " }, false, parseHomeCellColor(colorText), firstCourse.courseId, false, firstCourse, firstRule, dayOfWeek, sectionIndex, rowIndex, columnIndex, rowSpan)
    }

    private fun addHomeBlankCourseCell(dayOfWeek: Int, sectionIndex: Int, rowIndex: Int, columnIndex: Int): Unit {
        val isSelected: Boolean = pendingHomeBlankCell == HomeBlankCellSelection(dayOfWeek, sectionIndex)
        val text: String = if (isSelected) "+" else " "
        val color: Int = if (isSelected) HOME_BLANK_SELECTED_COLOR else HOME_BLANK_CELL_COLOR
        addHomeGridCell(text, false, color, null, false, null, null, dayOfWeek, sectionIndex, rowIndex, columnIndex)
    }

    private fun buildHomeCourseCellText(course: ScheduleCourseEntity?, rule: ScheduleCourseRuleEntity): String {
        val courseNameText: String = course?.courseName ?: "未知课程"
        val classroomText: String = rule.classroomOverride.ifBlank { course?.classroom.orEmpty() }
        return listOf(courseNameText, classroomText).filter { value: String -> value.isNotBlank() }.joinToString(separator = "\n")
    }

    private fun findHomeLastSection(state: ScheduleUiState): Int {
        return state.sectionTemplates.maxOfOrNull { template -> template.sectionIndex } ?: HOME_LAST_SECTION
    }

    private fun calculateHomeTotalRows(lastSection: Int): Int {
        return lastSection - HOME_FIRST_SECTION + HOME_HEADER_ROW_COUNT + HOME_ROW_COUNT_STEP
    }

    private fun buildHomeSectionText(sectionIndex: Int, state: ScheduleUiState): String {
        val template = state.sectionTemplates.firstOrNull { item -> item.sectionIndex == sectionIndex }
        val fallbackTime: Pair<String, String>? = HOME_DEFAULT_SECTION_TIMES.getOrNull(sectionIndex - 1)
        val startTimeText: String = template?.startTimeText ?: fallbackTime?.first.orEmpty()
        val endTimeText: String = template?.endTimeText ?: fallbackTime?.second.orEmpty()
        val timeText: String = listOf(startTimeText, endTimeText)
            .filter { value: String -> value.isNotBlank() }
            .joinToString(separator = HOME_SECTION_TIME_RANGE_SEPARATOR)
        return listOf(sectionIndex.toString(), timeText).filter { value: String -> value.isNotBlank() }.joinToString(separator = "\n")
    }

    private fun addHomeGridCell(
        text: String,
        isHeader: Boolean,
        backgroundColor: Int,
        courseId: String?,
        isSectionColumn: Boolean,
        course: ScheduleCourseEntity? = null,
        rule: ScheduleCourseRuleEntity? = null,
        dayOfWeek: Int = 0,
        sectionIndex: Int = 0,
        rowIndex: Int,
        columnIndex: Int,
        rowSpan: Int = HOME_ROW_COUNT_STEP
    ): Unit {
        val cellView: View = if (isSectionColumn && !isHeader) {
            createHomeSectionCellView(text, backgroundColor)
        } else {
            TextView(requireContext()).apply {
                this.text = text
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = if (isHeader) HOME_HEADER_MAX_LINES else HOME_COURSE_MAX_LINES
                setTextColor(if (isHeader) Color.WHITE else if (text == "+") HOME_ACCENT_COLOR else Color.rgb(35, 35, 35))
                typeface = if (isHeader || text == "+") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textSize = if (isHeader) HOME_HEADER_TEXT_SIZE_SP else HOME_CELL_TEXT_SIZE_SP
            }
        }.apply {
            minimumHeight = calculateHomeCellHeight(isSectionColumn)
            setPadding(HOME_CELL_PADDING_DP.toPx(), HOME_CELL_PADDING_DP.toPx(), HOME_CELL_PADDING_DP.toPx(), HOME_CELL_PADDING_DP.toPx())
            background = GradientDrawable().apply {
                cornerRadius = HOME_CELL_CORNER_RADIUS_DP.toPx().toFloat()
                setStroke(HOME_GRID_STROKE_WIDTH_DP.toPx(), Color.WHITE)
                setColor(backgroundColor)
            }
            setOnTouchListener { view: View, event: MotionEvent -> handleHomeGridTouch(view, event) }
            setOnClickListener {
                when {
                    course != null && rule != null -> showHomeCourseDetail(course, rule)
                    !isHeader && !isSectionColumn -> handleHomeBlankCellClick(dayOfWeek, sectionIndex)
                    else -> pendingHomeBlankCell = null
                }
            }
        }
        if (rowIndex >= binding.homeWeekGrid.rowCount) {
            binding.homeWeekGrid.rowCount = rowIndex + HOME_ROW_COUNT_STEP
        }
        val params: GridLayout.LayoutParams = GridLayout.LayoutParams(
            GridLayout.spec(rowIndex, rowSpan, GridLayout.FILL),
            GridLayout.spec(columnIndex, GridLayout.FILL)
        ).apply {
            width = calculateHomeCellWidth(isSectionColumn)
            height = calculateHomeCellHeight(isSectionColumn) * rowSpan
            setGravity(Gravity.FILL)
            setMargins(HOME_CELL_MARGIN_DP.toPx(), HOME_CELL_MARGIN_DP.toPx(), HOME_CELL_MARGIN_DP.toPx(), HOME_CELL_MARGIN_DP.toPx())
        }
        binding.homeWeekGrid.addView(cellView, params)
    }

    private fun buildHomeWeekdayHeaderText(dayOfWeek: Int): String {
        val weekdayName: String = HOME_WEEKDAY_NAMES.getOrNull(dayOfWeek - 1) ?: HOME_WEEKDAY_NAMES.last()
        val dateText: String = buildHomeDateText(dayOfWeek)
        return "$weekdayName\n$dateText"
    }

    private fun buildHomeDateText(dayOfWeek: Int): String {
        val calendar: Calendar = Calendar.getInstance(HOME_TIME_ZONE)
        val semesterStartMillis: Long? = viewModel.uiState.value.semesters.firstOrNull { semester -> semester.isActive }?.startDateMillis
            ?: viewModel.uiState.value.semesters.firstOrNull()?.startDateMillis
        if (semesterStartMillis != null) {
            calendar.timeInMillis = semesterStartMillis + ((selectedHomeWeek - 1) * HOME_DAYS_PER_WEEK + (dayOfWeek - 1)) * HOME_DAY_MILLIS
        } else {
            val currentDayOfWeek: Int = calendar.get(Calendar.DAY_OF_WEEK).toHomeScheduleDayOfWeek()
            calendar.add(Calendar.DAY_OF_YEAR, dayOfWeek - currentDayOfWeek + (selectedHomeWeek - HOME_MIN_WEEK) * HOME_DAYS_PER_WEEK)
        }
        return HOME_DATE_FORMAT.format(calendar.time)
    }

    private fun createHomeSectionCellView(text: String, backgroundColor: Int): LinearLayout {
        val lines: List<String> = text.lines()
        val sectionIndexText: String = lines.firstOrNull().orEmpty()
        val sectionTimeText: String = lines.drop(HOME_SECTION_TIME_LINE_START_INDEX).filter { line: String -> line.isNotBlank() }.joinToString(separator = HOME_SECTION_TIME_RANGE_SEPARATOR)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(requireContext()).apply {
                this.text = sectionIndexText
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                textSize = HOME_SECTION_INDEX_TEXT_SIZE_SP
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(requireContext()).apply {
                this.text = sectionTimeText
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT
                textSize = HOME_SECTION_TIME_TEXT_SIZE_SP
                setSingleLine(true)
                setLineSpacing(HOME_SECTION_LINE_SPACING_EXTRA_DP.toPx().toFloat(), HOME_SECTION_LINE_SPACING_MULTIPLIER)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
    }

    private fun calculateHomeCellHeight(isSectionColumn: Boolean): Int {
        return if (isSectionColumn) HOME_SECTION_CELL_HEIGHT_DP.toPx() else HOME_CELL_HEIGHT_DP.toPx()
    }

    private fun calculateHomeCellWidth(isSectionColumn: Boolean): Int {
        val totalWidth: Int = binding.homeWeekGrid.width.takeIf { width: Int -> width > 0 } ?: resources.displayMetrics.widthPixels
        val marginWidth: Int = HOME_CELL_MARGIN_DP.toPx() * HOME_TOTAL_COLUMNS * HOME_MARGIN_SIDES
        val sectionWidth: Int = HOME_SECTION_COLUMN_WIDTH_DP.toPx()
        if (isSectionColumn) return sectionWidth
        return ((totalWidth - sectionWidth - marginWidth) / HOME_DAY_COLUMNS).coerceAtLeast(HOME_MIN_DAY_CELL_WIDTH_DP.toPx())
    }

    private fun parseHomeCellColor(colorText: String): Int {
        return runCatching {
            ColorUtils.setAlphaComponent(Color.parseColor(colorText), HOME_CELL_ALPHA)
        }.getOrDefault(Color.rgb(245, 241, 255))
    }

    private fun handleHomeBlankCellClick(dayOfWeek: Int, sectionIndex: Int): Unit {
        val selection: HomeBlankCellSelection = HomeBlankCellSelection(dayOfWeek, sectionIndex)
        if (pendingHomeBlankCell == selection) {
            openChildPage(ScheduleCourseEditorFragment.newInstance(null, dayOfWeek, sectionIndex, selectedHomeWeek))
            return
        }
        pendingHomeBlankCell = selection
        renderHomeWeekGrid(viewModel.uiState.value)
    }

    private fun showHomeCourseDetail(course: ScheduleCourseEntity, rule: ScheduleCourseRuleEntity): Unit {
        pendingHomeBlankCell = null
        BottomSheetDialog(requireContext()).apply {
            val container: LinearLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(HOME_DETAIL_PADDING_DP.toPx(), HOME_DETAIL_PADDING_DP.toPx(), HOME_DETAIL_PADDING_DP.toPx(), HOME_DETAIL_PADDING_DP.toPx())
            }
            container.addView(createHomeDetailText(course.courseName, HOME_DETAIL_TITLE_TEXT_SIZE_SP, true))
            container.addView(createHomeDetailText(buildHomeCourseDetailText(course, rule), HOME_DETAIL_BODY_TEXT_SIZE_SP, false))
            container.addView(MaterialButton(requireContext()).apply {
                text = "编辑课程"
                setOnClickListener {
                    dismiss()
                    openChildPage(ScheduleCourseEditorFragment.newInstance(course.courseId, rule.dayOfWeek, rule.startSection, selectedHomeWeek))
                }
            })
            container.addView(MaterialButton(requireContext()).apply {
                text = "删除课程"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(211, 47, 47))
                setOnClickListener {
                    dismiss()
                    confirmHomeDeleteCourse(course)
                }
            })
            setContentView(container)
            show()
        }
    }

    private fun createHomeDetailText(text: String, textSize: Float, isBold: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = textSize
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(Color.rgb(35, 35, 35))
            setPadding(0, HOME_DETAIL_TEXT_PADDING_DP.toPx(), 0, HOME_DETAIL_TEXT_PADDING_DP.toPx())
        }
    }

    private fun buildHomeCourseDetailText(course: ScheduleCourseEntity, rule: ScheduleCourseRuleEntity): String {
        val classroomText: String = rule.classroomOverride.ifBlank { course.classroom }.ifBlank { "未填写教室" }
        val teacherText: String = course.teacherName.ifBlank { "未填写老师" }
        return "第 $selectedHomeWeek 周｜${rule.dayOfWeek.toHomeWeekdayName()}｜第 ${rule.startSection}-${rule.endSection} 节\n$classroomText｜$teacherText"
    }

    private fun confirmHomeDeleteCourse(course: ScheduleCourseEntity): Unit {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除课程")
            .setMessage("会删除「${course.courseName}」以及它的上课时间规则。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> viewModel.deleteCourse(course.courseId) }
            .show()
    }

    private fun showSemesterSwitcher(): Unit {
        val state: ScheduleUiState = viewModel.uiState.value
        val semesters: List<ScheduleSemesterEntity> = state.semesters
        if (semesters.isEmpty()) return
        val activeSemester: ScheduleSemesterEntity? = findCurrentSemester(state)
        val semesterItems: Array<String> = semesters.map { semester: ScheduleSemesterEntity -> semester.semesterName }.toTypedArray()
        val checkedIndex: Int = semesters.indexOfFirst { semester: ScheduleSemesterEntity -> semester.semesterId == activeSemester?.semesterId }
            .coerceAtLeast(SEMESTER_SWITCHER_DEFAULT_INDEX)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("切换课表")
            .setSingleChoiceItems(semesterItems, checkedIndex) { dialog, selectedIndex: Int ->
                val semester: ScheduleSemesterEntity = semesters[selectedIndex]
                dialog.dismiss()
                selectedHomeWeek = HOME_MIN_WEEK
                pendingHomeBlankCell = null
                viewModel.switchSemester(semester.semesterId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHomeWeekPicker(): Unit {
        val maxWeek: Int = findHomeMaxWeek(viewModel.uiState.value)
        val weekItems: Array<String> = (HOME_MIN_WEEK..maxWeek).map { week: Int -> "第 $week 周" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("快速切换周次")
            .setSingleChoiceItems(weekItems, selectedHomeWeek - HOME_MIN_WEEK) { dialog, selectedIndex: Int ->
                val targetWeek: Int = selectedIndex + HOME_MIN_WEEK
                dialog.dismiss()
                changeSelectedHomeWeekToDirectly(targetWeek)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun changeSelectedHomeWeekToDirectly(targetWeek: Int): Boolean {
        if (isHomePageAnimating) return false
        val maxWeek: Int = findHomeMaxWeek(viewModel.uiState.value)
        val nextWeek: Int = targetWeek.coerceIn(HOME_MIN_WEEK, maxWeek)
        if (nextWeek == selectedHomeWeek) return false
        pendingHomeBlankCell = null
        selectedHomeWeek = nextWeek
        updateHomeWeekTitle()
        renderHomeWeekGrid(viewModel.uiState.value)
        return true
    }

    private fun changeSelectedHomeWeekDirectly(delta: Int): Boolean {
        val maxWeek: Int = findHomeMaxWeek(viewModel.uiState.value)
        return changeSelectedHomeWeekToDirectly((selectedHomeWeek + delta).coerceIn(HOME_MIN_WEEK, maxWeek))
    }

    private fun changeSelectedHomeWeek(delta: Int): Boolean {
        if (isHomePageAnimating) return false
        val maxWeek: Int = findHomeMaxWeek(viewModel.uiState.value)
        val nextWeek: Int = (selectedHomeWeek + delta).coerceIn(HOME_MIN_WEEK, maxWeek)
        if (nextWeek == selectedHomeWeek) return false
        pendingHomeBlankCell = null
        animateHomeWeekPageChange(delta, nextWeek)
        return true
    }

    private fun animateHomeWeekPageChange(delta: Int, nextWeek: Int): Unit {
        val currentBinding: FragmentScheduleBinding = _binding ?: return
        val gridWidth: Float = currentBinding.homeWeekGrid.width.takeIf { width: Int -> width > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val exitTranslationX: Float = if (delta > 0) -gridWidth else gridWidth
        val enterTranslationX: Float = -exitTranslationX
        isHomePageAnimating = true
        currentBinding.homeWeekGrid.animate()
            .translationX(exitTranslationX)
            .setDuration(HOME_PAGE_SLIDE_OUT_DURATION_MILLIS)
            .withEndAction {
                val postedBinding: FragmentScheduleBinding = _binding ?: return@withEndAction
                selectedHomeWeek = nextWeek
                updateHomeWeekTitle()
                renderHomeWeekGrid(viewModel.uiState.value)
                postedBinding.homeWeekGrid.translationX = enterTranslationX
                postedBinding.homeWeekGrid.alpha = HOME_FULL_VISIBLE_ALPHA
                postedBinding.homeWeekGrid.animate()
                    .translationX(0f)
                    .setDuration(HOME_PAGE_SLIDE_IN_DURATION_MILLIS)
                    .withEndAction { isHomePageAnimating = false }
                    .start()
            }
            .start()
    }

    private fun handleHomeGridTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isHomePageAnimating) return true
                homeSwipeStartX = event.rawX
                homeSwipeStartY = event.rawY
                isHomeHorizontalSwipe = false
                binding.homeWeekGrid.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val distanceX: Float = event.rawX - homeSwipeStartX
                val distanceY: Float = event.rawY - homeSwipeStartY
                if (!isHomeHorizontalSwipe && shouldStartHomeHorizontalSwipe(distanceX, distanceY)) {
                    isHomeHorizontalSwipe = true
                    binding.homeWeekGrid.parent.requestDisallowInterceptTouchEvent(true)
                }
                if (!isHomeHorizontalSwipe) return true
                binding.homeWeekGrid.translationX = distanceX.coerceIn(-binding.homeWeekGrid.width.toFloat(), binding.homeWeekGrid.width.toFloat())
                return true
            }
            MotionEvent.ACTION_UP -> {
                val distanceX: Float = event.rawX - homeSwipeStartX
                val hasHandledSwipe: Boolean = isHomeHorizontalSwipe
                binding.homeWeekGrid.parent.requestDisallowInterceptTouchEvent(false)
                isHomeHorizontalSwipe = false
                if (hasHandledSwipe && kotlin.math.abs(distanceX) > HOME_SWIPE_THRESHOLD_DP.toPx()) {
                    if (!handleHomeSwipe(distanceX)) {
                        binding.homeWeekGrid.animate().translationX(0f).setDuration(HOME_PAGE_SLIDE_CANCEL_DURATION_MILLIS).start()
                    }
                    return true
                }
                binding.homeWeekGrid.animate().translationX(0f).setDuration(HOME_PAGE_SLIDE_CANCEL_DURATION_MILLIS).start()
                if (!hasHandledSwipe) view.performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                binding.homeWeekGrid.parent.requestDisallowInterceptTouchEvent(false)
                binding.homeWeekGrid.animate().translationX(0f).setDuration(HOME_PAGE_SLIDE_CANCEL_DURATION_MILLIS).start()
                isHomeHorizontalSwipe = false
                return true
            }
            else -> Unit
        }
        return false
    }

    private fun shouldStartHomeHorizontalSwipe(distanceX: Float, distanceY: Float): Boolean {
        val absoluteDistanceX: Float = kotlin.math.abs(distanceX)
        val absoluteDistanceY: Float = kotlin.math.abs(distanceY)
        return absoluteDistanceX > HOME_HORIZONTAL_TOUCH_SLOP_DP.toPx() && absoluteDistanceX > absoluteDistanceY * HOME_HORIZONTAL_DIRECTION_RATIO
    }

    private fun handleHomeSwipe(distanceX: Float): Boolean {
        return when {
            distanceX > HOME_SWIPE_THRESHOLD_DP.toPx() -> changeSelectedHomeWeek(HOME_WEEK_STEP_BACKWARD)
            distanceX < -HOME_SWIPE_THRESHOLD_DP.toPx() -> changeSelectedHomeWeek(HOME_WEEK_STEP_FORWARD)
            else -> false
        }
    }

    private fun coerceSelectedHomeWeek(state: ScheduleUiState): Unit {
        selectedHomeWeek = selectedHomeWeek.coerceIn(HOME_MIN_WEEK, findHomeMaxWeek(state))
        updateHomeWeekTitle()
    }

    private fun updateHomeWeekTitle(): Unit {
        binding.homeWeekTitleText.text = "第 $selectedHomeWeek 周"
    }

    private fun findHomeMaxWeek(state: ScheduleUiState): Int {
        return state.semesters.firstOrNull { semester -> semester.isActive }?.totalWeeks
            ?: state.semesters.firstOrNull()?.totalWeeks
            ?: HOME_DEFAULT_TOTAL_WEEKS
    }

    private fun Int.toHomeWeekdayName(): String {
        return HOME_WEEKDAY_NAMES.getOrNull(this - 1) ?: HOME_WEEKDAY_NAMES.last()
    }

    private fun Int.toHomeScheduleDayOfWeek(): Int {
        return if (this == Calendar.SUNDAY) HOME_LAST_DAY else this - 1
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun updateSemesterBadge(state: ScheduleUiState): Unit {
        if (state.isLoading) {
            binding.semesterNameBadgeText.text = ""
            binding.semesterNameBadgeText.visibility = View.INVISIBLE
            return
        }
        val semesterName: String = findCurrentSemester(state)?.semesterName?.takeIf { name: String -> name.isNotBlank() } ?: "未设置学期"
        binding.semesterNameBadgeText.text = semesterName
        binding.semesterNameBadgeText.visibility = View.VISIBLE
    }

    private fun createSemesterBadgeBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = SEMESTER_BADGE_CORNER_RADIUS_DP.toPx().toFloat()
            setColor(SEMESTER_BADGE_BACKGROUND_COLOR)
            setStroke(SEMESTER_BADGE_STROKE_WIDTH_DP.toPx(), SEMESTER_BADGE_STROKE_COLOR)
        }
    }

    private fun findCurrentSemester(state: ScheduleUiState): ScheduleSemesterEntity? {
        return state.semesters.firstOrNull { semester: ScheduleSemesterEntity -> semester.isActive }
            ?: state.semesters.firstOrNull()
    }

    private data class HomeBlankCellSelection(
        val dayOfWeek: Int,
        val sectionIndex: Int
    )

    companion object {
        private const val DIALOG_PADDING: Int = 48
        private const val DIALOG_PADDING_HALF: Int = 24
        private const val TEXT_INPUT_MAX_LINES: Int = 3
        private const val NUMBER_INPUT_MAX_LINES: Int = 1

        private const val HOME_FIRST_DAY: Int = 1
        private const val HOME_LAST_DAY: Int = 7
        private const val HOME_FIRST_SECTION: Int = 1
        private const val HOME_LAST_SECTION: Int = 12
        private const val HOME_TOTAL_COLUMNS: Int = 8
        private const val HOME_HEADER_ROW_COUNT: Int = 1
        private const val HOME_ROW_COUNT_STEP: Int = 1
        private const val HOME_DAY_COLUMNS: Int = 7
        private const val HOME_MARGIN_SIDES: Int = 2
        private const val HOME_SECTION_COLUMN_WIDTH_DP: Int = 44
        private const val HOME_MIN_DAY_CELL_WIDTH_DP: Int = 32
        private const val HOME_CELL_HEIGHT_DP: Int = 72
        private const val HOME_SECTION_CELL_HEIGHT_DP: Int = 72
        private const val HOME_CELL_PADDING_DP: Int = 3
        private const val HOME_CELL_MARGIN_DP: Int = 1
        private const val HOME_CELL_CORNER_RADIUS_DP: Int = 6
        private const val HOME_GRID_STROKE_WIDTH_DP: Int = 1
        private const val HOME_CELL_ALPHA: Int = 54
        private const val HOME_SECTION_MAX_LINES: Int = 3
        private const val HOME_HEADER_MAX_LINES: Int = 2
        private const val HOME_COURSE_MAX_LINES: Int = 5
        private const val HOME_HEADER_TEXT_SIZE_SP: Float = 10f
        private const val HOME_SECTION_INDEX_TEXT_SIZE_SP: Float = 34f
        private const val HOME_SECTION_TIME_TEXT_SIZE_SP: Float = 4f
        private const val HOME_SECTION_TIME_LINE_START_INDEX: Int = 1
        private const val HOME_SECTION_TIME_RANGE_SEPARATOR: String = "-"
        private const val HOME_SECTION_LINE_SPACING_EXTRA_DP: Int = 0
        private const val HOME_SECTION_LINE_SPACING_MULTIPLIER: Float = 0.8f
        private const val HOME_CELL_TEXT_SIZE_SP: Float = 10f
        private const val HOME_SWIPE_THRESHOLD_DP: Int = 42
        private const val HOME_HORIZONTAL_TOUCH_SLOP_DP: Int = 8
        private const val HOME_HORIZONTAL_DIRECTION_RATIO: Float = 1.05f
        private const val HOME_PAGE_SLIDE_OUT_DURATION_MILLIS: Long = 160L
        private const val HOME_PAGE_SLIDE_IN_DURATION_MILLIS: Long = 180L
        private const val HOME_PAGE_SLIDE_CANCEL_DURATION_MILLIS: Long = 120L
        private const val HOME_DAYS_PER_WEEK: Int = 7
        private const val HOME_DAY_MILLIS: Long = 86_400_000L
        private const val HOME_FULL_VISIBLE_ALPHA: Float = 1f
        private const val HOME_WEEK_STEP_FORWARD: Int = 1
        private const val HOME_WEEK_STEP_BACKWARD: Int = -1
        private const val HOME_MIN_WEEK: Int = 1
        private const val HOME_DEFAULT_TOTAL_WEEKS: Int = 20
        private const val HOME_DETAIL_PADDING_DP: Int = 20
        private const val HOME_DETAIL_TEXT_PADDING_DP: Int = 6
        private const val HOME_DETAIL_TITLE_TEXT_SIZE_SP: Float = 20f
        private const val HOME_DETAIL_BODY_TEXT_SIZE_SP: Float = 14f
        private const val SEMESTER_BADGE_CORNER_RADIUS_DP: Int = 10
        private const val SEMESTER_BADGE_STROKE_WIDTH_DP: Int = 1
        private const val SEMESTER_SWITCHER_DEFAULT_INDEX: Int = 0
        private const val HOME_HEADER_ROW_INDEX: Int = 0
        private const val HOME_SECTION_COLUMN_INDEX: Int = 0
        private val HOME_HEADER_COLOR: Int = Color.rgb(124, 77, 255)
        private val HOME_SECTION_COLOR: Int = Color.rgb(96, 125, 139)
        private val HOME_ACCENT_COLOR: Int = Color.rgb(124, 77, 255)
        private val HOME_BLANK_CELL_COLOR: Int = Color.rgb(250, 250, 250)
        private val HOME_BLANK_SELECTED_COLOR: Int = Color.rgb(241, 233, 255)
        private val SEMESTER_BADGE_BACKGROUND_COLOR: Int = Color.rgb(243, 238, 255)
        private val SEMESTER_BADGE_STROKE_COLOR: Int = Color.rgb(210, 196, 255)
        private const val HOME_CELL_COLOR: String = "#F5F1FF"
        // 课程表首页与 AI 摘要、提醒 Worker 统一使用中国时区，避免设备处于其他时区时表头日期错位一天
        private val HOME_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        private val HOME_DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("M/d", Locale.CHINA).apply {
            timeZone = HOME_TIME_ZONE
        }
        private val HOME_FULL_DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = HOME_TIME_ZONE
        }
        private val HOME_WEEKDAY_NAMES: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        private val HOME_DEFAULT_SECTION_TIMES: List<Pair<String, String>> = listOf(
            "08:00" to "08:45",
            "08:55" to "09:40",
            "10:00" to "10:45",
            "10:55" to "11:40",
            "14:00" to "14:45",
            "14:55" to "15:40",
            "16:00" to "16:45",
            "16:55" to "17:40",
            "19:00" to "19:45",
            "19:55" to "20:40",
            "20:50" to "21:35",
            "21:45" to "22:30"
        )

        private const val ARG_TARGET_TYPE: String = "target_type"
        private const val ARG_TARGET_ID: String = "target_id"

        fun newInstance(targetType: String? = null, targetId: String? = null): ScheduleFragment {
            return ScheduleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_TYPE, targetType)
                    putString(ARG_TARGET_ID, targetId)
                }
            }
        }
    }
}
