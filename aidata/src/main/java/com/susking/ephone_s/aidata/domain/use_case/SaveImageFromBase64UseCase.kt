package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import com.susking.ephone_s.aidata.util.ImageFileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 将Base64图片数据保存为本地文件的通用UseCase
 * 用于处理NovelAI等服务返回的Base64图片数据
 */
class SaveImageFromBase64UseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 将Base64编码的图片转换为本地文件路径
     * 
     * @param base64String Base64编码的图片字符串(格式: "data:image/png;base64,...")
     * @return 成功返回文件路径,失败返回null
     */
    operator fun invoke(base64String: String?): String? {
        if (base64String.isNullOrBlank()) return null
        
        // 如果已经是文件路径,直接返回
        if (!base64String.startsWith("data:image")) {
            return base64String
        }
        
        // 使用ImageFileHelper转换Base64为文件
        return ImageFileHelper.saveImageFromBase64(context, base64String)
    }
    
    /**
     * 批量转换Base64图片列表
     * 
     * @param base64List Base64图片列表
     * @return 文件路径列表(失败的项为null)
     */
    fun invokeBatch(base64List: List<String?>): List<String?> {
        return base64List.map { invoke(it) }
    }
}