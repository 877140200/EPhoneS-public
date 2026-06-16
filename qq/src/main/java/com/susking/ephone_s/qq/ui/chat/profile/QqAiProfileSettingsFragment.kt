package com.susking.ephone_s.qq.ui.chat.profile

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.SleepSchedule
import com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import com.susking.ephone_s.aidata.prompt.AiPromptService
import com.susking.ephone_s.qq.databinding.FragmentQqAiProfileSettingsBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.contactList.ManageGroupsDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class QqAiProfileSettingsFragment : Fragment() {

    private var _binding: FragmentQqAiProfileSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 Manager
    @Inject lateinit var contactManager: QqContactManager
    @Inject lateinit var aiPromptService: AiPromptService
    @Inject lateinit var settingsRepository: com.susking.ephone_s.aidata.domain.repository.SettingsRepository
    @Inject lateinit var aiRequestService: AiRequestService
    
    // 创建OkHttpClient实例
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var contactId: String? = null
    private var isDataBound = false // 新增标志位，确保数据只绑定一次
    private var ttsPreviewPlayer: MediaPlayer? = null
    private var isTtsPreviewLoading: Boolean = false
    private var latestTtsPreviewAudioPath: String? = null
    private var latestTtsPreviewVoiceId: String? = null
    // 人设输入框已改为按钮，人设内容暂存于此变量，退出页面时随其它字段一并保存
    private var currentPersona: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
        if (contactId == null) {
            Toast.makeText(context, "错误：未找到联系人ID", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqAiProfileSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeContact()
        setupClickListeners()
        setupChangeListeners()
        setupFriendGroupSpinner()
        setupTtsVoiceSpinner()
        setupFragmentResultListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeContact() {
        viewModel.contactManager.contacts.observe(viewLifecycleOwner) { contacts ->
            contacts.find { it.id == contactId }?.let { contact ->
                if (!isDataBound) {
                    Log.d("QqProfileSettings", "绑定联系人设置数据: ${contact.id}, 自动总结: ${contact.autoSummaryEnabled}")
                    bindContactData(contact)
                    isDataBound = true
                }
            }
        }
    }

    private fun bindContactData(contact: PersonProfile) {
        binding.longTermMemorySwitch.isChecked = contact.autoSummaryEnabled
        binding.summaryIntervalContainer.visibility = if (contact.autoSummaryEnabled) View.VISIBLE else View.GONE
        binding.summaryIntervalEditText.setText(contact.summaryInterval.toString())
        binding.shortTermMemoryEditText.setText(contact.shortTermMemoryLimit.toString())
        binding.attachMemoryEditText.setText(contact.attachMemoryLimit.toString())
        currentPersona = contact.persona
        updatePersonaButtonText()
        binding.remarkNameEditText.setText(contact.remarkName)
        binding.nicknameForUserEditText.setText(contact.nicknameForUser)
        binding.realNameEditText.setText(contact.realName)
        binding.friendGroupSpinner.setText(contact.group ?: "我的好友", false)
        binding.privacyModeSwitch.isChecked = contact.privacyModeEnabled
        val voiceId: String = contact.ttsVoiceId ?: settingsRepository.getTtsVoiceId()
        binding.ttsVoiceSpinner.setText(voiceId, false)
        binding.voiceDescriptionEditText.setText(contact.voiceDescription)
        bindTtsPreviewAudioForVoiceId(voiceId)
    }

    private fun setupClickListeners() {
        // 人设按钮：点击弹出对话框编辑人设内容
        binding.personaEditButton.setOnClickListener {
            showPersonaEditDialog()
        }
        binding.manageGroupsButton.setOnClickListener {
            val dialog = ManageGroupsDialogFragment()
            dialog.show(parentFragmentManager, "ManageGroupsDialog")
        }
        binding.autoSummarizeInfoIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("自动提取结构化事件说明")
                .setMessage("开启后，AI 会在对话达到一定长度时，自动将内容提取为多条可编辑、可搜索、可召回的结构化事件。")
                .setPositiveButton("确定", null)
                .show()
        }

        binding.voiceDescriptionInfoIcon.setOnClickListener {
            showVoiceDesignGuideDialog()
        }
        
        // 时间感知设置按钮
        binding.timeAwarenessSettingsButton.setOnClickListener {
            val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId }
            if (contact != null) {
                val dialog = TimeAwarenessSettingsDialogFragment.newInstance(contact)
                dialog.show(parentFragmentManager, TimeAwarenessSettingsDialogFragment.TAG)
            } else {
                Toast.makeText(context, "无法加载联系人数据", Toast.LENGTH_SHORT).show()
            }
        }
        
        // AI分析人设按钮
        binding.analyzePersonaButton.setOnClickListener {
            analyzePersonaWithAi()
        }
        
        // 世界书绑定按钮
        binding.worldBooksBindingButton.setOnClickListener {
            openWorldBookBindingDialog()
        }
        binding.ttsVoiceSpinner.setOnItemClickListener { _, _, _, _ ->
            bindTtsPreviewAudioForVoiceId(binding.ttsVoiceSpinner.text.toString())
        }
        binding.previewTtsVoiceButton.setOnClickListener {
            previewContactTtsVoice()
        }
        binding.playTtsPreviewAudioButton.setOnClickListener {
            val audioPath: String = latestTtsPreviewAudioPath.orEmpty()
            if (audioPath.isBlank() || !File(audioPath).exists()) {
                Toast.makeText(requireContext(), "请先重新生成试听音频", Toast.LENGTH_SHORT).show()
                binding.playTtsPreviewAudioButton.isEnabled = false
                return@setOnClickListener
            }
            playTtsPreview(audioPath, latestTtsPreviewVoiceId.orEmpty())
        }
    }

    private fun previewContactTtsVoice() {
        if (isTtsPreviewLoading) {
            return
        }
        val model: String = settingsRepository.getTtsModel()
        val apiKey: String = settingsRepository.getTtsApiKey()
        val voiceId: String = binding.ttsVoiceSpinner.text.toString().ifBlank { settingsRepository.getTtsVoiceId() }
        val voiceDescription: String = binding.voiceDescriptionEditText.text.toString()

        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置中填写小米 MiMo TTS API Key", Toast.LENGTH_SHORT).show()
            return
        }
        if (model.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置中拉取并选择小米 MiMo TTS 模型", Toast.LENGTH_SHORT).show()
            return
        }
        // 校验：若使用 voicedesign 模型但未填写音色描述，弹窗提醒
        if (model.contains("voicedesign", ignoreCase = true) && voiceDescription.isBlank()) {
            Toast.makeText(requireContext(), "请先填写角色音色描述", Toast.LENGTH_LONG).show()
            return
        }
        val previewText: String = binding.ttsPreviewTextInput.text?.toString()?.trim().orEmpty()
        executeContactTtsPreview(model, voiceId, voiceDescription, previewText)
    }

    private fun executeContactTtsPreview(model: String, voiceId: String, voiceDescription: String, previewText: String) {
        isTtsPreviewLoading = true
        binding.previewTtsVoiceButton.isEnabled = false
        binding.playTtsPreviewAudioButton.isEnabled = false
        binding.previewTtsVoiceButton.text = "正在生成..."
        lifecycleScope.launch {
            val result: TtsSynthesisResult = withContext(Dispatchers.IO) {
                aiRequestService.synthesizeSpeechWithLogging(
                    TtsSynthesisRequest(
                        text = previewText.ifBlank { TTS_PREVIEW_TEXT },
                        model = model,
                        voiceId = voiceId,
                        isStreaming = settingsRepository.isTtsStreamingEnabled(),
                        description = voiceDescription.ifBlank { "联系人音色试听" }
                    )
                )
            }
            isTtsPreviewLoading = false
            binding.previewTtsVoiceButton.isEnabled = true
            binding.previewTtsVoiceButton.text = "重新生成音色"
            if (!result.errorMessage.isNullOrBlank()) {
                Toast.makeText(requireContext(), "试听失败：${result.errorMessage}", Toast.LENGTH_LONG).show()
                return@launch
            }
            val audioFile: File = result.audioFile ?: run {
                Toast.makeText(requireContext(), "试听失败：没有可播放的语音文件", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (!audioFile.exists()) {
                Toast.makeText(requireContext(), "试听失败：语音文件不存在", Toast.LENGTH_LONG).show()
                return@launch
            }
            latestTtsPreviewAudioPath = audioFile.absolutePath
            latestTtsPreviewVoiceId = result.voiceId
            settingsRepository.saveTtsPreviewAudioPath(result.voiceId, audioFile.absolutePath)
            binding.playTtsPreviewAudioButton.isEnabled = true
            Toast.makeText(requireContext(), "试听音频已生成，可点击播放", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindTtsPreviewAudioForVoiceId(voiceId: String): Unit {
        val safeVoiceId: String = voiceId.ifBlank { settingsRepository.getTtsVoiceId() }
        val audioPath: String = settingsRepository.getTtsPreviewAudioPath(safeVoiceId)
        val hasPlayableAudio: Boolean = audioPath.isNotBlank() && File(audioPath).exists()
        latestTtsPreviewAudioPath = audioPath.takeIf { hasPlayableAudio }
        latestTtsPreviewVoiceId = safeVoiceId
        binding.playTtsPreviewAudioButton.isEnabled = hasPlayableAudio && !isTtsPreviewLoading
    }

    private fun playTtsPreview(audioPath: String, voiceId: String) {
        releaseTtsPreviewPlayer()
        ttsPreviewPlayer = MediaPlayer().apply {
            setDataSource(audioPath)
            setOnPreparedListener { player: MediaPlayer ->
                player.start()
                Toast.makeText(requireContext(), "正在试听 $voiceId", Toast.LENGTH_SHORT).show()
            }
            setOnCompletionListener { releaseTtsPreviewPlayer() }
            setOnErrorListener { _, _, _ ->
                releaseTtsPreviewPlayer()
                Toast.makeText(requireContext(), "试听播放失败", Toast.LENGTH_SHORT).show()
                true
            }
            prepareAsync()
        }
    }

    private fun releaseTtsPreviewPlayer() {
        ttsPreviewPlayer?.release()
        ttsPreviewPlayer = null
    }
    
    /**
     * 打开世界书绑定配置对话框
     */
    private fun openWorldBookBindingDialog() {
        contactId?.let { id ->
            val dialog = WorldBookBindingDialogFragment.newInstance(id)
            parentFragmentManager.beginTransaction()
                .replace(this.id, dialog)
                .addToBackStack(null)
                .commit()
        }
    }
    
    /**
     * 使用AI分析人设并自动配置时间感知参数
     */
    private fun analyzePersonaWithAi() {
        val personaText = currentPersona
        
        if (personaText.isBlank()) {
            Toast.makeText(requireContext(), "请先输入AI人设描述", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示确认对话框
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("AI智能分析")
            .setMessage("将使用AI分析人设并自动推断时间配置\n(作息时间、回复习惯等)\n\n这会覆盖当前的时间感知设置,是否继续?")
            .setPositiveButton("确定") { _, _ ->
                performAiAnalysis(personaText)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行AI分析
     */
    private fun performAiAnalysis(personaText: String) {
        // 显示加载提示
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在分析...")
            .setMessage("AI正在分析人设,请稍候...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        lifecycleScope.launch {
            try {
                // 1. 获取API密钥
                val apiKey = settingsRepository.getMainApiKey()
                
                // 2. 构建AI请求
                val promptRequest = aiPromptService.buildPersonaAnalysisPrompt(personaText)
                
                // 3. 发送请求(添加Authorization头)
                val response = withContext(Dispatchers.IO) {
                    val requestBody = Gson().toJson(promptRequest.request)
                        .toRequestBody("application/json".toMediaType())
                    
                    val request = Request.Builder()
                        .url(promptRequest.url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(requestBody)
                        .build()
                    
                    okHttpClient.newCall(request).execute()
                }
                
                loadingDialog.dismiss()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        // 3. 解析响应
                        val result = parseAiAnalysisResponse(responseBody)
                        
                        if (result != null) {
                            // 4. 更新联系人配置
                            applyAnalysisResult(result)
                            
                            Toast.makeText(requireContext(), "分析完成!时间感知配置已自动设置", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireContext(), "AI分析失败:无法解析响应", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "AI分析失败: ${response.code}", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Log.e("QqProfileSettings", "AI分析出错", e)
                Toast.makeText(requireContext(), "AI分析出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 解析AI分析响应
     */
    private fun parseAiAnalysisResponse(responseBody: String): AnalysisResult? {
        return try {
            val gson = Gson()
            Log.d("QqProfileSettings", "原始响应: $responseBody")
            
            val jsonResponse = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
            val content = jsonResponse.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
            
            Log.d("QqProfileSettings", "提取的content: $content")
            
            if (content != null) {
                // 清理可能的markdown代码块标记
                val cleanedContent = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                
                Log.d("QqProfileSettings", "清理后的content: $cleanedContent")
                
                // 解析JSON内容
                val analysisJson = gson.fromJson(cleanedContent, com.google.gson.JsonObject::class.java)
                
                val sleepScheduleJson = analysisJson.getAsJsonObject("sleepSchedule")
                val timeSensitivityJson = analysisJson.getAsJsonObject("timeSensitivityConfig")
                
                val sleepSchedule = SleepSchedule(
                    bedtime = sleepScheduleJson.get("bedtime").asInt,
                    wakeTime = sleepScheduleJson.get("wakeTime").asInt,
                    isNightOwl = sleepScheduleJson.get("isNightOwl").asBoolean
                )
                
                val timeSensitivity = TimeSensitivityConfig(
                    needsSleep = timeSensitivityJson.get("needsSleep").asBoolean,
                    longTimeNoContactThreshold = timeSensitivityJson.get("longTimeNoContactThreshold").asLong.toInt(),
                    responseUrgencyLevel = timeSensitivityJson.get("responseUrgencyLevel").asInt
                )
                
                Log.d("QqProfileSettings", "解析成功: sleepSchedule=$sleepSchedule, timeSensitivity=$timeSensitivity")
                AnalysisResult(sleepSchedule, timeSensitivity)
            } else {
                Log.e("QqProfileSettings", "content为null")
                null
            }
        } catch (e: Exception) {
            Log.e("QqProfileSettings", "解析AI响应失败", e)
            Log.e("QqProfileSettings", "响应内容: $responseBody")
            null
        }
    }
    
    /**
     * 应用分析结果
     */
    private fun applyAnalysisResult(result: AnalysisResult) {
        val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId }
        if (contact != null) {
            val updatedContact = contact.copy(
                sleepSchedule = result.sleepSchedule,
                timeSensitivityConfig = result.timeSensitivity
            )
            contactManager.updateContact(updatedContact)
            Log.d("QqProfileSettings", "AI分析结果已应用: ${result}")
        }
    }
    
    /**
     * 分析结果数据类
     */
    private data class AnalysisResult(
        val sleepSchedule: SleepSchedule,
        val timeSensitivity: TimeSensitivityConfig
    )

    private fun setupChangeListeners() {
        // 当 longTermMemorySwitch 状态改变时，更新 summaryIntervalContainer 的可见性
        binding.longTermMemorySwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.summaryIntervalContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupFriendGroupSpinner() {
        // 确保在观察之前，我们请求了最新的分组数据
        contactManager.loadAllGroups()
        contactManager.allGroupNames.observe(viewLifecycleOwner) { groups ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, groups)
            binding.friendGroupSpinner.setAdapter(adapter)
        }
    }

    private fun setupTtsVoiceSpinner() {
        val voiceIds: List<String> = listOf("冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, voiceIds)
        binding.ttsVoiceSpinner.setAdapter(adapter)

        // 根据全局 TTS 模型更新 UI 可见性
        updateTtsUiForModel()
    }

    /**
     * 根据全局 TTS 模型更新 UI 可见性。
     * 若模型包含 voicedesign，则隐藏音色选择器（包括其父布局 TextInputLayout）。
     */
    private fun updateTtsUiForModel(): Unit {
        val model: String = settingsRepository.getTtsModel()
        val isVoiceDesign: Boolean = model.contains("voicedesign", ignoreCase = true)
        binding.ttsVoiceSpinner.parent.let { parent ->
            if (parent is View) {
                parent.visibility = if (isVoiceDesign) View.GONE else View.VISIBLE
            }
        }
    }

    /**
     * 显示音色描述编写指南对话框（来自小米 MiMo 官方文档）
     */
    private fun showVoiceDesignGuideDialog(): Unit {
        val guideText = """
            <h3>关键维度</h3>
            一条好的音色描述通常涵盖以下多个维度（不需要面面俱到）：
            
            <p><b>• 性别与年龄：</b>如	"young woman in her mid-20s"、"五十多岁的中年男性"</p>
            <p><b>• 音色/质感：</b>如	"deep and gravelly"、"丝滑醇厚、带着磁性"</p>
            <p><b>• 情绪/语气：</b>如	"warm and confident"、"温柔但带着一丝疲惫"</p>
            <p><b>• 语速/节奏：</b>如	"slow and deliberate"、"语速极快，像连珠炮"</p>

            <h3>以下维度可选择性加入，增加丰富度：</h3>
            <p><b>• 角色/人设：narrator, podcast host, 评书先生, 深夜电台DJ</p>
            <p><b>• 说话风格：casual and colloquial, 一本正经地, 压低嗓音像在密谋</p>
            <p><b>• 场景描写：narrating a nature documentary, 在给投资人路演</p>
            <p><b>• 年代参照：1940s film noir, 八十年代译制片配音</p>

            <h3>写法建议</h3>
            长度：1-4 句即可，不需要写长文。核心特征描述清楚比堆砌维度更重要

            避免冲突：不要同时要求矛盾的特征（如"稚嫩的童声 + CEO气场"）

            避免音质效果词：不要写混响、回声、EQ、压缩等后期处理相关描述

            避免模糊词：不要用"普通的""正常的""外国的"等缺乏具体指向的描述

            中英文均可：模型同时支持中英文音色描述，选择你最能精确表达的语言

            合成文本要贴合音色：assistant 消息中的合成文本（text）应与音色描述相匹配，才能获得最佳效果。例如为"温柔治愈系女声"搭配一段晚安独白，而非一段激烈的体育解说。建议使用 LLM 根据你的音色描述自动生成适配的合成文本；在 Studio 页面上，输入音色描述后可直接点击「生成文本」按钮

            <h3>示例</h3>
            简洁描述型 -- 用关键词或一句话快速勾勒声音轮廓

            - Heavy Russian accent, gruff middle-aged male, blunt and matter-of-fact.
            
            专业描述型 -- 通过场景、人设或多维度细节立体刻画声音
            
            - Young female, extreme close-up with a binaural, ear-to-ear ASMR feel. Audible breathing, subtle swallowing, and soft natural lip sounds. She speaks very slowly, creating a deeply relaxing and immersive experience.
            - 一位年迈的老先生，说带北方口音的普通话，语速缓慢而沉稳，嗓音略带沙哑和沧桑感，仿佛一位饱经风霜的老爷爷在讲故事，充满岁月的智慧。

            <br><small><font color="#6B7280">💡 以上内容来自小米 MiMo 官方文档</font></small>
        """

        AlertDialog.Builder(requireContext())
            .setTitle("如何写好音色描述")
            .setMessage(android.text.Html.fromHtml(guideText, android.text.Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton("知道了", null)
            .show()
    }

    /**
     * 设置Fragment结果监听器
     * 监听时间感知设置对话框返回的结果
     */
    private fun setupFragmentResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            TimeAwarenessSettingsDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val sleepSchedule = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(
                    TimeAwarenessSettingsDialogFragment.RESULT_SLEEP_SCHEDULE,
                    com.susking.ephone_s.aidata.domain.model.SleepSchedule::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(TimeAwarenessSettingsDialogFragment.RESULT_SLEEP_SCHEDULE)
            }
            
            val timeSensitivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(
                    TimeAwarenessSettingsDialogFragment.RESULT_TIME_SENSITIVITY,
                    com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(TimeAwarenessSettingsDialogFragment.RESULT_TIME_SENSITIVITY)
            }

            // 立即更新联系人的时间感知配置
            if (sleepSchedule != null && timeSensitivity != null) {
                val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId }
                if (contact != null) {
                    val updatedContact = contact.copy(
                        sleepSchedule = sleepSchedule,
                        timeSensitivityConfig = timeSensitivity
                    )
                    contactManager.updateContact(updatedContact)
                    Log.d("QqProfileSettings", "时间感知配置已更新")
                }
            }
        }
    }

    /**
     * 编辑当前人设内容。
     * 弹出多行输入对话框，确认后更新暂存变量 currentPersona 并刷新按钮文本。
     * 实际持久化由退出页面时的自动保存统一完成。
     */
    private fun showPersonaEditDialog() {
        val density: Float = resources.displayMetrics.density
        // 外层 ScrollView 的固定高度，承担滚动手势
        val scrollHeightPx: Int = (260 * density + 0.5f).toInt()
        val paddingPx: Int = (16 * density + 0.5f).toInt()
        val editText = android.widget.EditText(requireContext()).apply {
            setText(currentPersona)
            setSelection(text.length)
            hint = "请输入 AI 人设描述"
            gravity = android.view.Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            // 高度随内容增长，自身不滚动；滚动统一交给外层 ScrollView 处理，避免手势冲突
            isVerticalScrollBarEnabled = false
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // 外层 ScrollView 固定高度并独占滚动：竖向拖动被它截为滚动，仅真正点击才落到 EditText 聚焦弹键盘
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            isVerticalScrollBarEnabled = true
            isVerticalFadingEdgeEnabled = true
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                scrollHeightPx
            )
            addView(editText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("编辑 AI 人设")
            .setView(scrollView)
            .setPositiveButton("确定") { dialog, _ ->
                currentPersona = editText.text.toString()
                updatePersonaButtonText()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 刷新人设按钮显示文本。
     * 按钮固定显示“编辑人设”，不展示具体人设内容，避免长文本撑大按钮。
     */
    private fun updatePersonaButtonText() {
        binding.personaEditButton.text = "编辑人设"
    }

    /**
     * 保存当前页面的所有设置。
     * 由退出页面时（onDestroyView）自动触发，不再依赖手动“保存设置”按钮。
     * 仅在数据已成功绑定（isDataBound）时才执行，避免数据未加载就以空表单覆盖。
     */
    private fun saveSettings() {
        if (!isDataBound) {
            Log.d("QqProfileSettings", "数据尚未绑定，跳过自动保存。")
            return
        }
        Log.d("QqProfileSettings", "退出页面，自动保存AI资料设置...")
        // 从ViewModel中获取最新的联系人数据作为更新基础
        val freshestContact = viewModel.contactManager.contacts.value?.find { it.id == contactId }
        if (freshestContact == null) {
            Log.e("QqProfileSettings", "保存失败：无法从ViewModel获取最新的联系人数据。")
            return
        }

        val updatedContact = freshestContact.copy(
            autoSummaryEnabled = binding.longTermMemorySwitch.isChecked,
            summaryInterval = binding.summaryIntervalEditText.text.toString().toIntOrNull() ?: 50,
            shortTermMemoryLimit = binding.shortTermMemoryEditText.text.toString().toIntOrNull() ?: 20,
            attachMemoryLimit = binding.attachMemoryEditText.text.toString().toIntOrNull() ?: 10,
            persona = currentPersona,
            remarkName = binding.remarkNameEditText.text.toString(),
            nicknameForUser = binding.nicknameForUserEditText.text.toString(),
            realName = binding.realNameEditText.text.toString(),
            group = binding.friendGroupSpinner.text.toString().let { if (it == "我的好友") null else it },
            privacyModeEnabled = binding.privacyModeSwitch.isChecked,
            ttsVoiceId = binding.ttsVoiceSpinner.text.toString().takeIf { voiceId: String -> voiceId.isNotBlank() },
            voiceDescription = binding.voiceDescriptionEditText.text.toString().takeIf { it.isNotBlank() },
            // 保留时间感知配置(通过对话框单独设置)
            sleepSchedule = freshestContact.sleepSchedule,
            timeSensitivityConfig = freshestContact.timeSensitivityConfig
        )
        Log.d("QqProfileSettings", "更新后的联系人设置数据: 自动总结: ${updatedContact.autoSummaryEnabled}, 总结间隔: ${updatedContact.summaryInterval}")
        contactManager.updateContact(updatedContact)
        Log.d("QqProfileSettings", "设置已自动保存。")
    }

    override fun onDestroyView() {
        // 退出页面时自动保存所有设置（替代原“保存设置”按钮）
        saveSettings()
        releaseTtsPreviewPlayer()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val TTS_PREVIEW_TEXT = "（开心）你好呀，这是小米 MiMo TTS 联系人音色试听。[笑]"

        fun newInstance(contactId: String): QqAiProfileSettingsFragment {
            return QqAiProfileSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}
