package com.susking.ephone_s.cphone.ui.qq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.domain.model.SimulatedQQConversation
import com.susking.ephone_s.cphone.domain.model.SimulatedQQMessage
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone模拟QQ Fragment - 会话列表
 * 混合显示真实AI对话和模拟对话
 */
@AndroidEntryPoint
class CPhoneSimulatedQQFragment : Fragment() {

    private lateinit var rvConversations: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    
    private val viewModel: CPhoneAppViewModel by viewModels()
    private lateinit var adapter: CPhoneQQConversationAdapter
    private var contactId: String = ""
    private val conversationList = mutableListOf<SimulatedQQConversation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cphone_qq, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadConversations()
    }

    /**
     * 初始化视图
     */
    private fun initializeViews(view: View) {
        rvConversations = view.findViewById(R.id.rv_conversations)
        emptyState = view.findViewById(R.id.empty_state)
        progressBar = view.findViewById(R.id.progress_bar)
    }


    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        val toolbar = view?.findViewById<MaterialToolbar>(R.id.toolbar)

        toolbar?.apply {
            title = "QQ"
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        
        // 设置刷新按钮点击事件 - 只刷新模拟对话，不影响真实对话
        view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
            refreshSimulatedConversations()
        }
    }


    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneQQConversationAdapter { conversation ->
            openChatFragment(conversation)
        }
        
        rvConversations.layoutManager = LinearLayoutManager(requireContext())
        rvConversations.adapter = adapter
    }

    /**
     * 观察QQ会话数据
     * 混合真实对话和模拟对话
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getQQData(contactId).collect { qqConversations ->
                conversationList.clear()
                conversationList.addAll(qqConversations)
                
                if (conversationList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent(conversationList.toList())
                }
            }
        }
    }
    
    /**
     * 观察刷新状态
     */
    private fun observeRefreshState() {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CPhoneAppViewModel.RefreshState.Idle -> {
                    // 空闲状态
                }
                is CPhoneAppViewModel.RefreshState.Loading -> {
                    showLoading()
                }
                is CPhoneAppViewModel.RefreshState.Success -> {
                    if (state.appType == "qq") {
                        view?.let {
                            Snackbar.make(it, "模拟对话刷新成功", Snackbar.LENGTH_SHORT).show()
                        }
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "qq") {
                        // 刷新失败后仍显示原有数据
                        if (conversationList.isNotEmpty()) {
                            showContent(conversationList.toList())
                        }
                        view?.let {
                            Snackbar.make(
                                it,
                                "刷新失败: ${state.message}",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                        viewModel.resetRefreshState()
                    }
                }
            }
        }
    }
    
    /**
     * 加载会话列表
     * 数据通过observeData自动加载
     */
    private fun loadConversations() {
        // 数据通过Flow自动加载
    }
    
    /**
     * 刷新模拟对话
     * 只刷新AI生成的模拟对话，不影响真实对话
     */
    private fun refreshSimulatedConversations() {
        viewModel.refreshAppData(contactId, "qq")
    }

    /**
     * 打开聊天界面
     */
    private fun openChatFragment(conversation: SimulatedQQConversation) {
        val chatFragment = CPhoneQQChatFragment.newInstance(
            conversationId = conversation.id,
            conversationName = conversation.name,
            conversationType = conversation.conversationType
        )

        // 获取当前Fragment所在的容器ID
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, chatFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * 显示加载中状态
     */
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        rvConversations.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    /**
     * 显示内容
     */
    private fun showContent(data: List<SimulatedQQConversation>) {
        progressBar.visibility = View.GONE
        rvConversations.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        adapter.submitList(data)
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        progressBar.visibility = View.GONE
        rvConversations.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        
        // 设置空状态文本
        emptyState.findViewById<TextView>(R.id.tv_empty_message)?.text = "暂无会话"
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        
        /**
         * 创建Fragment实例
         */
        fun newInstance(contactId: String): CPhoneSimulatedQQFragment {
            return CPhoneSimulatedQQFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}