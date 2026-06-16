package com.susking.ephone_s.aidata.domain.model.import_export

/**
 * 用于在导入前向用户展示预览信息的数据类。
 */
sealed class ImportPreview {
    /**
     * 冲突统计信息
     */
    data class ConflictStats(
        val newContacts: Int = 0,           // 新增联系人数量
        val conflictingContacts: Int = 0,   // 冲突联系人数量
        val newMessages: Int = 0,            // 新增消息数量
        val conflictingMessages: Int = 0,    // 冲突消息数量
        val newWorldBooks: Int = 0,          // 新增世界书数量
        val conflictingWorldBooks: Int = 0,  // 冲突世界书数量
        val newPhotos: Int = 0,              // 新增相册数量
        val conflictingPhotos: Int = 0       // 冲突相册数量
    )
    
    /**
     * 单个聊天记录的导入预览。
     * @param ephoneSChat 解析出的聊天数据。
     * @param isNewCharacter 这是否是一个全新的角色。
     * @param characterNickname 导入角色的昵称。
     * @param characterRealName 导入角色的真实姓名。
     * @param existingCharacterNickname 如果是覆盖，现有角色的昵称。
     * @param existingCharacterRealName 如果是覆盖，现有角色的真实姓名。
     * @param conflictStats 冲突统计信息。
     * @param mode 导入模式,默认为覆盖导入。
     */
    data class SingleChat(
        val ephoneSChat: EPhoneSChat,
        val isNewCharacter: Boolean,
        val characterNickname: String,
        val characterRealName: String,
        val existingCharacterNickname: String? = null,
        val existingCharacterRealName: String? = null,
        val conflictStats: ConflictStats = ConflictStats(),
        val mode: ImportMode = ImportMode.OVERWRITE
    ) : ImportPreview()

    /**
     * 全部数据的导入预览。
     * @param exportData 解析出的所有应用数据。
     * @param contactCount 包含的联系人数量。
     * @param messageCount 包含的消息数量。
     * @param conflictStats 冲突统计信息。
     * @param mode 导入模式,默认为覆盖导入。
     */
    data class AllData(
        val exportData: ExportData,
        val contactCount: Int,
        val messageCount: Int,
        val conflictStats: ConflictStats = ConflictStats(),
        val mode: ImportMode = ImportMode.OVERWRITE
    ) : ImportPreview()
}