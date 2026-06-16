package com.susking.ephone_s.schedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.core.widget.DesktopWidgetConfig
import com.susking.ephone_s.core.widget.DesktopWidgetType
import com.susking.ephone_s.core.widget.WidgetLayoutStore
import com.susking.ephone_s.aidata.data.local.entity.ScheduleSectionTemplateEntity
import com.susking.ephone_s.schedule.databinding.FragmentScheduleSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 课表设置页。
 * 承载学期、节次、提醒、AI 关心和桌面小组件等高级配置入口。
 */
@AndroidEntryPoint
class ScheduleSettingsFragment : Fragment() {

    private var _binding: FragmentScheduleSettingsBinding? = null
    private val binding: FragmentScheduleSettingsBinding
        get() = _binding ?: throw IllegalStateException("Schedule settings binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    // 课表卡片布局配置统一委托给 core 的 WidgetLayoutStore，与桌面拖拽共用同一数据源
    private val widgetLayoutStore: WidgetLayoutStore by lazy {
        WidgetLayoutStore(requireContext().applicationContext, Gson())
    }
    private var scheduleWidgetConfig: DesktopWidgetConfig = DesktopWidgetConfig.fromType(DesktopWidgetType.SCHEDULE)
    private var sectionTimeRows: List<ScheduleSettingsSectionTimeRow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        setupToolbar()
        setupActions()
        viewModel.ensureAcademicTemplatesInitialized()
        observeUiState()
        observeWidgetConfig()
    }

    override fun onPause() {
        saveAcademicSettingsFromInputs()
        super.onPause()
    }

