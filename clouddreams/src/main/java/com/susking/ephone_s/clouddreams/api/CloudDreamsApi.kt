package com.susking.ephone_s.clouddreams.api

import android.content.Context
import android.content.Intent

/**
 * CloudDreams模块API接口
 * 提供启动聊天记录查看器的功能
 */
object CloudDreamsApi {
    
    /**
     * 启动CloudDreams主界面(聊天记录列表)
     * @param context 上下文
     */
    fun launchCloudDreams(context: Context) {
        val intent = Intent(context, Class.forName("com.susking.ephone_s.clouddreams.ui.MainActivity"))
        context.startActivity(intent)
    }
    
    /**
     * 直接打开指定的聊天记录文件
     * @param context 上下文
     * @param filePath 聊天记录文件的完整路径
     */
    fun openChatFile(context: Context, filePath: String) {
        val intent = Intent(context, Class.forName("com.susking.ephone_s.clouddreams.ui.ChatActivity"))
        intent.putExtra("file_path", filePath)
        context.startActivity(intent)
    }
}