package com.susking.ephone_s.schedule.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.schedule.databinding.FragmentScheduleImportBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 课表导入页。
 * 将原本分散在首页的“文本导入预览”和“确认导入草稿”收敛成一个正式导入流程。
 */
@AndroidEntryPoint
class ScheduleImportFragment : Fragment() {

    private var _binding: FragmentScheduleImportBinding? = null
    private val binding: FragmentScheduleImportBinding
        get() = _binding ?: throw IllegalStateException("Schedule import binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    private var hasCurrentPreview: Boolean = false
    private var selectedPreviewDayOfWeek: Int = DEFAULT_PREVIEW_DAY_OF_WEEK

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        setupToolbar()
        setupWeekdayFilter()
        setupActions()
        clearPreview()
        observeUiState()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(): Unit {
        binding.importToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupWeekdayFilter(): Unit {
        binding.previewWeekdayToggleGroup.check(binding.previewMondayButton.id)
        binding.previewWeekdayToggleGroup.addOnButtonCheckedListener { _, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedPreviewDayOfWeek = when (checkedId) {
                binding.previewTuesdayButton.id -> TUESDAY_DAY_OF_WEEK
                binding.previewWednesdayButton.id -> WEDNESDAY_DAY_OF_WEEK
                binding.previewThursdayButton.id -> THURSDAY_DAY_OF_WEEK
                binding.previewFridayButton.id -> FRIDAY_DAY_OF_WEEK
                binding.previewSaturdayButton.id -> SATURDAY_DAY_OF_WEEK
                binding.previewSundayButton.id -> SUNDAY_DAY_OF_WEEK
                else -> DEFAULT_PREVIEW_DAY_OF_WEEK
            }
            renderFilteredPreview(binding.importPreviewText.tag as? String ?: "")
        }
    }

    private fun setupActions(): Unit {
        binding.importRawTextInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int): Unit = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int): Unit {
                clearPreview()
            }

            override fun afterTextChanged(text: Editable?): Unit = Unit
        })
        binding.parsePreviewButton.setOnClickListener {
            val rawContent: String = binding.importRawTextInput.text.toString()
            hasCurrentPreview = rawContent.isNotBlank()
            selectedPreviewDayOfWeek = DEFAULT_PREVIEW_DAY_OF_WEEK
            binding.previewWeekdayToggleGroup.check(binding.previewMondayButton.id)
            binding.importPreviewText.text = ""
            binding.importPreviewText.tag = ""
            viewModel.createTextImportDraft(rawContent)
            if (rawContent.isBlank()) {
                Snackbar.make(binding.root, "请先粘贴 HTML 课表源码", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.confirmImportButton.setOnClickListener {
            if (!hasCurrentPreview) {
                Snackbar.make(binding.root, "请先解析并确认本次识别结果", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmOverwriteExistingScheduleIfNeeded()
        }
    }

    private fun observeUiState(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    renderPreview(state)
                }
            }
        }
    }

    private fun renderPreview(state: ScheduleUiState): Unit {
        val latestDraftRawContent: String = state.importDrafts.firstOrNull()?.rawContent.orEmpty()
        val currentRawContent: String = binding.importRawTextInput.text.toString().trim()
        if (!hasCurrentPreview || latestDraftRawContent != currentRawContent) {
            binding.importPreviewText.text = ""
            binding.importPreviewText.tag = ""
            binding.previewWeekdayScrollView.visibility = View.GONE
            return
        }
        binding.importPreviewText.tag = state.importPreviewText
        binding.previewWeekdayScrollView.visibility = if (state.importPreviewText.contains(PREVIEW_COURSE_TITLE)) View.VISIBLE else View.GONE
        renderFilteredPreview(state.importPreviewText)
    }

    private fun renderFilteredPreview(previewText: String): Unit {
        if (previewText.isBlank()) {
            binding.importPreviewText.text = ""
            return
        }
        binding.importPreviewText.text = buildSelectedWeekdayPreviewText(previewText, selectedPreviewDayOfWeek)
    }

    private fun buildSelectedWeekdayPreviewText(previewText: String, dayOfWeek: Int): String {
        val previewParts: List<String> = previewText.split(PREVIEW_COURSE_TITLE, limit = PREVIEW_SPLIT_LIMIT)
        if (previewParts.size < PREVIEW_SPLIT_LIMIT) return previewText
        val summaryText: String = previewParts.first().trim()
        val courseAndSectionParts: List<String> = previewParts.last().split(PREVIEW_SECTION_TITLE, limit = PREVIEW_SPLIT_LIMIT)
        val selectedDayText: String = extractSelectedWeekdayBlock(courseAndSectionParts.first(), dayOfWeek)
        val sectionTemplateText: String = courseAndSectionParts.getOrNull(PREVIEW_SECTION_PART_INDEX)
            ?.trim()
            ?.takeIf { text: String -> text.isNotBlank() }
            ?.let { text: String -> "${PREVIEW_SECTION_TITLE.trim()}\n$text" }
            .orEmpty()
        return listOf(summaryText, PREVIEW_COURSE_TITLE.trim(), selectedDayText, sectionTemplateText)
            .filter { text: String -> text.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    private fun extractSelectedWeekdayBlock(coursePreviewText: String, dayOfWeek: Int): String {
        val selectedTitle: String = "【${getWeekdayName(dayOfWeek)}】"
        val nextTitle: String? = (dayOfWeek + NEXT_DAY_OFFSET).takeIf { nextDayOfWeek: Int -> nextDayOfWeek <= SUNDAY_DAY_OF_WEEK }
            ?.let { nextDayOfWeek: Int -> "【${getWeekdayName(nextDayOfWeek)}】" }
        val startIndex: Int = coursePreviewText.indexOf(selectedTitle)
        if (startIndex < PREVIEW_START_INDEX) return "${selectedTitle}\n   无课程"
        val endIndex: Int = nextTitle?.let { title: String -> coursePreviewText.indexOf(title, startIndex + selectedTitle.length) }
            ?.takeIf { index: Int -> index >= PREVIEW_START_INDEX }
            ?: coursePreviewText.length
        return coursePreviewText.substring(startIndex, endIndex).trim()
    }

    private fun getWeekdayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            TUESDAY_DAY_OF_WEEK -> "周二"
            WEDNESDAY_DAY_OF_WEEK -> "周三"
            THURSDAY_DAY_OF_WEEK -> "周四"
            FRIDAY_DAY_OF_WEEK -> "周五"
            SATURDAY_DAY_OF_WEEK -> "周六"
            SUNDAY_DAY_OF_WEEK -> "周日"
            else -> "周一"
        }
    }

    private fun confirmOverwriteExistingScheduleIfNeeded(): Unit {
        if (!hasExistingScheduleData()) {
            commitImportAndReturnHome()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("覆盖当前课表")
            .setMessage("当前课表已有课程。继续导入会覆盖当前课表里的课程和课程规则，并使用本次识别到的节次模板更新时间。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认覆盖") { _, _ -> commitImportAndReturnHome() }
            .show()
    }

    private fun hasExistingScheduleData(): Boolean {
        return viewModel.uiState.value.allCourses.isNotEmpty()
    }

    private fun commitImportAndReturnHome(): Unit {
        viewModel.prepareLatestImportConflicts { conflictGroups: List<ScheduleImportConflictGroup> ->
            if (!isAdded) return@prepareLatestImportConflicts
            if (conflictGroups.isEmpty()) {
                commitImportWithChoices(emptyMap())
                return@prepareLatestImportConflicts
            }
            showImportConflictDialog(conflictGroups, mutableMapOf(), FIRST_CONFLICT_INDEX)
        }
    }

    private fun showImportConflictDialog(
        conflictGroups: List<ScheduleImportConflictGroup>,
        selectedChoices: MutableMap<Int, Int>,
        groupIndex: Int
    ): Unit {
        val conflictGroup: ScheduleImportConflictGroup = conflictGroups.getOrNull(groupIndex) ?: run {
            commitImportWithChoices(selectedChoices)
            return
        }
        if (conflictGroup.options.isEmpty()) {
            showImportConflictDialog(conflictGroups, selectedChoices, groupIndex + NEXT_CONFLICT_INDEX_OFFSET)
            return
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择保留课程 ${groupIndex + CONFLICT_DISPLAY_INDEX_OFFSET}/${conflictGroups.size}")
            .setNegativeButton("取消", null)
            .create()
        dialog.setView(
            createConflictOptionContainer(conflictGroup) { selectedCourseIndex: Int ->
                selectedChoices[conflictGroup.groupIndex] = selectedCourseIndex
                dialog.dismiss()
                showImportConflictDialog(conflictGroups, selectedChoices, groupIndex + NEXT_CONFLICT_INDEX_OFFSET)
            }
        )
        dialog.show()
    }

    private fun createConflictOptionContainer(
        conflictGroup: ScheduleImportConflictGroup,
        onOptionSelected: (Int) -> Unit
    ): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(CONFLICT_DIALOG_PADDING_DP.toPx(), CONFLICT_DIALOG_PADDING_HALF_DP.toPx(), CONFLICT_DIALOG_PADDING_DP.toPx(), 0)
            addView(TextView(requireContext()).apply {
                text = conflictGroup.description
                textSize = CONFLICT_DESCRIPTION_TEXT_SIZE_SP
            })
            conflictGroup.options.forEach { option: ScheduleImportConflictOption ->
                addView(MaterialButton(requireContext()).apply {
                    text = "${option.title}\n${option.detail}"
                    isAllCaps = false
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                    setOnClickListener { onOptionSelected(option.courseIndex) }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = CONFLICT_OPTION_MARGIN_TOP_DP.toPx()
                    }
                })
            }
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun commitImportWithChoices(selectedChoices: Map<Int, Int>): Unit {
        viewModel.commitLatestImportDraft(selectedChoices) {
            if (!isAdded) return@commitLatestImportDraft
            Toast.makeText(requireContext(), "导入成功", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun clearPreview(): Unit {
        hasCurrentPreview = false
        binding.importPreviewText.text = ""
        binding.importPreviewText.tag = ""
        binding.previewWeekdayScrollView.visibility = View.GONE
    }

    companion object {
        private const val DEFAULT_PREVIEW_DAY_OF_WEEK: Int = 1
        private const val TUESDAY_DAY_OF_WEEK: Int = 2
        private const val WEDNESDAY_DAY_OF_WEEK: Int = 3
        private const val THURSDAY_DAY_OF_WEEK: Int = 4
        private const val FRIDAY_DAY_OF_WEEK: Int = 5
        private const val SATURDAY_DAY_OF_WEEK: Int = 6
        private const val SUNDAY_DAY_OF_WEEK: Int = 7
        private const val NEXT_DAY_OFFSET: Int = 1
        private const val NEXT_CONFLICT_INDEX_OFFSET: Int = 1
        private const val CONFLICT_DISPLAY_INDEX_OFFSET: Int = 1
        private const val FIRST_CONFLICT_INDEX: Int = 0
        private const val CONFLICT_DIALOG_PADDING_DP: Int = 20
        private const val CONFLICT_DIALOG_PADDING_HALF_DP: Int = 10
        private const val CONFLICT_OPTION_MARGIN_TOP_DP: Int = 10
        private const val CONFLICT_DESCRIPTION_TEXT_SIZE_SP: Float = 14f
        private const val PREVIEW_START_INDEX: Int = 0
        private const val PREVIEW_SPLIT_LIMIT: Int = 2
        private const val PREVIEW_SECTION_PART_INDEX: Int = 1
        private const val PREVIEW_COURSE_TITLE: String = "课程预览\n"
        private const val PREVIEW_SECTION_TITLE: String = "节次模板\n"

        fun newInstance(): ScheduleImportFragment {
            return ScheduleImportFragment()
        }
    }
}
