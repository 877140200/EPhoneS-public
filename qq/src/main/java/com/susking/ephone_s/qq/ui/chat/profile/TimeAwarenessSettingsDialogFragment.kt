package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.SleepSchedule
import com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig
import com.susking.ephone_s.qq.databinding.DialogTimeAwarenessSettingsBinding

/**
 * 时间感知设置对话框
 * 允许用户查看和编辑角色的时间感知配置
 */
class TimeAwarenessSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogTimeAwarenessSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentProfile: PersonProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentProfile = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_PROFILE, PersonProfile::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_PROFILE)
            } ?: throw IllegalArgumentException("TimeAwarenessSettingsDialogFragment requires a PersonProfile argument")
        } ?: throw IllegalArgumentException("TimeAwarenessSettingsDialogFragment requires a PersonProfile argument")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTimeAwarenessSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 设置对话框宽度为MATCH_PARENT，高度为WRAP_CONTENT
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化UI - 填充当前配置
        initializeUI()

        // 设置监听器
        setupListeners()
    }

    /**
     * 初始化UI - 填充当前配置
     */
    private fun initializeUI() {
        val schedule = currentProfile.sleepSchedule
        val config = currentProfile.timeSensitivityConfig

        // 填充作息时间
        if (schedule != null) {
            binding.bedtimeEditText.setText(schedule.bedtime.toString())
            binding.wakeTimeEditText.setText(schedule.wakeTime.toString())
            binding.nightOwlSwitch.isChecked = schedule.isNightOwl
        } else {
            // 默认值
            binding.bedtimeEditText.setText("23")
            binding.wakeTimeEditText.setText("7")
            binding.nightOwlSwitch.isChecked = false
        }

        // 填充时间敏感度
        binding.needsSleepSwitch.isChecked = config.needsSleep
        binding.longTimeThresholdEditText.setText(config.longTimeNoContactThreshold.toString())
        binding.responseUrgencySlider.value = config.responseUrgencyLevel.toFloat()
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 保存按钮
        binding.saveButton.setOnClickListener {
            saveSettings()
        }

        // 取消按钮
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    /**
     * 保存设置
     */
    private fun saveSettings() {
        try {
            // 获取输入值
            val bedtime = binding.bedtimeEditText.text.toString().toIntOrNull()
            val wakeTime = binding.wakeTimeEditText.text.toString().toIntOrNull()
            val isNightOwl = binding.nightOwlSwitch.isChecked
            val needsSleep = binding.needsSleepSwitch.isChecked
            val longTimeThreshold = binding.longTimeThresholdEditText.text.toString().toLongOrNull()
            val responseUrgency = binding.responseUrgencySlider.value.toInt()

            // 验证输入
            if (bedtime == null || bedtime !in 0..23) {
                Toast.makeText(requireContext(), "就寝时间必须在0-23之间", Toast.LENGTH_SHORT).show()
                return
            }
            if (wakeTime == null || wakeTime !in 0..23) {
                Toast.makeText(requireContext(), "起床时间必须在0-23之间", Toast.LENGTH_SHORT).show()
                return
            }
            if (longTimeThreshold == null || longTimeThreshold < 1) {
                Toast.makeText(requireContext(), "联系阈值必须大于0", Toast.LENGTH_SHORT).show()
                return
            }

            // 创建新的配置对象
            val newSleepSchedule = SleepSchedule(
                bedtime = bedtime,
                wakeTime = wakeTime,
                isNightOwl = isNightOwl
            )

            val newTimeSensitivityConfig = TimeSensitivityConfig(
                needsSleep = needsSleep,
                longTimeNoContactThreshold = longTimeThreshold.toInt(),
                responseUrgencyLevel = responseUrgency
            )

            // 通过FragmentResult返回结果
            val resultBundle = Bundle().apply {
                putParcelable(RESULT_SLEEP_SCHEDULE, newSleepSchedule)
                putParcelable(RESULT_TIME_SENSITIVITY, newTimeSensitivityConfig)
            }
            setFragmentResult(REQUEST_KEY, resultBundle)

            Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show()
            dismiss()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TimeAwarenessSettingsDialogFragment"
        const val REQUEST_KEY = "time_awareness_settings_request_key"
        const val RESULT_SLEEP_SCHEDULE = "result_sleep_schedule"
        const val RESULT_TIME_SENSITIVITY = "result_time_sensitivity"
        private const val ARG_PROFILE = "arg_profile"

        /**
         * 创建对话框实例
         */
        fun newInstance(profile: PersonProfile): TimeAwarenessSettingsDialogFragment {
            return TimeAwarenessSettingsDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PROFILE, profile)
                }
            }
        }
    }
}