package com.susking.ephone_s.qq.ui.chat.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.susking.ephone_s.qq.databinding.FragmentVideoCallHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VideoCallHistoryFragment : Fragment() {

    private var _binding: FragmentVideoCallHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: VideoCallHistoryViewModel by viewModels()
    private lateinit var adapter: VideoCallHistoryAdapter
    private var contactId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        
        // 设置RecyclerView的Adapter
        adapter = VideoCallHistoryAdapter { historyItem ->
            // 点击历史记录条目，跳转到详情页面
            val currentContactId = contactId ?: return@VideoCallHistoryAdapter
            parentFragmentManager.beginTransaction()
                .replace(com.susking.ephone_s.qq.R.id.fragment_container_for_chat, VideoCallHistoryDetailFragment.newInstance(historyItem.id, currentContactId))
                .addToBackStack(null)
                .commit()
        }
        binding.videoCallHistoryRecyclerView.adapter = adapter
        
        // 观察数据库中的视频通话历史记录
        observeVideoCallHistories()
    }
    
    /**
     * 观察视频通话历史记录数据
     */
    private fun observeVideoCallHistories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.videoCallHistories.collect { histories: List<VideoCallHistoryItem> ->
                    adapter.submitList(histories)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contactId"
        
        fun newInstance(contactId: String): VideoCallHistoryFragment {
            return VideoCallHistoryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}