package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PersonProfile字段对比器
 * 用于逐字段对比两个PersonProfile对象并生成冲突项
 */
object PersonProfileFieldComparator {
    
    /**
     * 字段定义
     * @param key 字段键名(英文属性名)
     * @param name 字段显示名(中文)
     * @param getValue 获取字段值的lambda
     */
    private data class FieldDefinition<T>(
        val key: String,
        val name: String,
        val getValue: (PersonProfile) -> T
    )
    
    /**
     * 所有需要对比的字段定义列表
     */
    private val fieldDefinitions = listOf(
        FieldDefinition("remarkName", "备注名") { it.remarkName },
        FieldDefinition("realName", "真名") { it.realName },
        FieldDefinition("persona", "角色设定") { it.persona },
        FieldDefinition("nicknameForUser", "对用户的称呼") { it.nicknameForUser },
        FieldDefinition("signature", "个性签名") { it.signature },
        FieldDefinition("description", "个人简介") { it.description },
        FieldDefinition("gender", "性别") { it.gender },
        FieldDefinition("age", "年龄") { it.age },
        FieldDefinition("birthday", "生日") { it.birthday },
        FieldDefinition("zodiacSign", "星座") { it.zodiacSign },
        FieldDefinition("location", "地区") { it.location },
        FieldDefinition("companyOrSchool", "公司/学校") { it.companyOrSchool },
        FieldDefinition("profession", "职业") { it.profession },
        FieldDefinition("info_line_text", "资料栏文字") { it.info_line_text },
        FieldDefinition("avatarUri", "头像") { it.avatarUri },
        FieldDefinition("backgroundUri", "背景图") { it.backgroundUri },
        FieldDefinition("chatBackgroundUri", "聊天背景") { it.chatBackgroundUri },
        FieldDefinition("statusText", "状态文字") { it.statusText },
        FieldDefinition("isBusy", "忙碌状态") { it.isBusy },
        FieldDefinition("group", "分组") { it.group },
        FieldDefinition("isPinned", "置顶") { it.isPinned },
        FieldDefinition("offlineModeEnabled", "离线模式") { it.offlineModeEnabled },
        FieldDefinition("ttsVoiceId", "TTS音色") { it.ttsVoiceId },
        FieldDefinition("voiceDescription", "音色描述") { it.voiceDescription },
        FieldDefinition("naiPromptSource", "NovelAI提示词来源") { it.naiPromptSource },
        FieldDefinition("naiPositivePrompt", "NovelAI正面提示词") { it.naiPositivePrompt },
        FieldDefinition("naiNegativePrompt", "NovelAI负面提示词") { it.naiNegativePrompt },
        FieldDefinition("autoSummaryEnabled", "自动总结") { it.autoSummaryEnabled },
        FieldDefinition("summaryInterval", "总结间隔") { it.summaryInterval },
        FieldDefinition("messagesSinceLastSummary", "距上次总结消息数") { it.messagesSinceLastSummary },
        FieldDefinition("injectThoughts", "注入思考") { it.injectThoughts },
        FieldDefinition("backgroundActivityEnabled", "背景活动") { it.backgroundActivityEnabled },
        FieldDefinition("actionCooldownMinutes", "操作冷却时间(分钟)") { it.actionCooldownMinutes },
        FieldDefinition("lastBackgroundActionTimestamp", "上次独立后台行动时间") { it.lastBackgroundActionTimestamp },
        FieldDefinition("timeAwarenessEnabled", "时间感知") { it.timeAwarenessEnabled },
        FieldDefinition("shortTermMemoryLimit", "短期记忆限制") { it.shortTermMemoryLimit },
        FieldDefinition("attachMemoryLimit", "附加记忆限制") { it.attachMemoryLimit },
        FieldDefinition("selectedPhotos", "已选照片") { it.selectedPhotos },
        FieldDefinition("isBlocked", "是否拉黑") { it.isBlocked },
        FieldDefinition("blockTimestamp", "拉黑时间戳") { it.blockTimestamp },
        FieldDefinition("blockCooldownHours", "拉黑冷却时间(小时)") { it.blockCooldownHours },
        FieldDefinition("applicationReason", "申请理由") { it.applicationReason },
        FieldDefinition("isBlockedByContact", "被对方拉黑") { it.isBlockedByContact },
        FieldDefinition("isGroupChat", "是否群聊") { it.isGroupChat },
        FieldDefinition("groupChatRole", "群聊角色") { it.groupChatRole },
        FieldDefinition("sleepSchedule", "睡眠时间表") { it.sleepSchedule },
        FieldDefinition("timeSensitivityConfig", "时间敏感配置") { it.timeSensitivityConfig }
    )
    
