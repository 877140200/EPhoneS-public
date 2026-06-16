package com.susking.ephone_s.settings.ui.conflict

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.settings.R

/**
 * 冲突解决对话框
 * 用于在导入过程中让用户选择如何处理数据冲突
 */
class ConflictResolutionDialogFragment : DialogFragment() {
    
    private var onResolutionSelectedListener: ((ConflictResolution) -> Unit)? = null
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_conflict_resolution,
            null
        )
        
        // 设置冲突信息
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvConflictTitle)
        val tvExisting = dialogView.findViewById<TextView>(R.id.tvExistingData)
        val tvImport = dialogView.findViewById<TextView>(R.id.tvImportData)
        
        // 从arguments获取冲突信息
        val title = arguments?.getString(ARG_TITLE) ?: "数据冲突"
        val existingValue = arguments?.getString(ARG_EXISTING) ?: ""
        val importValue = arguments?.getString(ARG_IMPORT) ?: ""
        
        tvTitle.text = title
        tvExisting.text = "现有数据:\n$existingValue"
        tvImport.text = "导入数据:\n$importValue"
        
        // 使用AlertDialog的按钮而不是布局中的按钮
        return AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("使用导入") { _, _ ->
                onResolutionSelectedListener?.invoke(ConflictResolution.USE_IMPORT)
            }
            .setNegativeButton("保留现有") { _, _ ->
                onResolutionSelectedListener?.invoke(ConflictResolution.KEEP_EXISTING)
            }
            .setCancelable(false)
            .create()
    }
    
    /**
     * 设置冲突解决选择监听器
     */
    fun setOnResolutionSelectedListener(listener: (ConflictResolution) -> Unit) {
        onResolutionSelectedListener = listener
    }
    
    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_EXISTING = "existing"
        private const val ARG_IMPORT = "import"
        
        /**
         * 创建新实例
         */
        fun newInstance(conflictItem: ConflictItem): ConflictResolutionDialogFragment {
            val (title, existingValue, importValue) = when (conflictItem) {
                is ConflictItem.PersonProfileFieldConflict -> {
                    Triple(
                        "联系人字段冲突: ${conflictItem.fieldName}",
                        conflictItem.existingValue?.toString() ?: "空",
                        conflictItem.importValue?.toString() ?: "空"
                    )
                }
                is ConflictItem.ChatMessageConflict -> {
                    Triple(
                        "聊天消息冲突",
                        formatMap(conflictItem.existingMessage),
                        formatMap(conflictItem.importMessage)
                    )
                }
                is ConflictItem.LongTermMemoryConflict -> {
                    Triple(
                        "长期记忆冲突",
                        formatMap(conflictItem.existingMemory),
                        formatMap(conflictItem.importMemory)
                    )
                }
                is ConflictItem.JottingConflict -> {
                    Triple(
                        "随笔冲突",
                        formatMap(conflictItem.existingJotting),
                        formatMap(conflictItem.importJotting)
                    )
                }
                is ConflictItem.HeartbeatConflict -> {
                    Triple(
                        "心声冲突",
                        formatMap(conflictItem.existingHeartbeat),
                        formatMap(conflictItem.importHeartbeat)
                    )
                }
                is ConflictItem.WorldBookConflict -> {
                    Triple(
                        "世界书冲突",
                        formatMap(conflictItem.existingWorldBook),
                        formatMap(conflictItem.importWorldBook)
                    )
                }
                is ConflictItem.WorldBookEntryConflict -> {
                    Triple(
                        "世界书条目冲突",
                        formatMap(conflictItem.existingEntry),
                        formatMap(conflictItem.importEntry)
                    )
                }
                is ConflictItem.FavoriteMessageConflict -> {
                    Triple(
                        "收藏消息冲突",
                        formatMap(conflictItem.existingFavorite),
                        formatMap(conflictItem.importFavorite)
                    )
                }
                is ConflictItem.FeedConflict -> {
                    Triple(
                        "动态冲突",
                        formatMap(conflictItem.existingFeed),
                        formatMap(conflictItem.importFeed)
                    )
                }
            }
            
            return ConflictResolutionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_EXISTING, existingValue)
                    putString(ARG_IMPORT, importValue)
                }
            }
        }
        
        /**
         * 格式化Map为可读字符串
         */
        private fun formatMap(map: Map<String, Any?>): String {
            return map.entries.joinToString("\n") { (key, value) ->
                "$key: ${value?.toString() ?: "空"}"
            }
        }
    }
}