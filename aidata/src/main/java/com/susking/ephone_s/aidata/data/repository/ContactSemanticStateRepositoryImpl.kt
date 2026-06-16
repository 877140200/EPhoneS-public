package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.ContactSemanticStateDao
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.domain.model.AiAction
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 联系人语义状态仓库实现。
 *
 * 这里执行模型明确给出的账本维护动作，并在写入前做去重、容量裁剪和低价值临时线索裁剪。
 */
class ContactSemanticStateRepositoryImpl(
    private val contactSemanticStateDao: ContactSemanticStateDao
) : ContactSemanticStateRepository {

    private val gson: Gson = Gson()

    override fun getSemanticStateForContact(contactId: String): Flow<ContactSemanticStateEntity?> {
        return contactSemanticStateDao.getSemanticStateForContact(contactId)
    }

    override suspend fun getSemanticStateSnapshotForContact(contactId: String): ContactSemanticStateEntity? = withContext(Dispatchers.IO) {
        contactSemanticStateDao.getSemanticStateSnapshotForContact(contactId)
    }

    override fun getAllSemanticStates(): Flow<List<ContactSemanticStateEntity>> {
        return contactSemanticStateDao.getAllSemanticStates()
    }

    override suspend fun upsertSemanticState(semanticState: ContactSemanticStateEntity): Unit = withContext(Dispatchers.IO) {
        contactSemanticStateDao.insertSemanticState(semanticState)
    }

    override suspend fun updateSemanticState(semanticState: ContactSemanticStateEntity): Unit = withContext(Dispatchers.IO) {
        contactSemanticStateDao.insertSemanticState(semanticState.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun applySemanticStateUpdate(
        contactId: String,
        action: AiAction.UpdateSemanticState,
        sourceMessageId: String?,
        rawUpdateJson: String?,
        aiTurnId: String?
    ): Unit = withContext(Dispatchers.IO) {
        val currentState: ContactSemanticStateEntity = contactSemanticStateDao.getSemanticStateSnapshotForContact(contactId)
            ?: ContactSemanticStateEntity(contactId = contactId)
        // 写前快照：把当前整态序列化留存，供“重试/重说”丢弃本轮时一步回退。
        // 快照内部的 previousStateJson 置空，避免逐轮累积形成无限嵌套 JSON。
        val previousStateSnapshot: String = gson.toJson(currentState.copy(previousStateJson = null))
        val archivedActiveContext: String = buildArchiveContent(
            currentContent = currentState.activeSemanticContext,
            fieldAction = action.activeSemanticContext
        )
        val resolvedArchiveContent: String = archivedActiveContext.takeIf { content: String -> hasResolvedSignal(content) }.orEmpty()
        val historicalArchiveContent: String = archivedActiveContext.takeUnless { content: String -> hasResolvedSignal(content) }.orEmpty()
        val resolvedEventAnchors: String = applyFieldAction(
            currentContent = appendContent(currentState.resolvedEventAnchors, resolvedArchiveContent, RESOLVED_EVENT_MAX_LINES),
            fieldAction = action.resolvedEventAnchors,
            maxLines = RESOLVED_EVENT_MAX_LINES
        )
        val updatedState: ContactSemanticStateEntity = currentState.copy(
            activeSemanticContext = applyFieldAction(
                currentContent = currentState.activeSemanticContext,
                fieldAction = action.activeSemanticContext,
                maxLines = ACTIVE_CONTEXT_MAX_LINES
            ),
            historicalRecallAnchors = removeOverlappingLedgerLines(
                content = applyFieldAction(
                    currentContent = appendContent(currentState.historicalRecallAnchors, historicalArchiveContent, HISTORICAL_ANCHOR_MAX_LINES),
                    fieldAction = action.historicalRecallAnchors,
                    maxLines = HISTORICAL_ANCHOR_MAX_LINES
                ),
                otherContent = resolvedEventAnchors,
                maxLines = HISTORICAL_ANCHOR_MAX_LINES
            ),
            resolvedEventAnchors = resolvedEventAnchors,
            semanticKeywords = applyKeywordFieldAction(currentState.semanticKeywords, action.semanticKeywords),
            lifecycleNotes = applyFieldAction(
                currentContent = currentState.lifecycleNotes,
                fieldAction = action.lifecycleNotes,
                maxLines = LIFECYCLE_NOTE_MAX_LINES
            ),
            lastSourceMessageId = sourceMessageId,
            updatedAt = System.currentTimeMillis(),
            rawUpdateJson = rawUpdateJson,
            confidenceScore = action.confidenceScore,
            schemaVersion = ContactSemanticStateEntity.CURRENT_SCHEMA_VERSION,
            previousStateJson = previousStateSnapshot,
            lastUpdateAiTurnId = aiTurnId
        )
        contactSemanticStateDao.insertSemanticState(updatedState)
    }

    override suspend fun revertSemanticStateForTurns(
        contactId: String,
        discardedAiTurnIds: Set<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (discardedAiTurnIds.isEmpty()) return@withContext false
        val currentState: ContactSemanticStateEntity =
            contactSemanticStateDao.getSemanticStateSnapshotForContact(contactId) ?: return@withContext false
        // 仅当当前态确由被丢弃的某一轮写入时才回退，避免误退用户保留的合法轮次。
        val currentTurnId: String = currentState.lastUpdateAiTurnId ?: return@withContext false
        if (currentTurnId !in discardedAiTurnIds) return@withContext false
        val previousJson: String = currentState.previousStateJson ?: return@withContext false
        val restoredState: ContactSemanticStateEntity = runCatching {
            gson.fromJson(previousJson, ContactSemanticStateEntity::class.java)
        }.getOrNull() ?: return@withContext false
        // 还原上一态：主键沿用当前 contactId，刷新 updatedAt 让召回查询读到回退后的状态。
        contactSemanticStateDao.insertSemanticState(
            restoredState.copy(
                contactId = contactId,
                updatedAt = System.currentTimeMillis()
            )
        )
        true
    }

    override suspend fun deleteSemanticStateForContact(contactId: String): Unit = withContext(Dispatchers.IO) {
        contactSemanticStateDao.deleteSemanticStateForContact(contactId)
    }

    private fun applyFieldAction(
        currentContent: String,
        fieldAction: AiAction.SemanticStateFieldAction?,
        maxLines: Int
    ): String {
        val normalizedAction: String = fieldAction?.action?.trim()?.lowercase().orEmpty()
        val newContent: String = fieldAction?.content?.trim().orEmpty()
        return when (normalizedAction) {
            "update" -> normalizeLedgerContent(newContent, maxLines)
            "append" -> appendContent(currentContent, newContent, maxLines)
            "archive" -> ""
            "prune" -> pruneContent(currentContent, newContent, maxLines)
            "keep", "" -> normalizeLedgerContent(currentContent, maxLines)
            else -> normalizeLedgerContent(currentContent, maxLines)
        }
    }

    private fun applyKeywordFieldAction(
        currentContent: String,
        fieldAction: AiAction.SemanticStateFieldAction?
    ): String {
        val normalizedAction: String = fieldAction?.action?.trim()?.lowercase().orEmpty()
        val newContent: String = fieldAction?.content?.trim().orEmpty()
        return when (normalizedAction) {
            "update" -> normalizeKeywordContent(newContent)
            "append" -> normalizeKeywordContent("$currentContent、$newContent")
            "prune" -> pruneKeywords(currentContent, newContent)
            "archive", "keep", "" -> normalizeKeywordContent(currentContent)
            else -> normalizeKeywordContent(currentContent)
        }
    }

    private fun buildArchiveContent(
        currentContent: String,
        fieldAction: AiAction.SemanticStateFieldAction?
    ): String {
        val normalizedAction: String = fieldAction?.action?.trim()?.lowercase().orEmpty()
        val newContent: String = fieldAction?.content?.trim().orEmpty()
        if (normalizedAction != "archive") return ""
        return newContent.ifBlank { currentContent }
    }

    private fun appendContent(currentContent: String, newContent: String, maxLines: Int): String {
        if (newContent.isBlank()) return normalizeLedgerContent(currentContent, maxLines)
        return normalizeLedgerLines(splitLedgerLines(currentContent) + splitLedgerLines(newContent), maxLines)
    }

    private fun pruneContent(currentContent: String, pruneHint: String, maxLines: Int): String {
        if (pruneHint.isBlank()) return normalizeLedgerContent(currentContent, maxLines)
        val pruneTokens: List<String> = splitLedgerLines(pruneHint)
        val remainingLines: List<String> = splitLedgerLines(currentContent).filterNot { line: String ->
            pruneTokens.any { token: String -> isSimilarLedgerLine(line, token) }
        }
        return normalizeLedgerLines(remainingLines, maxLines)
    }

    private fun normalizeLedgerContent(content: String, maxLines: Int): String {
        return normalizeLedgerLines(splitLedgerLines(content), maxLines)
    }

    private fun normalizeLedgerLines(lines: List<String>, maxLines: Int): String {
        val normalizedLines: List<String> = lines
            .map { line: String -> normalizeLedgerLine(line) }
            .filter { line: String -> line.length in LEDGER_MIN_LINE_LENGTH..LEDGER_MAX_LINE_LENGTH }
            .fold(emptyList()) { acceptedLines: List<String>, line: String -> mergeLedgerLine(acceptedLines, line) }
        return normalizedLines.takeLast(maxLines).joinToString(separator = "\n")
    }

    private fun mergeLedgerLine(acceptedLines: List<String>, newLine: String): List<String> {
        val matchedLine: String? = acceptedLines.firstOrNull { line: String -> isSimilarLedgerLine(line, newLine) }
        if (matchedLine == null) return acceptedLines + newLine
        val replacementLine: String = chooseRicherLine(matchedLine, newLine)
        return acceptedLines.map { line: String -> if (line == matchedLine) replacementLine else line }
    }

    private fun chooseRicherLine(firstLine: String, secondLine: String): String {
        if (firstLine.length == secondLine.length) return firstLine
        return if (firstLine.length > secondLine.length) firstLine else secondLine
    }

    private fun removeOverlappingLedgerLines(content: String, otherContent: String, maxLines: Int): String {
        if (otherContent.isBlank()) return normalizeLedgerContent(content, maxLines)
        val otherLines: List<String> = splitLedgerLines(otherContent)
        val remainingLines: List<String> = splitLedgerLines(content).filterNot { line: String ->
            otherLines.any { otherLine: String -> isSimilarLedgerLine(line, otherLine) }
        }
        return normalizeLedgerLines(remainingLines, maxLines)
    }

    private fun splitLedgerLines(content: String): List<String> {
        return content.lines()
            .flatMap { line: String -> line.split('；', ';') }
            .map { line: String -> line.trim() }
            .filter { line: String -> line.isNotBlank() }
    }

    private fun normalizeLedgerLine(line: String): String {
        return line
            .trim()
            .trimStart('-', '*', '•', '·', ' ', '\t')
            .replace(LEDGER_FIELD_PREFIX_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .replace(REPEATED_PUNCTUATION_REGEX, "。")
            .trim(' ', '。', '，', ',', '；', ';')
    }

    private fun isSimilarLedgerLine(firstLine: String, secondLine: String): Boolean {
        val firstKey: String = buildComparableText(firstLine)
        val secondKey: String = buildComparableText(secondLine)
        if (firstKey.isBlank() || secondKey.isBlank()) return false
        return firstKey == secondKey || firstKey.contains(secondKey) || secondKey.contains(firstKey)
    }

    private fun normalizeKeywordContent(content: String): String {
        val keywords: List<String> = splitKeywords(content)
            .filter { keyword: String -> isValidKeyword(keyword) }
            .fold(emptyList()) { acceptedKeywords: List<String>, keyword: String -> mergeKeyword(acceptedKeywords, keyword) }
        return keywords.takeLast(KEYWORD_MAX_COUNT).joinToString(separator = "、")
    }

    private fun mergeKeyword(acceptedKeywords: List<String>, newKeyword: String): List<String> {
        val matchedKeyword: String? = acceptedKeywords.firstOrNull { keyword: String -> isSimilarKeyword(keyword, newKeyword) }
        if (matchedKeyword == null) return acceptedKeywords + newKeyword
        val replacementKeyword: String = chooseStableKeyword(matchedKeyword, newKeyword)
        return acceptedKeywords.map { keyword: String -> if (keyword == matchedKeyword) replacementKeyword else keyword }
    }

    private fun chooseStableKeyword(firstKeyword: String, secondKeyword: String): String {
        if (firstKeyword.length == secondKeyword.length) return firstKeyword
        return if (firstKeyword.length < secondKeyword.length) firstKeyword else secondKeyword
    }

    private fun pruneKeywords(currentContent: String, pruneHint: String): String {
        if (pruneHint.isBlank()) return normalizeKeywordContent(currentContent)
        val pruneKeywords: List<String> = splitKeywords(pruneHint)
        val remainingKeywords: List<String> = splitKeywords(currentContent).filterNot { keyword: String ->
            pruneKeywords.any { pruneKeyword: String -> isSimilarKeyword(keyword, pruneKeyword) }
        }
        return normalizeKeywordContent(remainingKeywords.joinToString(separator = "、"))
    }

    private fun splitKeywords(content: String): List<String> {
        return content.split('、', ',', '，', ';', '；', '\n', '/', '／')
            .map { keyword: String -> normalizeKeyword(keyword) }
            .filter { keyword: String -> keyword.isNotBlank() }
    }

    private fun normalizeKeyword(keyword: String): String {
        return keyword
            .trim()
            .trimStart('-', '*', '•', '·', ' ', '\t')
            .replace(LEDGER_FIELD_PREFIX_REGEX, "")
            .replace(WHITESPACE_REGEX, "")
            .trim(' ', '。', '，', ',', '；', ';', '：', ':')
    }

    private fun isValidKeyword(keyword: String): Boolean {
        if (keyword.length !in KEYWORD_MIN_LENGTH..KEYWORD_MAX_LENGTH) return false
        if (KEYWORD_SENTENCE_MARKERS.any { marker: String -> keyword.contains(marker) }) return false
        if (NOISY_KEYWORD_PARTS.any { noisyPart: String -> keyword.contains(noisyPart, ignoreCase = true) }) return false
        return true
    }

    private fun isSimilarKeyword(firstKeyword: String, secondKeyword: String): Boolean {
        val firstKey: String = buildComparableText(firstKeyword)
        val secondKey: String = buildComparableText(secondKeyword)
        if (firstKey.isBlank() || secondKey.isBlank()) return false
        return firstKey == secondKey || firstKey.contains(secondKey) || secondKey.contains(firstKey)
    }

    private fun hasResolvedSignal(content: String): Boolean {
        return RESOLVED_SIGNAL_PARTS.any { signal: String -> content.contains(signal, ignoreCase = true) }
    }

    private fun buildComparableText(content: String): String {
        return content.lowercase()
            .replace(COMPARABLE_REMOVE_REGEX, "")
            .replace(WHITESPACE_REGEX, "")
            .trim()
    }

    private companion object {
        const val ACTIVE_CONTEXT_MAX_LINES: Int = 6
        const val HISTORICAL_ANCHOR_MAX_LINES: Int = 18
        const val RESOLVED_EVENT_MAX_LINES: Int = 12
        const val LIFECYCLE_NOTE_MAX_LINES: Int = 8
        const val KEYWORD_MAX_COUNT: Int = 20
        const val LEDGER_MIN_LINE_LENGTH: Int = 6
        const val LEDGER_MAX_LINE_LENGTH: Int = 96
        const val KEYWORD_MIN_LENGTH: Int = 2
        const val KEYWORD_MAX_LENGTH: Int = 12
        val LEDGER_FIELD_PREFIX_REGEX: Regex = Regex("^(当前互动语义|历史召回锚点|已结束事件线索|语义关键词|生命周期说明|activeSemanticContext|historicalRecallAnchors|resolvedEventAnchors|semanticKeywords|lifecycleNotes)[：:]")
        val WHITESPACE_REGEX: Regex = Regex("\\s+")
        val REPEATED_PUNCTUATION_REGEX: Regex = Regex("[。！？!?]{2,}")
        val COMPARABLE_REMOVE_REGEX: Regex = Regex("[\\s。！？!?，,；;：:\\-—_（）()【】\\[\\]《》<>\"'“”‘’]")
        val KEYWORD_SENTENCE_MARKERS: List<String> = listOf("。", "！", "？", "需要", "正在", "已经", "用户", "角色")
        val NOISY_KEYWORD_PARTS: List<String> = listOf("情绪", "状态", "氛围", "安抚", "委屈", "难过", "开心", "生气", "正在", "当前", "临时", "本轮")
        val RESOLVED_SIGNAL_PARTS: List<String> = listOf("结束", "已结束", "解决", "已解决", "完成", "已完成", "告一段落", "和好", "取消", "过期")
    }
}
