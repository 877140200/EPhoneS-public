package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.PromptSentenceEntity
import com.susking.ephone_s.aidata.data.local.entity.PromptWordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 提示词储存器仓库契约。
 *
 * 供「锦囊」提示词储存器读写句子/词语，并管理「词语随机数量范围」配置。
 * 数量范围存独立 SharedPreferences，随完整备份的 allSharedPreferences 一起导入导出。
 */
interface PromptStorageRepository {

    /** 词语随机数量范围（闭区间）。 */
    data class WordCountRange(val min: Int, val max: Int)

    // ==================== 句子 ====================
    fun getAllSentences(): Flow<List<PromptSentenceEntity>>
    suspend fun addSentence(content: String)
    suspend fun deleteSentence(id: Long)

    // ==================== 词语 ====================
    fun getAllWords(): Flow<List<PromptWordEntity>>
    suspend fun addWord(content: String)
    suspend fun deleteWord(id: Long)

    // ==================== 词语随机数量范围 ====================
    /** 读取当前数量范围，未设置时返回默认值。 */
    fun getWordCountRange(): WordCountRange
    /** 保存数量范围；调用方需保证 min in [1, max]。 */
    fun saveWordCountRange(min: Int, max: Int)
}
