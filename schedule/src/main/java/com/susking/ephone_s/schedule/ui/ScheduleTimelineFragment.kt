package com.susking.ephone_s.schedule.ui

import android.graphics.Typeface
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
import com.google.android.material.card.MaterialCardView
import com.susking.ephone_s.aidata.data.local.entity.CampusEventEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleCourseEntity
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.schedule.databinding.FragmentScheduleTimelineBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 完整日程流页面。
 * 将课程、作业、考试、校园动态统一展示，作为课程表首页摘要之外的详细入口。
 */
@AndroidEntryPoint
class ScheduleTimelineFragment : Fragment() {

    private var _binding: FragmentScheduleTimelineBinding? = null
    private val binding: FragmentScheduleTimelineBinding
        get() = _binding ?: throw IllegalStateException("Schedule timeline binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    private val dateFormatter: SimpleDateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        binding.timelineToolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        observeUiState()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun observeUiState(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState -> renderTimeline(state) }
            }
        }
    }

    private fun renderTimeline(state: ScheduleUiState): Unit {
        binding.timelineContainer.removeAllViews()
        val items: List<TimelineItem> = buildTimelineItems(state)
        if (items.isEmpty()) {
            addTimelineCard(TimelineItem("空状态", "暂无日程", "课程、作业、考试和校园动态会显示在这里。", 0L, "EMPTY", ""))
            return
        }
        items.forEach { item: TimelineItem -> addTimelineCard(item) }
    }

    private fun buildTimelineItems(state: ScheduleUiState): List<TimelineItem> {
        val courseItems: List<TimelineItem> = state.allCourses.map { course: ScheduleCourseEntity ->
            TimelineItem("课程", course.courseName, course.classroom.ifBlank { "固定课程" }, course.updatedAt, "COURSE", course.courseId)
        }
        val assignmentItems: List<TimelineItem> = state.allAssignments.map { assignment: ScheduleAssignmentEntity ->
            TimelineItem("作业", assignment.title, "截止：${formatTime(assignment.dueAt)}｜状态：${assignment.status}", assignment.dueAt, "ASSIGNMENT", assignment.assignmentId)
        }
        val examItems: List<TimelineItem> = state.allExams.map { exam: ScheduleExamEntity ->
            TimelineItem("考试", exam.examName, "时间：${formatTime(exam.examAt)}｜复习：${exam.reviewStatus}", exam.examAt, "EXAM", exam.examId)
        }
        val eventItems: List<TimelineItem> = state.allCampusEvents.map { event: CampusEventEntity ->
            TimelineItem("校园", event.title, "时间：${formatTime(event.startAt)}${event.location.takeIf { value: String -> value.isNotBlank() }?.let { value: String -> "｜$value" }.orEmpty()}", event.startAt, "EVENT", event.eventId)
        }
        return (courseItems + assignmentItems + examItems + eventItems).sortedBy { item: TimelineItem -> item.sortTime }
    }

    private fun addTimelineCard(item: TimelineItem): Unit {
        val cardView: MaterialCardView = MaterialCardView(requireContext()).apply {
            radius = CARD_RADIUS_DP.toPx().toFloat()
            cardElevation = CARD_ELEVATION_DP.toPx().toFloat()
            useCompatPadding = true
        }
        val contentLayout: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(CARD_PADDING_DP.toPx(), CARD_PADDING_DP.toPx(), CARD_PADDING_DP.toPx(), CARD_PADDING_DP.toPx())
        }
        contentLayout.addView(createTextView("${item.type}｜${item.title}", true))
        contentLayout.addView(createTextView(item.description, false))
        cardView.setOnClickListener { openTimelineTarget(item) }
        cardView.addView(contentLayout)
        binding.timelineContainer.addView(cardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun createTextView(text: String, isTitle: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = if (isTitle) TITLE_TEXT_SIZE_SP else BODY_TEXT_SIZE_SP
            typeface = if (isTitle) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setPadding(0, TEXT_VERTICAL_PADDING_DP.toPx(), 0, TEXT_VERTICAL_PADDING_DP.toPx())
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "未设置"
        return dateFormatter.format(Date(timestamp))
    }

    private fun openTimelineTarget(item: TimelineItem): Unit {
        val fragment: Fragment = when (item.targetType) {
            "COURSE" -> ScheduleCourseEditorFragment.newInstance(item.targetId)
            "ASSIGNMENT" -> ScheduleAssignmentEditorFragment.newInstance(item.targetId)
            "EXAM" -> ScheduleExamEditorFragment.newInstance(item.targetId)
            else -> return
        }
        parentFragmentManager.beginTransaction()
            .replace(requireParentContainerId(), fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun requireParentContainerId(): Int {
        val containerId: Int = (view?.parent as? View)?.id ?: View.NO_ID
        if (containerId != View.NO_ID) return containerId
        return requireView().id
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private data class TimelineItem(
        val type: String,
        val title: String,
        val description: String,
        val sortTime: Long,
        val targetType: String,
        val targetId: String
    )

    companion object {
        private const val CARD_RADIUS_DP: Int = 18
        private const val CARD_ELEVATION_DP: Int = 2
        private const val CARD_PADDING_DP: Int = 16
        private const val TEXT_VERTICAL_PADDING_DP: Int = 2
        private const val TITLE_TEXT_SIZE_SP: Float = 16f
        private const val BODY_TEXT_SIZE_SP: Float = 13f

        fun newInstance(): ScheduleTimelineFragment {
            return ScheduleTimelineFragment()
        }
    }
}