    /**
     * 对比两个PersonProfile并生成冲突项列表
     * @param existing 现有的PersonProfile
     * @param imported 导入的PersonProfile
     * @param onConflict 冲突回调函数
     * @return 合并后的PersonProfile
     */
    suspend fun compareAndResolve(
        existing: PersonProfile,
        imported: PersonProfile,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): PersonProfile {
        // 用于存储已选择的字段值
        val selectedFields = mutableMapOf<String, Any?>()
        
        // 逐字段对比
        for (field in fieldDefinitions) {
            val existingValue = field.getValue(existing)
            val importedValue = field.getValue(imported)
            
            // 如果字段值不同,询问用户
            if (existingValue != importedValue) {
                val conflictItem = ConflictItem.PersonProfileFieldConflict(
                    contactId = existing.id,
                    contactRealName = existing.realName,
                    fieldName = field.name,
                    fieldKey = field.key,
                    existingValue = existingValue,
                    importValue = importedValue,
                    selectedFields = selectedFields.toMap()
                )
                
                // 调用冲突回调获取用户选择（在主线程上执行）
                val resolution = withContext(Dispatchers.Main) {
                    onConflict(conflictItem)
                }
                
                // 根据用户选择保存字段值
                when (resolution) {
                    ConflictResolution.KEEP_EXISTING -> {
                        selectedFields[field.key] = existingValue
                    }
                    ConflictResolution.USE_IMPORT -> {
                        selectedFields[field.key] = importedValue
                    }
                }
            } else {
                // 字段值相同,使用现有值
                selectedFields[field.key] = existingValue
            }
        }
        
        // 根据selectedFields构建最终的PersonProfile
        return buildPersonProfileFromFields(existing.id, selectedFields)
    }
    
    /**
     * 根据字段Map构建PersonProfile对象
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildPersonProfileFromFields(
        id: String,
        fields: Map<String, Any?>
    ): PersonProfile {
        return PersonProfile(
            id = id,
            remarkName = fields["remarkName"] as String,
            realName = fields["realName"] as String,
            persona = fields["persona"] as String,
            nicknameForUser = fields["nicknameForUser"] as String?,
            signature = fields["signature"] as String?,
            description = fields["description"] as String?,
            gender = fields["gender"] as String?,
            age = fields["age"] as Int?,
            birthday = fields["birthday"] as String?,
            zodiacSign = fields["zodiacSign"] as String?,
            location = fields["location"] as String?,
            companyOrSchool = fields["companyOrSchool"] as String?,
            profession = fields["profession"] as String?,
            info_line_text = fields["info_line_text"] as String?,
            avatarUri = fields["avatarUri"] as String?,
            backgroundUri = fields["backgroundUri"] as String?,
            chatBackgroundUri = fields["chatBackgroundUri"] as String?,
            statusText = fields["statusText"] as String?,
            isBusy = fields["isBusy"] as Boolean,
            group = fields["group"] as String?,
            isPinned = fields["isPinned"] as Boolean,
            offlineModeEnabled = fields["offlineModeEnabled"] as Boolean,
            ttsVoiceId = fields["ttsVoiceId"] as String?,
            voiceDescription = fields["voiceDescription"] as String?,
            naiPromptSource = fields["naiPromptSource"] as String,
            naiPositivePrompt = fields["naiPositivePrompt"] as String?,
            naiNegativePrompt = fields["naiNegativePrompt"] as String?,
            autoSummaryEnabled = fields["autoSummaryEnabled"] as Boolean,
            summaryInterval = fields["summaryInterval"] as Int,
            messagesSinceLastSummary = fields["messagesSinceLastSummary"] as Int,
            injectThoughts = fields["injectThoughts"] as Boolean,
            backgroundActivityEnabled = fields["backgroundActivityEnabled"] as Boolean,
            actionCooldownMinutes = fields["actionCooldownMinutes"] as Int,
            lastBackgroundActionTimestamp = fields["lastBackgroundActionTimestamp"] as Long?,
            timeAwarenessEnabled = fields["timeAwarenessEnabled"] as Boolean,
            shortTermMemoryLimit = fields["shortTermMemoryLimit"] as Int,
            attachMemoryLimit = fields["attachMemoryLimit"] as Int,
            selectedPhotos = fields["selectedPhotos"] as List<String>,
            isBlocked = fields["isBlocked"] as Boolean,
            blockTimestamp = fields["blockTimestamp"] as Long?,
            blockCooldownHours = fields["blockCooldownHours"] as Double,
            applicationReason = fields["applicationReason"] as String?,
            isBlockedByContact = fields["isBlockedByContact"] as Boolean,
            isGroupChat = fields["isGroupChat"] as Boolean,
            groupChatRole = fields["groupChatRole"] as String?,
            sleepSchedule = fields["sleepSchedule"] as com.susking.ephone_s.aidata.domain.model.SleepSchedule?,
            timeSensitivityConfig = fields["timeSensitivityConfig"] as com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig
        )
    }
}