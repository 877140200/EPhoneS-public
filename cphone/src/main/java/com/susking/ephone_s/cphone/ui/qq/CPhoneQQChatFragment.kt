package com.susking.ephone_s.cphone.ui.qq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.domain.model.SimulatedQQMessage

/**
 * CPhone模拟QQ聊天Fragment
 * 显示与某个联系人/群组的聊天记录
 */
class CPhoneQQChatFragment : Fragment() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnEmoji: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var btnMore: ImageButton
    private lateinit var btnSend: MaterialButton
    
    private lateinit var adapter: CPhoneQQMessageAdapter
    private lateinit var conversationId: String
    private lateinit var conversationName: String
    private lateinit var conversationType: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            conversationId = it.getString(ARG_CONVERSATION_ID) ?: ""
            conversationName = it.getString(ARG_CONVERSATION_NAME) ?: ""
            conversationType = it.getString(ARG_CONVERSATION_TYPE) ?: "private"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cphone_qq_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupAppBar()
        setupRecyclerView()
        setupInputArea()
        loadMessages()
    }

    /**
     * 初始化视图
     */
    private fun initializeViews(view: View) {
        rvMessages = view.findViewById(R.id.rv_messages)
        emptyState = view.findViewById(R.id.empty_state)
        btnEmoji = view.findViewById(R.id.btn_emoji)
        etMessage = view.findViewById(R.id.et_message)
        btnMore = view.findViewById(R.id.btn_more)
        btnSend = view.findViewById(R.id.btn_send)
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.toolbar
        )
        
        toolbar?.apply {
            title = conversationName
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            
            // 菜单按钮可以显示聊天设置、查看资料等
            inflateMenu(R.menu.menu_cphone_app)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        // TODO: 显示聊天菜单（查看资料、清空聊天记录等）
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneQQMessageAdapter(currentUserId = "me")
        
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // 从底部开始显示
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = adapter
    }

    /**
     * 设置输入区域
     */
    private fun setupInputArea() {
        // 表情按钮
        btnEmoji.setOnClickListener {
            // TODO: 显示表情面板
        }
        
        // 更多按钮（发送图片、文件等）
        btnMore.setOnClickListener {
            // TODO: 显示更多选项面板
        }
        
        // 发送按钮
        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    /**
     * 加载消息列表
     */
    private fun loadMessages() {
        // TODO: 从Repository加载真实数据
        // 这里使用模拟数据演示
        val mockMessages = generateMockMessages()
        
        if (mockMessages.isEmpty()) {
            showEmptyState()
        } else {
            showContent(mockMessages)
        }
    }

    /**
     * 生成模拟消息数据
     */
    private fun generateMockMessages(): List<SimulatedQQMessage> {
        val now = System.currentTimeMillis()
        
        return listOf(
            SimulatedQQMessage(
                id = "msg_1",
                senderId = conversationId.replace("conv_", ""),
                senderName = conversationName,
                content = "你好！",
                timestamp = now - 3600000
            ),
            SimulatedQQMessage(
                id = "msg_2",
                senderId = "me",
                senderName = "我",
                content = "你好！很高兴认识你。",
                timestamp = now - 3000000
            ),
            SimulatedQQMessage(
                id = "msg_3",
                senderId = conversationId.replace("conv_", ""),
                senderName = conversationName,
                content = "最近怎么样？",
                timestamp = now - 2400000
            ),
            SimulatedQQMessage(
                id = "msg_4",
                senderId = "me",
                senderName = "我",
                content = "挺好的，你呢？",
                timestamp = now - 1800000
            )
        )
    }

    /**
     * 发送消息
     */
    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            return
        }
        
        // 创建新消息
        val newMessage = SimulatedQQMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderId = "me",
            senderName = "我",
            content = messageText,
            timestamp = System.currentTimeMillis()
        )
        
        // 添加到列表
        val currentList = adapter.currentList.toMutableList()
        currentList.add(newMessage)
        adapter.submitList(currentList)
        
        // 滚动到底部
        rvMessages.postDelayed({
            rvMessages.smoothScrollToPosition(currentList.size - 1)
        }, 100)
        
        // 清空输入框
        etMessage.text.clear()
        
        // TODO: 发送到Repository保存
        // TODO: 模拟AI回复（如果是与AI对话）
    }

    /**
     * 显示内容
     */
    private fun showContent(messages: List<SimulatedQQMessage>) {
        rvMessages.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        adapter.submitList(messages)
        
        // 滚动到底部
        rvMessages.postDelayed({
            if (messages.isNotEmpty()) {
                rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }, 100)
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        rvMessages.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        
        // 设置空状态文本
        emptyState.findViewById<TextView>(R.id.tv_empty_message)?.text = "还没有消息，开始聊天吧"
    }

    companion object {
        private const val ARG_CONVERSATION_ID = "conversation_id"
        private const val ARG_CONVERSATION_NAME = "conversation_name"
        private const val ARG_CONVERSATION_TYPE = "conversation_type"

        /**
         * 创建Fragment实例
         */
        fun newInstance(
            conversationId: String,
            conversationName: String,
            conversationType: String
        ): CPhoneQQChatFragment {
            return CPhoneQQChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_ID, conversationId)
                    putString(ARG_CONVERSATION_NAME, conversationName)
                    putString(ARG_CONVERSATION_TYPE, conversationType)
                }
            }
        }
    }
}