    override fun onDestroyView() {
        saveAcademicSettingsFromInputs()
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(): Unit {
        binding.settingsToolbar.setNavigationOnClickListener {
            saveAcademicSettingsFromInputs()
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupActions(): Unit {
        binding.initializeTemplatesButton.setOnClickListener {
            confirmRestoreDefaultSectionTimes()
        }
        binding.addSectionTimeButton.setOnClickListener {
            addSectionTimeRow()
        }
        binding.deleteSectionTimeButton.setOnClickListener {
            confirmDeleteLastSectionTimeRow()
        }
        binding.semesterStartDateInput.setOnClickListener {
            showSemesterStartDatePicker()
        }
        binding.importScheduleButton.setOnClickListener {
            openImportPage()
        }
        binding.createScheduleButton.setOnClickListener {
            showCreateScheduleDialog()
        }
        binding.deleteCurrentScheduleButton.setOnClickListener {
            confirmDeleteCurrentSchedule()
        }
        binding.widgetSize2x1Button.setOnClickListener {
            saveScheduleWidgetConfig(scheduleWidgetConfig.copy(widthMode = DesktopWidgetType.WIDTH_COMPACT, heightMode = DesktopWidgetType.HEIGHT_NORMAL))
        }
        binding.widgetSize4x1Button.setOnClickListener {
            saveScheduleWidgetConfig(scheduleWidgetConfig.copy(widthMode = DesktopWidgetType.WIDTH_FULL, heightMode = DesktopWidgetType.HEIGHT_NORMAL))
        }
        binding.widgetSize2x2Button.setOnClickListener {
            saveScheduleWidgetConfig(scheduleWidgetConfig.copy(widthMode = DesktopWidgetType.WIDTH_COMPACT, heightMode = DesktopWidgetType.HEIGHT_LARGE))
        }
        binding.widgetSize4x2Button.setOnClickListener {
            saveScheduleWidgetConfig(scheduleWidgetConfig.copy(widthMode = DesktopWidgetType.WIDTH_FULL, heightMode = DesktopWidgetType.HEIGHT_LARGE))
        }
    }

    private fun saveAcademicSettingsFromInputs(): Unit {
        val currentState: ScheduleUiState = viewModel.uiState.value
        if (!currentState.canRenderAcademicContent()) return
        val currentBinding: FragmentScheduleSettingsBinding = _binding ?: return
        currentBinding.sectionTimesInput.setText(buildSectionTimesTextFromRows())
        viewModel.saveAcademicSettings(
            semesterName = currentBinding.semesterNameInput.text.toString(),
            semesterStartDateText = currentBinding.semesterStartDateInput.text.toString(),
            totalWeeksText = currentBinding.totalWeeksInput.text.toString(),
            note = currentBinding.semesterNoteInput.text.toString(),
            sectionTimesText = currentBinding.sectionTimesInput.text.toString()
        )
    }

    private fun showCreateScheduleDialog(): Unit {
        val nameInput: android.widget.EditText = android.widget.EditText(requireContext()).apply {
            hint = "课表名称，例如 2026 春季课表"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建课表")
            .setView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(DIALOG_PADDING_DP.toPx(), DIALOG_PADDING_HALF_DP.toPx(), DIALOG_PADDING_DP.toPx(), 0)
                addView(nameInput)
            })
            .setNegativeButton("取消", null)
            .setPositiveButton("新建") { _, _ ->
                saveAcademicSettingsFromInputs()
                viewModel.createSemester(nameInput.text.toString())
            }
            .show()
    }

    private fun confirmDeleteCurrentSchedule(): Unit {
        val currentSemesterName: String = findCurrentSemester(viewModel.uiState.value)?.semesterName ?: "当前课表"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除课表")
            .setMessage("会删除「$currentSemesterName」以及该课表下的课程、课程规则、关联作业和考试。删除最后一个课表时会自动新建默认课表。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> viewModel.deleteActiveSemester() }
            .show()
    }

    private fun openImportPage(): Unit {
        saveAcademicSettingsFromInputs()
        parentFragmentManager.beginTransaction()
            .replace(requireParentContainerId(), ScheduleImportFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    private fun requireParentContainerId(): Int {
        val containerId: Int = (view?.parent as? View)?.id ?: View.NO_ID
        if (containerId != View.NO_ID) return containerId
        return requireView().id
    }

    private fun observeUiState(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    renderSettings(state)
                }
            }
        }
    }

    private fun observeWidgetConfig(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                widgetLayoutStore.getWidgetConfig(DesktopWidgetType.SCHEDULE).collect { config: DesktopWidgetConfig ->
                    scheduleWidgetConfig = config
                    renderWidgetConfig(config)
                }
            }
        }
    }

