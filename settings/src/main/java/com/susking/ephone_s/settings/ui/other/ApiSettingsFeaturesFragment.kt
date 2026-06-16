package com.susking.ephone_s.settings.ui.other
import android.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.core.worker.BackgroundWorkerManager
import com.susking.ephone_s.settings.databinding.FragmentApiSettingsFeaturesBinding
import com.susking.ephone_s.settings.ui.novelai.NovelAiSettingsDialogFragment
import com.susking.ephone_s.settings.ui.novelai.NovelAiTestGenDialogFragment
import com.susking.ephone_s.settings.ui.main.ApiSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ApiSettingsFeaturesFragment : Fragment() {

    private var _binding: FragmentApiSettingsFeaturesBinding? = null
    private val binding get() = _binding!!

    // 使用 Hilt 自动注入 ViewModel
    private val viewModel: ApiSettingsViewModel by activityViewModels()
    
    // 注入后台工作器管理器
    @Inject
    lateinit var backgroundWorkerManager: BackgroundWorkerManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiSettingsFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.settingsState.observe(viewLifecycleOwner) { state ->
            if (state == null) return@observe

            // 只在UI与状态不一致时更新UI，避免无限循环
            if (binding.enableNovelAiSwitch.isChecked != state.isNovelAiEnabled) {
                binding.enableNovelAiSwitch.isChecked = state.isNovelAiEnabled
            }
            if (binding.novelAiApiKeyInput.text.toString() != state.novelAiApiKey) {
                binding.novelAiApiKeyInput.setText(state.novelAiApiKey)
            }
            if (binding.novelAiModelSpinner.text.toString() != state.novelAiModel) {
                binding.novelAiModelSpinner.setText(state.novelAiModel, false)
            }
            // 更新大脑悬浮窗开关状态
            if (binding.enableBrainFloatingWindowSwitch.isChecked != state.isBrainFloatingWindowEnabled) {
                binding.enableBrainFloatingWindowSwitch.isChecked = state.isBrainFloatingWindowEnabled
            }
            if (binding.backgroundActivitySwitch.isChecked != state.isBackgroundActivityEnabled) {
                binding.backgroundActivitySwitch.isChecked = state.isBackgroundActivityEnabled
            }
            if (binding.chatAutoReplySwitch.isChecked != state.isChatAutoReplyEnabled) {
                binding.chatAutoReplySwitch.isChecked = state.isChatAutoReplyEnabled
            }
            if (binding.backgroundIntervalInput.text.toString() != state.backgroundActivityInterval.toString()) {
                binding.backgroundIntervalInput.setText(state.backgroundActivityInterval.toString())
            }
            if (binding.cooldownPeriodInput.text.toString() != state.aiCooldownPeriod.toString()) {
                binding.cooldownPeriodInput.setText(state.aiCooldownPeriod.toString())
            }
            if (binding.chatRequestTimeoutInput.text.toString() != state.chatRequestTimeoutSeconds.toString()) {
                binding.chatRequestTimeoutInput.setText(state.chatRequestTimeoutSeconds.toString())
            }
            if (binding.chatAutoReplyIntervalInput.text.toString() != state.chatAutoReplyIntervalSeconds.toString()) {
                binding.chatAutoReplyIntervalInput.setText(state.chatAutoReplyIntervalSeconds.toString())
            }
            val followUpDelayMinutes: Int = (state.chatFollowUpDelaySeconds / SECONDS_PER_MINUTE).coerceAtLeast(MINIMUM_FOLLOW_UP_DELAY_MINUTES)
            if (binding.chatFollowUpDelayInput.text.toString() != followUpDelayMinutes.toString()) {
                binding.chatFollowUpDelayInput.setText(followUpDelayMinutes.toString())
            }
            if (binding.chatTypingDelayPerCharInput.text.toString() != state.chatTypingDelayPerCharMillis.toString()) {
                binding.chatTypingDelayPerCharInput.setText(state.chatTypingDelayPerCharMillis.toString())
            }

        }
    }

    private fun setupListeners() {
        binding.enableNovelAiSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNovelAiEnabled(isChecked)
        }

        // 大脑悬浮窗开关监听
        binding.enableBrainFloatingWindowSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateBrainFloatingWindowEnabled(isChecked)
        }

        binding.backgroundActivitySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateBackgroundActivityEnabled(isChecked)
            backgroundWorkerManager.setupAiBackgroundWorker()
        }

        binding.chatAutoReplySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateChatAutoReplyEnabled(isChecked)
        }

        binding.chatRequestTimeoutInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val timeoutSeconds: Int = s.toString().toIntOrNull() ?: 180
                if (viewModel.settingsState.value?.chatRequestTimeoutSeconds != timeoutSeconds) {
                    viewModel.updateChatRequestTimeoutSeconds(timeoutSeconds)
                }
            }
        })

        binding.chatAutoReplyIntervalInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val intervalSeconds: Int = s.toString().toIntOrNull() ?: 5
                if (viewModel.settingsState.value?.chatAutoReplyIntervalSeconds != intervalSeconds) {
                    viewModel.updateChatAutoReplyIntervalSeconds(intervalSeconds)
                }
            }
        })

        binding.chatFollowUpDelayInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val delayMinutes: Int = s.toString().toIntOrNull() ?: DEFAULT_FOLLOW_UP_DELAY_MINUTES
                val delaySeconds: Int = delayMinutes.coerceAtLeast(MINIMUM_FOLLOW_UP_DELAY_MINUTES) * SECONDS_PER_MINUTE
                if (viewModel.settingsState.value?.chatFollowUpDelaySeconds != delaySeconds) {
                    viewModel.updateChatFollowUpDelaySeconds(delaySeconds)
                }
            }
        })

        binding.chatTypingDelayPerCharInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val perCharMillis: Int = s.toString().toIntOrNull() ?: DEFAULT_TYPING_DELAY_PER_CHAR_MILLIS
                if (viewModel.settingsState.value?.chatTypingDelayPerCharMillis != perCharMillis) {
                    viewModel.updateChatTypingDelayPerCharMillis(perCharMillis)
                }
            }
        })

        binding.backgroundIntervalInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val interval = s.toString().toIntOrNull() ?: 0
                if (viewModel.settingsState.value?.backgroundActivityInterval != interval) {
                    viewModel.updateBackgroundActivityInterval(interval)
                    // 修改间隔时间后，立即重置后台任务，从当前时刻重新计时
                    backgroundWorkerManager.setupAiBackgroundWorker()
                }
            }
        })

        binding.cooldownPeriodInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val cooldown = s.toString().toFloatOrNull() ?: 0f
                if (viewModel.settingsState.value?.aiCooldownPeriod != cooldown) {
                    viewModel.updateAiCooldownPeriod(cooldown)
                }
            }
        })

        binding.novelAiApiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 只有当文本确实改变时才更新，防止在observeViewModel中设置文本时触发不必要的回调
                if (viewModel.settingsState.value?.novelAiApiKey != s.toString()) {
                    viewModel.updateNovelAiApiKey(s.toString())
                }
            }
        })

        binding.novelAiModelSpinner.setOnItemClickListener { _, _, position, _ ->
            val adapter = binding.novelAiModelSpinner.adapter
            if (position >= 0 && position < adapter.count) {
                val model = adapter.getItem(position) as String
                viewModel.updateNovelAiModel(model)
            }
        }

        binding.buttonGenerateSettings.setOnClickListener {
            NovelAiSettingsDialogFragment.newInstance()
                .show(childFragmentManager, NovelAiSettingsDialogFragment.TAG)
        }

        binding.buttonTestGeneration.setOnClickListener {
            NovelAiTestGenDialogFragment.newInstance()
                .show(childFragmentManager, NovelAiTestGenDialogFragment.TAG)
        }
        
    }

    private fun setupSpinners() {
        val novelAiModels = arrayOf(
            "NAI Diffusion V4.5 Full（完整版含nsfw）",
            "NAI Diffusion V4.5 Curated (精选版无nsfw)",
            "NAI Diffusion Anime V3（旧版）",
            "NAI Diffusion Furry V3（旧旧版）"
        )
        val novelAiAdapter = ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, novelAiModels)
        binding.novelAiModelSpinner.setAdapter(novelAiAdapter)
    }

    fun getNovelAiSettings(): Triple<Boolean, String, String> {
        return Triple(
            binding.enableNovelAiSwitch.isChecked,
            binding.novelAiApiKeyInput.text.toString(),
            binding.novelAiModelSpinner.text.toString()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 当视图销毁时，保存所有在ViewModel中缓存的更改
        viewModel.saveSettings()
        _binding = null
    }

    companion object {
        private const val SECONDS_PER_MINUTE: Int = 60
        private const val DEFAULT_FOLLOW_UP_DELAY_MINUTES: Int = 20
        private const val MINIMUM_FOLLOW_UP_DELAY_MINUTES: Int = 1
        // 打字速度输入框的兜底默认值：用户清空输入框时回退到此值（每字符毫秒）
        private const val DEFAULT_TYPING_DELAY_PER_CHAR_MILLIS: Int = 60
    }
}