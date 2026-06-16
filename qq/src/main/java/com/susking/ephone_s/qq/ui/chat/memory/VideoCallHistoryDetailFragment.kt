package com.susking.ephone_s.qq.ui.chat.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.qq.databinding.FragmentVideoCallHistoryDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoCallHistoryDetailFragment : Fragment() {

    private var _binding: FragmentVideoCallHistoryDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: VideoCallHistoryViewModel by viewModels()
    private lateinit var adapter: VideoCallMessageAdapter
    private var historyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyId = arguments?.getString(ARG_HISTORY_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCallHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        
        // 设置RecyclerView的Adapter
        adapter = VideoCallMessageAdapter()
        binding.messagesRecyclerView.adapter = adapter
        
        // 加载消息数据
        loadMessages()
    }
    
    /**
     * 从数据库加载消息数据
     */
    private fun loadMessages() {
        val currentHistoryId = historyId
        if (currentHistoryId == null) {
            // 如果没有historyId，显示空列表
            adapter.submitList(emptyList())
            return
        }
        
        // 从数据库加载真实数据
        viewLifecycleOwner.lifecycleScope.launch {
            val historyItem = viewModel.getVideoCallHistoryById(currentHistoryId)
            if (historyItem != null) {
                adapter.submitList(historyItem.messages)
            } else {
                // 如果找不到历史记录，显示空列表
                adapter.submitList(emptyList())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_HISTORY_ID = "historyId"
        private const val ARG_CONTACT_ID = "contactId"

        fun newInstance(historyId: String, contactId: String): VideoCallHistoryDetailFragment {
            return VideoCallHistoryDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_HISTORY_ID, historyId)
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}