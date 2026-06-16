package com.susking.ephone_s.brain.ui

import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugEntry
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallDebugRecordWithEntries
import com.susking.ephone_s.brain.R
import com.susking.ephone_s.brain.api.FloatingWindowStyleProvider
import com.susking.ephone_s.brain.databinding.FragmentBrainBinding
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.core.ui.dialog.PromptSection
import com.susking.ephone_s.core.ui.dialog.PromptSectionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * "大脑"悬浮窗的 Fragment，用于展示 AI 活动日志。
 */
class BrainFragment : Fragment() {

    private var _binding: FragmentBrainBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var brainViewModel: BrainViewModel
    private lateinit var aiActivityAdapter: AiActivityAdapter
    private lateinit var autoPlanAdapter: AutoPlanAdapter
    
    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    
    private var styleProvider: FloatingWindowStyleProvider? = null
    private var hasUnreadActivities: Boolean = false
    private val themeChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_THEME_CHANGED) return
            applyFloatingWindowStyle()
            applyCurrentFabImage()
        }
    }
    
    /**
     * 设置依赖项。应在Fragment附加后立即调用。
     */
    fun setDependencies(
        viewModel: BrainViewModel,
        styleProvider: FloatingWindowStyleProvider?,
        colorProvider: AiActivityAdapter.ColorProvider
    ) {
        this.brainViewModel = viewModel
        this.styleProvider = styleProvider
        this.aiActivityAdapter = AiActivityAdapter(
            onItemClick = { activity ->
                showActivityDetailsDialog(activity)
            },
            colorProvider = colorProvider,
            onCancelTask = { activityChainId ->
                brainViewModel.cancelTask(activityChainId)
            }
        )
        this.autoPlanAdapter = AutoPlanAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireContext(),
            themeChangedReceiver,
            IntentFilter(ACTION_THEME_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(themeChangedReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyFloatingWindowStyle()
        setupRecyclerView()
        setupClickListeners()
        setupDraggableFab()
        setupBackgroundTaskSummary()
        observeAiActivities()
        observeTaskStats()
        setupAutoPlanSummary()
        observeAutoPlans()
 
        view.post {
            val currentBinding: FragmentBrainBinding = _binding ?: return@post
            val fab: View = currentBinding.fabBrain
            val screenWidth: Int = resources.displayMetrics.widthPixels
            val savedPosition: Pair<Float, Float>? = brainViewModel.getFabPosition()
            if (savedPosition != null) {
                animateFabToEdge(fab, savedPosition.first, savedPosition.second)
            } else {
                val targetX: Float = (screenWidth - fab.width).toFloat()
                fab.x = targetX
                fab.alpha = 1f
            }
        }
    }

    /**
     * 应用主题系统提供的 Brain 悬浮窗样式。
     */
    private fun applyFloatingWindowStyle(): Unit {
        val provider: FloatingWindowStyleProvider = styleProvider ?: return
        val backgroundColor: Int = provider.getBackgroundColor()
        val textColor: Int = provider.getTextColor()
        val accentColor: Int = provider.getAccentColor()
        val cardBackgroundColor: Int = provider.getCardBackgroundColor()

        binding.fabBrain.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        binding.fabBrain.imageTintList = ColorStateList.valueOf(textColor)
        applyCurrentFabImage()

        binding.cardViewBrain.setCardBackgroundColor(cardBackgroundColor)
        (binding.cardViewBrain.getChildAt(0) as? View)?.setBackgroundColor(cardBackgroundColor)
        binding.textViewTitle.setTextColor(textColor)
        binding.buttonCancelAllTasks.imageTintList = ColorStateList.valueOf(accentColor)
        binding.buttonMarkAllRead.imageTintList = ColorStateList.valueOf(accentColor)
        binding.buttonClearAll.imageTintList = ColorStateList.valueOf(accentColor)
    }

    /**
     * 当Fragment的容器可见性改变时调用此方法
     * 用于重新初始化FAB位置
     */
    fun onContainerVisibilityChanged(isVisible: Boolean) {
        if (isVisible && view != null) {
            val fab = binding.fabBrain
            val screenWidth = resources.displayMetrics.widthPixels
            val savedPosition = brainViewModel.getFabPosition()
            if (savedPosition != null) {
                animateFabToEdge(fab, savedPosition.first, savedPosition.second)
            } else {
                val targetX = (screenWidth - fab.width).toFloat()
                fab.x = targetX
                fab.alpha = 1f
            }
        }
    }

    private fun animateFabToEdge(fab: View, currentX: Float, currentY: Float) {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (currentX + fab.width / 2 < screenWidth / 2) {
            0f
        } else {
            (screenWidth - fab.width).toFloat()
        }
        fab.animate()
            .x(targetX)
            .y(currentY)
            .setDuration(200)
            .withEndAction {
                fab.alpha = 1f
                brainViewModel.saveFabPosition(fab.x, fab.y)
            }
            .start()
    }

    private fun setupClickListeners() {
        binding.fabBrain.setOnClickListener {
            toggleExpandState()
        }
        binding.buttonCancelAllTasks.setOnClickListener {
            showCancelAllTasksDialog()
        }
        binding.buttonMarkAllRead.setOnClickListener {
            brainViewModel.markAllAsRead()
        }
        binding.buttonClearAll.setOnClickListener {
            brainViewModel.clearAllActivities()
        }
    }
    
    /**
     * 显示取消所有后台任务的确认对话框
     */
    private fun showCancelAllTasksDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("取消所有后台生图任务")
            .setMessage("确定要取消所有正在进行和等待中的后台任务吗？此操作不可撤销。")
            .setPositiveButton("确定") { _, _ ->
                brainViewModel.cancelAllBackgroundTasks()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 标记当前是否在显示后台任务列表
    private var isShowingBackgroundTasks = false

    // 标记当前是否在显示自动计划列表
    private var isShowingAutoPlans = false

    /**
     * 设置后台任务统计条目
     */
    private fun setupBackgroundTaskSummary() {
        binding.layoutBackgroundTaskSummary.root.setOnClickListener {
            // 点击后台任务条目,切换显示后台任务列表
            isShowingBackgroundTasks = !isShowingBackgroundTasks
            if (isShowingBackgroundTasks) {
                // 显示后台任务列表
                isShowingAutoPlans = false
                binding.layoutAutoPlanSummary.textViewAutoPlanTitle.text = "自动计划"
                binding.recyclerViewAiActivities.adapter = aiActivityAdapter
                aiActivityAdapter.submitList(brainViewModel.backgroundTasks.value)
                binding.layoutBackgroundTaskSummary.textViewTaskTitle.text = "后台任务 (点击返回)"
            } else {
                // 显示普通日志
                binding.recyclerViewAiActivities.adapter = aiActivityAdapter
                aiActivityAdapter.submitList(brainViewModel.normalActivities.value)
                binding.layoutBackgroundTaskSummary.textViewTaskTitle.text = "后台任务"
            }
        }
    }

    /**
     * 设置自动计划统计条目。
     */
    private fun setupAutoPlanSummary() {
        binding.layoutAutoPlanSummary.root.setOnClickListener {
            isShowingAutoPlans = !isShowingAutoPlans
            if (isShowingAutoPlans) {
                isShowingBackgroundTasks = false
                binding.layoutBackgroundTaskSummary.textViewTaskTitle.text = "后台任务"
                binding.recyclerViewAiActivities.adapter = autoPlanAdapter
                autoPlanAdapter.submitList(brainViewModel.autoPlans.value)
                binding.layoutAutoPlanSummary.textViewAutoPlanTitle.text = "自动计划 (点击返回)"
            } else {
                binding.recyclerViewAiActivities.adapter = aiActivityAdapter
                aiActivityAdapter.submitList(brainViewModel.normalActivities.value)
                binding.layoutAutoPlanSummary.textViewAutoPlanTitle.text = "自动计划"
            }
        }
    }

    /**
     * 观察任务统计数据
     */
    private fun observeTaskStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            brainViewModel.backgroundTaskStats.collectLatest { stats ->
                binding.layoutBackgroundTaskSummary.textViewTotalCount.text = stats.total.toString()
                binding.layoutBackgroundTaskSummary.textViewSuccessCount.text = stats.success.toString()
                binding.layoutBackgroundTaskSummary.textViewFailedCount.text = stats.failed.toString()
            }
        }
    }

    private fun toggleExpandState() {
        if (binding.cardViewBrain.visibility == View.VISIBLE) {
            collapse()
        } else {
            expand()
        }
    }

    private fun expand() {
        binding.cardViewBrain.visibility = View.VISIBLE
        brainViewModel.markAllAsRead()
        binding.recyclerViewAiActivities.adapter?.notifyItemRangeChanged(0, aiActivityAdapter.itemCount)
    }

    private fun collapse() {
        binding.cardViewBrain.visibility = View.GONE
        if (::brainViewModel.isInitialized) {
            brainViewModel.clearFabPosition()
        }
    }

    private fun setupDraggableFab() {
        var initialX = 0f
        var initialY = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f
        val clickThreshold = 10

        binding.fabBrain.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    applyDraggingImageIfAvailable()
                    initialX = view.x
                    initialY = view.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    view.x = initialX + dx
                    view.y = initialY + dy
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) < clickThreshold && abs(dy) < clickThreshold) {
                        view.performClick()
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val targetX = if (view.x + view.width / 2 < screenWidth / 2) {
                            0f
                        } else {
                            (screenWidth - view.width).toFloat()
                        }
                        view.animate()
                            .x(targetX)
                            .setDuration(200)
                            .withEndAction {
                                brainViewModel.saveFabPosition(view.x, view.y)
                                applyDockedImageIfAvailable()
                            }
                            .start()
                    }
                    if (abs(dx) < clickThreshold && abs(dy) < clickThreshold) {
                        applyDockedImageIfAvailable()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyDraggingImageIfAvailable(): Unit {
        val draggingImageUri: String = styleProvider?.getDraggingImageUri().orEmpty()
        if (draggingImageUri.isNotBlank()) {
            binding.fabBrain.setImageURI(Uri.parse(draggingImageUri))
            binding.fabBrain.imageTintList = null
        }
    }

    private fun applyDockedImageIfAvailable(): Unit {
        applyCurrentFabImage()
    }

    private fun applyCurrentFabImage(): Unit {
        val provider: FloatingWindowStyleProvider? = styleProvider
        val imageUri: String = if (hasUnreadActivities) {
            provider?.getDefaultImageUri().orEmpty()
        } else {
            provider?.getDockedImageUri().orEmpty()
        }
        if (imageUri.isNotBlank()) {
            binding.fabBrain.setImageURI(Uri.parse(imageUri))
            binding.fabBrain.imageTintList = null
            return
        }
        binding.fabBrain.setImageResource(
            if (hasUnreadActivities) R.drawable.ic_brain_unread else R.drawable.ic_brain_24
        )
    }

    private fun setupRecyclerView() {
        binding.recyclerViewAiActivities.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = aiActivityAdapter
        }
    }

    private fun observeAutoPlans() {
        viewLifecycleOwner.lifecycleScope.launch {
            brainViewModel.autoPlans.collectLatest { plans: List<AutoPlanItem> ->
                val enabledPlanCount: Int = plans.count { plan: AutoPlanItem -> plan.type != AutoPlanType.EMPTY }
                binding.layoutAutoPlanSummary.textViewAutoPlanCount.text = enabledPlanCount.toString()
                if (isShowingAutoPlans) {
                    autoPlanAdapter.submitList(plans)
                }
            }
        }
    }

    private fun observeAiActivities() {
        // 观察普通日志(不包括后台任务)
        viewLifecycleOwner.lifecycleScope.launch {
            brainViewModel.normalActivities.collectLatest { activities ->
                // 只有在显示普通日志时才更新列表
                if (!isShowingBackgroundTasks && !isShowingAutoPlans) {
                    aiActivityAdapter.submitList(activities)
                }
                
                if (activities.isNotEmpty()) {
                    binding.recyclerViewAiActivities.scrollToPosition(0)
                }
            }
        }
        
        // 观察后台任务列表
        viewLifecycleOwner.lifecycleScope.launch {
            brainViewModel.backgroundTasks.collectLatest { tasks ->
                // 只有在显示后台任务时才更新列表
                if (isShowingBackgroundTasks) {
                    aiActivityAdapter.submitList(tasks)
                }
            }
        }
        
        // 观察所有活动用于未读标记
        viewLifecycleOwner.lifecycleScope.launch {
            brainViewModel.aiActivities.collectLatest { activities ->
                hasUnreadActivities = activities.any { !it.isRead }
                applyCurrentFabImage()
            }
        }
        
        // 取消按钮不需要状态观察,图标固定为叉号
    }

    private fun showActivityDetailsDialog(activity: AiActivity) {
        val binding = com.susking.ephone_s.core.databinding.DialogAiActivityDetailBinding.inflate(
            requireActivity().layoutInflater,
            null,
            false
        )
        
        // 设置基本信息
        binding.textViewDescription.text = "描述: ${activity.description}"
        binding.textViewTimestamp.text = "时间: ${formatTimestamp(activity.timestamp)}"
        binding.textViewStatus.text = "状态: ${activity.status}"
        
        // 在后台线程解析并分组内容
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val sections: List<PromptSection> = buildActivityDetailSections(activity)
            val rawResponseSections: List<PromptSection> = buildRawResponseSections(activity)
            
            withContext(Dispatchers.Main) {
                binding.activityDetailRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = PromptSectionAdapter(sections)
                    isVerticalScrollBarEnabled = true
                    scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
                }
                binding.rawResponseRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = PromptSectionAdapter(rawResponseSections)
                    visibility = if (rawResponseSections.isEmpty()) View.GONE else View.VISIBLE
                    isVerticalScrollBarEnabled = false
                }
                
                // 显示对话框
                AlertDialog.Builder(requireContext())
                    .setTitle("AI 活动详情")
                    .setView(binding.root)
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }
    
    /**
     * 解析活动详情内容。原始回复由底部固定区域单独渲染，不进入可滚动列表。
     */
    private suspend fun buildActivityDetailSections(activity: AiActivity): List<PromptSection> {
        val promptSections: List<PromptSection> = buildPromptSections(activity)
        val recallDebugSections: List<PromptSection> = buildRecallDebugSections(activity)
        return promptSections + recallDebugSections
    }

    /**
     * 解析提示词内容并按 role 分组。
     */
    private fun buildPromptSections(activity: AiActivity): List<PromptSection> {
        if (activity.prompt.isBlank()) return emptyList()
        return parseJsonToSections(activity.prompt, "提示词")
    }

    /**
     * 解析原始回复内容，并截断 base64 图片数据避免详情弹窗过长。
     */
    private fun buildRawResponseSections(activity: AiActivity): List<PromptSection> {
        if (activity.rawResponse.isBlank()) return emptyList()
        val truncatedResponse: String = truncateBase64InMessage(activity.rawResponse)
        return parseJsonToSections(truncatedResponse, "原始回复")
    }

    private suspend fun buildRecallDebugSections(activity: AiActivity): List<PromptSection> {
        if (!isEmbeddingActivity(activity)) return emptyList()
        val recordWithEntries: MemoryRecallDebugRecordWithEntries = getRecallDebugRecord(activity) ?: return listOf(
            PromptSection(
                title = "记忆召回调试",
                content = "暂无可关联的记忆召回调试记录。新请求完成结构化记忆召回后会显示候选、注入和各项分数。",
                isExpanded = false
            )
        )
        val summarySection = PromptSection(
            title = "记忆召回调试 - 总览",
            content = buildRecallDebugSummary(recordWithEntries),
            isExpanded = false
        )
        val entrySections: List<PromptSection> = recordWithEntries.entries.map { entry: MemoryRecallDebugEntry ->
            val entryTitle: String = entry.snapshotTitle.ifBlank { entry.objectType.name }
            PromptSection(
                title = "${entryTitle} - #${entry.rank} ${entry.objectType} ${if (entry.isInjected) "已注入" else "未注入"}",
                content = buildRecallDebugEntryText(entry),
                isExpanded = false
            )
        }
        return listOf(summarySection) + entrySections
    }

    private suspend fun getRecallDebugRecord(activity: AiActivity): MemoryRecallDebugRecordWithEntries? {
        val recallService = AiDataApi.getMemoryRecallServiceOrNull() ?: return null
        val activityRecord = recallService.getLatestRecallDebugRecordForActivity(activity.activityChainId)
        if (activityRecord != null) return activityRecord
        return recallService.getLatestRecallDebugRecord()
    }

    private fun isEmbeddingActivity(activity: AiActivity): Boolean {
        return activity.prompt.contains("\"type\": \"embedding\"") || activity.prompt.contains("\"type\":\"embedding\"")
    }

    private fun buildRecallDebugSummary(recordWithEntries: MemoryRecallDebugRecordWithEntries): String {
        val record = recordWithEntries.record
        return """
            记录ID：${record.id}
            活动链ID：${record.activityChainId ?: "未绑定"}
            联系人ID：${record.contactId}
            场景：${record.sceneType}
            目的：${record.recallPurpose}
            候选数量：${record.candidateCount}
            注入数量：${record.injectedCount}
            预算 token：${record.maxTokenBudget}
            估算 token：${record.estimatedTokenCount}
            TopK：${record.topK}
            创建时间：${formatTimestamp(record.createdAt)}
            当前查询：${record.currentMessage}
            语义状态：${record.semanticStateText.ifBlank { "无" }}
            近期上下文：${record.recentMessagesText.ifBlank { "无" }}
        """.trimIndent()
    }

    private fun buildRecallDebugEntryText(entry: MemoryRecallDebugEntry): String {
        return """
            对象类型：${entry.objectType}
            对象ID：${entry.objectId}
            来源：${entry.sourceTypesText.ifBlank { "无" }}
            是否注入：${if (entry.isInjected) "是" else "否"}
            最终分数：${formatScore(entry.finalScore)}
            语义分数：${formatScore(entry.semanticScore)}
            关键词分数：${formatScore(entry.keywordScore)}
            图谱分数：${formatScore(entry.graphScore)}
            重要性分数：${formatScore(entry.importanceScore)}
            时效分数：${formatScore(entry.recencyScore)}
            置信分数：${formatScore(entry.confidenceScore)}
            状态分数：${formatScore(entry.stateScore)}
            标题：${entry.snapshotTitle}
            内容：${entry.snapshotText}
        """.trimIndent()
    }

    private fun formatScore(score: Float): String {
        return String.format(Locale.getDefault(), "%.3f", score)
    }
    
    /**
     * 将JSON字符串解析为可折叠的部分
     */
    private fun parseJsonToSections(json: String, prefix: String): List<PromptSection> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap = gson.fromJson<Map<String, Any>>(json, type)
            val messagesList = jsonMap["messages"] as? List<Map<String, Any>>
            
            if (messagesList != null) {
                // 如果是标准的消息格式，按role分组
                messagesList.mapIndexed { index, message ->
                    val role = message["role"] as? String ?: "unknown"
                    PromptSection(
                        title = "$prefix - $role #${index + 1}",
                        content = formatJsonTextForDisplay(gson.toJson(message)),
                        isExpanded = false // 默认全部折叠
                    )
                }
            } else {
                // 如果不是消息格式，直接显示整个JSON
                listOf(
                    PromptSection(
                        title = prefix,
                        content = formatJsonTextForDisplay(gson.toJson(jsonMap)),
                        isExpanded = false
                    )
                )
            }
        } catch (e: JsonSyntaxException) {
            // 如果JSON解析失败，则将整个内容作为一个部分显示
            listOf(
                PromptSection(
                    title = prefix,
                    content = formatJsonTextForDisplay(json),
                    isExpanded = false
                )
            )
        }
    }

    private fun formatJsonTextForDisplay(text: String): String {
        val unicodePattern = Regex("""\\+u([0-9a-fA-F]{4})""")
        val textWithUnicodeCharacters = unicodePattern.replace(text) { matchResult ->
            matchResult.groupValues[1].toInt(16).toChar().toString()
        }
        return textWithUnicodeCharacters
            .replace(Regex("""\\+n"""), "\n")
            .replace(Regex("""\\+t"""), "\t")
            .replace(Regex("""\\+r"""), "\r")
            .replace(Regex("""\\+\""""), "\"")
            .replace(Regex("""\\+'"""), "'")
            .replace(Regex("""\\+/"""), "/")
    }

    /**
     * 截断消息中的base64图片数据,只保留前50位
     */
    private fun truncateBase64InMessage(message: String): String {
        // 匹配 data:image/...;base64, 后面的base64数据
        val base64Pattern = Regex("""(data:image/[^;]+;base64,)([A-Za-z0-9+/=]{50})[A-Za-z0-9+/=]+""")
        return base64Pattern.replace(message) { matchResult ->
            "${matchResult.groupValues[1]}${matchResult.groupValues[2]}...[已截断]"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private companion object {
        const val ACTION_THEME_CHANGED = "com.susking.ephone_s.ACTION_THEME_CHANGED"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}