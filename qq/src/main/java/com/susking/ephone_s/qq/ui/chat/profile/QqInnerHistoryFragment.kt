package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.qq.databinding.FragmentQqInnerHistoryBinding
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QqInnerHistoryFragment : DialogFragment() {

    private var _binding: FragmentQqInnerHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    @Inject lateinit var innerActivityManager: QqContentManager
    
    private lateinit var adapter: QqInnerHistoryAdapter
    private lateinit var contactId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID) ?: throw IllegalArgumentException("Contact ID is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqInnerHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        innerActivityManager.loadAllInnerHistory(contactId)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        adapter = QqInnerHistoryAdapter()
        binding.historyRecyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        innerActivityManager.allHeartbeats.observe(viewLifecycleOwner) { heartbeats ->
            val jottings = innerActivityManager.allJottings.value?.mapNotNull { it as? JottingEntity } ?: emptyList()
            updateHistoryList(heartbeats.mapNotNull { it as? HeartbeatEntity }, jottings)
        }

        innerActivityManager.allJottings.observe(viewLifecycleOwner) { jottings ->
            val heartbeats = innerActivityManager.allHeartbeats.value?.mapNotNull { it as? HeartbeatEntity } ?: emptyList()
            updateHistoryList(heartbeats, jottings.mapNotNull { it as? JottingEntity })
        }
    }

    private fun updateHistoryList(heartbeats: List<HeartbeatEntity>, jottings: List<JottingEntity>) {
        val historyItems = mutableListOf<InnerHistoryItem>()
        val combinedMap = mutableMapOf<String, Pair<HeartbeatEntity?, JottingEntity?>>()

        // 1. 处理有 sourceMessageId 的心声
        heartbeats.filter { it.sourceMessageId != null }.forEach {
            val id = it.sourceMessageId!!
            combinedMap[id] = Pair(it, combinedMap[id]?.second)
        }

        // 2. 处理有 sourceMessageId 的散记
        jottings.filter { it.sourceMessageId != null }.forEach {
            val id = it.sourceMessageId!!
            combinedMap[id] = Pair(combinedMap[id]?.first, it)
        }

        // 3. 将合并后的项目加入列表
        combinedMap.values.forEach { (heartbeat, jotting) ->
            // 使用心声的时间戳作为基准，如果心声不存在，则用散记的
            val timestamp = heartbeat?.timestamp ?: jotting?.timestamp ?: return@forEach
            historyItems.add(
                InnerHistoryItem(
                    timestamp = timestamp,
                    heartbeatContent = heartbeat?.content,
                    jottingContent = jotting?.content
                )
            )
        }

        // 4. 添加没有 sourceMessageId 的独立心声
        heartbeats.filter { it.sourceMessageId == null }.forEach {
            historyItems.add(
                InnerHistoryItem(
                    timestamp = it.timestamp,
                    heartbeatContent = it.content,
                    jottingContent = null
                )
            )
        }

        // 5. 添加没有 sourceMessageId 的独立散记
        jottings.filter { it.sourceMessageId == null }.forEach {
            historyItems.add(
                InnerHistoryItem(
                    timestamp = it.timestamp,
                    heartbeatContent = null,
                    jottingContent = it.content
                )
            )
        }

        // 6. 按时间戳降序排序
        historyItems.sortByDescending { it.timestamp }

        adapter.submitList(historyItems)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): QqInnerHistoryFragment {
            return QqInnerHistoryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}
