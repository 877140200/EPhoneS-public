package com.susking.ephone_s.eventgraph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphNode
import com.susking.ephone_s.aidata.domain.model.memory.MemoryGraphRelation
import com.susking.ephone_s.aidata.domain.model.memory.RelationshipChangeAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 事件图谱页面状态管理。
 */
@HiltViewModel
class EventGraphViewModel @Inject constructor(
    private val memoryGraphDao: MemoryGraphDao
) : ViewModel() {
    private val _uiState: MutableStateFlow<EventGraphUiState> = MutableStateFlow(EventGraphUiState())
    val uiState: StateFlow<EventGraphUiState> = _uiState

    init {
        observeGraphData()
    }

    private fun observeGraphData(): Unit {
        viewModelScope.launch {
            combine(
                memoryGraphDao.getAllNodes(),
                memoryGraphDao.getAllRelations()
            ) { nodes: List<MemoryGraphNode>, relations: List<MemoryGraphRelation> ->
                val items: List<EventGraphItem> = buildGraphItems(nodes, relations)
                val relationshipItems: List<EventGraphItem> = buildRelationshipChainItems(nodes, relations)
                _uiState.value.copy(
                    nodes = nodes,
                    relations = relations,
                    items = items,
                    relationshipItems = relationshipItems,
                    graphNodes = buildGraphNodes(nodes),
                    graphEdges = buildGraphEdges(nodes, relations),
                    relationshipGraphEdges = buildRelationshipGraphEdges(nodes, relations)
                )
            }.collect { state: EventGraphUiState ->
                _uiState.value = state
            }
        }
    }

    private fun buildGraphNodes(nodes: List<MemoryGraphNode>): List<EventGraphNodeItem> {
        return nodes.map { node: MemoryGraphNode ->
            EventGraphNodeItem(
                id = node.id,
                label = node.name,
                entityType = node.entityType,
                contactId = node.contactId
            )
        }
    }

    private fun buildGraphEdges(
        nodes: List<MemoryGraphNode>,
        relations: List<MemoryGraphRelation>
    ): List<EventGraphEdgeItem> {
        val nodeNameById: Map<String, String> = nodes.associate { node: MemoryGraphNode -> node.id to node.name }
        return relations.map { relation: MemoryGraphRelation ->
            EventGraphEdgeItem(
                id = relation.id,
                fromNodeId = relation.fromNodeId,
                toNodeId = relation.toNodeId,
                label = relation.relationType,
                fromLabel = nodeNameById[relation.fromNodeId] ?: "未知节点",
                toLabel = nodeNameById[relation.toNodeId] ?: "未知节点",
                confidenceScore = relation.confidenceScore,
                statusLabel = relation.status.name,
                lifecycleLabel = buildRelationLifecycleLabel(relation)
            )
        }
    }

    private fun buildRelationshipGraphEdges(
        nodes: List<MemoryGraphNode>,
        relations: List<MemoryGraphRelation>
    ): List<EventGraphEdgeItem> {
        val nodeNameById: Map<String, String> = nodes.associate { node: MemoryGraphNode -> node.id to node.name }
        return relations
            .groupBy { relation: MemoryGraphRelation -> buildRelationshipChainKey(relation) }
            .map { entry: Map.Entry<String, List<MemoryGraphRelation>> ->
                val sortedRelations: List<MemoryGraphRelation> = entry.value.sortedWith(
                    compareByDescending<MemoryGraphRelation> { relation: MemoryGraphRelation -> relation.effectiveFrom }
                        .thenByDescending { relation: MemoryGraphRelation -> relation.updatedAt }
                )
                val latestRelation: MemoryGraphRelation = sortedRelations.first()
                val activeCount: Int = sortedRelations.count { relation: MemoryGraphRelation -> isActiveRelation(relation) }
                EventGraphEdgeItem(
                    id = entry.key,
                    fromNodeId = latestRelation.fromNodeId,
                    toNodeId = latestRelation.toNodeId,
                    label = buildRelationshipGraphLabel(latestRelation, sortedRelations.size),
                    fromLabel = nodeNameById[latestRelation.fromNodeId] ?: "未知节点",
                    toLabel = nodeNameById[latestRelation.toNodeId] ?: "未知节点",
                    confidenceScore = sortedRelations.maxOf { relation: MemoryGraphRelation -> relation.confidenceScore },
                    statusLabel = buildRelationshipGraphStatusLabel(activeCount, sortedRelations.size, latestRelation.changeAction),
                    lifecycleLabel = buildRelationshipGraphLifecycleLabel(sortedRelations)
                )
            }
    }

    private fun buildGraphItems(
        nodes: List<MemoryGraphNode>,
        relations: List<MemoryGraphRelation>
    ): List<EventGraphItem> {
        val nodeNameById: Map<String, String> = nodes.associate { node: MemoryGraphNode -> node.id to node.name }
        val relationItems: List<EventGraphItem> = relations.map { relation: MemoryGraphRelation ->
            EventGraphItem(
                id = relation.id,
                type = "关系",
                title = "${nodeNameById[relation.fromNodeId] ?: "未知节点"} → ${nodeNameById[relation.toNodeId] ?: "未知节点"}",
                content = relation.relationType,
                meta = "联系人：${relation.contactId} · 状态：${relation.status.name} · 证据可靠度：${formatScore(relation.confidenceScore)} · 动作：${relation.changeAction.name} · 证据：${relation.evidenceMemoryId ?: "无"}",
                timestamp = relation.eventTime,
                itemType = EventGraphItemType.RELATION
            )
        }
        val nodeItems: List<EventGraphItem> = nodes.map { node: MemoryGraphNode ->
            EventGraphItem(
                id = node.id,
                type = "节点",
                title = node.name,
                content = "实体类型：${node.entityType}",
                meta = "联系人：${node.contactId} · 别名：${node.aliases ?: "无"}",
                timestamp = node.updatedAt,
                itemType = EventGraphItemType.NODE
            )
        }
        return (relationItems + nodeItems).sortedByDescending { item: EventGraphItem -> item.timestamp }
    }

    private fun buildRelationshipChainItems(
        nodes: List<MemoryGraphNode>,
        relations: List<MemoryGraphRelation>
    ): List<EventGraphItem> {
        val nodeNameById: Map<String, String> = nodes.associate { node: MemoryGraphNode -> node.id to node.name }
        return relations
            .groupBy { relation: MemoryGraphRelation -> buildRelationshipChainKey(relation) }
            .map { entry: Map.Entry<String, List<MemoryGraphRelation>> ->
                val sortedRelations: List<MemoryGraphRelation> = entry.value.sortedByDescending { relation: MemoryGraphRelation -> relation.effectiveFrom }
                val latestRelation: MemoryGraphRelation = sortedRelations.first()
                val activeCount: Int = entry.value.count { relation: MemoryGraphRelation -> isActiveRelation(relation) }
                EventGraphItem(
                    id = entry.key,
                    type = "关系变化",
                    title = "${nodeNameById[latestRelation.fromNodeId] ?: "未知节点"} → ${nodeNameById[latestRelation.toNodeId] ?: "未知节点"}",
                    content = buildRelationshipChainSummary(entry.value),
                    meta = "关系类型：${latestRelation.relationType} · 声明：${entry.value.size} · 当前有效：${activeCount} · 最近动作：${latestRelation.changeAction.name} · 最近状态：${latestRelation.status.name}",
                    timestamp = sortedRelations.maxOf { relation: MemoryGraphRelation -> relation.updatedAt },
                    itemType = EventGraphItemType.RELATION_CHAIN
                )
            }
            .sortedByDescending { item: EventGraphItem -> item.timestamp }
    }

    fun selectItemDetail(item: EventGraphItem): Unit {
        val detail: EventGraphDetail? = when (item.itemType) {
            EventGraphItemType.RELATION -> buildRelationDetail(item.id)
            EventGraphItemType.NODE -> buildNodeDetail(item.id)
            EventGraphItemType.RELATION_CHAIN -> buildRelationshipChainDetail(item.id)
        }
        _uiState.value = _uiState.value.copy(selectedDetail = detail)
    }

    fun selectNodeDetail(nodeId: String): Unit {
        _uiState.value = _uiState.value.copy(selectedDetail = buildNodeDetail(nodeId))
    }

    fun selectEdgeDetail(edgeId: String): Unit {
        val detail: EventGraphDetail? = buildRelationDetail(edgeId) ?: buildRelationshipChainDetail(edgeId)
        _uiState.value = _uiState.value.copy(selectedDetail = detail)
    }

    fun clearSelectedDetail(): Unit {
        _uiState.value = _uiState.value.copy(selectedDetail = null)
    }

    fun deleteItem(item: EventGraphItem): Unit {
        viewModelScope.launch {
            when (item.itemType) {
                EventGraphItemType.RELATION -> deleteRelation(item.id)
                EventGraphItemType.NODE -> deleteNode(item.id)
                EventGraphItemType.RELATION_CHAIN -> return@launch
            }
        }
    }

    fun createNode(contactId: String, entityType: String, name: String, aliases: String?): Unit {
        viewModelScope.launch {
            val trimmedContactId: String = contactId.trim()
            val trimmedName: String = name.trim()
            val trimmedEntityType: String = entityType.trim().ifBlank { DEFAULT_ENTITY_TYPE }
            if (trimmedContactId.isBlank() || trimmedName.isBlank()) return@launch
            memoryGraphDao.insertNode(
                MemoryGraphNode(
                    contactId = trimmedContactId,
                    entityType = trimmedEntityType,
                    name = trimmedName,
                    normalizedName = normalizeManualKey(trimmedName),
                    aliases = aliases?.trim()?.ifBlank { null }
                )
            )
        }
    }

    fun createRelation(
        contactId: String,
        fromNodeId: String,
        toNodeId: String,
        relationType: String,
        changeAction: RelationshipChangeAction,
        confidenceScore: Float,
        changeReason: String?
    ): Unit {
        viewModelScope.launch {
            val trimmedContactId: String = contactId.trim()
            val trimmedFromNodeId: String = fromNodeId.trim()
            val trimmedToNodeId: String = toNodeId.trim()
            val trimmedRelationType: String = relationType.trim()
            if (trimmedContactId.isBlank() || trimmedFromNodeId.isBlank() || trimmedToNodeId.isBlank() || trimmedRelationType.isBlank()) return@launch
            val now: Long = System.currentTimeMillis()
            val endpointKey: String = buildManualEndpointKey(trimmedContactId, trimmedFromNodeId, trimmedRelationType, trimmedToNodeId)
            memoryGraphDao.insertRelation(
                MemoryGraphRelation(
                    contactId = trimmedContactId,
                    fromNodeId = trimmedFromNodeId,
                    toNodeId = trimmedToNodeId,
                    relationType = trimmedRelationType,
                    endpointKey = endpointKey,
                    relationKey = "$endpointKey|manual|$now|${changeAction.name}",
                    evidenceMemoryId = null,
                    eventTime = now,
                    effectiveFrom = now,
                    changeAction = changeAction,
                    changeReason = changeReason?.trim()?.ifBlank { null },
                    validitySource = MANUAL_SOURCE_MODULE,
                    confidenceScore = confidenceScore.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE),
                    status = MemoryEventStatus.ACTIVE
                )
            )
        }
    }

    fun updateRelation(
        id: String,
        contactId: String,
        fromNodeId: String,
        toNodeId: String,
        relationType: String,
        changeAction: RelationshipChangeAction,
        confidenceScore: Float,
        changeReason: String?,
        status: MemoryEventStatus,
        statusReason: String?
    ): Unit {
        viewModelScope.launch {
            val relation: MemoryGraphRelation = _uiState.value.relations.firstOrNull { currentRelation: MemoryGraphRelation -> currentRelation.id == id } ?: return@launch
            val trimmedContactId: String = contactId.trim()
            val trimmedFromNodeId: String = fromNodeId.trim()
            val trimmedToNodeId: String = toNodeId.trim()
            val trimmedRelationType: String = relationType.trim()
            if (trimmedContactId.isBlank() || trimmedFromNodeId.isBlank() || trimmedToNodeId.isBlank() || trimmedRelationType.isBlank()) return@launch
            val endpointKey: String = buildManualEndpointKey(trimmedContactId, trimmedFromNodeId, trimmedRelationType, trimmedToNodeId)
            memoryGraphDao.updateRelation(
                relation.copy(
                    contactId = trimmedContactId,
                    fromNodeId = trimmedFromNodeId,
                    toNodeId = trimmedToNodeId,
                    relationType = trimmedRelationType,
                    endpointKey = endpointKey,
                    changeAction = changeAction,
                    changeReason = changeReason?.trim()?.ifBlank { null },
                    confidenceScore = confidenceScore.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE),
                    status = status,
                    statusReason = statusReason?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateNode(id: String, contactId: String, entityType: String, name: String, aliases: String?): Unit {
        viewModelScope.launch {
            val node: MemoryGraphNode = _uiState.value.nodes.firstOrNull { currentNode: MemoryGraphNode -> currentNode.id == id } ?: return@launch
            val trimmedContactId: String = contactId.trim()
            val trimmedName: String = name.trim()
            val trimmedEntityType: String = entityType.trim().ifBlank { DEFAULT_ENTITY_TYPE }
            if (trimmedContactId.isBlank() || trimmedName.isBlank()) return@launch
            memoryGraphDao.updateNode(
                node.copy(
                    contactId = trimmedContactId,
                    entityType = trimmedEntityType,
                    name = trimmedName,
                    normalizedName = normalizeManualKey(trimmedName),
                    aliases = aliases?.trim()?.ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun deleteRelation(id: String): Unit {
        val relation: MemoryGraphRelation = _uiState.value.relations.firstOrNull { it.id == id } ?: return
        memoryGraphDao.deleteRelation(relation)
    }

    private suspend fun deleteNode(id: String): Unit {
        val node: MemoryGraphNode = _uiState.value.nodes.firstOrNull { it.id == id } ?: return
        memoryGraphDao.deleteNode(node)
    }

    private fun buildNodeDetail(nodeId: String): EventGraphDetail? {
        val state: EventGraphUiState = _uiState.value
        val node: MemoryGraphNode = state.nodes.firstOrNull { currentNode: MemoryGraphNode -> currentNode.id == nodeId } ?: return null
        val relatedRelations: List<MemoryGraphRelation> = state.relations.filter { relation: MemoryGraphRelation ->
            relation.fromNodeId == nodeId || relation.toNodeId == nodeId
        }
        val relatedRelationText: String = relatedRelations.joinToString(separator = "\n") { relation: MemoryGraphRelation ->
            "${formatRelationTitle(relation, state.nodes)} · ${relation.relationType} · 状态=${relation.status.name} · 动作=${relation.changeAction.name} · ${buildRelationLifecycleLabel(relation)}"
        }.ifBlank { "无" }
        return EventGraphDetail(
            id = node.id,
            type = "节点",
            title = node.name,
            content = "实体类型：${node.entityType}",
            rows = listOf(
                EventGraphDetailRow(label = "联系人", value = node.contactId),
                EventGraphDetailRow(label = "实体类型", value = node.entityType),
                EventGraphDetailRow(label = "别名", value = node.aliases ?: "无"),
                EventGraphDetailRow(label = "关联关系", value = relatedRelationText),
                EventGraphDetailRow(label = "创建时间", value = formatTimestamp(node.createdAt)),
                EventGraphDetailRow(label = "更新时间", value = formatTimestamp(node.updatedAt))
            )
        )
    }

    private fun buildRelationDetail(relationId: String): EventGraphDetail? {
        val state: EventGraphUiState = _uiState.value
        val relation: MemoryGraphRelation = state.relations.firstOrNull { currentRelation: MemoryGraphRelation -> currentRelation.id == relationId } ?: return null
        return EventGraphDetail(
            id = relation.id,
            type = "关系",
            title = formatRelationTitle(relation, state.nodes),
            content = relation.relationType,
            rows = listOf(
                EventGraphDetailRow(label = "联系人", value = relation.contactId),
                EventGraphDetailRow(label = "关系类型", value = relation.relationType),
                EventGraphDetailRow(label = "起始节点", value = getNodeName(relation.fromNodeId, state.nodes)),
                EventGraphDetailRow(label = "目标节点", value = getNodeName(relation.toNodeId, state.nodes)),
                EventGraphDetailRow(label = "状态", value = relation.status.name),
                EventGraphDetailRow(label = "状态原因", value = relation.statusReason ?: "无"),
                EventGraphDetailRow(label = "证据可靠度", value = formatScore(relation.confidenceScore)),
                EventGraphDetailRow(label = "变化动作", value = relation.changeAction.name),
                EventGraphDetailRow(label = "变化原因", value = relation.changeReason ?: "无"),
                EventGraphDetailRow(label = "生命周期键 endpointKey", value = relation.endpointKey.ifBlank { "无" }),
                EventGraphDetailRow(label = "声明去重键 relationKey", value = relation.relationKey),
                EventGraphDetailRow(label = "上一关系引用", value = relation.previousRelationId ?: "无"),
                EventGraphDetailRow(label = "失效触发关系 ID", value = relation.invalidatedByRelationId ?: "无"),
                EventGraphDetailRow(label = "替代关系 ID", value = relation.supersededByRelationId ?: "无"),
                EventGraphDetailRow(label = "证据记忆 ID", value = relation.evidenceMemoryId ?: "无"),
                EventGraphDetailRow(label = "生效区间来源", value = relation.validitySource ?: "无"),
                EventGraphDetailRow(label = "关系时间", value = formatTimestamp(relation.eventTime)),
                EventGraphDetailRow(label = "生效开始", value = formatTimestamp(relation.effectiveFrom)),
                EventGraphDetailRow(label = "生效结束", value = formatNullableTimestamp(relation.effectiveTo)),
                EventGraphDetailRow(label = "系统确认失效时间", value = formatNullableTimestamp(relation.invalidatedAt)),
                EventGraphDetailRow(label = "抽取批次 ID", value = relation.extractionBatchId ?: "无"),
                EventGraphDetailRow(label = "创建时间", value = formatTimestamp(relation.createdAt)),
                EventGraphDetailRow(label = "更新时间", value = formatTimestamp(relation.updatedAt))
            )
        )
    }

    private fun buildRelationshipChainDetail(chainKey: String): EventGraphDetail? {
        val state: EventGraphUiState = _uiState.value
        val chainRelations: List<MemoryGraphRelation> = state.relations
            .filter { relation: MemoryGraphRelation -> buildRelationshipChainKey(relation) == chainKey }
            .sortedWith(compareByDescending<MemoryGraphRelation> { relation: MemoryGraphRelation -> relation.effectiveFrom }.thenByDescending { relation: MemoryGraphRelation -> relation.updatedAt })
        val latestRelation: MemoryGraphRelation = chainRelations.firstOrNull() ?: return null
        val activeRelations: List<MemoryGraphRelation> = chainRelations.filter { relation: MemoryGraphRelation -> isActiveRelation(relation) }
        return EventGraphDetail(
            id = chainKey,
            type = "关系变化",
            title = formatRelationTitle(latestRelation, state.nodes),
            content = buildRelationshipChainSummary(chainRelations),
            rows = listOf(
                EventGraphDetailRow(label = "调试说明", value = "本视图按 endpointKey 聚合同一关系生命周期链；不会推断互斥，只展示模型证据与服务端执行结果。"),
                EventGraphDetailRow(label = "生命周期键 endpointKey", value = latestRelation.endpointKey.ifBlank { chainKey }),
                EventGraphDetailRow(label = "关系类型", value = latestRelation.relationType),
                EventGraphDetailRow(label = "起始节点", value = getNodeName(latestRelation.fromNodeId, state.nodes)),
                EventGraphDetailRow(label = "目标节点", value = getNodeName(latestRelation.toNodeId, state.nodes)),
                EventGraphDetailRow(label = "声明总数", value = chainRelations.size.toString()),
                EventGraphDetailRow(label = "当前有效声明数", value = activeRelations.size.toString()),
                EventGraphDetailRow(label = "当前有效声明", value = activeRelations.joinToString(separator = "\n") { relation: MemoryGraphRelation -> buildRelationDebugLine(relation, state.nodes) }.ifBlank { "无" }),
                EventGraphDetailRow(label = "完整变化时间线", value = chainRelations.joinToString(separator = "\n\n") { relation: MemoryGraphRelation -> buildRelationDebugBlock(relation, state.nodes) })
            )
        )
    }

    private fun formatRelationTitle(relation: MemoryGraphRelation, nodes: List<MemoryGraphNode>): String {
        return "${getNodeName(relation.fromNodeId, nodes)} → ${getNodeName(relation.toNodeId, nodes)}"
    }

    private fun getNodeName(nodeId: String, nodes: List<MemoryGraphNode>): String {
        return nodes.firstOrNull { node: MemoryGraphNode -> node.id == nodeId }?.name ?: "未知节点"
    }

    private fun buildRelationshipChainKey(relation: MemoryGraphRelation): String {
        return relation.endpointKey.ifBlank { "${relation.contactId}:${relation.fromNodeId}:${relation.toNodeId}:${relation.relationType}" }
    }

    private fun buildRelationshipChainSummary(relations: List<MemoryGraphRelation>): String {
        val activeCount: Int = relations.count { relation: MemoryGraphRelation -> isActiveRelation(relation) }
        val latestRelation: MemoryGraphRelation = relations.maxBy { relation: MemoryGraphRelation -> relation.updatedAt }
        return "当前有效 $activeCount 条 / 共 ${relations.size} 条声明 · ${buildRelationLifecycleLabel(latestRelation)} · 最近原因：${latestRelation.changeReason ?: latestRelation.statusReason ?: "无"}"
    }

    private fun isActiveRelation(relation: MemoryGraphRelation): Boolean {
        return relation.status == com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus.ACTIVE && relation.invalidatedAt == null
    }

    private fun buildRelationshipGraphLabel(relation: MemoryGraphRelation, relationCount: Int): String {
        val relationType: String = relation.relationType.trim().ifBlank { "关系" }
        return if (relationCount > 1) "$relationType ×$relationCount" else relationType
    }

    private fun buildRelationshipGraphStatusLabel(activeCount: Int, totalCount: Int, changeAction: RelationshipChangeAction): String {
        return "有效$activeCount/$totalCount · ${changeAction.name}"
    }

    private fun buildRelationshipGraphLifecycleLabel(relations: List<MemoryGraphRelation>): String {
        val oldestRelation: MemoryGraphRelation = relations.minBy { relation: MemoryGraphRelation -> relation.effectiveFrom }
        val latestRelation: MemoryGraphRelation = relations.maxWith(
            compareBy<MemoryGraphRelation> { relation: MemoryGraphRelation -> relation.effectiveFrom }
                .thenBy { relation: MemoryGraphRelation -> relation.updatedAt }
        )
        return "${formatShortTimestamp(oldestRelation.effectiveFrom)} → ${formatNullableShortTimestamp(latestRelation.effectiveTo)}"
    }

    private fun buildRelationLifecycleLabel(relation: MemoryGraphRelation): String {
        return "生效：${formatTimestamp(relation.effectiveFrom)} → ${formatNullableTimestamp(relation.effectiveTo)}"
    }

    private fun buildRelationDebugLine(relation: MemoryGraphRelation, nodes: List<MemoryGraphNode>): String {
        return "${formatRelationTitle(relation, nodes)} · ${relation.relationType} · 状态=${relation.status.name} · 动作=${relation.changeAction.name} · 证据可靠度=${formatScore(relation.confidenceScore)}"
    }

    private fun buildRelationDebugBlock(relation: MemoryGraphRelation, nodes: List<MemoryGraphNode>): String {
        return listOf(
            buildRelationDebugLine(relation, nodes),
            "生效区间：${formatTimestamp(relation.effectiveFrom)} → ${formatNullableTimestamp(relation.effectiveTo)}；系统失效时间=${formatNullableTimestamp(relation.invalidatedAt)}",
            "变化原因：${relation.changeReason ?: "无"}；状态原因：${relation.statusReason ?: "无"}；生效来源：${relation.validitySource ?: "无"}",
            "证据记忆=${relation.evidenceMemoryId ?: "无"}；上一关系=${relation.previousRelationId ?: "无"}；触发失效=${relation.invalidatedByRelationId ?: "无"}；替代=${relation.supersededByRelationId ?: "无"}",
            "relationId=${relation.id}；relationKey=${relation.relationKey}；batch=${relation.extractionBatchId ?: "无"}"
        ).joinToString(separator = "\n")
    }

    private fun buildManualEndpointKey(contactId: String, fromNodeId: String, relationType: String, toNodeId: String): String {
        return listOf(contactId, fromNodeId, normalizeManualKey(relationType), toNodeId).joinToString(separator = "|")
    }

    private fun normalizeManualKey(value: String): String {
        return value.trim().lowercase(java.util.Locale.CHINA)
    }

    private fun formatScore(score: Float): String {
        return "%.2f".format(score)
    }

    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(timestamp))
    }

    private fun formatShortTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(timestamp))
    }

    private fun formatNullableShortTimestamp(timestamp: Long?): String {
        return timestamp?.let { value: Long -> formatShortTimestamp(value) } ?: "未记录"
    }

    private fun formatNullableTimestamp(timestamp: Long?): String {
        return timestamp?.let { value: Long -> formatTimestamp(value) } ?: "未记录"
    }
    companion object {
        private const val DEFAULT_ENTITY_TYPE: String = "Person"
        private const val MANUAL_SOURCE_MODULE: String = "Manual"
        private const val MIN_CONFIDENCE_SCORE: Float = 0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1f
    }
}

data class EventGraphUiState(
    val nodes: List<MemoryGraphNode> = emptyList(),
    val relations: List<MemoryGraphRelation> = emptyList(),
    val items: List<EventGraphItem> = emptyList(),
    val relationshipItems: List<EventGraphItem> = emptyList(),
    val graphNodes: List<EventGraphNodeItem> = emptyList(),
    val graphEdges: List<EventGraphEdgeItem> = emptyList(),
    val relationshipGraphEdges: List<EventGraphEdgeItem> = emptyList(),
    val selectedDetail: EventGraphDetail? = null
)

data class EventGraphItem(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val meta: String,
    val timestamp: Long,
    val itemType: EventGraphItemType
)

data class EventGraphNodeItem(
    val id: String,
    val label: String,
    val entityType: String,
    val contactId: String
)

data class EventGraphEdgeItem(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String,
    val fromLabel: String,
    val toLabel: String,
    val confidenceScore: Float,
    val statusLabel: String,
    val lifecycleLabel: String
)

data class EventGraphDetail(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val rows: List<EventGraphDetailRow>
)

data class EventGraphDetailRow(
    val label: String,
    val value: String
)

enum class EventGraphItemType {
    RELATION,
    NODE,
    RELATION_CHAIN
}
