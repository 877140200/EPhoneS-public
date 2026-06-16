package com.susking.ephone_s.eventgraph.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipChangeAction
import com.susking.ephone_s.eventgraph.databinding.BottomSheetEventGraphDetailBinding
import com.susking.ephone_s.eventgraph.databinding.FragmentEventGraphBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 事件图谱可视化页面。
 */
@AndroidEntryPoint
class EventGraphFragment : Fragment() {
    private var _binding: FragmentEventGraphBinding? = null
    private val binding: FragmentEventGraphBinding get() = _binding!!
    private val viewModel: EventGraphViewModel by viewModels()
    private lateinit var adapter: EventGraphAdapter
    private var displayMode: EventGraphDisplayMode = EventGraphDisplayMode.ALL
    private var allFilterMode: EventGraphAllFilterMode = EventGraphAllFilterMode.ALL_ITEMS
    private var activeDetailId: String? = null
    private var searchQuery: String = ""
    private var selectedGraphEdgeId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventGraphBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupGraphModeToggle()
        setupAllFilterToggle()
        setupSearchAndAddActions()
        setupGraphCanvas()
        observeUiState()
    }

    private fun setupToolbar(): Unit {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView(): Unit {
        adapter = EventGraphAdapter(
            onEditItem = { item: EventGraphItem -> showEditDialog(item) },
            onDeleteItem = { item: EventGraphItem -> showDeleteDialog(item) },
            onOpenDetail = { item: EventGraphItem -> viewModel.selectItemDetail(item) }
        )
        binding.rvGraphItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGraphItems.adapter = adapter
    }

    private fun setupGraphModeToggle(): Unit {
        binding.toggleGraphMode.addOnButtonCheckedListener { _, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener
            displayMode = when (checkedId) {
                binding.btnRelationshipMode.id -> EventGraphDisplayMode.RELATIONSHIPS
                else -> EventGraphDisplayMode.ALL
            }
            clearSelectedGraphEdge()
            renderItems(viewModel.uiState.value)
            renderGraph(viewModel.uiState.value)
            updateContentVisibility(viewModel.uiState.value)
        }
    }

    private fun setupAllFilterToggle(): Unit {
        binding.toggleAllFilter.addOnButtonCheckedListener { _, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener
            allFilterMode = when (checkedId) {
                binding.btnFilterNodes.id -> EventGraphAllFilterMode.NODES
                binding.btnFilterRelations.id -> EventGraphAllFilterMode.RELATIONS
                else -> EventGraphAllFilterMode.ALL_ITEMS
            }
            renderItems(viewModel.uiState.value)
            updateContentVisibility(viewModel.uiState.value)
        }
    }

    private fun setupSearchAndAddActions(): Unit {
        binding.etSearchGraph.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int): Unit = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int): Unit {
                searchQuery = text?.toString().orEmpty().trim()
                renderItems(viewModel.uiState.value)
                updateContentVisibility(viewModel.uiState.value)
            }

            override fun afterTextChanged(text: Editable?): Unit = Unit
        })
        binding.btnAddGraphItem.setOnClickListener { showAddTypeDialog() }
        binding.btnViewGraphEdgeDetail.setOnClickListener {
            selectedGraphEdgeId?.let { edgeId: String -> viewModel.selectEdgeDetail(edgeId) }
        }
    }

    private fun setupGraphCanvas(): Unit {
        binding.viewGraphCanvas.setOnGraphNodeClickListener { nodeId: String ->
            clearSelectedGraphEdge()
            viewModel.selectNodeDetail(nodeId)
        }
        binding.viewGraphCanvas.setOnGraphEdgeClickListener { edgeId: String ->
            selectedGraphEdgeId = edgeId
            updateGraphEdgeDetailButtonVisibility()
        }
        binding.viewGraphCanvas.setOnGraphSelectionClearListener {
            clearSelectedGraphEdge()
        }
    }

    private fun observeUiState(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state: EventGraphUiState ->
                binding.tvOverviewMeta.text = "节点：${state.nodes.size} · 关系声明：${state.relations.size} · 关系链：${state.relationshipItems.size}"
                renderItems(state)
                renderGraph(state)
                updateContentVisibility(state)
                showSelectedDetailIfNeeded(state.selectedDetail)
            }
        }
    }

    private fun renderItems(state: EventGraphUiState): Unit {
        val visibleItems: List<EventGraphItem> = when (displayMode) {
            EventGraphDisplayMode.ALL -> filterSearchItems(filterAllItems(state.items))
            EventGraphDisplayMode.RELATIONSHIPS -> emptyList()
        }
        adapter.submitList(visibleItems)
    }

    private fun filterAllItems(items: List<EventGraphItem>): List<EventGraphItem> {
        return when (allFilterMode) {
            EventGraphAllFilterMode.ALL_ITEMS -> items
            EventGraphAllFilterMode.NODES -> items.filter { item: EventGraphItem -> item.itemType == EventGraphItemType.NODE }
            EventGraphAllFilterMode.RELATIONS -> items.filter { item: EventGraphItem -> item.itemType == EventGraphItemType.RELATION }
        }
    }

    private fun filterSearchItems(items: List<EventGraphItem>): List<EventGraphItem> {
        if (searchQuery.isBlank()) return items
        val query: String = searchQuery.lowercase()
        return items.filter { item: EventGraphItem ->
            listOf(item.type, item.title, item.content, item.meta).any { value: String -> value.lowercase().contains(query) }
        }
    }

    private fun renderGraph(state: EventGraphUiState): Unit {
        val visibleEdges: List<EventGraphEdgeItem> = when (displayMode) {
            EventGraphDisplayMode.RELATIONSHIPS -> state.relationshipGraphEdges
            EventGraphDisplayMode.ALL -> emptyList()
        }
        binding.viewGraphCanvas.setGraphData(state.graphNodes, visibleEdges)
        if (visibleEdges.none { edge: EventGraphEdgeItem -> edge.id == selectedGraphEdgeId }) {
            selectedGraphEdgeId = null
        }
        updateGraphEdgeDetailButtonVisibility()
    }

    private fun updateContentVisibility(state: EventGraphUiState): Unit {
        val visibleItems: List<EventGraphItem> = filterSearchItems(filterAllItems(state.items))
        val hasListItems: Boolean = displayMode == EventGraphDisplayMode.ALL && visibleItems.isNotEmpty()
        val hasVisibleGraph: Boolean = when (displayMode) {
            EventGraphDisplayMode.RELATIONSHIPS -> state.graphNodes.isNotEmpty() && state.relationshipGraphEdges.isNotEmpty()
            EventGraphDisplayMode.ALL -> false
        }
        binding.rvGraphItems.visibility = if (hasListItems) View.VISIBLE else View.GONE
        binding.viewGraphCanvas.visibility = if (hasVisibleGraph) View.VISIBLE else View.GONE
        binding.tvEmptyGraph.text = getEmptyText(state, visibleItems)
        binding.tvEmptyGraph.visibility = if (hasListItems || hasVisibleGraph) View.GONE else View.VISIBLE
        binding.toggleAllFilter.visibility = if (displayMode == EventGraphDisplayMode.ALL) View.VISIBLE else View.GONE
        updateGraphEdgeDetailButtonVisibility()
    }

    private fun clearSelectedGraphEdge(): Unit {
        selectedGraphEdgeId = null
        updateGraphEdgeDetailButtonVisibility()
    }

    private fun updateGraphEdgeDetailButtonVisibility(): Unit {
        val hasSelectedEdge: Boolean = selectedGraphEdgeId != null
        val canShowButton: Boolean = displayMode == EventGraphDisplayMode.RELATIONSHIPS && binding.viewGraphCanvas.visibility == View.VISIBLE && hasSelectedEdge
        binding.btnViewGraphEdgeDetail.visibility = if (canShowButton) View.VISIBLE else View.GONE
    }

    private fun getEmptyText(state: EventGraphUiState, visibleItems: List<EventGraphItem>): String {
        if (displayMode != EventGraphDisplayMode.ALL) return "暂无关系变化数据\n等待关系变化记录生成后会显示在这里"
        if (searchQuery.isNotBlank() && filterAllItems(state.items).isNotEmpty() && visibleItems.isEmpty()) return "没有匹配的图谱项"
        return "暂无图谱数据\n等待结构化抽取生成节点和关系后会显示在这里"
    }

    private fun showSelectedDetailIfNeeded(detail: EventGraphDetail?): Unit {
        if (detail == null || activeDetailId == detail.id) return
        activeDetailId = detail.id
        showDetailBottomSheet(detail)
    }

    private fun showDetailBottomSheet(detail: EventGraphDetail): Unit {
        val detailBinding: BottomSheetEventGraphDetailBinding = BottomSheetEventGraphDetailBinding.inflate(layoutInflater)
        detailBinding.tvDetailType.text = detail.type
        detailBinding.tvDetailTitle.text = detail.title
        detailBinding.tvDetailContent.text = detail.content
        detail.rows.forEach { row: EventGraphDetailRow ->
            detailBinding.containerDetailRows.addView(createDetailRowView(row))
        }
        BottomSheetDialog(requireContext()).apply {
            setContentView(detailBinding.root)
            setOnDismissListener {
                activeDetailId = null
                viewModel.clearSelectedDetail()
            }
            show()
        }
    }

    private fun createDetailRowView(row: EventGraphDetailRow): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, DETAIL_ROW_VERTICAL_PADDING, 0, DETAIL_ROW_VERTICAL_PADDING)
            addView(createDetailLabelView(row.label))
            addView(createDetailValueView(row.value))
        }
    }

    private fun createDetailLabelView(label: String): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = DETAIL_LABEL_TEXT_SIZE_SP
            setTextColor(requireContext().getColor(android.R.color.darker_gray))
        }
    }

    private fun createDetailValueView(value: String): TextView {
        return TextView(requireContext()).apply {
            text = value
            textSize = DETAIL_VALUE_TEXT_SIZE_SP
            setTextIsSelectable(true)
        }
    }

    private fun showEditDialog(item: EventGraphItem): Unit {
        when (item.itemType) {
            EventGraphItemType.NODE -> showEditNodeDialog(item.id)
            EventGraphItemType.RELATION -> showEditRelationDialog(item.id)
            EventGraphItemType.RELATION_CHAIN -> viewModel.selectItemDetail(item)
        }
    }

    private fun showAddTypeDialog(): Unit {
        val labels: Array<String> = arrayOf("节点", "关系")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("增加图谱项")
            .setItems(labels) { _, which: Int ->
                when (which) {
                    ADD_NODE_INDEX -> showAddNodeDialog()
                    ADD_RELATION_INDEX -> showAddRelationDialog()
                }
            }
            .show()
    }

    private fun showAddNodeDialog(): Unit {
        var selectedEntityType: String = DEFAULT_ENTITY_TYPE
        val contactInput: TextInputEditText = createInputEditText(guessDefaultContactId())
        val nameInput: TextInputEditText = createInputEditText("")
        val aliasesInput: TextInputEditText = createInputEditText("")
        val typeButton: MaterialButton = createEntityTypeButton(selectedEntityType) { button: MaterialButton ->
            showEntityTypePickerDialog(selectedEntityType) { entityType: String ->
                selectedEntityType = entityType
                button.text = "实体类型：$entityType"
            }
        }
        val container: LinearLayout = createDialogContainer().apply {
            addView(createTextInputLayout("联系人 ID", contactInput))
            addView(createTextInputLayout("节点名称", nameInput))
            addView(typeButton)
            addView(createTextInputLayout("别名（可选）", aliasesInput))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增节点")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                viewModel.createNode(
                    contactId = contactInput.text?.toString().orEmpty(),
                    entityType = selectedEntityType,
                    name = nameInput.text?.toString().orEmpty(),
                    aliases = aliasesInput.text?.toString()
                )
            }
            .show()
    }

    private fun showAddRelationDialog(): Unit {
        val nodes: List<MemoryGraphNode> = viewModel.uiState.value.nodes
        if (nodes.size < MIN_RELATION_NODE_COUNT) {
            Toast.makeText(requireContext(), "至少需要两个节点才能新增关系", Toast.LENGTH_SHORT).show()
            return
        }
        var selectedFromNode: MemoryGraphNode = nodes.first()
        var selectedToNode: MemoryGraphNode = nodes.drop(1).firstOrNull() ?: nodes.first()
        val contactInput: TextInputEditText = createInputEditText(selectedFromNode.contactId)
        val relationTypeInput: TextInputEditText = createInputEditText("")
        val confidenceInput: TextInputEditText = createInputEditText(DEFAULT_CONFIDENCE_TEXT)
        val reasonInput: TextInputEditText = createInputEditText("")
        val fromButton: MaterialButton = createNodePickerButton("起始节点：${selectedFromNode.name}") { button: MaterialButton ->
            showNodePickerDialog("选择起始节点", nodes) { node: MemoryGraphNode ->
                selectedFromNode = node
                button.text = "起始节点：${node.name}"
                contactInput.setText(node.contactId)
            }
        }
        val toButton: MaterialButton = createNodePickerButton("目标节点：${selectedToNode.name}") { button: MaterialButton ->
            showNodePickerDialog("选择目标节点", nodes) { node: MemoryGraphNode ->
                selectedToNode = node
                button.text = "目标节点：${node.name}"
            }
        }
        val container: LinearLayout = createDialogContainer().apply {
            addView(createTextInputLayout("联系人 ID", contactInput))
            addView(fromButton)
            addView(toButton)
            addView(createTextInputLayout("关系类型", relationTypeInput))
            addView(createTextInputLayout("置信度 0-1", confidenceInput))
            addView(createTextInputLayout("变化原因（可选）", reasonInput))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增关系")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (selectedFromNode.id == selectedToNode.id) {
                    Toast.makeText(requireContext(), "起始节点和目标节点不能相同", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createRelation(
                    contactId = contactInput.text?.toString().orEmpty(),
                    fromNodeId = selectedFromNode.id,
                    toNodeId = selectedToNode.id,
                    relationType = relationTypeInput.text?.toString().orEmpty(),
                    changeAction = RelationshipChangeAction.ASSERT_ACTIVE,
                    confidenceScore = parseFloatOrDefault(confidenceInput.text?.toString().orEmpty(), DEFAULT_CONFIDENCE_SCORE),
                    changeReason = reasonInput.text?.toString()
                )
            }
            .show()
    }

    private fun showEditNodeDialog(nodeId: String): Unit {
        val node: MemoryGraphNode = viewModel.uiState.value.nodes.firstOrNull { currentNode: MemoryGraphNode -> currentNode.id == nodeId } ?: return
        var selectedEntityType: String = normalizeEntityType(node.entityType)
        val contactInput: TextInputEditText = createInputEditText(node.contactId)
        val nameInput: TextInputEditText = createInputEditText(node.name)
        val aliasesInput: TextInputEditText = createInputEditText(node.aliases.orEmpty())
        val typeButton: MaterialButton = createEntityTypeButton(selectedEntityType) { button: MaterialButton ->
            showEntityTypePickerDialog(selectedEntityType) { entityType: String ->
                selectedEntityType = entityType
                button.text = "实体类型：$entityType"
            }
        }
        val container: LinearLayout = createDialogContainer().apply {
            addView(createTextInputLayout("联系人 ID", contactInput))
            addView(createTextInputLayout("节点名称", nameInput))
            addView(typeButton)
            addView(createTextInputLayout("别名（可选）", aliasesInput))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改节点")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                viewModel.updateNode(
                    id = node.id,
                    contactId = contactInput.text?.toString().orEmpty(),
                    entityType = selectedEntityType,
                    name = nameInput.text?.toString().orEmpty(),
                    aliases = aliasesInput.text?.toString()
                )
            }
            .show()
    }

    private fun showEditRelationDialog(relationId: String): Unit {
        val state: EventGraphUiState = viewModel.uiState.value
        val relation: MemoryGraphRelation = state.relations.firstOrNull { currentRelation: MemoryGraphRelation -> currentRelation.id == relationId } ?: return
        val nodes: List<MemoryGraphNode> = state.nodes
        if (nodes.size < MIN_RELATION_NODE_COUNT) {
            Toast.makeText(requireContext(), "至少需要两个节点才能修改关系节点", Toast.LENGTH_SHORT).show()
            return
        }
        var selectedFromNode: MemoryGraphNode = nodes.firstOrNull { node: MemoryGraphNode -> node.id == relation.fromNodeId } ?: nodes.first()
        var selectedToNode: MemoryGraphNode = nodes.firstOrNull { node: MemoryGraphNode -> node.id == relation.toNodeId } ?: nodes.drop(1).firstOrNull() ?: nodes.first()
        var selectedAction: RelationshipChangeAction = relation.changeAction
        var selectedStatus: MemoryEventStatus = relation.status
        val contactInput: TextInputEditText = createInputEditText(relation.contactId)
        val relationTypeInput: TextInputEditText = createInputEditText(relation.relationType)
        val confidenceInput: TextInputEditText = createInputEditText(relation.confidenceScore.toString())
        val reasonInput: TextInputEditText = createInputEditText(relation.changeReason.orEmpty())
        val statusReasonInput: TextInputEditText = createInputEditText(relation.statusReason.orEmpty())
        val fromButton: MaterialButton = createNodePickerButton("起始节点：${selectedFromNode.name}") { button: MaterialButton ->
            showNodePickerDialog("选择起始节点", nodes) { node: MemoryGraphNode ->
                selectedFromNode = node
                button.text = "起始节点：${node.name}"
                contactInput.setText(node.contactId)
            }
        }
        val toButton: MaterialButton = createNodePickerButton("目标节点：${selectedToNode.name}") { button: MaterialButton ->
            showNodePickerDialog("选择目标节点", nodes) { node: MemoryGraphNode ->
                selectedToNode = node
                button.text = "目标节点：${node.name}"
            }
        }
        val actionButton: MaterialButton = createRelationshipActionButton(selectedAction) { button: MaterialButton ->
            showRelationshipActionPickerDialog(selectedAction) { action: RelationshipChangeAction ->
                selectedAction = action
                button.text = "变化动作：${action.name}"
            }
        }
        val statusButton: MaterialButton = createMemoryEventStatusButton(selectedStatus) { button: MaterialButton ->
            showMemoryEventStatusPickerDialog(selectedStatus) { status: MemoryEventStatus ->
                selectedStatus = status
                button.text = "状态：${status.name}"
            }
        }
        val container: LinearLayout = createDialogContainer().apply {
            addView(createTextInputLayout("联系人 ID", contactInput))
            addView(fromButton)
            addView(toButton)
            addView(createTextInputLayout("关系类型", relationTypeInput))
            addView(createTextInputLayout("置信度 0-1", confidenceInput))
            addView(actionButton)
            addView(createTextInputLayout("变化原因（可选）", reasonInput))
            addView(statusButton)
            addView(createTextInputLayout("状态原因（可选）", statusReasonInput))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改关系")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (selectedFromNode.id == selectedToNode.id) {
                    Toast.makeText(requireContext(), "起始节点和目标节点不能相同", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateRelation(
                    id = relation.id,
                    contactId = contactInput.text?.toString().orEmpty(),
                    fromNodeId = selectedFromNode.id,
                    toNodeId = selectedToNode.id,
                    relationType = relationTypeInput.text?.toString().orEmpty(),
                    changeAction = selectedAction,
                    confidenceScore = parseFloatOrDefault(confidenceInput.text?.toString().orEmpty(), DEFAULT_CONFIDENCE_SCORE),
                    changeReason = reasonInput.text?.toString(),
                    status = selectedStatus,
                    statusReason = statusReasonInput.text?.toString()
                )
            }
            .show()
    }

    private fun showDeleteDialog(item: EventGraphItem): Unit {
        if (item.itemType == EventGraphItemType.RELATION_CHAIN) {
            viewModel.selectItemDetail(item)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除${item.type}")
            .setMessage("确定要删除“${item.title}”吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> viewModel.deleteItem(item) }
            .show()
    }

    private fun createDialogContainer(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding: Int = (DIALOG_HORIZONTAL_PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
    }

    private fun createNodePickerButton(text: String, onClick: (MaterialButton) -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            this.text = text
            setOnClickListener { onClick(this) }
        }
    }

    private fun createEntityTypeButton(entityType: String, onClick: (MaterialButton) -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = "实体类型：$entityType"
            setOnClickListener { onClick(this) }
        }
    }

    private fun createRelationshipActionButton(action: RelationshipChangeAction, onClick: (MaterialButton) -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = "变化动作：${action.name}"
            setOnClickListener { onClick(this) }
        }
    }

    private fun createMemoryEventStatusButton(status: MemoryEventStatus, onClick: (MaterialButton) -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = "状态：${status.name}"
            setOnClickListener { onClick(this) }
        }
    }

    private fun showNodePickerDialog(title: String, nodes: List<MemoryGraphNode>, onSelected: (MemoryGraphNode) -> Unit): Unit {
        val labels: Array<String> = nodes.map { node: MemoryGraphNode -> "${node.name}（${node.entityType}）" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(labels) { _, which: Int -> onSelected(nodes[which]) }
            .show()
    }

    private fun showEntityTypePickerDialog(selectedType: String, onSelected: (String) -> Unit): Unit {
        val types: Array<String> = ENTITY_TYPE_OPTIONS
        val labels: Array<String> = types.map { type: String -> getEntityTypeLabel(type) }.toTypedArray()
        val checkedIndex: Int = types.indexOf(normalizeEntityType(selectedType)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择实体类型")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which: Int ->
                onSelected(types[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showRelationshipActionPickerDialog(selectedAction: RelationshipChangeAction, onSelected: (RelationshipChangeAction) -> Unit): Unit {
        val actions: Array<RelationshipChangeAction> = RelationshipChangeAction.values()
        val labels: Array<String> = actions.map { action: RelationshipChangeAction -> action.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择变化动作")
            .setSingleChoiceItems(labels, actions.indexOf(selectedAction)) { dialog, which: Int ->
                onSelected(actions[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showMemoryEventStatusPickerDialog(selectedStatus: MemoryEventStatus, onSelected: (MemoryEventStatus) -> Unit): Unit {
        val statuses: Array<MemoryEventStatus> = MemoryEventStatus.values()
        val labels: Array<String> = statuses.map { status: MemoryEventStatus -> status.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择状态")
            .setSingleChoiceItems(labels, statuses.indexOf(selectedStatus)) { dialog, which: Int ->
                onSelected(statuses[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun guessDefaultContactId(): String {
        val state: EventGraphUiState = viewModel.uiState.value
        return state.nodes.firstOrNull()?.contactId ?: state.relations.firstOrNull()?.contactId ?: ""
    }

    private fun parseFloatOrDefault(value: String, defaultValue: Float): Float {
        return value.trim().toFloatOrNull() ?: defaultValue
    }

    private fun createTextInputLayout(label: String, editText: TextInputEditText): TextInputLayout {
        return TextInputLayout(requireContext()).apply {
            hint = label
            addView(editText)
        }
    }

    private fun createInputEditText(value: String): TextInputEditText {
        return TextInputEditText(requireContext()).apply {
            setText(value)
        }
    }

    private fun normalizeEntityType(entityType: String): String {
        val trimmedType: String = entityType.trim()
        return ENTITY_TYPE_OPTIONS.firstOrNull { option: String -> option.equals(trimmedType, ignoreCase = true) } ?: DEFAULT_ENTITY_TYPE
    }

    private fun getEntityTypeLabel(entityType: String): String {
        return when (entityType) {
            "Person" -> "人物"
            "Location" -> "地点"
            "Organization" -> "组织"
            "Item" -> "物品"
            "Concept" -> "概念"
            "Pet" -> "宠物"
            "Account" -> "账号"
            "Other" -> "其他"
            else -> entityType
        }
    }

    private enum class EventGraphDisplayMode {
        ALL,
        RELATIONSHIPS
    }

    private enum class EventGraphAllFilterMode {
        ALL_ITEMS,
        NODES,
        RELATIONS
    }

    override fun onDestroyView(): Unit {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DIALOG_HORIZONTAL_PADDING_DP: Int = 24
        private const val DETAIL_ROW_VERTICAL_PADDING: Int = 10
        private const val DETAIL_LABEL_TEXT_SIZE_SP: Float = 12f
        private const val DETAIL_VALUE_TEXT_SIZE_SP: Float = 14f
        private const val ADD_NODE_INDEX: Int = 0
        private const val ADD_RELATION_INDEX: Int = 1
        private const val MIN_RELATION_NODE_COUNT: Int = 2
        private const val DEFAULT_ENTITY_TYPE: String = "Person"
        private val ENTITY_TYPE_OPTIONS: Array<String> = arrayOf("Person", "Location", "Organization", "Item", "Concept", "Pet", "Account", "Other")
        private const val DEFAULT_CONFIDENCE_TEXT: String = "1.0"
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 1f

        fun newInstance(): EventGraphFragment {
            return EventGraphFragment()
        }
    }
}
