package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.domain.model.import_export.ImportMode
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult

/**
 * 数据导入导出 Repository 接口
 * 统一管理所有数据的导入和导出操作
 */
interface DataImportExportRepository {
    
    /**
     * 导入完整聊天记录（单个角色）- 覆盖模式
     * @param contact 角色设定
     * @param messages 聊天记录列表
     * @param memories 长期记忆列表
     * @param jottings 随笔列表
     * @param heartbeats 心声列表
     */
    suspend fun importFullChatHistory(
        contact: PersonProfile,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        jottings: List<JottingEntity>,
        heartbeats: List<HeartbeatEntity>
    )
    
    /**
     * 导入完整聊天记录（单个角色）- 增量模式
     * 支持智能合并现有数据和导入数据
     * @param contact 角色设定
     * @param messages 聊天记录列表
     * @param memories 长期记忆列表
     * @param jottings 随笔列表
     * @param heartbeats 心声列表
     * @param mode 导入模式
     */
    suspend fun importFullChatHistoryIncremental(
        contact: PersonProfile,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        jottings: List<JottingEntity>,
        heartbeats: List<HeartbeatEntity>,
        mode: ImportMode
    )
    
    /**
     * 导入所有数据 - 覆盖模式
     * @param data 导出数据对象
     */
    suspend fun importAllData(data: ExportData)
    
    /**
     * 导入所有数据 - 增量模式
     * 支持智能合并现有数据和导入数据
     * @param data 导出数据对象
     * @param mode 导入模式
     */
    suspend fun importAllDataIncremental(data: ExportData, mode: ImportMode)
    
    /**
     * 获取好友分组信息
     * @return 好友分组列表
     */
    suspend fun getFriendGroups(): List<String>?
    
    /**
     * 保存好友分组信息
     * @param groups 好友分组列表
     */
    suspend fun saveFriendGroups(groups: List<String>)
    
    /**
     * 获取所有联系人ID
     * @return 联系人ID列表
     */
    suspend fun getAllContactIds(): List<String>
    
    /**
     * 导入所有数据 - 交互式增量模式
     * 遇到冲突时通过回调函数询问用户如何处理
     * @param data 导出数据对象
     * @param onConflict 冲突回调函数,返回用户的选择
     * @return 导入结果统计
     */
    suspend fun importAllDataInteractive(
        data: ExportData,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportResult
    
    /**
     * 交互式导入单个角色的完整聊天历史(智能合并模式)
     * @param contact 联系人信息
     * @param chatMessages 聊天消息列表
     * @param longTermMemories 长期记忆列表
     * @param jottings 随笔列表
     * @param heartbeats 心声列表
     * @param onConflict 冲突解决回调函数
     * @return 导入结果统计
     */
    suspend fun importFullChatHistoryInteractive(
        contact: PersonProfile,
        chatMessages: List<ChatMessage>,
        longTermMemories: List<LongTermMemory>,
        jottings: List<JottingEntity>,
        heartbeats: List<HeartbeatEntity>,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportResult
}