    private fun saveScheduleWidgetConfig(config: DesktopWidgetConfig): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            widgetLayoutStore.saveWidgetConfig(DesktopWidgetType.SCHEDULE, config)
        }
    }

    private fun renderWidgetConfig(config: DesktopWidgetConfig): Unit {
        val sizeText: String = buildWidgetSizeText(config)
        binding.widgetSizeText.text = "当前尺寸：$sizeText"
        binding.widgetSize2x1Button.isEnabled = sizeText != WIDGET_SIZE_2X1
        binding.widgetSize4x1Button.isEnabled = sizeText != WIDGET_SIZE_4X1
        binding.widgetSize2x2Button.isEnabled = sizeText != WIDGET_SIZE_2X2
        binding.widgetSize4x2Button.isEnabled = sizeText != WIDGET_SIZE_4X2
    }

    private fun buildWidgetSizeText(config: DesktopWidgetConfig): String {
        val columnSpan: Int = DesktopWidgetType.SCHEDULE.resolveColumnSpan(config.widthMode)
        val rowSpan: Int = DesktopWidgetType.SCHEDULE.resolveRowSpan(config.heightMode)
        return "${columnSpan}x${rowSpan}"
    }

    private fun renderSettings(state: ScheduleUiState): Unit {
        if (!state.canRenderAcademicContent()) {
            binding.settingsScrollView.visibility = View.INVISIBLE
            binding.sectionTimesListContainer.visibility = View.INVISIBLE
            return
        }
        binding.settingsScrollView.visibility = View.VISIBLE
        binding.sectionTimesListContainer.visibility = View.VISIBLE
        findCurrentSemester(state)?.let { semester ->
            val startDateText: String = formatDateText(semester.startDateMillis)
            binding.semesterNameInput.setTextIfDifferent(semester.semesterName)
            binding.semesterStartDateInput.setTextIfDifferent(startDateText)
            binding.totalWeeksInput.setTextIfDifferent(semester.totalWeeks.toString())
            binding.semesterNoteInput.setTextIfDifferent(semester.note)
        }
        if (state.sectionTemplates.isNotEmpty()) {
            updateSectionTimeRows(state.sectionTemplates)
            binding.sectionTimesInput.setTextIfDifferent(buildSectionTimesTextFromRows())
        }
    }

    private fun updateSectionTimeRows(templates: List<ScheduleSectionTemplateEntity>): Unit {
        val rows: List<ScheduleSettingsSectionTimeRow> = templates
            .sortedBy { template: ScheduleSectionTemplateEntity -> template.sectionIndex }
            .map { template: ScheduleSectionTemplateEntity ->
                ScheduleSettingsSectionTimeRow(
                    sectionIndex = template.sectionIndex,
                    startTimeText = template.startTimeText,
                    endTimeText = template.endTimeText
                )
            }
        updateSectionTimeRowsFromRows(rows)
    }

    private fun updateSectionTimeRowsFromRows(rows: List<ScheduleSettingsSectionTimeRow>): Unit {
        sectionTimeRows = rows.ifEmpty { parseDefaultSectionTimeRows() }
        renderSectionTimeRows()
    }

    private fun addSectionTimeRow(): Unit {
        val nextSectionIndex: Int = (sectionTimeRows.maxOfOrNull { row: ScheduleSettingsSectionTimeRow -> row.sectionIndex } ?: 0) + SECTION_INDEX_STEP
        val fallbackRow: ScheduleSettingsSectionTimeRow = parseDefaultSectionTimeRows().getOrNull(nextSectionIndex - SECTION_INDEX_STEP)
            ?: ScheduleSettingsSectionTimeRow(nextSectionIndex, DEFAULT_TIME_TEXT, DEFAULT_TIME_TEXT)
        sectionTimeRows = sectionTimeRows + fallbackRow.copy(sectionIndex = nextSectionIndex)
        binding.sectionTimesInput.setText(buildSectionTimesTextFromRows())
        renderSectionTimeRows()
    }

    private fun confirmRestoreDefaultSectionTimes(): Unit {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("恢复默认节次")
            .setMessage("恢复默认会保留 12 节默认时间，并删除第 13 节及之后节次的课程、作业、考试和调课数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ -> viewModel.seedDefaultAcademicTemplates() }
            .show()
    }

    private fun confirmDeleteLastSectionTimeRow(): Unit {
        val lastRow: ScheduleSettingsSectionTimeRow = sectionTimeRows.maxByOrNull { row: ScheduleSettingsSectionTimeRow -> row.sectionIndex } ?: return
        if (sectionTimeRows.size <= MIN_SECTION_COUNT) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除第 ${lastRow.sectionIndex} 节")
            .setMessage("删除该节次会同时删除第 ${lastRow.sectionIndex} 节及之后节次的课程、作业、考试和调课数据。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> deleteLastSectionTimeRow(lastRow.sectionIndex) }
            .show()
    }

    private fun deleteLastSectionTimeRow(sectionIndex: Int): Unit {
        sectionTimeRows = sectionTimeRows.filter { row: ScheduleSettingsSectionTimeRow -> row.sectionIndex < sectionIndex }
        binding.sectionTimesInput.setText(buildSectionTimesTextFromRows())
        renderSectionTimeRows()
        viewModel.deleteSectionsAtOrAfter(sectionIndex)
    }

    private fun renderSectionTimeRows(): Unit {
        binding.sectionTimesListContainer.removeAllViews()
        sectionTimeRows.forEach { row: ScheduleSettingsSectionTimeRow ->
            binding.sectionTimesListContainer.addView(createSectionTimeRowView(row))
        }
    }

    private fun createSectionTimeRowView(row: ScheduleSettingsSectionTimeRow): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, SECTION_ROW_VERTICAL_PADDING_DP.toPx(), 0, SECTION_ROW_VERTICAL_PADDING_DP.toPx())
            addView(TextView(requireContext()).apply {
                text = "第 ${row.sectionIndex} 节"
                textSize = SECTION_ROW_TITLE_SIZE_SP
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SECTION_TITLE_WEIGHT)
            })
            addView(createTimeButton(row.sectionIndex, row.startTimeText, true))
            addView(TextView(requireContext()).apply {
                text = " 至 "
                textSize = SECTION_ROW_CONNECTOR_SIZE_SP
            })
            addView(createTimeButton(row.sectionIndex, row.endTimeText, false))
        }
    }

    private fun createTimeButton(sectionIndex: Int, timeText: String, isStartTime: Boolean): MaterialButton {
        return MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = timeText.ifBlank { DEFAULT_TIME_TEXT }
            minWidth = 0
            setPadding(0, paddingTop, 0, paddingBottom)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SECTION_TIME_WEIGHT)
            setOnClickListener { showTimePicker(sectionIndex, text.toString(), isStartTime) }
        }
    }

    private fun showTimePicker(sectionIndex: Int, timeText: String, isStartTime: Boolean): Unit {
        val timeParts: List<Int> = parseTimeParts(timeText)
        TimePickerDialog(requireContext(), { _, hourOfDay: Int, minute: Int ->
            updateSectionTime(sectionIndex, formatTimeText(hourOfDay, minute), isStartTime)
        }, timeParts.first(), timeParts.last(), true).show()
    }

    private fun updateSectionTime(sectionIndex: Int, timeText: String, isStartTime: Boolean): Unit {
        sectionTimeRows = sectionTimeRows.map { row: ScheduleSettingsSectionTimeRow ->
            if (row.sectionIndex != sectionIndex) return@map row
            if (isStartTime) row.copy(startTimeText = timeText) else row.copy(endTimeText = timeText)
        }
        binding.sectionTimesInput.setText(buildSectionTimesTextFromRows())
        renderSectionTimeRows()
    }

    private fun buildSectionTimesTextFromRows(): String {
        return sectionTimeRows
            .sortedBy { row: ScheduleSettingsSectionTimeRow -> row.sectionIndex }
            .joinToString(separator = "\n") { row: ScheduleSettingsSectionTimeRow ->
                "第${row.sectionIndex}节 ${row.startTimeText} ${row.endTimeText}"
            }
    }

    private fun parseDefaultSectionTimeRows(): List<ScheduleSettingsSectionTimeRow> {
        return DEFAULT_SECTION_TIMES_TEXT.lines().mapIndexedNotNull { index: Int, line: String ->
            val tokens: List<String> = line.trim().split(Regex("\\s+")).filter { token: String -> token.isNotBlank() }
            ScheduleSettingsSectionTimeRow(
                sectionIndex = index + 1,
                startTimeText = tokens.getOrNull(DEFAULT_START_TIME_TOKEN_INDEX) ?: DEFAULT_TIME_TEXT,
                endTimeText = tokens.getOrNull(DEFAULT_END_TIME_TOKEN_INDEX) ?: DEFAULT_TIME_TEXT
            )
        }
    }

    private fun parseTimeParts(timeText: String): List<Int> {
        val parts: List<Int> = timeText.split(":").mapNotNull { part: String -> part.toIntOrNull() }
        return listOf(parts.getOrNull(0) ?: DEFAULT_HOUR, parts.getOrNull(1) ?: DEFAULT_MINUTE)
    }

    private fun showSemesterStartDatePicker(): Unit {
        val calendar: Calendar = parseDateCalendar(binding.semesterStartDateInput.text.toString())
        DatePickerDialog(requireContext(), { _, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth, START_DATE_HOUR, START_DATE_MINUTE, START_DATE_SECOND)
            calendar.set(Calendar.MILLISECOND, START_DATE_MILLISECOND)
            binding.semesterStartDateInput.setText(formatDateText(calendar.timeInMillis))
            saveAcademicSettingsFromInputs()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun parseDateCalendar(dateText: String): Calendar {
        val calendar: Calendar = Calendar.getInstance(Locale.CHINA)
        val millis: Long = runCatching { DATE_FORMAT.parse(dateText)?.time }.getOrNull() ?: System.currentTimeMillis()
        calendar.timeInMillis = millis
        return calendar
    }

    private fun formatDateText(timeMillis: Long): String {
        return DATE_FORMAT.format(java.util.Date(timeMillis))
    }

    private fun formatTimeText(hour: Int, minute: Int): String {
        return String.format(Locale.CHINA, "%02d:%02d", hour, minute)
    }

    private fun findCurrentSemester(state: ScheduleUiState): com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity? {
        return state.semesters.firstOrNull { semester: com.susking.ephone_s.aidata.data.local.entity.ScheduleSemesterEntity -> semester.isActive }
            ?: state.semesters.firstOrNull()
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun android.widget.EditText.setTextIfDifferent(value: String): Unit {
        if (text.toString() != value) setText(value)
    }

    companion object {
        private const val WIDGET_SIZE_2X1: String = "2x1"
        private const val WIDGET_SIZE_4X1: String = "4x1"
        private const val WIDGET_SIZE_2X2: String = "2x2"
        private const val WIDGET_SIZE_4X2: String = "4x2"
        private const val DEFAULT_SECTION_TIMES_TEXT: String = "第1节 08:00 08:45\n第2节 08:55 09:40\n第3节 10:00 10:45\n第4节 10:55 11:40\n第5节 14:00 14:45\n第6节 14:55 15:40\n第7节 16:00 16:45\n第8节 16:55 17:40\n第9节 19:00 19:45\n第10节 19:55 20:40\n第11节 20:50 21:35\n第12节 21:45 22:30"
        private const val SECTION_ROW_VERTICAL_PADDING_DP: Int = 4
        private const val SECTION_ROW_TITLE_SIZE_SP: Float = 15f
        private const val SECTION_ROW_CONNECTOR_SIZE_SP: Float = 13f
        private const val SECTION_TITLE_WEIGHT: Float = 1.05f
        private const val SECTION_TIME_WEIGHT: Float = 1f
        private const val DEFAULT_START_TIME_TOKEN_INDEX: Int = 1
        private const val DEFAULT_END_TIME_TOKEN_INDEX: Int = 2
        private const val SECTION_INDEX_STEP: Int = 1
        private const val MIN_SECTION_COUNT: Int = 1
        private const val DEFAULT_HOUR: Int = 8
        private const val DEFAULT_MINUTE: Int = 0
        private const val DEFAULT_TIME_TEXT: String = "08:00"
        private const val START_DATE_HOUR: Int = 0
        private const val START_DATE_MINUTE: Int = 0
        private const val START_DATE_SECOND: Int = 0
        private const val START_DATE_MILLISECOND: Int = 0
        private const val DIALOG_PADDING_DP: Int = 24
        private const val DIALOG_PADDING_HALF_DP: Int = 12
        private val DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

        fun newInstance(): ScheduleSettingsFragment {
            return ScheduleSettingsFragment()
        }
    }
}

private data class ScheduleSettingsSectionTimeRow(
    val sectionIndex: Int,
    val startTimeText: String,
    val endTimeText: String
)
