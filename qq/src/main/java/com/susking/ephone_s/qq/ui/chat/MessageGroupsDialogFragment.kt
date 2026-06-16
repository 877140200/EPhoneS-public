package com.susking.ephone_s.qq.ui.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.prompt.MessageGroup
import com.susking.ephone_s.aidata.prompt.MessageGroupAnalysis
import com.susking.ephone_s.aidata.prompt.MessageGroupType
import com.susking.ephone_s.qq.databinding.DialogMessageGroupsBinding
import com.susking.ephone_s.qq.databinding.ItemMessageGroupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 消息分组展示对话框
 * 
 * 在用户点击接收按钮后、提示词确认对话框之前显示
 * 展示所有消息组的详细信息
 */
class MessageGroupsDialogFragment : DialogFragment() {

    private var _binding: DialogMessageGroupsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var analysis: MessageGroupAnalysis

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMessageGroupsBinding.inflate(layoutInflater)
        
        // 从参数中获取消息分组分析结果
        analysis = requireArguments().getParcelable(ARG_ANALYSIS)
            ?: throw IllegalArgumentException("MessageGroupAnalysis is required")
        
        setupRecyclerView()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("消息分组分析")
            .setView(binding.root)
            .setPositiveButton("继续") { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to true))
            }
            .setNegativeButton("取消") { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to false))
            }
            .create()
    }
    
    private fun setupRecyclerView() {
        val groups = mutableListOf<MessageGroup>()
        
        // 添加对话组（最早的）
        analysis.conversationGroup?.let { groups.add(it) }
        
        // 添加所有自说自话组（这些组在时间上晚于对话组）
        // monologueGroups 是倒序的（最新的在前），需要反转以获得从旧到新的顺序
        groups.addAll(analysis.monologueGroups.reversed())
        
        val adapter = MessageGroupAdapter(groups)
        binding.groupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        
        // 设置总体统计信息
        binding.totalGroupsText.text = "共 ${analysis.groupCount} 个消息组"
        binding.totalDurationText.text = "总时间跨度: ${formatDuration(analysis.totalDuration)}"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun formatDuration(durationMillis: Long): String {
        if (durationMillis == 0L) return "0秒"
        
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}秒")
        
        return parts.joinToString("")
    }
    
    companion object {
        const val TAG = "MessageGroupsDialogFragment"
        const val REQUEST_KEY = "message_groups_dialog_request"
        const val RESULT_CONFIRMED = "confirmed"
        private const val ARG_ANALYSIS = "analysis"
        
        fun newInstance(analysis: MessageGroupAnalysis): MessageGroupsDialogFragment {
            return MessageGroupsDialogFragment().apply {
                arguments = bundleOf(ARG_ANALYSIS to analysis)
            }
        }
    }
}

/**
 * 消息组适配器
 */
private class MessageGroupAdapter(
    private val groups: List<MessageGroup>
) : RecyclerView.Adapter<MessageGroupAdapter.ViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    inner class ViewHolder(val binding: ItemMessageGroupBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        
        with(holder.binding) {
            // 设置消息组类型和标题
            when (group.type) {
                MessageGroupType.CONVERSATION -> {
                    groupTypeText.text = "对话组"
                    groupTitleText.text = "完整对话 (双方互动)"
                }
                MessageGroupType.MONOLOGUE -> {
                    groupTypeText.text = "独白组"
                    val speaker = if (group.participants.contains("assistant")) "AI" else "用户"
                    groupTitleText.text = "独白消息 (仅$speaker)"
                }
            }
            
            // 设置时间信息
            startTimeText.text = "起始: ${dateFormat.format(Date(group.startTime))}"
            endTimeText.text = "结束: ${dateFormat.format(Date(group.endTime))}"
            
            // 设置持续时间
            durationText.text = "持续: ${formatDuration(group.duration)}"
            
            // 设置消息数量
            messageCountText.text = "${group.messages.size}条消息"
            
            // 设置参与者
            val participantsStr = group.participants.joinToString(", ") {
                when (it) {
                    "user" -> "用户"
                    "assistant" -> "AI"
                    else -> it
                }
            }
            participantsText.text = "参与者: $participantsStr"
            
            // 设置消息内容列表
            val messageAdapter = MessageInGroupAdapter(group.messages)
            messagesRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = messageAdapter
            }
        }
    }
    
    override fun getItemCount(): Int = groups.size
    
    private fun formatDuration(durationMillis: Long): String {
        if (durationMillis == 0L) return "0秒"
        
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        
        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}秒")
        
        return parts.joinToString("")
    }
}

/**
 * 消息组内的消息适配器
 */
private class MessageInGroupAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<MessageInGroupAdapter.MessageViewHolder>() {
    
    inner class MessageViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val senderText: android.widget.TextView = view.findViewById(com.susking.ephone_s.qq.R.id.messageSenderText)
        val contentText: android.widget.TextView = view.findViewById(com.susking.ephone_s.qq.R.id.messageContentText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.susking.ephone_s.qq.R.layout.item_message_in_group, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        // 设置发送者
        holder.senderText.text = when (message.role) {
            "user" -> "用户:"
            "assistant" -> "AI:"
            "system" -> "系统:"
            else -> "${message.role}:"
        }
        
        // 设置消息内容
        val content = when (message.type) {
            "text", "offline_text" -> message.content ?: ""
            "image_url", "image" -> "[图片消息]"
            "sticker" -> "[表情: ${message.stickerName ?: "表情"}]"
            "transfer" -> "[转账: ${message.amount}元]"
            "gift" -> "[礼物: ${message.giftName ?: "礼物"}]"
            "location_share" -> "[位置: ${message.content ?: "位置"}]"
            "voice_message" -> "[语音消息]"
            "pat" -> message.content ?: "[拍一拍]"
            else -> message.content ?: "[${message.type}]"
        }
        holder.contentText.text = content
    }
    
    override fun getItemCount(): Int = messages.size
}