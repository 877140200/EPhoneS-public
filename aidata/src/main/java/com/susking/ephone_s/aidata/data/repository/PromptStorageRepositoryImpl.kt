package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import androidx.core.content.edit
import com.susking.ephone_s.aidata.data.local.dao.PromptStorageDao
import com.susking.ephone_s.aidata.data.local.entity.PromptSentenceEntity
import com.susking.ephone_s.aidata.data.local.entity.PromptWordEntity
import com.susking.ephone_s.aidata.domain.repository.PromptStorageRepository
import kotlinx.coroutines.flow.Flow

/**
 * [PromptStorageRepository] 的实现。
 *
 * 句子/词语走 Room（[PromptStorageDao]）；数量范围走独立 SharedPreferences
 * （文件名 [PREFS_NAME]），因 getAllSharedPreferences() 会扫描整个 shared_prefs 目录，
 * 故该配置天然进完整备份。
 */
class PromptStorageRepositoryImpl(
    context: Context,
    private val dao: PromptStorageDao
) : PromptStorageRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getAllSentences(): Flow<List<PromptSentenceEntity>> = dao.getAllSentences()

    override suspend fun addSentence(content: String) {
        val trimmed: String = content.trim()
        if (trimmed.isEmpty()) return
        dao.insertSentence(PromptSentenceEntity(content = trimmed))
    }

    override suspend fun deleteSentence(id: Long) = dao.deleteSentenceById(id)

    override fun getAllWords(): Flow<List<PromptWordEntity>> = dao.getAllWords()

    override suspend fun addWord(content: String) {
        val trimmed: String = content.trim()
        if (trimmed.isEmpty()) return
        dao.insertWord(PromptWordEntity(content = trimmed))
    }

    override suspend fun deleteWord(id: Long) = dao.deleteWordById(id)

    override fun getWordCountRange(): PromptStorageRepository.WordCountRange {
        val min: Int = prefs.getInt(KEY_WORD_MIN, DEFAULT_WORD_MIN)
        val max: Int = prefs.getInt(KEY_WORD_MAX, DEFAULT_WORD_MAX)
        // 防御历史脏数据：保证 1 <= min <= max
        val safeMin: Int = min.coerceAtLeast(MIN_ALLOWED)
        val safeMax: Int = max.coerceAtLeast(safeMin)
        return PromptStorageRepository.WordCountRange(safeMin, safeMax)
    }

    override fun saveWordCountRange(min: Int, max: Int) {
        val safeMin: Int = min.coerceAtLeast(MIN_ALLOWED)
        val safeMax: Int = max.coerceAtLeast(safeMin)
        prefs.edit {
            putInt(KEY_WORD_MIN, safeMin)
            putInt(KEY_WORD_MAX, safeMax)
        }
    }

    private companion object {
        const val PREFS_NAME = "prompt_storage_prefs"
        const val KEY_WORD_MIN = "prompt_random_word_min"
        const val KEY_WORD_MAX = "prompt_random_word_max"
        const val DEFAULT_WORD_MIN = 3
        const val DEFAULT_WORD_MAX = 5
        const val MIN_ALLOWED = 1
    }
}
