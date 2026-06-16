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
import com.susking.ephone_s.aidata.data.local.entity.ScheduleExamEntity
import com.susking.ephone_s.schedule.databinding.FragmentScheduleExamEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 独立考试编辑页。
 * 负责新增考试安排，保存后返回课程表首页。
 */
@AndroidEntryPoint
class ScheduleExamEditorFragment : Fragment() {

    private var _binding: FragmentScheduleExamEditorBinding? = null
    private val binding: FragmentScheduleExamEditorBinding
        get() = _binding ?: throw IllegalStateException("Schedule exam editor binding is not available")

    private val viewModel: ScheduleViewModel by viewModels()
    private val examId: String?
        get() = arguments?.getString(ARG_EXAM_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleExamEditorBinding.inflate(inflater, container, false)
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
        binding.examEditorToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupActions(): Unit {
        binding.saveExamButton.setOnClickListener {
            viewModel.saveExamFromEditor(
                examName = binding.examNameInput.text.toString(),
                classroom = binding.examClassroomInput.text.toString(),
                scopeText = binding.examScopeInput.text.toString(),
                importanceText = binding.examImportanceInput.text.toString(),
                examId = examId
            )
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeEditorTarget(): Unit {
        val targetExamId: String = examId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state: ScheduleUiState ->
                    val exam: ScheduleExamEntity = state.allExams.firstOrNull { item: ScheduleExamEntity -> item.examId == targetExamId } ?: return@collect
                    binding.examNameInput.setTextIfDifferent(exam.examName)
                    binding.examClassroomInput.setTextIfDifferent(exam.classroom)
                    binding.examScopeInput.setTextIfDifferent(exam.scopeText)
                    binding.examImportanceInput.setTextIfDifferent(exam.importance.toString())
                }
            }
        }
    }

    private fun android.widget.EditText.setTextIfDifferent(value: String): Unit {
        if (text.toString() != value) setText(value)
    }

    companion object {
        private const val ARG_EXAM_ID: String = "exam_id"

        fun newInstance(examId: String? = null): ScheduleExamEditorFragment {
            return ScheduleExamEditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_EXAM_ID, examId) }
            }
        }
    }
}
