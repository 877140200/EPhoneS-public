package com.susking.ephone_s.qq.ui.chat.memory

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.core.ui.dialog.ConfirmAiPromptDialogFragment
import com.susking.ephone_s.qq.databinding.FragmentLongTermMemoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class LongTermMemoryFragment : Fragment(), LongTermMemoryAdapter.OnMemoryInteractionListener, StructuredMemoryEventAdapter.OnStructuredEventInteractionListener {

    private var _binding: FragmentLongTermMemoryBinding? = null
    private val binding get() = _binding!!

    // ViewModel会从SavedStateHandle中获取contactId参数
    private val viewModel: LongTermMemoryViewModel by viewModels()
    private var contactId: String? = null
    private var currentMemoryCenterUiState: MemoryCenterUiState = MemoryCenterUiState.EMPTY
    private lateinit var legacyMemoryAdapter: LongTermMemoryAdapter
    private lateinit var structuredMemoryEventAdapter: StructuredMemoryEventAdapter
    private var currentDisplayMode: MemoryCenterDisplayMode = MemoryCenterDisplayMode.STRUCTURED_EVENTS
    private var currentSearchQuery: String = ""
    private var currentLegacyMemories: List<LongTermMemory> = emptyList()
    private var currentStructuredEvents: List<MemoryEvent> = emptyList()
    private var lastExtractionPositionTimestamp: Long? = null
    private var newMessageCountSinceLastExtraction: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLongTermMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.susking.ephone_s.qq.R.id.action_video_call_history -> {
                    // 跳转到视频通话历史页面
                    val currentContactId = contactId
                    if (currentContactId != null) {
                        parentFragmentManager.beginTransaction()
                            .replace(com.susking.ephone_s.qq.R.id.fragment_container_for_chat, VideoCallHistoryFragment.newInstance(currentContactId))
                            .addToBackStack(null)
                            .commit()
                    } else {
                        Toast.makeText(context, "无法获取联系人ID", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        legacyMemoryAdapter = LongTermMemoryAdapter(this)
        structuredMemoryEventAdapter = StructuredMemoryEventAdapter(this)
        binding.memoryRecyclerView.adapter = structuredMemoryEventAdapter

        binding.addMemoryFab.setOnClickListener { showStructuredEventDialog(null) }
        binding.structuredEventsTabButton.setOnClickListener { switchDisplayMode(MemoryCenterDisplayMode.STRUCTURED_EVENTS) }
        binding.legacyMemoriesTabButton.setOnClickListener { switchDisplayMode(MemoryCenterDisplayMode.LEGACY_MEMORIES) }
        binding.memorySearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int): Unit = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int): Unit {
                currentSearchQuery = text?.toString().orEmpty()
                renderCurrentList()
            }

            override fun afterTextChanged(text: Editable?): Unit = Unit
        })
        switchDisplayMode(MemoryCenterDisplayMode.STRUCTURED_EVENTS)

        // 修改：提取结构化事件按钮 - 先显示选择对话框
        binding.summarizeChatFab.setOnClickListener {
            showSummarizeOptionsDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.memories.collectLatest { memories: List<LongTermMemory> ->
                currentLegacyMemories = memories
                renderCurrentList()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.structuredEvents.collectLatest { events: List<MemoryEvent> ->
                currentStructuredEvents = events
                renderCurrentList()
            }
        }

        viewModel.memoryCenterUiState.onEach { uiState: MemoryCenterUiState ->
            currentMemoryCenterUiState = uiState
            renderMemoryCenter(uiState)
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.lastExtractionPositionTimestamp.onEach { timestamp: Long? ->
            lastExtractionPositionTimestamp = timestamp
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.newMessageCountSinceLastExtraction.onEach { messageCount: Int ->
            newMessageCountSinceLastExtraction = messageCount
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        // 观察待处理的Prompt请求
        // 监听待确认的提示词请求
        viewModel.pendingPromptRequest.onEach { request ->
            request?.let {
                ConfirmAiPromptDialogFragment.newInstance(
                    promptJson = it.displayPromptJson,
                    url = it.url,
                    model = it.request.model,
                    timestamp = it.timestamp
                ).show(childFragmentManager, ConfirmAiPromptDialogFragment.TAG)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        // 监听确认对话框的结果
        childFragmentManager.setFragmentResultListener(
            ConfirmAiPromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(ConfirmAiPromptDialogFragment.RESULT_CONFIRMED)
            if (confirmed) {
                viewModel.executeConfirmedRequest()
                Toast.makeText(context, "正在请求AI...", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.cancelRequest()
            }
        }

        viewModel.pendingExtractionPreview.onEach { preview: FactGraphExtractionPreview? ->
            if (preview != null) {
                viewModel.onExtractionPreviewShown()
                showExtractionPreviewDialog(preview)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        // 观察最终的执行结果
        viewModel.summaryResult.onEach { result ->
            result?.let {
                it.onSuccess { summary ->
                    showSummaryDialog(summary)
                }.onFailure { error ->
                    Toast.makeText(context, "操作失败: ${error.message}", Toast.LENGTH_LONG).show()
                }
                viewModel.onSummaryResultConsumed()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

    }


    private fun renderMemoryCenter(uiState: MemoryCenterUiState) {
        binding.memoryCenterCategoryText.text = uiState.categorySummaryText
        binding.memoryCenterVectorText.text = uiState.vectorSummaryText
    }

    private fun switchDisplayMode(displayMode: MemoryCenterDisplayMode): Unit {
        currentDisplayMode = displayMode
        binding.memoryRecyclerView.adapter = when (displayMode) {
            MemoryCenterDisplayMode.STRUCTURED_EVENTS -> structuredMemoryEventAdapter
            MemoryCenterDisplayMode.LEGACY_MEMORIES -> legacyMemoryAdapter
        }
        binding.memorySearchInputLayout.hint = when (displayMode) {
            MemoryCenterDisplayMode.STRUCTURED_EVENTS -> "搜索结构化事件"
            MemoryCenterDisplayMode.LEGACY_MEMORIES -> "搜索原子事件纪念记录"
        }
        binding.addMemoryFab.visibility = if (displayMode == MemoryCenterDisplayMode.STRUCTURED_EVENTS) View.VISIBLE else View.GONE
        binding.summarizeChatFab.visibility = if (displayMode == MemoryCenterDisplayMode.STRUCTURED_EVENTS) View.VISIBLE else View.GONE
        binding.structuredEventsTabButton.isChecked = displayMode == MemoryCenterDisplayMode.STRUCTURED_EVENTS
        binding.legacyMemoriesTabButton.isChecked = displayMode == MemoryCenterDisplayMode.LEGACY_MEMORIES
        renderCurrentList()
    }

    private fun renderCurrentList(): Unit {
        when (currentDisplayMode) {
            MemoryCenterDisplayMode.STRUCTURED_EVENTS -> structuredMemoryEventAdapter.submitList(filterStructuredEvents(currentStructuredEvents, currentSearchQuery))
            MemoryCenterDisplayMode.LEGACY_MEMORIES -> legacyMemoryAdapter.submitList(filterLegacyMemories(currentLegacyMemories, currentSearchQuery))
        }
    }

    private fun filterStructuredEvents(events: List<MemoryEvent>, query: String): List<MemoryEvent> {
        val normalizedQuery: String = query.trim()
        if (normalizedQuery.isBlank()) return events
        return events.filter { event: MemoryEvent ->
            event.title.contains(normalizedQuery, ignoreCase = true) ||
                event.content.contains(normalizedQuery, ignoreCase = true) ||
                event.eventType.name.contains(normalizedQuery, ignoreCase = true) ||
                event.status.name.contains(normalizedQuery, ignoreCase = true)
        }
    }

    private fun filterLegacyMemories(memories: List<LongTermMemory>, query: String): List<LongTermMemory> {
        val normalizedQuery: String = query.trim()
        if (normalizedQuery.isBlank()) return memories
        return memories.filter { memory: LongTermMemory -> memory.memoryText.contains(normalizedQuery, ignoreCase = true) }
    }

    private fun showExtractionPreviewDialog(preview: FactGraphExtractionPreview): Unit {
        val rootObject: JsonObject = parseExtractionRoot(preview.responseText)
        val editableItems: MutableList<ExtractionEditableItem> = mutableListOf()
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(EXTRACTION_DIALOG_SIDE_PADDING, EXTRACTION_DIALOG_TOP_PADDING, EXTRACTION_DIALOG_SIDE_PADDING, EXTRACTION_DIALOG_BOTTOM_PADDING)
            addView(createExtractionHeroView(preview.changeSummaryText))
            addView(createExtractionSectionCard("事件", "AI 从对话中提炼出的可保存记忆", createExtractionEditableSection("events", rootObject.getJsonArrayOrEmpty("events"), buildEventExtractionFields(), editableItems)))
            addView(createExtractionSectionCard("节点", "人物、地点、物品或概念等实体", createExtractionEditableSection("nodes", rootObject.getJsonArrayOrEmpty("nodes"), buildNodeExtractionFields(), editableItems)))
            addView(createExtractionSectionCard("关系", "实体之间新增或变更的关联", createExtractionEditableSection("relations", rootObject.getJsonArrayOrEmpty("relations"), buildRelationExtractionFields(), editableItems)))
            addView(createExtractionFooterText())
        }
        val dialog: AlertDialog = AlertDialog.Builder(requireContext())
            .setTitle("确认结构化记忆变更")
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setPositiveButton("确认保存") { _, _ ->
                viewModel.confirmSelectedFactGraphExtraction(collectEditedExtractionJson(rootObject, editableItems))
                Toast.makeText(context, "正在保存结构化记忆...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消") { _, _ -> viewModel.cancelSelectedFactGraphPreview() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor(EXTRACTION_ACCENT_COLOR))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor(EXTRACTION_SECONDARY_TEXT_COLOR))
    }

    private fun parseExtractionRoot(responseText: String): JsonObject {
        val cleanedText: String = responseText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val startIndex: Int = cleanedText.indexOf('{')
        val endIndex: Int = cleanedText.lastIndexOf('}')
        val jsonText: String = if (startIndex >= 0 && endIndex > startIndex) cleanedText.substring(startIndex, endIndex + 1) else cleanedText
        return runCatching { Gson().fromJson(jsonText, JsonObject::class.java) }.getOrDefault(JsonObject().apply {
            add("events", JsonArray())
            add("nodes", JsonArray())
            add("relations", JsonArray())
        })
    }

    private fun createExtractionEditableSection(categoryKey: String, items: JsonArray, fields: List<ExtractionEditField>, editableItems: MutableList<ExtractionEditableItem>): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            if (items.size() == 0) {
                addView(createExtractionEmptyText("本次没有提取到这一类内容"))
                return@apply
            }
            items.forEachIndexed { index: Int, element: JsonElement ->
                if (!element.isJsonObject) return@forEachIndexed
                val itemObject: JsonObject = element.asJsonObject
                val fieldEditors: MutableMap<String, EditText> = mutableMapOf()
                val itemContainer: LinearLayout = createExtractionItemCard()
                itemContainer.addView(createExtractionCardTitle("${index + 1}. ${buildExtractionCardSummary(itemObject, fields)}"))
                fields.forEach { field: ExtractionEditField ->
                    val editor: EditText = createExtractionFieldEditText(field, itemObject.getReadableValue(field.key))
                    fieldEditors[field.key] = editor
                    itemContainer.addView(createExtractionFieldLabel(field.label, field.hint))
                    itemContainer.addView(editor)
                }
                addView(itemContainer)
                editableItems.add(ExtractionEditableItem(categoryKey, itemObject, fieldEditors))
            }
        }
    }

    private fun createExtractionHeroView(summaryText: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING)
            background = createRoundedBackground(EXTRACTION_HERO_BACKGROUND_COLOR, EXTRACTION_CARD_RADIUS)
            addView(TextView(requireContext()).apply {
                text = "预计变更"
                textSize = EXTRACTION_HERO_TITLE_TEXT_SIZE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(EXTRACTION_PRIMARY_TEXT_COLOR))
            })
            addView(createExtractionTipText(summaryText).apply {
                setPadding(0, EXTRACTION_SMALL_SPACING, 0, 0)
            })
        }
    }

    private fun createExtractionSectionCard(title: String, subtitle: String, contentView: View): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING, EXTRACTION_CARD_PADDING)
            background = createRoundedStrokeBackground(EXTRACTION_CARD_BACKGROUND_COLOR, EXTRACTION_CARD_STROKE_COLOR, EXTRACTION_CARD_RADIUS)
            val layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(0, EXTRACTION_SECTION_TOP_MARGIN, 0, 0)
            this.layoutParams = layoutParams
            addView(createExtractionSectionHeader(title, subtitle))
            addView(contentView)
        }
    }

    private fun createExtractionSectionHeader(title: String, subtitle: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, EXTRACTION_SMALL_SPACING)
            addView(TextView(requireContext()).apply {
                text = title
                textSize = EXTRACTION_SECTION_TITLE_TEXT_SIZE
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(EXTRACTION_PRIMARY_TEXT_COLOR))
            })
            addView(TextView(requireContext()).apply {
                text = subtitle
                textSize = EXTRACTION_HELPER_TEXT_SIZE
                setTextColor(Color.parseColor(EXTRACTION_SECONDARY_TEXT_COLOR))
                setPadding(0, EXTRACTION_TINY_SPACING, 0, 0)
            })
        }
    }

    private fun createExtractionItemCard(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(EXTRACTION_ITEM_PADDING, EXTRACTION_ITEM_PADDING, EXTRACTION_ITEM_PADDING, EXTRACTION_ITEM_PADDING)
            background = createRoundedBackground(EXTRACTION_ITEM_BACKGROUND_COLOR, EXTRACTION_ITEM_RADIUS)
            val layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(0, EXTRACTION_SMALL_SPACING, 0, EXTRACTION_SMALL_SPACING)
            this.layoutParams = layoutParams
        }
    }

    private fun createExtractionTipText(textValue: String): TextView {
        return TextView(requireContext()).apply {
            text = textValue
            textSize = EXTRACTION_BODY_TEXT_SIZE
            setTextColor(Color.parseColor(EXTRACTION_SECONDARY_TEXT_COLOR))
            setLineSpacing(EXTRACTION_LINE_SPACING_EXTRA, EXTRACTION_LINE_SPACING_MULTIPLIER)
            setPadding(0, 0, 0, DETAIL_SECTION_PADDING_BOTTOM)
        }
    }

    private fun createExtractionEmptyText(textValue: String): TextView {
        return TextView(requireContext()).apply {
            text = textValue
            textSize = EXTRACTION_BODY_TEXT_SIZE
            setTextColor(Color.parseColor(EXTRACTION_SECONDARY_TEXT_COLOR))
            gravity = android.view.Gravity.CENTER
            setPadding(0, EXTRACTION_EMPTY_VERTICAL_PADDING, 0, EXTRACTION_EMPTY_VERTICAL_PADDING)
            background = createRoundedStrokeBackground(EXTRACTION_EMPTY_BACKGROUND_COLOR, EXTRACTION_CARD_STROKE_COLOR, EXTRACTION_ITEM_RADIUS)
        }
    }

    private fun createExtractionCardTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = EXTRACTION_CARD_TITLE_TEXT_SIZE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(EXTRACTION_ACCENT_COLOR))
            setPadding(0, 0, 0, EXTRACTION_SMALL_SPACING)
        }
    }

    private fun createExtractionFieldLabel(label: String, hint: String): TextView {
        return TextView(requireContext()).apply {
            text = if (hint.isBlank()) label else "$label · $hint"
            textSize = EXTRACTION_HELPER_TEXT_SIZE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(EXTRACTION_PRIMARY_TEXT_COLOR))
            setPadding(0, EXTRACTION_SMALL_SPACING, 0, EXTRACTION_TINY_SPACING)
        }
    }

    private fun createExtractionFieldEditText(field: ExtractionEditField, value: String): EditText {
        return createInputEditText(value).apply {
            hint = field.hint
            minLines = field.minLines
            textSize = EXTRACTION_BODY_TEXT_SIZE
            setTextColor(Color.parseColor(EXTRACTION_PRIMARY_TEXT_COLOR))
            setHintTextColor(Color.parseColor(EXTRACTION_HINT_TEXT_COLOR))
            setPadding(EXTRACTION_INPUT_HORIZONTAL_PADDING, EXTRACTION_INPUT_VERTICAL_PADDING, EXTRACTION_INPUT_HORIZONTAL_PADDING, EXTRACTION_INPUT_VERTICAL_PADDING)
            background = createRoundedStrokeBackground(EXTRACTION_INPUT_BACKGROUND_COLOR, EXTRACTION_INPUT_STROKE_COLOR, EXTRACTION_INPUT_RADIUS)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
        }
    }

    private fun createExtractionFooterText(): TextView {
        return createExtractionTipText("确认保存时会按上方字段重新生成结构化结果；取消则不会写入本次变更。你可以先检查每个字段，再放心保存。").apply {
            setPadding(EXTRACTION_CARD_PADDING, EXTRACTION_SECTION_TOP_MARGIN, EXTRACTION_CARD_PADDING, 0)
        }
    }

    private fun createRoundedBackground(colorString: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(colorString))
        }
    }

    private fun createRoundedStrokeBackground(colorString: String, strokeColorString: String, radius: Float): GradientDrawable {
        return createRoundedBackground(colorString, radius).apply {
            setStroke(EXTRACTION_STROKE_WIDTH, Color.parseColor(strokeColorString))
        }
    }

    private fun collectEditedExtractionJson(rootObject: JsonObject, editableItems: List<ExtractionEditableItem>): String {
        editableItems.forEach { editableItem: ExtractionEditableItem ->
            editableItem.fieldEditors.forEach { (key: String, editor: EditText) ->
                val originalValue: JsonElement? = if (editableItem.itemObject.has(key)) editableItem.itemObject.get(key) else null
                editableItem.itemObject.add(key, buildEditedJsonElement(originalValue, editor.text.toString().trim()))
            }
        }
        return Gson().toJson(rootObject)
    }

    private fun buildEditedJsonElement(originalValue: JsonElement?, editedValue: String): JsonElement {
        if (originalValue != null && originalValue.isJsonArray) {
            val jsonArray: JsonArray = JsonArray()
            editedValue.split("，", ",").map { value: String -> value.trim() }.filter { value: String -> value.isNotBlank() }.forEach { value: String ->
                jsonArray.add(value)
            }
            return jsonArray
        }
        if (originalValue != null && originalValue.isJsonPrimitive && originalValue.asJsonPrimitive.isNumber) {
            return JsonPrimitive(editedValue.toDoubleOrNull() ?: 0.0)
        }
        if (originalValue != null && originalValue.isJsonPrimitive && originalValue.asJsonPrimitive.isBoolean) {
            return JsonPrimitive(editedValue.equals("true", ignoreCase = true) || editedValue == "是")
        }
        return JsonPrimitive(editedValue)
    }

    private fun buildExtractionCardSummary(itemObject: JsonObject, fields: List<ExtractionEditField>): String {
        val primaryField: ExtractionEditField = fields.firstOrNull() ?: return "待编辑条目"
        val primaryText: String = itemObject.getReadableValue(primaryField.key).ifBlank { "待填写${primaryField.label}" }
        return primaryText.take(EXTRACTION_CARD_SUMMARY_MAX_LENGTH)
    }

    private fun buildEventExtractionFields(): List<ExtractionEditField> {
        return listOf(
            ExtractionEditField("title", "标题", "一句话说明这条记忆", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("content", "内容", "写清事实、约定或偏好", STRUCTURED_EVENT_CONTENT_MIN_LINES),
            ExtractionEditField("eventType", "类型", "FACT / COMMITMENT / PREFERENCE 等", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("status", "状态", "ACTIVE / PENDING / RESOLVED 等", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("importanceScore", "重要度", "1-10", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("confidenceScore", "置信度", "0-1", DETAIL_EDIT_MIN_LINES)
        )
    }

    private fun buildNodeExtractionFields(): List<ExtractionEditField> {
        return listOf(
            ExtractionEditField("name", "名称", "人物、地点、物品或概念名", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("entityType", "实体类型", "Person / Location / Item 等", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("aliases", "别名", "多个别名用逗号分隔", DETAIL_EDIT_MIN_LINES)
        )
    }

    private fun buildRelationExtractionFields(): List<ExtractionEditField> {
        return listOf(
            ExtractionEditField("fromName", "起点", "关系发起方", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("relationType", "关系类型", "例如 likes / promised / is_friend_of", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("toName", "终点", "关系指向方", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("changeAction", "变化动作", "ASSERT_ACTIVE / ASSERT_ENDED 等", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("changeReason", "变化原因", "来自对话证据的原因", DETAIL_EDIT_MIN_LINES),
            ExtractionEditField("confidenceScore", "置信度", "0-1", DETAIL_EDIT_MIN_LINES)
        )
    }

    private fun JsonObject.getJsonArrayOrEmpty(name: String): JsonArray {
        return if (has(name) && get(name).isJsonArray) getAsJsonArray(name) else JsonArray()
    }

    private fun JsonObject.getReadableValue(name: String): String {
        val value: JsonElement = if (has(name)) get(name) else return ""
        if (value.isJsonNull) return ""
        if (value.isJsonArray) return value.asJsonArray.joinToString(separator = "，") { item: JsonElement -> item.toReadableText() }
        return value.toReadableText()
    }

    private fun JsonElement.toReadableText(): String {
        if (isJsonNull) return ""
        if (isJsonArray) return asJsonArray.joinToString(separator = "，") { item: JsonElement -> item.toReadableText() }
        if (isJsonObject) return Gson().toJson(this)
        return asString
    }

    override fun onMemoryClick(memory: LongTermMemory) {
        showDetailDialog(memory)
    }

    override fun onDetail(memory: LongTermMemory) {
        showDetailDialog(memory)
    }

    override fun onStructuredEventClick(event: MemoryEvent): Unit {
        showStructuredEventDialog(event)
    }

    override fun onStructuredEventEdit(event: MemoryEvent): Unit {
        showStructuredEventDialog(event)
    }

    override fun onStructuredEventDelete(event: MemoryEvent): Unit {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除结构化事件")
            .setMessage("确定要删除“${event.title}”吗？旧原子事件数据不会被删除。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteStructuredEvent(event) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onStructuredEventInsertAfter(event: MemoryEvent): Unit {
        val insertionTimeBounds: InsertEventTimeBounds = findInsertAfterTimeBounds(event) ?: run {
            Toast.makeText(context, "最后一条没有下方事件，不能在上下两个事件之间插入", Toast.LENGTH_SHORT).show()
            return
        }
        val suggestedEventTime: Long = calculateMiddleEventTime(insertionTimeBounds)
        showStructuredEventDialog(
            event = null,
            initialEventTime = suggestedEventTime,
            insertAnchorTitle = event.title,
            insertionTimeBounds = insertionTimeBounds
        )
    }

    private fun showStructuredEventDialog(
        event: MemoryEvent?,
        initialEventTime: Long = event?.eventTime ?: System.currentTimeMillis(),
        insertAnchorTitle: String? = null,
        insertionTimeBounds: InsertEventTimeBounds? = null
    ) {
        var selectedType: MemoryEventType = event?.eventType ?: MemoryEventType.FACT
        var selectedStatus: MemoryEventStatus = event?.status ?: MemoryEventStatus.ACTIVE
        var selectedEventTime: Long = initialEventTime
        val titleEditText: EditText = createInputEditText(event?.title.orEmpty())
        val contentEditText: EditText = createInputEditText(event?.content.orEmpty()).apply {
            minLines = STRUCTURED_EVENT_CONTENT_MIN_LINES
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val importanceEditText: EditText = createInputEditText((event?.importanceScore ?: DEFAULT_IMPORTANCE_SCORE).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val confidenceEditText: EditText = createInputEditText((event?.confidenceScore ?: DEFAULT_CONFIDENCE_SCORE).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val typeButton: MaterialButton = createPickerButton(selectedType.toDisplayText()) { button: MaterialButton ->
            showEventTypePickerDialog(selectedType) { eventType: MemoryEventType ->
                selectedType = eventType
                button.text = eventType.toDisplayText()
            }
        }
        val statusButton: MaterialButton = createPickerButton(selectedStatus.toDisplayText()) { button: MaterialButton ->
            showEventStatusPickerDialog(selectedStatus) { status: MemoryEventStatus ->
                selectedStatus = status
                button.text = status.toDisplayText()
            }
        }
        val eventTimeButton: MaterialButton = createPickerButton(buildEventTimeButtonText(selectedEventTime, insertionTimeBounds)) { button: MaterialButton ->
            if (insertionTimeBounds == null) {
                pickDateTime("选择事件时间", selectedEventTime) { eventTime: Long ->
                    selectedEventTime = eventTime
                    button.text = buildEventTimeButtonText(eventTime, insertionTimeBounds)
                }
                return@createPickerButton
            }
            showInsertionTimeSliderDialog(insertionTimeBounds, selectedEventTime) { eventTime: Long ->
                selectedEventTime = eventTime
                button.text = buildEventTimeButtonText(eventTime, insertionTimeBounds)
            }
        }
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DETAIL_DIALOG_PADDING, 0, DETAIL_DIALOG_PADDING, 0)
            addView(createSectionTitle("结构化事件类型"))
            addView(typeButton)
            addView(createSectionTitle("结构化事件状态"))
            addView(statusButton)
            addView(createSectionTitle("事件时间"))
            addView(eventTimeButton)
            addView(createSectionTitle("标题"))
            addView(titleEditText)
            addView(createSectionTitle("内容"))
            addView(contentEditText)
            addView(createSectionTitle("重要度 1-10"))
            addView(importanceEditText)
            addView(createSectionTitle("置信度 0-1"))
            addView(confidenceEditText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(buildStructuredEventDialogTitle(event, insertAnchorTitle))
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setPositiveButton("保存") { _, _ ->
                saveStructuredEvent(event, selectedType, selectedStatus, selectedEventTime, insertionTimeBounds, titleEditText, contentEditText, importanceEditText, confidenceEditText)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveStructuredEvent(
        event: MemoryEvent?,
        eventType: MemoryEventType,
        status: MemoryEventStatus,
        eventTime: Long,
        insertionTimeBounds: InsertEventTimeBounds?,
        titleEditText: EditText,
        contentEditText: EditText,
        importanceEditText: EditText,
        confidenceEditText: EditText
    ): Unit {
        if (!isEventTimeWithinBounds(eventTime, insertionTimeBounds)) {
            Toast.makeText(context, buildInsertionTimeLimitText(insertionTimeBounds), Toast.LENGTH_SHORT).show()
            return
        }
        val title: String = titleEditText.text.toString().trim()
        val content: String = contentEditText.text.toString().trim()
        val importanceScore: Int = importanceEditText.text.toString().toIntOrNull()?.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE) ?: DEFAULT_IMPORTANCE_SCORE
        val confidenceScore: Float = confidenceEditText.text.toString().toFloatOrNull()?.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE) ?: DEFAULT_CONFIDENCE_SCORE
        if (event == null) {
            viewModel.createStructuredEventAtTime(eventType, title, content, importanceScore, confidenceScore, eventTime)
        } else {
            viewModel.updateStructuredEvent(event, eventType, title, content, importanceScore, confidenceScore, status, eventTime)
        }
    }

    private fun buildStructuredEventDialogTitle(event: MemoryEvent?, insertAnchorTitle: String?): String {
        if (event != null) return "编辑结构化事件"
        if (insertAnchorTitle != null) return "在“$insertAnchorTitle”后插入事件"
        return "添加结构化事件"
    }

    private fun findInsertAfterTimeBounds(anchorEvent: MemoryEvent): InsertEventTimeBounds? {
        val displayedEvents: List<MemoryEvent> = filterStructuredEvents(currentStructuredEvents, currentSearchQuery)
        val anchorIndex: Int = displayedEvents.indexOfFirst { event: MemoryEvent -> event.id == anchorEvent.id }
        if (anchorIndex < 0) return null
        val lowerEvent: MemoryEvent = displayedEvents.getOrNull(anchorIndex + 1) ?: return null
        val minEventTime: Long = minOf(anchorEvent.eventTime, lowerEvent.eventTime)
        val maxEventTime: Long = maxOf(anchorEvent.eventTime, lowerEvent.eventTime)
        return InsertEventTimeBounds(
            upperEvent = anchorEvent,
            lowerEvent = lowerEvent,
            minTimeInclusive = minEventTime,
            maxTimeInclusive = maxEventTime
        )
    }

    private fun calculateMiddleEventTime(insertionTimeBounds: InsertEventTimeBounds): Long {
        return insertionTimeBounds.minTimeInclusive + ((insertionTimeBounds.maxTimeInclusive - insertionTimeBounds.minTimeInclusive) / 2L)
    }

    private fun isEventTimeWithinBounds(eventTime: Long, insertionTimeBounds: InsertEventTimeBounds?): Boolean {
        if (insertionTimeBounds == null) return true
        return eventTime in insertionTimeBounds.minTimeInclusive..insertionTimeBounds.maxTimeInclusive
    }

    private fun buildEventTimeButtonText(eventTime: Long, insertionTimeBounds: InsertEventTimeBounds?): String {
        if (insertionTimeBounds == null) return formatEventTime(eventTime)
        return "插入位置：${formatInsertionProgress(eventTime, insertionTimeBounds)} · ${formatEventTime(eventTime)}"
    }

    private fun formatInsertionProgress(eventTime: Long, insertionTimeBounds: InsertEventTimeBounds): String {
        val totalDuration: Long = insertionTimeBounds.lowerEvent.eventTime - insertionTimeBounds.upperEvent.eventTime
        if (totalDuration == 0L) return "两个事件之间"
        val rawProgress: Float = ((eventTime - insertionTimeBounds.upperEvent.eventTime).toFloat() / totalDuration.toFloat()) * INSERT_TIME_SLIDER_MAX_VALUE
        val progressPercent: Int = rawProgress.coerceIn(0f, INSERT_TIME_SLIDER_MAX_VALUE).toInt()
        return "${progressPercent}%"
    }

    private fun buildInsertionTimeLimitText(insertionTimeBounds: InsertEventTimeBounds?): String {
        if (insertionTimeBounds == null) return "事件时间不合法"
        return "插入事件时间必须在 ${formatEventTime(insertionTimeBounds.minTimeInclusive)} 至 ${formatEventTime(insertionTimeBounds.maxTimeInclusive)} 之间"
    }

    private fun showInsertionTimeSliderDialog(insertionTimeBounds: InsertEventTimeBounds, selectedEventTime: Long, onSelected: (Long) -> Unit): Unit {
        var sliderEventTime: Long = selectedEventTime.coerceIn(insertionTimeBounds.minTimeInclusive, insertionTimeBounds.maxTimeInclusive)
        val selectedTimeTextView: TextView = TextView(requireContext()).apply {
            text = buildInsertionSliderSelectedTimeText(sliderEventTime)
            setPadding(0, DETAIL_SECTION_PADDING_BOTTOM, 0, DETAIL_SECTION_PADDING_BOTTOM)
        }
        val slider: Slider = Slider(requireContext()).apply {
            valueFrom = 0f
            valueTo = INSERT_TIME_SLIDER_MAX_VALUE
            stepSize = INSERT_TIME_SLIDER_STEP_SIZE
            value = calculateSliderValueFromEventTime(sliderEventTime, insertionTimeBounds)
            addOnChangeListener { _, value, _ ->
                sliderEventTime = calculateEventTimeFromSliderValue(value, insertionTimeBounds)
                selectedTimeTextView.text = buildInsertionSliderSelectedTimeText(sliderEventTime)
            }
        }
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DETAIL_DIALOG_PADDING, 0, DETAIL_DIALOG_PADDING, 0)
            addView(createSectionTitle("上方事件"))
            addView(TextView(requireContext()).apply { text = "${insertionTimeBounds.upperEvent.title}\n${formatEventTime(insertionTimeBounds.upperEvent.eventTime)}" })
            addView(createSectionTitle("拖动滑块选择插入时间"))
            addView(slider)
            addView(selectedTimeTextView)
            addView(createSectionTitle("下方事件"))
            addView(TextView(requireContext()).apply { text = "${insertionTimeBounds.lowerEvent.title}\n${formatEventTime(insertionTimeBounds.lowerEvent.eventTime)}" })
        }
        AlertDialog.Builder(requireContext())
            .setTitle("选择插入时间")
            .setView(container)
            .setPositiveButton("确认") { _, _ -> onSelected(sliderEventTime) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildInsertionSliderSelectedTimeText(eventTime: Long): String {
        return "当前插入时间：${formatEventTime(eventTime)}"
    }

    private fun calculateSliderValueFromEventTime(eventTime: Long, insertionTimeBounds: InsertEventTimeBounds): Float {
        val totalDuration: Long = insertionTimeBounds.lowerEvent.eventTime - insertionTimeBounds.upperEvent.eventTime
        if (totalDuration == 0L) return INSERT_TIME_SLIDER_MAX_VALUE / 2f
        val rawValue: Float = ((eventTime - insertionTimeBounds.upperEvent.eventTime).toFloat() / totalDuration.toFloat()) * INSERT_TIME_SLIDER_MAX_VALUE
        return rawValue.coerceIn(0f, INSERT_TIME_SLIDER_MAX_VALUE)
    }

    private fun calculateEventTimeFromSliderValue(sliderValue: Float, insertionTimeBounds: InsertEventTimeBounds): Long {
        val sliderRatio: Float = sliderValue.coerceIn(0f, INSERT_TIME_SLIDER_MAX_VALUE) / INSERT_TIME_SLIDER_MAX_VALUE
        val eventTimeOffset: Long = ((insertionTimeBounds.lowerEvent.eventTime - insertionTimeBounds.upperEvent.eventTime) * sliderRatio).toLong()
        return (insertionTimeBounds.upperEvent.eventTime + eventTimeOffset).coerceIn(insertionTimeBounds.minTimeInclusive, insertionTimeBounds.maxTimeInclusive)
    }

    private fun formatEventTime(timestamp: Long): String {
        val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        return dateFormat.format(Date(timestamp))
    }

    private fun createInputEditText(value: String): EditText {
        return EditText(requireContext()).apply {
            setText(value)
            setSingleLine(false)
        }
    }

    private fun createPickerButton(text: String, onClick: (MaterialButton) -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick(this) }
        }
    }

    private fun showEventTypePickerDialog(selectedType: MemoryEventType, onSelected: (MemoryEventType) -> Unit): Unit {
        val eventTypes: Array<MemoryEventType> = MemoryEventType.values()
        val labels: Array<String> = eventTypes.map { eventType: MemoryEventType -> eventType.toDisplayText() }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择结构化事件类型")
            .setSingleChoiceItems(labels, eventTypes.indexOf(selectedType)) { dialog, index ->
                onSelected(eventTypes[index])
                dialog.dismiss()
            }
            .show()
    }

    private fun showEventStatusPickerDialog(selectedStatus: MemoryEventStatus, onSelected: (MemoryEventStatus) -> Unit): Unit {
        val statuses: Array<MemoryEventStatus> = MemoryEventStatus.values()
        val labels: Array<String> = statuses.map { status: MemoryEventStatus -> status.toDisplayText() }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择结构化事件状态")
            .setSingleChoiceItems(labels, statuses.indexOf(selectedStatus)) { dialog, index ->
                onSelected(statuses[index])
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditDialog(memory: LongTermMemory?) {
        if (memory == null) {
            Toast.makeText(context, "原子事件已改为只读纪念记录，不再允许新增", Toast.LENGTH_SHORT).show()
            return
        }
        showDetailDialog(memory)
    }

    private fun showDetailDialog(memory: LongTermMemory) {
        val editText: EditText = createMemoryEditText(memory).apply {
            isEnabled = false
        }
        val vectorInfoText: TextView = createVectorInfoText().apply {
            text = "原子事件已改为只读纪念记录，不再参与新增、编辑、删除、手动向量化和召回。"
        }
        val contentView: ScrollView = createDetailContentView(editText, vectorInfoText)
        AlertDialog.Builder(requireContext())
            .setTitle("原子事件纪念记录")
            .setView(contentView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun createMemoryEditText(memory: LongTermMemory): EditText {
        return EditText(requireContext()).apply {
            setText(memory.memoryText)
            setHint("输入原子事件内容...")
            minLines = DETAIL_EDIT_MIN_LINES
        }
    }

    private fun createVectorInfoText(): TextView {
        return TextView(requireContext()).apply {
            text = "正在读取向量信息..."
            setPadding(0, DETAIL_SECTION_PADDING_TOP, 0, 0)
        }
    }

    private fun createDetailContentView(editText: EditText, vectorInfoText: TextView): ScrollView {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DETAIL_DIALOG_PADDING, 0, DETAIL_DIALOG_PADDING, 0)
            addView(createSectionTitle("原子事件内容"))
            addView(editText)
            addView(createSectionTitle("向量相关信息"))
            addView(vectorInfoText)
        }
        return ScrollView(requireContext()).apply {
            addView(container)
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = DETAIL_TITLE_TEXT_SIZE
            setPadding(0, DETAIL_SECTION_PADDING_TOP, 0, DETAIL_SECTION_PADDING_BOTTOM)
        }
    }

    private fun showSummaryDialog(summary: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("结构化事件已保存")
            .setMessage(summary)
            .setPositiveButton("查看结构化事件") { _, _ ->
                viewModel.confirmAndUpdateTimestamp()
                switchDisplayMode(MemoryCenterDisplayMode.STRUCTURED_EVENTS)
            }
            .setNegativeButton("关闭") { _, _ ->
                viewModel.confirmAndUpdateTimestamp()
            }
            .show()
    }

    /**
     * 显示结构化事件提取选项对话框
     */
    private fun showSummarizeOptionsDialog() {
        val options = arrayOf(
            "自动提取（从上次位置开始：${buildLastExtractionPositionText(lastExtractionPositionTimestamp)}，距离上次提取已有${newMessageCountSinceLastExtraction}条新消息）",
            "手动提取（自定义起止时间）"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("选择结构化事件提取范围")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 从lastSummaryTimestamp开始
                        viewModel.summarizeChatHistory()
                        Toast.makeText(context, "正在请求AI提取结构化事件...", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // 自定义时间范围
                        showDateTimePicker()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildLastExtractionPositionText(timestamp: Long?): String {
        return timestamp?.let { positionTimestamp: Long -> formatEventTime(positionTimestamp) } ?: "暂无，将使用最近若干条"
    }

    private fun pickDateTime(title: String, initialTimestamp: Long = System.currentTimeMillis(), onDateTimePicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initialTimestamp }
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val timePickerDialog = TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onDateTimePicked(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )
                timePickerDialog.setTitle(title)
                timePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.setTitle(title)
        datePickerDialog.show()
    }

    private fun showDateTimePicker() {
        pickDateTime("选择开始时间") { startTime ->
            pickDateTime("选择结束时间") { endTime ->
                if (endTime <= startTime) {
                    Toast.makeText(context, "结束时间必须在开始时间之后", Toast.LENGTH_SHORT).show()
                    return@pickDateTime
                }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                val formattedStartDate = dateFormat.format(Date(startTime))
                val formattedEndDate = dateFormat.format(Date(endTime))
                AlertDialog.Builder(requireContext())
                    .setTitle("确认提取范围")
                    .setMessage("将从 $formattedStartDate 到 $formattedEndDate 的对话记录中提取结构化事件。")
                    .setPositiveButton("确认") { _, _ ->
                        viewModel.summarizeChatHistory(customStartTimestamp = startTime, customEndTimestamp = endTime)
                        Toast.makeText(context, "正在请求AI提取结构化事件...", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ExtractionEditField(
        val key: String,
        val label: String,
        val hint: String,
        val minLines: Int
    )

    private data class ExtractionEditableItem(
        val categoryKey: String,
        val itemObject: JsonObject,
        val fieldEditors: Map<String, EditText>
    )

    private data class InsertEventTimeBounds(
        val upperEvent: MemoryEvent,
        val lowerEvent: MemoryEvent,
        val minTimeInclusive: Long,
        val maxTimeInclusive: Long
    )

    private enum class MemoryCenterDisplayMode {
        STRUCTURED_EVENTS,
        LEGACY_MEMORIES
    }

    companion object {
        private const val ARG_CONTACT_ID = "contactId"
        private const val DETAIL_DIALOG_PADDING: Int = 16
        private const val DETAIL_EDIT_MIN_LINES: Int = 4
        private const val DETAIL_SECTION_PADDING_TOP: Int = 16
        private const val DETAIL_SECTION_PADDING_BOTTOM: Int = 8
        private const val DETAIL_TITLE_TEXT_SIZE: Float = 16f
        private const val STRUCTURED_EVENT_CONTENT_MIN_LINES: Int = 4
        private const val EXTRACTION_DIALOG_SIDE_PADDING: Int = 20
        private const val EXTRACTION_DIALOG_TOP_PADDING: Int = 8
        private const val EXTRACTION_DIALOG_BOTTOM_PADDING: Int = 18
        private const val EXTRACTION_CARD_PADDING: Int = 18
        private const val EXTRACTION_ITEM_PADDING: Int = 14
        private const val EXTRACTION_SECTION_TOP_MARGIN: Int = 14
        private const val EXTRACTION_SMALL_SPACING: Int = 8
        private const val EXTRACTION_TINY_SPACING: Int = 4
        private const val EXTRACTION_EMPTY_VERTICAL_PADDING: Int = 20
        private const val EXTRACTION_INPUT_HORIZONTAL_PADDING: Int = 12
        private const val EXTRACTION_INPUT_VERTICAL_PADDING: Int = 8
        private const val EXTRACTION_STROKE_WIDTH: Int = 1
        private const val EXTRACTION_CARD_RADIUS: Float = 28f
        private const val EXTRACTION_ITEM_RADIUS: Float = 22f
        private const val EXTRACTION_INPUT_RADIUS: Float = 18f
        private const val EXTRACTION_HERO_TITLE_TEXT_SIZE: Float = 18f
        private const val EXTRACTION_SECTION_TITLE_TEXT_SIZE: Float = 16f
        private const val EXTRACTION_CARD_TITLE_TEXT_SIZE: Float = 14f
        private const val EXTRACTION_BODY_TEXT_SIZE: Float = 14f
        private const val EXTRACTION_HELPER_TEXT_SIZE: Float = 12f
        private const val EXTRACTION_LINE_SPACING_EXTRA: Float = 4f
        private const val EXTRACTION_LINE_SPACING_MULTIPLIER: Float = 1.0f
        private const val EXTRACTION_ACCENT_COLOR: String = "#5E6AD2"
        private const val EXTRACTION_PRIMARY_TEXT_COLOR: String = "#1F2937"
        private const val EXTRACTION_SECONDARY_TEXT_COLOR: String = "#6B7280"
        private const val EXTRACTION_HINT_TEXT_COLOR: String = "#9CA3AF"
        private const val EXTRACTION_HERO_BACKGROUND_COLOR: String = "#EEF2FF"
        private const val EXTRACTION_CARD_BACKGROUND_COLOR: String = "#FFFFFF"
        private const val EXTRACTION_ITEM_BACKGROUND_COLOR: String = "#F8FAFC"
        private const val EXTRACTION_EMPTY_BACKGROUND_COLOR: String = "#FAFAFA"
        private const val EXTRACTION_CARD_STROKE_COLOR: String = "#E5E7EB"
        private const val EXTRACTION_INPUT_BACKGROUND_COLOR: String = "#FFFFFF"
        private const val EXTRACTION_INPUT_STROKE_COLOR: String = "#D1D5DB"
        private const val EXTRACTION_CARD_SUMMARY_MAX_LENGTH: Int = 24
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.9f
        private const val INSERT_TIME_SLIDER_MAX_VALUE: Float = 100f
        private const val INSERT_TIME_SLIDER_STEP_SIZE: Float = 1f

        fun newInstance(contactId: String): LongTermMemoryFragment {
            return LongTermMemoryFragment().apply {
                arguments = Bundle().apply { putString(ARG_CONTACT_ID, contactId) }
            }
        }
    }
}

private fun MemoryEventType.toDisplayText(): String {
    return when (this) {
        MemoryEventType.COMMITMENT -> "承诺"
        MemoryEventType.PREFERENCE -> "偏好"
        MemoryEventType.PROHIBITION -> "禁忌"
        MemoryEventType.ANNIVERSARY -> "纪念日"
        MemoryEventType.RELATIONSHIP -> "关系"
        MemoryEventType.FACT -> "事实"
        MemoryEventType.OPINION -> "观点"
        MemoryEventType.OTHER -> "其他"
    }
}

private fun MemoryEventStatus.toDisplayText(): String {
    return when (this) {
        MemoryEventStatus.ACTIVE -> "活跃"
        MemoryEventStatus.PENDING -> "待确认"
        MemoryEventStatus.RESOLVED -> "已完成"
        MemoryEventStatus.CANCELLED -> "已取消"
        MemoryEventStatus.EXPIRED -> "已过期"
        MemoryEventStatus.SUPERSEDED -> "已替代"
        MemoryEventStatus.ARCHIVED -> "已归档"
    }
}