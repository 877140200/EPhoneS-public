package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.PromptSentenceEntity
import com.susking.ephone_s.aidata.data.local.entity.PromptWordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 提示词储存器 DAO，统一管理句子与词语两张表。
 *
 * 两类提示词结构一致、行为对称，放在同一个 DAO 里便于「锦囊」提示词储存器一次性读取。
 * 列表查询返回 Flow 以便 UI 实时刷新；备份导出用 *List 的挂起方法一次性取全量。
 */
@Dao
interface PromptStorageDao {

    // ==================== 句子 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentence(sentence: PromptSentenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<PromptSentenceEntity>)

    @Query("SELECT * FROM prompt_sentences ORDER BY createdAt DESC")
    fun getAllSentences(): Flow<List<PromptSentenceEntity>>

    @Query("SELECT * FROM prompt_sentences ORDER BY createdAt DESC")
    suspend fun getAllSentencesList(): List<PromptSentenceEntity>

    @Query("DELETE FROM prompt_sentences WHERE id = :id")
    suspend fun deleteSentenceById(id: Long)

    @Query("DELETE FROM prompt_sentences")
    suspend fun clearAllSentences()

    // ==================== 词语 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: PromptWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<PromptWordEntity>)

    @Query("SELECT * FROM prompt_words ORDER BY createdAt DESC")
    fun getAllWords(): Flow<List<PromptWordEntity>>

    @Query("SELECT * FROM prompt_words ORDER BY createdAt DESC")
    suspend fun getAllWordsList(): List<PromptWordEntity>

    @Query("DELETE FROM prompt_words WHERE id = :id")
    suspend fun deleteWordById(id: Long)

    @Query("DELETE FROM prompt_words")
    suspend fun clearAllWords()
}
