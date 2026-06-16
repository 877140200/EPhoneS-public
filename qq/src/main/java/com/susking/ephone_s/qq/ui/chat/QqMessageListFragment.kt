package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.core.R
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.EventObserver
import com.susking.ephone_s.qq.databinding.FragmentQqMessageListBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.chat.QqContactAdapter
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QqMessageListFragment : Fragment() {

    private var _binding: FragmentQqMessageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 QqContactManager
    @Inject lateinit var contactManager: QqContactManager
    @Inject lateinit var settingsRepository: SettingsRepository
    private lateinit var contactAdapter: QqContactAdapter
    private var displayedContactCount: Int = 0
    private var hasReachedLastContact: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqMessageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        contactAdapter = QqContactAdapter(
            onItemClicked = { contact -> viewModel.navigateToChat(contact) }
        )
        displayedContactCount = getSafeChatListLoadCount()
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = contactAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val canLoadMoreContacts: Boolean = dy > 0 && layoutManager.findLastVisibleItemPosition() >= contactAdapter.itemCount - 2 && !hasReachedLastContact
                    if (canLoadMoreContacts) {
                        loadMoreContacts()
                    }
                }
            })
        }

        val swipeHelper = object : SwipeHelper(
            requireContext(),
            binding.recyclerViewMessages,
            300, // 增加按钮宽度
            onRightSwipe = { /* No-op, handled in QqMainFragment */ }
        ) {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // 只允许向左滑动来显示按钮
                return makeMovementFlags(0, ItemTouchHelper.LEFT)
            }
            override fun instantiateUnderlayButton(
                viewHolder: RecyclerView.ViewHolder,
                underlayButtons: MutableList<UnderlayButton>
            ) {
                val position = viewHolder.adapterPosition
                if (position == -1) return
                val contactWithMessage = contactAdapter.currentList[position]
                val contact = contactWithMessage.profile
                val pinButtonText = if (contact.isPinned) "取消置顶" else "置顶"
    
                underlayButtons.add(UnderlayButton(
                    pinButtonText,
                    requireContext().getColor(R.color.purple_500), // 使用 purple_700 资源
                    object : UnderlayButtonClickListener {
                        override fun onClick(pos: Int) {
                            contactManager.pinContact(contact.id)
                        }
                    }
                ))
    
                underlayButtons.add(UnderlayButton(
                    "移除",
                    requireContext().getColor(R.color.red_500),
                    object : UnderlayButtonClickListener {
                        override fun onClick(pos: Int) {
                            contactManager.hideFromChatList(contact.id)
                        }
                    }
                ))
            }
        }
        swipeHelper.attachSwipe()
    }

    private fun observeViewModel() {
        contactManager.messageListContacts.observe(viewLifecycleOwner) { contacts ->
            contactAdapter.submitList(createDisplayedContacts(contacts))
        }

        // 【新增】监听全局AI活动事件
        EventBus.newAiActivityEvent.observe(viewLifecycleOwner, EventObserver {
            // 当收到事件时，调用ViewModel的刷新方法
            contactManager.loadContacts()
        })
    }

    private fun getSafeChatListLoadCount(): Int {
        val configuredCount: Int = settingsRepository.getChatListLoadCount()
        return if (configuredCount > 0) configuredCount else DEFAULT_CHAT_LIST_LOAD_COUNT
    }

    private fun loadMoreContacts(): Unit {
        val loadCount: Int = getSafeChatListLoadCount()
        displayedContactCount += loadCount
        val contacts = contactManager.messageListContacts.value.orEmpty()
        contactAdapter.submitList(createDisplayedContacts(contacts))
    }

    private fun createDisplayedContacts(contacts: List<com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage>): List<com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage> {
        if (contacts.isEmpty()) {
            hasReachedLastContact = true
            return emptyList()
        }
        val safeDisplayCount: Int = displayedContactCount.coerceAtLeast(getSafeChatListLoadCount())
        val displayedContacts: List<com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage> = contacts.take(safeDisplayCount)
        hasReachedLastContact = displayedContacts.size >= contacts.size
        return displayedContacts
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DEFAULT_CHAT_LIST_LOAD_COUNT: Int = 20

        fun newInstance() = QqMessageListFragment()
    }
}