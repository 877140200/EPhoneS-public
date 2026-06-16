package com.susking.ephone_s.aidata.domain.model

/**
 * 图片生成参数
 * 封装图片生成所需的所有参数
 */
data class ImageGenerationParams(
    /**
     * 图片生成提示词
     */
    val prompt: String,
    
    /**
     * 角色配置信息
     * 用于获取API配置、角色特征等
     */
    val personProfile: PersonProfile,
    
    /**
     * 是否保存到相册
     * 默认为true
     */
    val saveToAlbum: Boolean = true,
    
    /**
     * 自定义相册名称
     * 如果为null则使用默认相册
     */
    val albumName: String? = null
)