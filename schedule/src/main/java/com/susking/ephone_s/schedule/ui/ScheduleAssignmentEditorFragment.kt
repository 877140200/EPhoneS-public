package com.susking.ephone_s.schedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.susking.ephone_s.aidata.data.local.entity.ScheduleAssignmentEntity
import com.susking.ephone_s.schedule.databinding.FragmentScheduleAssignmentEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 独立作业编辑页。
 * 负责收集作业标题、内容和优先级，并交给 ViewModel 写入数据层。
 */
@AndroidEntryPoint
class ScheduleAssignmentEditorFragment : Fragment() {

    private var _binding: FragmentScheduleAssignmentEditorBinding? = null
    private val binding: FragmentScheduleAssignmentEditorBinding
        get() = _binding ?: throw IllegalStateException("Schedule assignment editor binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    private val assignmentId: String?
        get() = arguments?.getString(ARG_ASSIGNMENT_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleAssignmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ScheduleSystemBarHelper.applySystemBarPadding(binding.root)
        setupToolbar()
        setupActions()
        observeEditorTarget()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(): Unit {
        binding.assignmentEditorToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupActions(): Unit {
        binding.saveAssignmentButton.setOnClickListener {
            viewModel.saveAssignmentFromEditor(
                title = binding.assignmentTitleInput.text.toString(),
                content = binding.assignmentContentInput.text.toString(),
                priorityText = binding.assignmentPriorityInput.text.toString(),
                assignmentId = assignmentId
            )
            parentFragmentManager.popBackStack()
        }
        // 新增作业时无完成状态可切换，按钮仅在编辑已有作业时显示
        val targetAssignmentId: String? = assignmentId
        if (targetAssignmentId == null) {
            binding.toggleAssignmentStatusButton.visibility = View.GONE
        } else {
            binding.toggleAssignmentStatusButton.visibility = View.VISIBLE
            binding.toggleAssignmentStatusButton.setOnClickListener {
                viewModel.toggleAssignmentStatus(targetAssignmentId)
            }
        }
    }

    private fun observeEditorTarget(): Unit {
        val targetAssignmentId: String = assignmentId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    val assignment: ScheduleAssignmentEntity = state.allAssignments.firstOrNull { item: ScheduleAssignmentEntity -> item.assignmentId == targetAssignmentId } ?: return@collect
                    binding.assignmentTitleInput.setTextIfDifferent(assignment.title)
                    binding.assignmentContentInput.setTextIfDifferent(assignment.content)
                    binding.assignmentPriorityInput.setTextIfDifferent(assignment.priority.toString())
                    // 已完成显示「标记未完成」，未完成显示「标记完成」，与 DONE_STATUS 口径一致
                    binding.toggleAssignmentStatusButton.text = if (assignment.status == ASSIGNMENT_DONE_STATUS) "标记未完成" else "标记完成"
                }
            }
        }
    }

    private fun android.widget.EditText.setTextIfDifferent(value: String): Unit {
        if (text.toString() != value) setText(value)
    }

    companion object {
        private const val ARG_ASSIGNMENT_ID: String = "assignment_id"
        // 与 ViewModel 的 DONE_STATUS、AI 摘要的 ASSIGNMENT_DONE_STATUS 口径一致
        private const val ASSIGNMENT_DONE_STATUS: String = "DONE"

        fun newInstance(assignmentId: String? = null): ScheduleAssignmentEditorFragment {
            return ScheduleAssignmentEditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_ASSIGNMENT_ID, assignmentId) }
            }
        }
    }
}
