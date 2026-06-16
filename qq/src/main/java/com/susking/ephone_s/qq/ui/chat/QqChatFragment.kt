package com.susking.ephone_s.qq.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.aidata.api.TtsStreamingCallback
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.aidata.domain.use_case.GenerateImageFromPromptUseCase
import com.susking.ephone_s.aidata.domain.use_case.RewriteImagePromptUseCase
import com.susking.ephone_s.album.data.service.AlbumServiceImpl
import com.susking.ephone_s.core.R
import com.susking.ephone_s.core.ui.dialog.ConfirmAiPromptDialogFragment
import com.susking.ephone_s.qq.databinding.FragmentQqChatBinding
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicySnapshot
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqEvent
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.domain.manager.QqTransactionManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.chat.memory.LongTermMemoryFragment
import com.susking.ephone_s.qq.ui.chat.memory.VideoCallHistoryFragment
import com.susking.ephone_s.qq.ui.chat.profile.QqAiProfileFragment
import com.susking.ephone_s.qq.ui.chat.profile.QqInnerDetailDialogFragment
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallFragment
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallState
import com.susking.ephone_s.qq.ui.sticker.StickerViewModel
import com.susking.ephone_s.qq.ui.sticker.StickerAdapter
import com.susking.ephone_s.qq.util.ImageSelector
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
@AndroidEntryPoint
class QqChatFragment : Fragment() {

    private var _binding: FragmentQqChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>
    private var selectedImagePath: String? = null
    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var imageSelector: ImageSelector
    private lateinit var stickerAdapter: StickerAdapter
 
    // 使用 Hilt 注入 ViewModel,不再需要手动创建依赖
    private val viewModel: QqViewModel by activityViewModels()
    private val stickerViewModel: StickerViewModel by viewModels()
    
    // 注入新的Manager和UseCase
    @Inject lateinit var qqChatManager: QqChatManager
    @Inject lateinit var qqContentManager: QqContentManager
    @Inject lateinit var qqTransactionManager: QqTransactionManager
    @Inject lateinit var qqContactManager: QqContactManager
    @Inject lateinit var generateImageUseCase: GenerateImageFromPromptUseCase
    @Inject lateinit var rewriteImagePromptUseCase: RewriteImagePromptUseCase
    @Inject lateinit var settingsRepository: com.susking.ephone_s.aidata.domain.repository.SettingsRepository
    @Inject lateinit var activeContactTracker: com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker
    @Inject lateinit var longTermMemoryRepository: com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
    @Inject lateinit var videoCallHistoryRepository: com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
    @Inject lateinit var followUpPolicyStore: FollowUpPolicyStore
    @Inject lateinit var aiRequestService: com.susking.ephone_s.aidata.api.AiRequestService

    // AlbumDatabaseProvider 不通过 Hilt 注入,而是直接从 Application 获取(与其他 Album Fragment 保持一致)
    private val albumDatabaseProvider by lazy {
        requireContext().applicationContext as AlbumDatabaseProvider
    }
    
    // 兼容性别名(指向新Manager)
    private val chatManager get() = qqChatManager
    private val aiManager get() = qqChatManager
    private val favoriteManager get() = qqContentManager
    private val transferManager get() = qqTransactionManager
    private val friendRequestManager get() = qqTransactionManager

    private lateinit var contactId: String
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var moreOptionsAdapter: MoreOptionsAdapter
    private var isMoreOptionsOpen = false
    private var isStickerPanelOpen = false
    private var isVoicePanelOpen = false
    private var isMultiSelectMode = false
    private var quotedMessage: ChatMessage? = null
    private var ttsAutoPlayPlayer: MediaPlayer? = null
    private var ttsStreamingAudioTrack: AudioTrack? = null
    private var typingAnimationJob: Job? = null
    private var targetTimestamp: Long? = null
    private var displayedMessageCount: Int = 0
    private var hasReachedOldestMessage: Boolean = false
    private var allVisibleMessages: List<ChatMessage> = emptyList()
    private val loadedMessageIds: MutableSet<String> = mutableSetOf()
    private var voiceRecorder: MediaRecorder? = null
    private var activeVoiceFile: File? = null
    private var readyVoiceFile: File? = null
    private var readyVoiceDurationMillis: Long = 0L
    private var voiceRecordStartMillis: Long = 0L
    private var voiceTimerJob: Job? = null
    private var autoReplyIdleJob: Job? = null
    private var autoReplyTypingJob: Job? = null
    private var autoReplySilentFollowUpJob: Job? = null
    private var isChatAutoReplyEnabled: Boolean = false
    private var chatAutoReplyIntervalSeconds: Int = DEFAULT_CHAT_AUTO_REPLY_INTERVAL_SECONDS
    private var chatFollowUpDelaySeconds: Int = DEFAULT_CHAT_FOLLOW_UP_DELAY_SECONDS
    private var isKeyboardVisible: Boolean = false
    private var isEditingMessage: Boolean = false
    private var lastAutoReplyTypingEasterEggAtMillis: Long = 0L
    
   // 用于处理冷静期倒计时的 Handler 和 Runnable
   private val cooldownHandler = Handler(Looper.getMainLooper())
   private var cooldownRunnable: Runnable? = null

    override fun onResume() {
        super.onResume()
        viewModel.setActiveContact(contactId)
        // 设置活跃联系人,防止在聊天界面时增加未读计数
        activeContactTracker.setActiveContact(contactId)
        qqContactManager.resetUnreadCount(contactId)
        // 当 Fragment 可见时，重新开始更新倒计时
        val contact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
        if (contact?.isBlocked == true) {
            startCooldownUpdates(contact)
        }
        refreshAutoReplySettingsAndScheduleSilentFollowUp()
    }

    override fun onPause() {
        triggerAutoReplyWhenLeavingChat()
        super.onPause()
        viewModel.setActiveContact(null)
        // 清除活跃联系人,允许增加未读计数
        activeContactTracker.setActiveContact(null)
       // 当 Fragment 不可见时，停止更新倒计时以节省资源
       stopCooldownUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = requireArguments().getString(ARG_CONTACT_ID)
            ?: throw IllegalArgumentException("QqChatFragment requires a contactId argument")

        requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startVoiceRecording()
            } else {
                Toast.makeText(requireContext(), "需要录音权限才能发送语音", Toast.LENGTH_SHORT).show()
            }
        }

        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val persistentPath = copyUriToInternalStorage(requireContext(), uri)
                if (persistentPath != null) {
                    selectedImagePath = persistentPath
                    binding.imagePreviewLayout.isVisible = true
                    Glide.with(this).load(selectedImagePath).into(binding.imagePreview)
                } else {
                    Toast.makeText(requireContext(), "加载图片失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        updateContactInfoInToolbar()
        setupMenu()
        setupImageSelector()
 
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val wasKeyboardVisible: Boolean = isKeyboardVisible
            isKeyboardVisible = ime.bottom > 0
            Log.d(TAG, "自动回复诊断: 键盘状态变化 old=$wasKeyboardVisible new=$isKeyboardVisible hasUnseen=${hasUnseenUserMessageLocally()} inputBlank=${binding.messageEditText.text?.isBlank() == true}")
            updateAutoReplyTypingTimer()
            if (wasKeyboardVisible && !isKeyboardVisible && hasUnseenUserMessageLocally()) {
                Log.d(TAG, "自动回复诊断: 键盘收起后重新调度普通自动回复 contactId=$contactId")
                scheduleAutoReplyAfterUserMessage()
            }
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }

        // 监听消息分组对话框结果
        childFragmentManager.setFragmentResultListener(
            MessageGroupsDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(MessageGroupsDialogFragment.RESULT_CONFIRMED)
            if (confirmed) {
                // 用户确认后，继续显示提示词确认对话框
                aiManager.proceedToPromptConfirmation()
            } else {
                // 用户取消，清理状态
                aiManager.cancelRequest()
                resetUiState()
            }
        }
        
        // 监听提示词确认对话框结果
        childFragmentManager.setFragmentResultListener(
            ConfirmAiPromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(ConfirmAiPromptDialogFragment.RESULT_CONFIRMED)
            if (confirmed) {
                aiManager.executeConfirmedResponse()
            } else {
                aiManager.cancelRequest()
                resetUiState()
            }
        }

        arguments?.getLong(ARG_TARGET_TIMESTAMP)?.let {
            if (it > 0) { // Check for default value
                targetTimestamp = it
            }
        }

        setupRecyclerView()
        setupChatContentCollapseListener()
        setupStickerPanel()
        setupVoicePanel()
        setupObservers()
        setupClickListeners()
        setupMultiSelectBar()

        if (arguments?.getBoolean(ARG_IS_LAUNCHED_FROM_SEARCH) == true) {
            binding.toolbar.isVisible = false
            binding.bottomPanel.isVisible = false
        }
    }

    private fun setupChatContentCollapseListener() {
        binding.chatRecyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                event: android.view.MotionEvent
            ): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    hideKeyboardAndClosePanelsIfNeeded()
                }
                return false
            }
        })
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatMessageAdapter(
            userAvatarUrl = viewModel.qqContactManager.userProfile.value?.avatarUri,
            contactsMap = emptyMap(),
            userNickname = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
        )
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = chatAdapter
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        displayedMessageCount = getSafeChatInitialLoadCount()
        binding.chatRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val canLoadOlderMessages: Boolean = dy < 0 && layoutManager.findFirstCompletelyVisibleItemPosition() == 0 && !hasReachedOldestMessage
                if (canLoadOlderMessages) {
                    loadOlderMessages()
                }
            }
        })

        chatAdapter.listener = object : ChatMessageInteractionListener {
            override fun onCopy(message: ChatMessage) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", message.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show()
            }

            override fun onForward(message: ChatMessage) {
                Toast.makeText(requireContext(), "转发功能待实现", Toast.LENGTH_SHORT).show()
            }

            override fun onFavorite(message: ChatMessage) {
                val senderName = if (message.role == "user") viewModel.qqContactManager.userProfile.value?.nickname ?: "我" else viewModel.qqContactManager.contacts.value?.find { it.id == message.contactId }?.remarkName ?: "对方"
                val senderAvatar = if (message.role == "user") viewModel.qqContactManager.userProfile.value?.avatarUri else viewModel.qqContactManager.contacts.value?.find { it.id == message.contactId }?.avatarUri
                favoriteManager.addFavorite(message, senderName, senderAvatar)
            }

            override fun onDelete(message: ChatMessage) {
                chatManager.deleteMessage(message)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }

            override fun onRetryError(message: ChatMessage) {
                // 取聊天列表末尾连续的错误消息一起删除（含被点击的这条），
                // 顺手清掉重试前堆积的多条错误气泡，再经由 aiManager → brain 链路直接重新发起请求。
                val trailingErrorMessages: List<ChatMessage> = allVisibleMessages.takeLastWhile { it.role == "error" }
                // 捕获被丢弃错误轮的 turnId：若同一轮里既报错又写过语义账本，重试前需据此回退那轮的语义更新，
                // 否则新提示词会读到被丢弃轮污染后的语义状态。必须在删除前采集（删除后历史里就查不到了）。
                val discardedAiTurnIds: Set<String> = trailingErrorMessages
                    .mapNotNull { errorMessage -> errorMessage.aiTurnId?.takeIf { turnId -> turnId.isNotBlank() } }
                    .toSet()
                if (trailingErrorMessages.isNotEmpty()) {
                    chatManager.deleteMessages(trailingErrorMessages)
                } else {
                    chatManager.deleteMessage(message)
                }
                aiManager.retryAiResponse(contactId, discardedAiTurnIds)
            }

            override fun onMultiSelect(message: ChatMessage) {
                if (!isMultiSelectMode) {
                    enterMultiSelectMode(message)
                }
            }

            override fun onQuote(message: ChatMessage) {
                quotedMessage = message
                binding.quotePreviewLayout.visibility = View.VISIBLE
                val senderName = if (message.role == "user") viewModel.qqContactManager.userProfile.value?.nickname ?: "我" else viewModel.qqContactManager.contacts.value?.find { it.id == message.contactId }?.remarkName ?: "对方"
                binding.quotePreviewSender.text = "回复 $senderName"
                binding.quotePreviewText.text = message.content
                binding.messageEditText.requestFocus()
            }

            override fun onEdit(message: ChatMessage) {
                when (message.type) {
                    "text", "offline_text", "voice_message" -> {
                        // 语音气泡的转写文本与普通文本一样存放在 content 中，复用文本编辑弹窗即可保存修改。
                        isEditingMessage = true
                        EditMessageDialogFragment.newInstance(message)
                            .show(childFragmentManager, EditMessageDialogFragment.TAG)
                    }
                    "sticker" -> {
                        isEditingMessage = true
                        EditStickerDialogFragment.newInstance(message)
                            .show(childFragmentManager, EditStickerDialogFragment.TAG)
                    }
                    "naiimag" -> {
                        // 如果图片消息没有提示词，则传递一个空字符串
                        isEditingMessage = true
                        EditImagePromptDialogFragment.newInstance(message.id, message.content ?: "")
                            .show(childFragmentManager, EditImagePromptDialogFragment.TAG)
                    }
                    "image_url" -> {
                        // 用户发送的图片，编辑图片描述
                        if (message.role == "user") {
                            isEditingMessage = true
                            EditImageDescriptionDialogFragment.newInstance(message)
                                .show(childFragmentManager, EditImageDescriptionDialogFragment.TAG)
                        } else {
                            Toast.makeText(requireContext(), "此消息类型不支持编辑", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "此消息类型不支持编辑", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onScreenshot(message: ChatMessage) {
                Toast.makeText(requireContext(), "截图功能待实现", Toast.LENGTH_SHORT).show()
            }

            override fun onSwitchResponse(message: ChatMessage, newIndex: Int) {
                aiManager.switchResponseVersion(message.id, message.contactId, newIndex)
            }

            override fun onContactAvatarClick(contactId: String) {
                    QqInnerDetailDialogFragment.newInstance(contactId)
                        .show(childFragmentManager, QqInnerDetailDialogFragment.TAG)
                }

            override fun onContactAvatarDoubleClick(contactId: String) {
                performDefaultPatAction(contactId)
            }

            override fun onImageBubbleClick(message: ChatMessage) {
                // 单击图片现在只用于显示/隐藏操作按钮，不直接显示大图
                // 移除 showImageViewer(message.imageUrl!!)
            }

            override fun onZoomImageClick(message: ChatMessage) {
                if (!message.imageUrl.isNullOrBlank()) {
                    ImageViewerFragment.newInstance(message.imageUrl!!)
                        .show(childFragmentManager, ImageViewerFragment.TAG)
                }
            }

            override fun onRewritePromptClick(message: ChatMessage) {
                if (message.type == "naiimag" && !message.content.isNullOrBlank()) {
                    isEditingMessage = true
                    EditImagePromptDialogFragment.newInstance(message.id, message.content!!)
                        .show(childFragmentManager, EditImagePromptDialogFragment.TAG)
                } else {
                    Toast.makeText(requireContext(), "此消息不包含可编辑的生图提示词", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onRerollImageClick(message: ChatMessage) {
                if (message.type == "naiimag" && !message.content.isNullOrBlank()) {
                    rerollImage(message.id, message.content!!)
                } else {
                    Toast.makeText(requireContext(), "此消息不是图片或缺少提示词，无法重roll", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onSaveImageToAlbum(message: ChatMessage) {
                if (!message.imageUrl.isNullOrBlank()) {
                    saveImageToAlbum(message.imageUrl!!)
                }
            }
            
            override fun onSelectionChanged(selectedCount: Int) {
                if (isMultiSelectMode) {
                    binding.contactNameTextView.text = if (selectedCount == 0) "未选择项目" else "已选择 $selectedCount 项"
                    binding.contactStatusTextView.isVisible = false
                    binding.contactStatusIcon.isVisible = false
                    activity?.invalidateOptionsMenu()
                }
            }
            override fun onWaimaiCardClick(message: ChatMessage) {
                WaimaiDetailDialogFragment.newInstance(message, this)
                    .show(childFragmentManager, WaimaiDetailDialogFragment.TAG)
            }

            override fun onTransferCardClick(message: ChatMessage) {
                if (message.status == "pending") {
                    TransferActionDialogFragment.newInstance(this) { action ->
                        when (action) {
                            TransferActionDialogFragment.ACTION_ACCEPT -> {
                                transferManager.acceptTransfer(message)
                            }
                            TransferActionDialogFragment.ACTION_DECLINE -> {
                                transferManager.declineTransfer(message)
                            }
                        }
                    }.show(childFragmentManager, TransferActionDialogFragment.TAG)
                }
            }

            override fun onAcceptFriendApplication(message: ChatMessage) {
                friendRequestManager.acceptFriendApplication(message)
            }

            override fun onDeclineFriendApplication(message: ChatMessage) {
                friendRequestManager.declineFriendApplication(message)
            }

            override fun onQuoteJump(messageId: String) {
                val position = chatAdapter.currentList.indexOfFirst { it.id == messageId }
                if (position != -1) {
                    binding.chatRecyclerView.smoothScrollToPosition(position)
                } else {
                    Toast.makeText(requireContext(), "找不到原消息", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onMessageRecalled(message: ChatMessage) {
                // 消息被撤回,更新数据库中的消息状态
                lifecycleScope.launch {
                    val updatedMessage = message.copy(
                        isRecalled = true,
                        recalledContent = message.content,
                        recallTimestamp = System.currentTimeMillis()
                    )
                    chatManager.updateMessage(updatedMessage)
                }
            }

            override fun onViewRecalledContent(message: ChatMessage) {
                // 查看被撤回消息的原始内容
                if (!message.recalledContent.isNullOrBlank()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("撤回的消息")
                        .setMessage(message.recalledContent)
                        .setPositiveButton("确定", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "无法查看原始内容", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAcceptOfflineRequest(message: ChatMessage) {
                // 用户同意线下见面请求
                AlertDialog.Builder(requireContext())
                    .setTitle("确认同意线下见面")
                    .setMessage("同意后将启用线下模式，是否确认？")
                    .setPositiveButton("确认") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                // 1. 更新消息状态为已同意
                                val updatedMessage = message.copy(status = "accepted")
                                chatManager.updateMessage(updatedMessage)
                                
                                // 2. 启用线下模式
                                val contact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
                                if (contact != null) {
                                    val updatedContact = contact.copy(offlineModeEnabled = true)
                                    qqContactManager.updateContact(updatedContact)
                                    Log.d(TAG, "线下模式已启用: contactId=$contactId")
                                }
                                
                                // 3. 获取用户名、联系人真名和格式化时间
                                val userName = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
                                val charName = contact?.realName ?: "对方"
                                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                                    .format(java.util.Date())
                                
                                // 4. 发送用户消息(role=user, type=offline_response),在对话中以系统样式渲染
                                chatManager.sendMessage(
                                    contactId = contactId,
                                    text = "${userName}于${timeStr}同意了与${charName}的见面邀请",
                                    imageUrl = null,
                                    quotedMessage = null,
                                    type = "offline_response"
                                )
                                
                                Toast.makeText(requireContext(), "已同意线下见面请求，线下模式已启用", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            override fun onDeclineOfflineRequest(message: ChatMessage) {
                // 用户拒绝线下见面请求
                lifecycleScope.launch {
                    try {
                        // 更新消息状态为已拒绝
                        val updatedMessage = message.copy(status = "rejected")
                        chatManager.updateMessage(updatedMessage)
                        
                        // 获取用户名、联系人真名和格式化时间
                        val userName = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
                        val contact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
                        val charName = contact?.realName ?: "对方"
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                            .format(java.util.Date())
                        
                        // 发送用户消息(role=user, type=offline_response),在对话中以系统样式渲染
                        chatManager.sendMessage(
                            contactId = contactId,
                            text = "${userName}于${timeStr}拒绝了与${charName}的见面邀请",
                            imageUrl = null,
                            quotedMessage = null,
                            type = "offline_response"
                        )
                        
                        Toast.makeText(requireContext(), "已拒绝线下见面请求", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onVideoCallRecordClick(message: ChatMessage) {
                // 点击视频通话结束气泡，优先显示对应的视频通话原文记录。
                lifecycleScope.launch {
                    try {
                        val videoCallId: Long? = parseVideoCallId(message.content)
                        val history = videoCallId?.let { historyId: Long ->
                            videoCallHistoryRepository.getVideoCallHistoryById(historyId)
                        }

                        if (history != null) {
                            showVideoCallHistoryDetailDialog(history)
                        } else {
                            Toast.makeText(requireContext(), "未找到对应通话记录，已打开视频通话历史", Toast.LENGTH_SHORT).show()
                            openVideoCallHistoryList()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "查询视频通话记录失败", e)
                        Toast.makeText(requireContext(), "查询视频通话记录失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        openVideoCallHistoryList()
                    }
                }
            }

            override fun onRegenerateVoice(message: ChatMessage) {
                synthesizeTtsForVoiceMessage(message, shouldForceRegenerate = true)
            }

            override fun onPlayVoiceMessage(message: ChatMessage) {
                synthesizeTtsForVoiceMessage(message, shouldForceRegenerate = false)
            }
        }
    }

    private fun synthesizeTtsForVoiceMessage(message: ChatMessage, shouldForceRegenerate: Boolean): Unit {
        if (message.type != "voice_message" || message.role != "assistant") {
            Toast.makeText(requireContext(), "只有 AI 语音气泡支持小米 MiMo TTS", Toast.LENGTH_SHORT).show()
            return
        }
        if (message.content.isNullOrBlank()) {
            Toast.makeText(requireContext(), "语音气泡正文为空，无法合成", Toast.LENGTH_SHORT).show()
            return
        }
        val cachedAudioFile: File? = message.voiceAudioPath
            ?.takeIf { path: String -> path.isNotBlank() }
            ?.let { path: String -> File(path) }
            ?.takeIf { file: File -> file.exists() && file.isFile }
        if (!shouldForceRegenerate && cachedAudioFile != null) {
            Toast.makeText(requireContext(), "已使用缓存语音，请再次点击播放", Toast.LENGTH_SHORT).show()
            return
        }
        val model: String = settingsRepository.getTtsModel().trim()
        val apiKey: String = settingsRepository.getTtsApiKey().trim()
        if (model.isBlank() || apiKey.isBlank()) {
            Toast.makeText(requireContext(), "请先在设置中配置小米 MiMo TTS Key 和模型", Toast.LENGTH_LONG).show()
            return
        }
        val contactVoiceId: String? = viewModel.qqContactManager.contacts.value
            ?.firstOrNull { contact -> contact.id == message.contactId }
            ?.ttsVoiceId
            ?.takeIf { voiceId: String -> voiceId.isNotBlank() }
        val voiceId: String = contactVoiceId ?: settingsRepository.getTtsVoiceId()

        // 校验：若使用 voicedesign 模型但未填写音色描述，弹窗提醒
        val contact = viewModel.qqContactManager.contacts.value?.firstOrNull { it.id == message.contactId }
        val voiceDescription: String = contact?.voiceDescription.orEmpty()
        if (model.contains("voicedesign", ignoreCase = true) && voiceDescription.isBlank()) {
            Toast.makeText(requireContext(), "请先在”AI 资料设置”中填写角色音色描述", Toast.LENGTH_LONG).show()
            return
        }

        val isVoiceDesign: Boolean = model.contains("voicedesign", ignoreCase = true)
        val request: TtsSynthesisRequest = TtsSynthesisRequest(
            text = message.content.orEmpty(),
            model = model,
            voiceId = voiceId,
            isStreaming = false,
            description = if (isVoiceDesign) voiceDescription else "QQ 语音气泡：${message.contactId}"
        )

        Toast.makeText(requireContext(), if (shouldForceRegenerate) "正在重新生成语音..." else "正在生成语音...", Toast.LENGTH_SHORT).show()
        chatManager.updateMessage(
            message.copy(
                ttsGenerationStatus = "generating",
                ttsModelId = model,
                ttsVoiceId = voiceId,
                ttsErrorMessage = null,
                ttsIsStreaming = request.isStreaming
            )
        )
        lifecycleScope.launch {
            val result: TtsSynthesisResult = withContext(Dispatchers.IO) {
                if (request.isStreaming) {
                    synthesizeTtsWithStreamingPlayback(request)
                } else {
                    aiRequestService.synthesizeSpeechWithLogging(request)
                }
            }
            val audioFile: File? = result.audioFile
            val updatedMessage: ChatMessage = if (audioFile != null && result.errorMessage == null) {
                message.copy(
                    voiceAudioPath = audioFile.absolutePath,
                    voiceDurationMillis = result.durationMillis,
                    ttsGenerationStatus = "success",
                    ttsModelId = result.model,
                    ttsVoiceId = result.voiceId,
                    ttsGeneratedAt = System.currentTimeMillis(),
                    ttsErrorMessage = null,
                    ttsIsStreaming = result.isStreaming
                )
            } else {
                message.copy(
                    ttsGenerationStatus = "failed",
                    ttsModelId = result.model,
                    ttsVoiceId = result.voiceId,
                    ttsGeneratedAt = System.currentTimeMillis(),
                    ttsErrorMessage = result.errorMessage ?: "语音合成失败",
                    ttsIsStreaming = result.isStreaming
                )
            }
            chatManager.updateMessage(updatedMessage)
            if (audioFile != null && result.errorMessage == null) {
                if (!result.isStreaming && !chatAdapter.hasActiveVoicePlayback()) {
                    chatAdapter.playVoiceAudioFromFragment(updatedMessage)
                }
            } else {
                Toast.makeText(requireContext(), "语音生成失败：${result.errorMessage ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun synthesizeTtsWithStreamingPlayback(request: TtsSynthesisRequest): TtsSynthesisResult {
        val audioTrack: AudioTrack = prepareTtsStreamingAudioTrack()
        val callback: TtsStreamingCallback = object : TtsStreamingCallback {
            override suspend fun onPcmChunk(pcmBytes: ByteArray): Unit {
                audioTrack.write(pcmBytes, 0, pcmBytes.size)
            }

            override suspend fun onCompleted(result: TtsSynthesisResult): Unit {
            }

            override suspend fun onFailed(result: TtsSynthesisResult): Unit {
                releaseTtsStreamingAudioTrack()
            }
        }
        return try {
            aiRequestService.synthesizeSpeechStreamingWithLogging(request, callback)
        } finally {
        }
    }

    private fun prepareTtsStreamingAudioTrack(): AudioTrack {
        releaseTtsAutoPlayPlayer()
        releaseTtsStreamingAudioTrack()
        val minBufferSize: Int = AudioTrack.getMinBufferSize(
            MIMO_TTS_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MIMO_TTS_STREAM_BUFFER_SIZE)
        val audioTrack: AudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(MIMO_TTS_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        ttsStreamingAudioTrack = audioTrack
        audioTrack.play()
        return audioTrack
    }

    private fun releaseTtsStreamingAudioTrack(): Unit {
        ttsStreamingAudioTrack?.runCatching {
            stop()
            release()
        }
        ttsStreamingAudioTrack = null
    }

    private fun playGeneratedTtsAudio(message: ChatMessage): Unit {
        releaseTtsAutoPlayPlayer()
        releaseTtsStreamingAudioTrack()
        chatAdapter.playVoiceAudioFromFragment(message)
    }

    private fun releaseTtsAutoPlayPlayer(): Unit {
        ttsAutoPlayPlayer?.release()
        ttsAutoPlayPlayer = null
    }

    private fun parseVideoCallId(content: String?): Long? {
        return try {
            val contentMap = com.google.gson.Gson().fromJson(content ?: "", Map::class.java)
            (contentMap["videoCallId"] as? Number)?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun showVideoCallHistoryDetailDialog(history: com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity) {
        val context: Context = requireContext()
        val startTimeText: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date(history.timestamp))
        val durationText: String = formatVideoCallDuration(history.duration)
        val rootLayout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(8))
            background = createFrostedGlassDialogBackground()
        }
        val titleTextView: TextView = createDetailTextView(
            text = "通话记录详情",
            textSizeSp = 20f,
            textColorAttr = com.google.android.material.R.attr.colorOnSurface
        ).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val summaryCard: com.google.android.material.card.MaterialCardView = com.google.android.material.card.MaterialCardView(context).apply {
            radius = dpToPx(18).toFloat()
            cardElevation = dpToPx(2).toFloat()
            setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = dpToPx(1)
            strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            val layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(14)
            }
            this.layoutParams = layoutParams
        }
        val summaryLayout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            addView(createDetailTextView("开始时间：$startTimeText", 14f, com.google.android.material.R.attr.colorOnSurfaceVariant))
            addView(createDetailTextView("通话时长：$durationText", 14f, com.google.android.material.R.attr.colorOnSurfaceVariant))
            addView(createDetailTextView("结束原因：${history.terminationReason ?: "未知"}", 14f, com.google.android.material.R.attr.colorOnSurfaceVariant))
            addView(createDetailTextView("通话状态：${history.callStatus}", 14f, com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        summaryCard.addView(summaryLayout)
        val transcriptTitleTextView: TextView = createDetailTextView(
            text = "通话内容",
            textSizeSp = 16f,
            textColorAttr = com.google.android.material.R.attr.colorOnSurface
        ).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(18)
                bottomMargin = dpToPx(8)
            }
            this.layoutParams = layoutParams
        }
        val messageListLayout: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }
        if (history.messages.isEmpty()) {
            messageListLayout.addView(createEmptyCallMessageTextView())
        } else {
            history.messages.forEach { message: com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity ->
                messageListLayout.addView(createVideoCallDetailBubble(message))
            }
        }
        val scrollView: ScrollView = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(messageListLayout)
        }
        rootLayout.addView(titleTextView)
        rootLayout.addView(summaryCard)
        rootLayout.addView(transcriptTitleTextView)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(360)
        ))
        val dialog: AlertDialog = AlertDialog.Builder(context)
            .setView(rootLayout)
            .setPositiveButton("确定", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            applyFrostedGlassWindowEffect(dialog)
        }
        dialog.show()
    }

    private fun createFrostedGlassDialogBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(24).toFloat()
            setColor(Color.argb(222, 255, 255, 255))
            setStroke(dpToPx(1), Color.argb(96, 255, 255, 255))
        }
    }

    private fun applyFrostedGlassWindowEffect(dialog: AlertDialog) {
        val window: android.view.Window = dialog.window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = dpToPx(22)
                dimAmount = 0.18f
            }
        } else {
            window.attributes = window.attributes.apply {
                dimAmount = 0.28f
            }
        }
    }

    private fun createVideoCallDetailBubble(message: com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity): View {
        val context: Context = requireContext()
        val isUserMessage: Boolean = message.role == "user"
        val speakerText: String = if (isUserMessage) "我" else binding.contactNameTextView.text.toString().ifBlank { "对方" }
        val messageTimeText: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date(message.timestamp))
        val bubbleTextView: TextView = TextView(context).apply {
            text = message.content
            textSize = 15f
            setTextColor(resolveThemeColor(if (isUserMessage) com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = ContextCompat.getDrawable(context, if (isUserMessage) R.drawable.bg_chat_bubble_sent else R.drawable.bg_chat_bubble_received)
        }
        val metaTextView: TextView = createDetailTextView(
            text = "$speakerText · $messageTimeText",
            textSizeSp = 12f,
            textColorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant
        ).apply {
            alpha = 0.7f
            gravity = if (isUserMessage) Gravity.END else Gravity.START
            val layoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4)
            }
            this.layoutParams = layoutParams
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUserMessage) Gravity.END else Gravity.START
            val containerParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            layoutParams = containerParams
            addView(bubbleTextView, LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.64f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(metaTextView)
        }
    }

    private fun createEmptyCallMessageTextView(): TextView {
        return createDetailTextView(
            text = "暂无通话消息记录",
            textSizeSp = 14f,
            textColorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(28), 0, dpToPx(28))
        }
    }

    private fun createDetailTextView(text: String, textSizeSp: Float, textColorAttr: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = textSizeSp
            setTextColor(resolveThemeColor(textColorAttr))
            includeFontPadding = true
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun resolveThemeColor(attribute: Int): Int {
        val typedValue: TypedValue = TypedValue()
        requireContext().theme.resolveAttribute(attribute, typedValue, true)
        return typedValue.data
    }

    private fun formatVideoCallDuration(durationSeconds: Long): String {
        val hours: Long = durationSeconds / 3600
        val minutes: Long = durationSeconds % 3600 / 60
        val seconds: Long = durationSeconds % 60
        val parts: MutableList<String> = mutableListOf()
        if (hours > 0) parts.add("${hours}小时")
        if (minutes > 0) parts.add("${minutes}分钟")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}秒")
        return parts.joinToString(separator = "")
    }

    private fun openVideoCallHistoryList() {
        triggerAutoReplyWhenEnteringChildPage()
        parentFragmentManager.beginTransaction()
            .replace(com.susking.ephone_s.qq.R.id.fragment_container_for_chat, VideoCallHistoryFragment.newInstance(contactId))
            .addToBackStack(null)
            .commit()
    }

    private fun updateContactInfoInToolbar() {
        val currentContact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
        binding.contactNameTextView.text = currentContact?.remarkName ?: ""
        val statusText = currentContact?.statusText.let { if (it.isNullOrBlank()) "离线" else it }
        binding.contactStatusTextView.text = statusText
        binding.contactStatusTextView.isVisible = true
        binding.contactStatusIcon.isVisible = statusText == "在线"
    }

    private fun setupObservers() {
        registerEditingDialogLifecycleObserver()

        // 观察后台活动开关的状态
        viewModel.isBackgroundActivityEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.blockedContactPanel.backgroundActivitySwitch.isChecked = isEnabled
        }

        viewModel.qqContactManager.userProfile.observe(viewLifecycleOwner) { userProfile ->
            userProfile?.let {
                chatAdapter.updateUserAvatarUrl(it.avatarUri)
                // 当用户信息更新时，同步更新Adapter中的用户昵称
                chatAdapter.updateUserNickname(it.nickname)
            }
        }

        viewModel.qqContactManager.contacts.observe(viewLifecycleOwner) { contactsList ->
            val newContactsMap = contactsList.associateBy { it.id }
            chatAdapter.updateContactsMap(newContactsMap)
            if (!isMultiSelectMode) {
                updateContactInfoInToolbar()
            }
             val oldContact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
            val newContact = contactsList.find { it.id == contactId }
            if (oldContact?.statusText != newContact?.statusText) {
                updateContactInfoInToolbar()
            }

            val currentContact = newContactsMap[contactId]
            if (!currentContact?.chatBackgroundUri.isNullOrBlank()) {
                Glide.with(this)
                    .load(currentContact?.chatBackgroundUri)
                    .into(binding.chatBackgroundImage)
            } else {
                binding.chatBackgroundImage.setImageDrawable(null)
                binding.chatBackgroundImage.setBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurface))
            }
           // 根据联系人拉黑状态更新UI
           if (currentContact != null) {
               if (currentContact.isBlocked) {
                   // 当联系人被拉黑时显示拉黑面板
                   binding.bottomPanel.isVisible = true
                   binding.inputLayout.isVisible = false
                   binding.iconBar.isVisible = false
                   binding.blockedContactPanel.root.isVisible = true
                   startCooldownUpdates(currentContact)
               } else {
                   // 解除拉黑时，恢复UI
                   stopCooldownUpdates()
                   binding.bottomPanel.isVisible = true
                   binding.inputLayout.isVisible = true
                   binding.iconBar.isVisible = true
                   binding.blockedContactPanel.root.isVisible = false
               }
           }
        }

        childFragmentManager.setFragmentResultListener(
            EditMessageDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val messageId = bundle.getString(EditMessageDialogFragment.RESULT_MESSAGE_ID) ?: return@setFragmentResultListener
            val newText = bundle.getString(EditMessageDialogFragment.RESULT_NEW_TEXT) ?: return@setFragmentResultListener
            chatManager.editMessage(messageId, contactId, newText)
            Toast.makeText(requireContext(), "消息已更新", Toast.LENGTH_SHORT).show()
        }

        childFragmentManager.setFragmentResultListener(
            EditStickerDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val messageId = bundle.getString(EditStickerDialogFragment.RESULT_MESSAGE_ID) ?: return@setFragmentResultListener
            val newUrl = bundle.getString(EditStickerDialogFragment.RESULT_NEW_URL) ?: return@setFragmentResultListener
            chatManager.updateStickerUrl(messageId, contactId, newUrl, "")
            Toast.makeText(requireContext(), "表情已更新", Toast.LENGTH_SHORT).show()
        }

        childFragmentManager.setFragmentResultListener(
            EditImageDescriptionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val messageId = bundle.getString(EditImageDescriptionDialogFragment.RESULT_MESSAGE_ID) ?: return@setFragmentResultListener
            val newDescription = bundle.getString(EditImageDescriptionDialogFragment.RESULT_NEW_DESCRIPTION) ?: ""
            chatManager.updateImageDescription(messageId, contactId, newDescription)
            Toast.makeText(requireContext(), "图片描述已更新", Toast.LENGTH_SHORT).show()
        }

        // 监听消息分组事件（在提示词确认之前显示）
        aiManager.messageGroupsEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { analysis ->
                MessageGroupsDialogFragment.newInstance(analysis)
                    .show(childFragmentManager, MessageGroupsDialogFragment.TAG)
            }
        }
        
        // 监听提示词确认事件
        aiManager.promptConfirmationEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { promptRequest ->
                // 【修改】直接使用预先生成好的JSON字符串，不再进行二次序列化
                ConfirmAiPromptDialogFragment.newInstance(
                    promptJson = promptRequest.displayPromptJson,
                    url = promptRequest.url,
                    model = promptRequest.request.model,
                    timestamp = promptRequest.timestamp
                ).show(childFragmentManager, ConfirmAiPromptDialogFragment.TAG)
            }
        }

        observeFollowUpPolicyChanges()

        // AI 发起的视频通话来电已统一收归 MainActivity 的全局 EventBus.IncomingCallEvent 消费，
        // 不再在此监听 incomingCallEvent 桥接。这样无论用户在桌面、其他 App 内页还是聊天页，
        // AI 来电都能在最顶层弹出来电界面，不再绑死聊天页的 viewLifecycleOwner 生命周期。

        childFragmentManager.setFragmentResultListener(
            EditImagePromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val messageId = bundle.getString(EditImagePromptDialogFragment.RESULT_MESSAGE_ID) ?: return@setFragmentResultListener
            val action = bundle.getString(EditImagePromptDialogFragment.RESULT_ACTION)

            if (action == EditImagePromptDialogFragment.ACTION_AI_REWRITE) {
                val specialRequirements = bundle.getString(EditImagePromptDialogFragment.RESULT_SPECIAL_REQUIREMENTS)
                val includeOriginalPrompt = bundle.getBoolean(EditImagePromptDialogFragment.RESULT_INCLUDE_ORIGINAL_PROMPT, true)
                rewriteAndRegenerateImage(messageId, specialRequirements, includeOriginalPrompt)
            } else {
                val newPrompt = bundle.getString(EditImagePromptDialogFragment.RESULT_NEW_PROMPT) ?: return@setFragmentResultListener
                regenerateImage(messageId, newPrompt)
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val initialLoadCount: Int = getSafeChatInitialLoadCount()
                displayedMessageCount = initialLoadCount
                chatManager.getLatestMessagesPagedFlow(contactId, initialLoadCount).collect { messages ->
                    // 关键修复：聊天界面只监听最新一页消息，避免进入聊天时加载全量历史导致卡顿。
                    if (messages.isEmpty()) {
                        loadedMessageIds.clear()
                        allVisibleMessages = emptyList()
                        hasReachedOldestMessage = true
                        chatAdapter.submitList(allVisibleMessages)
                        return@collect
                    }

                    loadedMessageIds.addAll(messages.map { it.id })
                    allVisibleMessages = mergeLatestVisibleMessages(allVisibleMessages, messages)
                    hasReachedOldestMessage = messages.size < initialLoadCount && allVisibleMessages.size <= messages.count { !it.isHidden }
                    Log.d("QqChatFragment", "Collecting latest page. Current item count: ${chatAdapter.itemCount}")
                    // 已读状态由数据库 Flow 异步刷新到 allVisibleMessages。
                    // AI 回复刚完成时，回复完成/策略变更/typing 三个调度点会先于已读状态落地，
                    // 全部因本地仍是“未读”而提前 return，导致追问漏调度。
                    // 这里在每次本地消息刷新后补一次调度：当“未读→已读”真正落地，便用最新状态重排追问，
                    // 避免用户停留在当前页不动时追问永远排不上。
                    scheduleAutoReplySilentFollowUpAfterAiResponse()
                    val isAtBottom: Boolean = isScrolledToBottom()

                    chatAdapter.submitList(allVisibleMessages) {
                        if (view != null) {
                            targetTimestamp?.let { timestamp ->
                                scrollToMessage(timestamp)
                                targetTimestamp = null
                            }

                            if (isAtBottom && allVisibleMessages.isNotEmpty()) {
                                Log.d("QqChatFragment", "Condition met. Scrolling to bottom.")
                                binding.chatRecyclerView.post {
                                    binding.chatRecyclerView.smoothScrollToPosition(allVisibleMessages.size - 1)
                                }
                            }
                            resetUiState()
                        }
                    }
                }
            }
        }
        
        childFragmentManager.setFragmentResultListener(
            EditRawMessageDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val newJson = bundle.getString(EditRawMessageDialogFragment.RESULT_RAW_JSON)
            if (newJson != null) {
                // TODO: 实现更新AI原始消息逻辑
                // aiManager.updateLastAiTurnWithRawJson(contactId, newJson)
                Toast.makeText(requireContext(), "原始消息已更新", Toast.LENGTH_SHORT).show()
            }
        }

        // 视频通话界面的拉起/移除已统一收归 MainActivity 的顶层 video_call_fragment_container。
        // 此处不再监听 videoCallState 自行拉起 VideoCallFragment，避免与 MainActivity 双重拉起
        // 导致叠加多个 Fragment 实例。通话结束的提示由 VideoCallFragment 自身收尾。

        viewLifecycleOwner.lifecycleScope.launch {
            aiManager.isAiTyping.collect { typingMap ->
                val isTyping: Boolean = typingMap[contactId] ?: false
                if (isTyping) {
                    cancelAutoReplySilentFollowUpJob()
                    startTypingAnimation()
                } else {
                    stopTypingAnimation()
                    scheduleAutoReplySilentFollowUpAfterAiResponse()
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.getChatAutoReplyEnabledFlow().collect { isEnabled ->
                isChatAutoReplyEnabled = isEnabled
                binding.acceptButton.isVisible = !isEnabled
                if (!isEnabled) {
                    cancelAutoReplyJobs()
                } else {
                    chatAutoReplyIntervalSeconds = settingsRepository.getChatAutoReplyIntervalSeconds().coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
                    chatFollowUpDelaySeconds = settingsRepository.getChatFollowUpDelaySeconds().coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
                    scheduleAutoReplyAfterUserMessage()
                    updateAutoReplyTypingTimer()
                    scheduleAutoReplySilentFollowUpAfterAiResponse()
                }
            }
        }
    }

    private fun getSafeChatInitialLoadCount(): Int {
        val configuredCount: Int = settingsRepository.getChatInitialLoadCount()
        return if (configuredCount > 0) configuredCount else DEFAULT_CHAT_INITIAL_LOAD_COUNT
    }

    private fun loadOlderMessages(): Unit {
        if (allVisibleMessages.isEmpty() || hasReachedOldestMessage) {
            return
        }

        val layoutManager: LinearLayoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val previousItemCount: Int = chatAdapter.itemCount
        val firstVisiblePosition: Int = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val firstVisibleTop: Int = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0
        val loadCount: Int = getSafeChatInitialLoadCount()
        val offset: Int = loadedMessageIds.size

        viewLifecycleOwner.lifecycleScope.launch {
            val pagedMessages: List<ChatMessage> = withContext(Dispatchers.IO) {
                chatManager.getMessagesPaged(contactId, loadCount, offset)
            }
            loadedMessageIds.addAll(pagedMessages.map { it.id })
            val olderMessages: List<ChatMessage> = pagedMessages.filter { !it.isHidden }
            if (pagedMessages.isEmpty()) {
                hasReachedOldestMessage = true
                return@launch
            }

            allVisibleMessages = mergeVisibleMessages(olderMessages, allVisibleMessages)
            displayedMessageCount = allVisibleMessages.size
            hasReachedOldestMessage = pagedMessages.size < loadCount

            chatAdapter.submitList(allVisibleMessages) {
                val insertedItemCount: Int = chatAdapter.itemCount - previousItemCount
                val restoredPosition: Int = firstVisiblePosition + insertedItemCount.coerceAtLeast(0)
                layoutManager.scrollToPositionWithOffset(restoredPosition, firstVisibleTop)
            }
        }
    }

    private fun mergeVisibleMessages(
        oldMessages: List<ChatMessage>,
        newMessages: List<ChatMessage>
    ): List<ChatMessage> {
        return (oldMessages + newMessages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    private fun mergeLatestVisibleMessages(
        currentVisibleMessages: List<ChatMessage>,
        latestMessages: List<ChatMessage>
    ): List<ChatMessage> {
        val latestWindowStartTimestamp: Long = latestMessages.minOf { it.timestamp }
        val latestVisibleMessages: List<ChatMessage> = latestMessages.filter { !it.isHidden }
        val olderCachedMessages: List<ChatMessage> = currentVisibleMessages.filter { message ->
            message.timestamp < latestWindowStartTimestamp
        }

        // 最新页 Flow 代表数据库当前最新窗口的权威状态。
        // 因此只保留窗口之前的旧分页缓存，窗口内消息完全以 Flow 结果为准，避免已删除气泡被旧缓存合并回来。
        return (olderCachedMessages + latestVisibleMessages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    private fun isScrolledToBottom(): Boolean {
        val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager ?: return false
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        val itemCount = chatAdapter.itemCount
        Log.d("QqChatFragment", "isScrolledToBottom check: lastVisible=$lastVisibleItemPosition, itemCount=$itemCount")

        // 关键修复：如果适配器当前为空，则不能认为它“在底部”，以防止在视图恢复期间发生不必要的滚动。
        if (itemCount == 0) {
            return false
        }

        // 如果最后一个可见项目的位置无效（例如，在布局计算之前），则不应触发自动滚动
        if (lastVisibleItemPosition < 0) {
            return false
        }

        // 核心逻辑：检查最后一个可见的项目是否接近列表末尾
        return lastVisibleItemPosition >= itemCount - 2
    }

    private fun scrollToMessage(timestamp: Long) {
        val position = chatAdapter.currentList.indexOfFirst { it.timestamp == timestamp }
        if (position != -1) {
            // 将消息滚动到屏幕中央
            val layoutManager = binding.chatRecyclerView.layoutManager as? LinearLayoutManager
            val recyclerViewHeight = binding.chatRecyclerView.height
            // 估算一个偏移量，使其大致位于中心。
            // 一个更精确的方法需要知道item的高度，但对于大多数情况来说，屏幕高度的一半减去一个固定值就足够了。
            val offset = recyclerViewHeight / 2 - 100 // 100px as a rough item half-height
            layoutManager?.scrollToPositionWithOffset(position, offset)
        } else {
            // 只有在没有待处理滚动时才显示Toast，避免在成功滚动后还显示
            if (targetTimestamp == null) {
                Toast.makeText(requireContext(), "找不到指定消息", Toast.LENGTH_SHORT).show()
            }
        }
    }
 
     private fun setupClickListeners() {
         binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString().trim()
            val imageUrl = selectedImagePath

            if (text.isNotEmpty() || imageUrl != null || quotedMessage != null) {
                val quoteToSend = quotedMessage?.let {
                    val senderName = if (it.role == "user") viewModel.qqContactManager.userProfile.value?.nickname ?: "我" else viewModel.qqContactManager.contacts.value?.find { c -> c.id == it.contactId }?.remarkName ?: "对方"
                    com.susking.ephone_s.aidata.domain.model.QuotedMessage(
                        messageId = it.id,
                        senderName = senderName,
                        content = it.content ?: ""
                    )
                }
                val messageType = if (imageUrl != null) "image_url" else "text"
                chatManager.sendMessage(
                    contactId = contactId,
                    text = text.ifEmpty { null },
                    imageUrl = imageUrl,
                    quotedMessage = quoteToSend,
                    type = messageType
                )
                binding.messageEditText.text.clear()
                Log.d(TAG, "自动回复诊断: 用户发送消息后请求调度 contactId=$contactId keyboardVisible=$isKeyboardVisible")
                cancelAutoReplySilentFollowUpJob()
                scheduleAutoReplyAfterUserMessage()
                selectedImagePath = null
                binding.imagePreviewLayout.isVisible = false
                binding.imagePreview.setImageDrawable(null)
                quotedMessage = null
                binding.quotePreviewLayout.visibility = View.GONE
            }
        }

        binding.acceptButton.setOnClickListener {
            binding.acceptButton.isEnabled = false
            binding.repeatButton.isEnabled = false
            binding.repeatButton.alpha = 0.5f
            aiManager.requestAiResponse(contactId)
        }

        binding.photoButton.setOnClickListener {
            // 隐藏输入法并关闭所有面板
            hideKeyboardAndClosePanels()
            val cropOptions = CropImageOptions(
                // 这里可以自定义裁剪选项，例如：
                // fixAspectRatio = true,
                // aspectRatioX = 1,
                // aspectRatioY = 1,
                // outputCompressQuality = 80
            )
            imageSelector.showSelectionDialog("选择图片", cropOptions, showClearOption = false)
        }
 
        binding.cancelImageButton.setOnClickListener {
            selectedImagePath = null
            binding.imagePreviewLayout.isVisible = false
            binding.imagePreview.setImageDrawable(null)
        }

        binding.repeatButton.setOnClickListener {
            // 隐藏输入法并关闭所有面板
            hideKeyboardAndClosePanels()
            binding.acceptButton.isEnabled = false
            binding.repeatButton.isEnabled = false
            binding.repeatButton.alpha = 0.5f
            aiManager.regenerateResponse(contactId)
            Toast.makeText(requireContext(), "正在准备...", Toast.LENGTH_SHORT).show()
        }

        binding.emojiButton.setOnClickListener {
            // 隐藏输入法
            hideKeyboard()
            toggleStickerPanel()
        }

        binding.voiceButton.setOnClickListener {
            // 隐藏输入法
            hideKeyboard()
            toggleVoicePanel()
        }
        
        setupMoreOptions()
        binding.moreButton.setOnClickListener {
            // 隐藏输入法
            hideKeyboard()
            toggleMoreOptions()
        }

        binding.blockedContactPanel.backgroundActivitySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBackgroundActivityEnabled(isChecked)
        }

        binding.cancelQuoteButton.setOnClickListener {
            quotedMessage = null
            binding.quotePreviewLayout.visibility = View.GONE
        }
        
        // 监听输入框焦点变化
        binding.messageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 输入框获得焦点时,关闭所有面板
                closeAllPanels()
            }
        }

        binding.messageEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleAutoReplyAfterUserMessage()
                updateAutoReplyTypingTimer()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }


    private fun setupStickerPanel() {
        // 设置表情网格RecyclerView
        stickerAdapter = StickerAdapter(
            onStickerClick = { sticker ->
                if (stickerViewModel.isManagementMode.value) {
                    stickerAdapter.toggleSelection(sticker.id)
                } else {
                    chatManager.sendSticker(contactId, sticker)
                    // 发送后保持面板打开,方便连续发送多个表情
                }
            },
            onStickerLongClick = { sticker ->
                if (!stickerViewModel.isManagementMode.value) {
                    stickerViewModel.toggleManagementMode()
                    stickerAdapter.toggleSelection(sticker.id)
                }
            },
            onUploadClick = {
                com.susking.ephone_s.qq.ui.sticker.UploadStickerDialogFragment()
                    .show(childFragmentManager, com.susking.ephone_s.qq.ui.sticker.UploadStickerDialogFragment.TAG)
            }
        )
        binding.stickerPanel.stickerGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = stickerAdapter
            itemAnimator = null
        }
        
        // 设置按钮监听：普通模式通过长按表情进入管理模式，顶部只保留管理模式完成按钮。
        binding.stickerPanel.doneButton.setOnClickListener {
            stickerViewModel.toggleManagementMode()
        }
        binding.stickerPanel.selectAllButton.setOnClickListener {
            stickerAdapter.selectAll()
        }
        binding.stickerPanel.stickerDeleteButton.setOnClickListener {
            val selected = stickerAdapter.getSelectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "请选择要删除的表情", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 删除前二次确认，避免误删表情
            AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除选中的 ${selected.size} 个表情吗？此操作无法撤销。")
                .setPositiveButton("删除") { dialog, _ ->
                    stickerViewModel.deleteStickers(selected)
                    stickerAdapter.clearSelection()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.stickerPanel.pinButton.setOnClickListener {
            val selectedStickers = stickerAdapter.getSelectedItems()
            if (selectedStickers.isEmpty()) {
                Toast.makeText(requireContext(), "请选择要置顶的表情", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stickerViewModel.pinStickers(selectedStickers)
            stickerAdapter.clearSelection()
            stickerViewModel.toggleManagementMode()
        }
        binding.stickerPanel.exportButton.setOnClickListener {
            Toast.makeText(requireContext(), "导出功能待实现", Toast.LENGTH_SHORT).show()
        }
        binding.stickerPanel.renameButton.setOnClickListener {
            showStickerRenameDialog()
        }

        // 设置内嵌搜索栏：输入时实时过滤，清空按钮用于快速恢复完整表情列表。
        binding.stickerPanel.searchStickerEditText.setText(stickerViewModel.searchTerm.value)
        binding.stickerPanel.searchStickerEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                stickerViewModel.setSearchTerm(s?.toString()?.trim() ?: "")
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.stickerPanel.clearSearchButton.setOnClickListener {
            binding.stickerPanel.searchStickerEditText.setText("")
            binding.stickerPanel.stickerGrid.post {
                binding.stickerPanel.stickerGrid.scrollToPosition(0)
            }
        }
        
        // 观察ViewModel数据
        viewLifecycleOwner.lifecycleScope.launch {
            stickerViewModel.filteredStickers.collectLatest { stickers ->
                stickerAdapter.submitList(stickers)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            stickerViewModel.isManagementMode.collectLatest { isManagementMode ->
                updateStickerPanelUiForManagementMode(isManagementMode)
            }
        }
    }
    
    private fun updateStickerPanelUiForManagementMode(isManagementMode: Boolean) {
        stickerAdapter.setManagementMode(isManagementMode)
        binding.stickerPanel.doneButton.isVisible = isManagementMode
        binding.stickerPanel.bottomActionBar.isVisible = !isManagementMode
        binding.stickerPanel.managementActionBar.isVisible = isManagementMode
    }

    /**
     * 弹出表情重命名对话框。
     * 仅当管理模式下恰好选中一个表情时可用，否则提示用户。
     * 重命名后列表由 Room Flow 自动刷新，无需手动通知。
     */
    private fun showStickerRenameDialog() {
        val selectedStickers = stickerAdapter.getSelectedItems()
        if (selectedStickers.size != 1) {
            Toast.makeText(requireContext(), "请仅选择一个表情进行重命名", Toast.LENGTH_SHORT).show()
            return
        }
        val targetSticker = selectedStickers.first()
        val editText = android.widget.EditText(requireContext()).apply {
            setText(targetSticker.name)
            setSelection(text.length)
            hint = "请输入表情名称"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("重命名表情")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val newName = editText.text.toString().trim()
                // 重名校验：除自身外，不允许与其它表情同名
                val isDuplicateName: Boolean = stickerViewModel.allStickers.value.any {
                    it.id != targetSticker.id && it.name == newName
                }
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                } else if (isDuplicateName) {
                    Toast.makeText(requireContext(), "已存在同名表情，请换一个名称", Toast.LENGTH_SHORT).show()
                } else if (newName != targetSticker.name) {
                    stickerViewModel.renameSticker(targetSticker, newName)
                    stickerAdapter.clearSelection()
                    stickerViewModel.toggleManagementMode()
                    Toast.makeText(requireContext(), "已重命名", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }


    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupVoicePanel() {
        binding.voicePanel.voiceRecordButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    executeVoiceRecordStart()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    executeVoiceRecordStopAndTranscribe()
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    executeVoiceRecordCancel()
                    true
                }
                else -> false
            }
        }

        binding.voicePanel.voiceCancelArea.setOnClickListener {
            executeVoiceDraftClear(deleteAudioFile = true)
            Toast.makeText(requireContext(), "已清空", Toast.LENGTH_SHORT).show()
        }

        binding.voicePanel.voiceRetryRecognizeButton.setOnClickListener {
            executeVoiceRetryTranscribe()
        }

        binding.voicePanel.voiceConfirmButton.setOnClickListener {
            executeVoiceMessageSend()
        }
    }

    private fun executeVoiceRecordStart() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
            return
        }
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceRecording() {
        if (voiceRecorder != null) {
            return
        }
        val audioFile: File = createVoiceAudioFile()
        activeVoiceFile = audioFile
        readyVoiceFile = null
        readyVoiceDurationMillis = 0L
        binding.voicePanel.voiceTextInput.setText("")
        binding.voicePanel.voiceHintText.text = "正在录音，松开发送识别"
        binding.voicePanel.voiceTimerText.visibility = View.VISIBLE
        binding.voicePanel.voiceTimerText.alpha = 1f
        binding.voicePanel.voiceCancelArea.visibility = View.VISIBLE
        binding.voicePanel.voiceCancelArea.alpha = 1f
        binding.voicePanel.voiceOuterCircle.visibility = View.VISIBLE
        binding.voicePanel.voiceOuterCircle.alpha = 1f
        binding.voicePanel.voiceRetryRecognizeButton.visibility = View.GONE
        binding.voicePanel.voiceRetryRecognizeButton.alpha = 0f
        binding.voicePanel.voiceConfirmButton.visibility = View.GONE
        binding.voicePanel.voiceConfirmButton.alpha = 0f
        voiceRecordStartMillis = System.currentTimeMillis()
        voiceRecorder = createMediaRecorder(audioFile).apply {
            prepare()
            start()
        }
        startVoiceTimerUpdates()
    }

    private fun executeVoiceRecordStopAndTranscribe() {
        val audioFile: File = activeVoiceFile ?: return
        val durationMillis: Long = (System.currentTimeMillis() - voiceRecordStartMillis).coerceAtLeast(0L)
        stopVoiceRecorder()
        stopVoiceTimerUpdates()
        binding.voicePanel.voiceOuterCircle.visibility = View.INVISIBLE
        binding.voicePanel.voiceOuterCircle.alpha = 0f
        if (durationMillis < MIN_VOICE_DURATION_MILLIS) {
            audioFile.delete()
            executeVoiceDraftClear(deleteAudioFile = false)
            Toast.makeText(requireContext(), "语音太短了，小北再说一次嘛", Toast.LENGTH_SHORT).show()
            return
        }
        readyVoiceFile = audioFile
        readyVoiceDurationMillis = durationMillis
        executeVoiceTranscribe(audioFile)
    }

    private fun executeVoiceRecordCancel() {
        val recordingFile: File? = activeVoiceFile
        stopVoiceRecorder()
        stopVoiceTimerUpdates()
        recordingFile?.delete()
        executeVoiceDraftClear(deleteAudioFile = true)
    }

    private fun executeVoiceRetryTranscribe() {
        val audioFile: File = readyVoiceFile ?: run {
            Toast.makeText(requireContext(), "请先录制语音", Toast.LENGTH_SHORT).show()
            return
        }
        if (!audioFile.exists() || !audioFile.isFile) {
            Toast.makeText(requireContext(), "录音文件不存在，请重新录音", Toast.LENGTH_SHORT).show()
            executeVoiceDraftClear(deleteAudioFile = false)
            return
        }
        executeVoiceTranscribe(audioFile)
    }

    private fun executeVoiceTranscribe(audioFile: File) {
        binding.voicePanel.voiceHintText.text = "正在识别语音..."
        binding.voicePanel.voiceTextInput.setText("识别中...")
        binding.voicePanel.voiceRetryRecognizeButton.visibility = View.GONE
        binding.voicePanel.voiceRetryRecognizeButton.alpha = 0f
        binding.voicePanel.voiceConfirmButton.visibility = View.GONE
        binding.voicePanel.voiceConfirmButton.alpha = 0f
        viewLifecycleOwner.lifecycleScope.launch {
            val transcript: String? = withContext(Dispatchers.IO) {
                com.susking.ephone_s.aidata.api.AiDataApi.getAiRequestService().transcribeAudioWithLogging(
                    audioFile = audioFile,
                    mimeType = VOICE_AUDIO_MIME_TYPE,
                    description = "QQ语音消息转写"
                )
            }
            if (transcript.isNullOrBlank()) {
                binding.voicePanel.voiceTextInput.setText("")
                binding.voicePanel.voiceHintText.text = "识别失败，可重新识别或重新录音"
                binding.voicePanel.voiceRetryRecognizeButton.visibility = View.VISIBLE
                binding.voicePanel.voiceRetryRecognizeButton.alpha = 1f
                binding.voicePanel.voiceConfirmButton.visibility = View.GONE
                binding.voicePanel.voiceConfirmButton.alpha = 0f
                Toast.makeText(requireContext(), "语音识别失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            binding.voicePanel.voiceTextInput.setText(transcript)
            binding.voicePanel.voiceHintText.text = "确认后发送真实录音"
            binding.voicePanel.voiceRetryRecognizeButton.visibility = View.VISIBLE
            binding.voicePanel.voiceRetryRecognizeButton.alpha = 1f
            binding.voicePanel.voiceConfirmButton.visibility = View.VISIBLE
            binding.voicePanel.voiceConfirmButton.alpha = 1f
        }
    }

    private fun executeVoiceDraftClear(deleteAudioFile: Boolean) {
        if (deleteAudioFile) {
            readyVoiceFile?.delete()
            activeVoiceFile?.delete()
        }
        readyVoiceFile = null
        activeVoiceFile = null
        readyVoiceDurationMillis = 0L
        binding.voicePanel.voiceTextInput.setText("")
        binding.voicePanel.voiceHintText.text = "按住说话"
        binding.voicePanel.voiceTimerText.text = "0:00"
        binding.voicePanel.voiceTimerText.visibility = View.INVISIBLE
        binding.voicePanel.voiceTimerText.alpha = 0f
        binding.voicePanel.voiceCancelArea.visibility = View.GONE
        binding.voicePanel.voiceCancelArea.alpha = 0f
        binding.voicePanel.voiceOuterCircle.visibility = View.INVISIBLE
        binding.voicePanel.voiceOuterCircle.alpha = 0f
        binding.voicePanel.voiceRetryRecognizeButton.visibility = View.GONE
        binding.voicePanel.voiceRetryRecognizeButton.alpha = 0f
        binding.voicePanel.voiceConfirmButton.visibility = View.GONE
        binding.voicePanel.voiceConfirmButton.alpha = 0f
    }

    private fun executeVoiceMessageSend() {
        val audioFile: File = readyVoiceFile ?: run {
            Toast.makeText(requireContext(), "请先录制语音", Toast.LENGTH_SHORT).show()
            return
        }
        val transcript: String = binding.voicePanel.voiceTextInput.text?.toString()?.trim().orEmpty()
        if (transcript.isBlank()) {
            Toast.makeText(requireContext(), "转写文字为空，不能发送", Toast.LENGTH_SHORT).show()
            return
        }
        val quoteToSend = quotedMessage?.let {
            val senderName: String = if (it.role == "user") {
                viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
            } else {
                viewModel.qqContactManager.contacts.value?.find { contact -> contact.id == it.contactId }?.remarkName ?: "对方"
            }
            com.susking.ephone_s.aidata.domain.model.QuotedMessage(
                messageId = it.id,
                senderName = senderName,
                content = it.content ?: ""
            )
        }
        chatManager.sendMessage(
            contactId = contactId,
            text = transcript,
            type = "voice_message",
            voiceAudioPath = audioFile.absolutePath,
            voiceDurationMillis = readyVoiceDurationMillis,
            quotedMessage = quoteToSend
        )
        quotedMessage = null
        binding.quotePreviewLayout.visibility = View.GONE
        executeVoiceDraftClear(deleteAudioFile = false)
        closeVoicePanel()
    }

    private fun createVoiceAudioFile(): File {
        val voiceDirectory = File(requireContext().filesDir, VOICE_AUDIO_DIRECTORY_NAME)
        if (!voiceDirectory.exists()) {
            voiceDirectory.mkdirs()
        }
        return File(voiceDirectory, "voice_${System.currentTimeMillis()}_${UUID.randomUUID()}.m4a")
    }

    private fun createMediaRecorder(audioFile: File): MediaRecorder {
        val recorder: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(VOICE_AUDIO_SAMPLE_RATE)
        recorder.setAudioEncodingBitRate(VOICE_AUDIO_BIT_RATE)
        recorder.setOutputFile(audioFile.absolutePath)
        return recorder
    }

    private fun stopVoiceRecorder() {
        val recorder: MediaRecorder = voiceRecorder ?: return
        try {
            recorder.stop()
        } catch (e: RuntimeException) {
            Log.e(TAG, "停止录音失败", e)
        } finally {
            recorder.release()
            voiceRecorder = null
            activeVoiceFile = null
        }
    }

    private fun startVoiceTimerUpdates() {
        voiceTimerJob?.cancel()
        voiceTimerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val durationMillis: Long = System.currentTimeMillis() - voiceRecordStartMillis
                binding.voicePanel.voiceTimerText.text = formatVoiceDuration(durationMillis)
                delay(VOICE_TIMER_UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopVoiceTimerUpdates() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
    }

    private fun formatVoiceDuration(durationMillis: Long): String {
        val totalSeconds: Long = (durationMillis / 1000L).coerceAtLeast(0L)
        val minutes: Long = totalSeconds / 60L
        val seconds: Long = totalSeconds % 60L
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
    private fun toggleVoicePanel() {
        if (isVoicePanelOpen) {
            // 关闭语音面板
            closeVoicePanel()
        } else {
            // 判断是否有其他面板已打开(内部切换)
            val hasOtherPanelOpen = isMoreOptionsOpen || isStickerPanelOpen

            // 关闭其他面板(直接切换,无动画)
            if (isMoreOptionsOpen) closeMoreOptionsPanelDirectly()
            if (isStickerPanelOpen) closeStickerPanelDirectly()

            // 打开语音面板
            openVoicePanel(withAnimation = !hasOtherPanelOpen)
        }
    }

    private fun openVoicePanel(withAnimation: Boolean = true) {
        isVoicePanelOpen = true

        // 设置语音面板高度为屏幕的45%
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()

        val layoutParams = binding.voicePanel.root.layoutParams
        layoutParams.height = panelHeight
        binding.voicePanel.root.layoutParams = layoutParams

        binding.voicePanel.root.isVisible = true

        if (withAnimation) {
            // 展开动画
            binding.voicePanel.root.translationY = panelHeight.toFloat()
            binding.voicePanel.root.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            // 直接显示,无动画
            binding.voicePanel.root.translationY = 0f
        }
    }

    private fun closeVoicePanel() {
        isVoicePanelOpen = false

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()

        // 收起动画
        binding.voicePanel.root.animate()
            .translationY(panelHeight.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.voicePanel.root.isVisible = false
                binding.voicePanel.root.translationY = 0f
                scheduleAutoReplyAfterInteractionCollapsed("voice_panel")
            }
            .start()
    }

    private fun closeVoicePanelDirectly() {
        isVoicePanelOpen = false
        binding.voicePanel.root.isVisible = false
        binding.voicePanel.root.translationY = 0f
        scheduleAutoReplyAfterInteractionCollapsed("voice_panel_direct")
    }
    private fun toggleStickerPanel() {
        if (isStickerPanelOpen) {
            // 关闭表情面板
            closeStickerPanel()
        } else {
            // 判断是否有其他面板已打开(内部切换)
            val hasOtherPanelOpen = isMoreOptionsOpen || isVoicePanelOpen
            
            // 关闭其他面板(直接切换,无动画)
            if (isMoreOptionsOpen) closeMoreOptionsPanelDirectly()
            if (isVoicePanelOpen) closeVoicePanelDirectly()
            
            // 打开表情面板
            openStickerPanel(withAnimation = !hasOtherPanelOpen)
        }
    }
    
    private fun openStickerPanel(withAnimation: Boolean = true) {
        isStickerPanelOpen = true
        
        // 设置表情面板高度为屏幕的45%
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()
        
        val layoutParams = binding.stickerPanel.root.layoutParams
        layoutParams.height = panelHeight
        binding.stickerPanel.root.layoutParams = layoutParams
        
        binding.stickerPanel.root.isVisible = true
        
        if (withAnimation) {
            // 展开动画
            binding.stickerPanel.root.translationY = panelHeight.toFloat()
            binding.stickerPanel.root.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            // 直接显示,无动画
            binding.stickerPanel.root.translationY = 0f
        }
    }
    
    private fun closeStickerPanel() {
        isStickerPanelOpen = false
        
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()
        
        // 收起动画
        binding.stickerPanel.root.animate()
            .translationY(panelHeight.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.stickerPanel.root.isVisible = false
                binding.stickerPanel.root.translationY = 0f
                scheduleAutoReplyAfterInteractionCollapsed("sticker_panel")
            }
            .start()
    }
    
    private fun closeStickerPanelDirectly() {
        isStickerPanelOpen = false
        binding.stickerPanel.root.isVisible = false
        binding.stickerPanel.root.translationY = 0f
        scheduleAutoReplyAfterInteractionCollapsed("sticker_panel_direct")
    }

    private fun setupMoreOptions() {
        val options = listOf(
            MoreOption("视频通话", R.drawable.ic_videocam_24),
            MoreOption("位置", R.drawable.ic_location_on_24),
            MoreOption("外卖", R.drawable.ic_delivery_24),
            MoreOption("红包", R.drawable.ic_red_packet_24),
            MoreOption("转账", R.drawable.ic_transfer_24),
            MoreOption("原始消息", R.drawable.ic_raw_content_24),
            MoreOption("推进", R.drawable.ic_fast_forward_24),
            MoreOption("拍一拍", R.drawable.ic_hand_24) ,
            MoreOption("礼物", R.drawable.ic_gift_24)
            )

        options[0].action = { viewModel.videoCallManager.startVideoCall(contactId) }
        options[1].action = { showLocationDialog() }
        options[2].action = {
            WaimaiOrderDialogFragment.newInstance(contactId)
                .show(childFragmentManager, WaimaiOrderDialogFragment.TAG)
        }
        options[3].action = { Toast.makeText(requireContext(), "红包功能待实现", Toast.LENGTH_SHORT).show() }
        options[4].action = { showTransferDialog() }
        options[5].action = { showRawMessageDialog() }
        options[6].action = { handlePropelAction() }
        options[7].action = { handlePatAction() }
        options[8].action = { showGiftSelectionDialog() }


        moreOptionsAdapter = MoreOptionsAdapter(options) { option ->
            option.action?.invoke()
            toggleMoreOptions()
        }
        binding.moreOptionsPanel.moreOptionsGrid.adapter = moreOptionsAdapter
    }
    private fun showTransferDialog() {
        TransferDialogFragment.newInstance(contactId)
            .show(childFragmentManager, TransferDialogFragment.TAG)
    }

    private fun toggleMoreOptions() {
        if (isMoreOptionsOpen) {
            // 关闭更多面板
            closeMoreOptionsPanel()
        } else {
            // 判断是否有其他面板已打开(内部切换)
            val hasOtherPanelOpen = isStickerPanelOpen || isVoicePanelOpen
            
            // 关闭其他面板(直接切换,无动画)
            if (isStickerPanelOpen) closeStickerPanelDirectly()
            if (isVoicePanelOpen) closeVoicePanelDirectly()
            
            // 打开更多面板
            openMoreOptionsPanel(withAnimation = !hasOtherPanelOpen)
        }
    }
    
    private fun openMoreOptionsPanel(withAnimation: Boolean = true) {
        isMoreOptionsOpen = true
        
        // more_button旋转动画
        binding.moreButton.animate()
            .rotation(45f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(300)
            .start()
        
        // 背景颜色变化
        binding.moreButton.background.setTint(ContextCompat.getColor(requireContext(), R.color.material_blue_grey_800))
        
        // 设置更多选项面板高度为屏幕的45%
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()
        
        val layoutParams = binding.moreOptionsPanel.root.layoutParams
        layoutParams.height = panelHeight
        binding.moreOptionsPanel.root.layoutParams = layoutParams
        
        // 计算item高度并设置给adapter
        val numColumns = 4
        val numRows = kotlin.math.ceil(moreOptionsAdapter.count.toDouble() / numColumns).toInt()
        val verticalSpacingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, displayMetrics
        ).toInt()
        val paddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f, displayMetrics
        ).toInt()
        val totalVerticalSpacing = verticalSpacingPx * (numRows - 1)
        val totalPadding = paddingPx * 2
        val availableHeight = panelHeight - totalVerticalSpacing - totalPadding
        val itemHeight = availableHeight / numRows
        
        moreOptionsAdapter.setItemHeight(itemHeight)
        
        binding.moreOptionsPanel.root.isVisible = true
        
        if (withAnimation) {
            // 展开动画
            binding.moreOptionsPanel.root.translationY = panelHeight.toFloat()
            binding.moreOptionsPanel.root.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            // 直接显示,无动画
            binding.moreOptionsPanel.root.translationY = 0f
        }
    }
    
    private fun closeMoreOptionsPanel() {
        isMoreOptionsOpen = false
        
        // more_button旋转动画
        binding.moreButton.animate()
            .rotation(0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(300)
            .start()
        
        // 背景颜色变化
        binding.moreButton.background.setTint(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.45f).toInt()
        
        // 收起动画
        binding.moreOptionsPanel.root.animate()
            .translationY(panelHeight.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.moreOptionsPanel.root.isVisible = false
                binding.moreOptionsPanel.root.translationY = 0f
                scheduleAutoReplyAfterInteractionCollapsed("more_options_panel")
            }
            .start()
    }
    
    private fun closeMoreOptionsPanelDirectly() {
        isMoreOptionsOpen = false
        
        // 重置more_button状态
        binding.moreButton.rotation = 0f
        binding.moreButton.background.setTint(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        
        // 直接隐藏面板
        binding.moreOptionsPanel.root.isVisible = false
        binding.moreOptionsPanel.root.translationY = 0f
        scheduleAutoReplyAfterInteractionCollapsed("more_options_panel_direct")
    }

    private fun showRawMessageDialog() {
        val rawContent = aiManager.getLastRawResponse(contactId)
        if (rawContent != null) {
            EditRawMessageDialogFragment.newInstance(rawContent)
                .show(childFragmentManager, EditRawMessageDialogFragment.TAG)
        } else {
            Toast.makeText(requireContext(), "没有可编辑的原始消息。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationDialog() {
        val dialog = LocationDialogFragment { location ->
            chatManager.sendMessage(
                contactId = contactId,
                text = location,
                imageUrl = null,
                type = "location_share"
            )
        }
        dialog.show(childFragmentManager, "LocationDialogFragment")
    }

    private fun showGiftSelectionDialog() {
        GiftSelectionDialogFragment.newInstance(contactId)
            .show(childFragmentManager, GiftSelectionDialogFragment.TAG)
    }

    private fun handlePropelAction() {
        binding.acceptButton.isEnabled = false
        binding.repeatButton.isEnabled = false
        binding.moreButton.isEnabled = false
        binding.contactNameTextView.text = "对方正在输入..."
        binding.contactStatusTextView.isVisible = false
        binding.contactStatusIcon.isVisible = false
        // TODO: 实现推进AI响应逻辑
        aiManager.requestAiResponse(contactId)
    }

    private fun handlePatAction() {
        val contact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId } ?: return
        val userNickname = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
        val characterOriginalName = contact.realName // 角色的本名
        val displayNameForUI = contact.remarkName // 显示在UI上的名称

        val editText = android.widget.EditText(requireContext()).apply {
            hint = "（可选）输入后缀"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("你拍了拍 “$displayNameForUI”")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val suffix = editText.text.toString().trim()
                chatManager.performPatAction(contactId, userNickname, displayNameForUI, characterOriginalName, suffix)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行默认拍一拍（无后缀）。
     * 由双击好友头像触发，直接发送拍一拍消息，不弹出后缀输入框。
     * @param targetContactId 被拍联系人的ID
     */
    private fun performDefaultPatAction(targetContactId: String) {
        val contact = viewModel.qqContactManager.contacts.value?.find { it.id == targetContactId } ?: return
        val userNickname = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
        val characterOriginalName = contact.realName // 角色的本名
        val displayNameForUI = contact.remarkName // 显示在UI上的名称
        chatManager.performPatAction(targetContactId, userNickname, displayNameForUI, characterOriginalName, "")
    }

    private fun resetUiState() {
        // 当AI结束输入时，isAiTyping会变为false，从而调用stopTypingAnimation，
        // stopTypingAnimation内部会调用updateContactInfoInToolbar，所以这里的调用是多余的，可以简化。
        if (aiManager.isAiTyping.value[contactId] == false) {
            binding.acceptButton.isEnabled = true
            binding.acceptButton.isVisible = !isChatAutoReplyEnabled
            binding.repeatButton.isEnabled = true
            binding.repeatButton.alpha = 1.0f
            binding.moreButton.isEnabled = true
            if (!isMultiSelectMode) {
                updateContactInfoInToolbar()
            }
        }
    }

    private fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e("QqChatFragment", "Failed to copy URI to internal storage", e)
            null
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            } else {
                parentFragmentManager.popBackStack()
            }
        }

        binding.actionLongTermMemoryButton.setOnClickListener {
            triggerAutoReplyWhenEnteringChildPage()
            (view?.parent as? ViewGroup)?.id?.let {
               parentFragmentManager.beginTransaction()
                   .add(it, LongTermMemoryFragment.newInstance(contactId))
                   .addToBackStack(null)
                   .commit()
           }
        }

        binding.actionAiProfileButton.setOnClickListener {
            triggerAutoReplyWhenEnteringChildPage()
            (view?.parent as? ViewGroup)?.id?.let {
               parentFragmentManager.beginTransaction()
                   .add(it, QqAiProfileFragment.newInstance(contactId))
                   .addToBackStack(null)
                   .commit()
           }
        }
    }

    private fun showImageViewer(imageUrl: String) {
        val imageView = android.widget.ImageView(requireContext()).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        Glide.with(this).load(imageUrl).into(imageView)
        AlertDialog.Builder(requireContext())
            .setView(imageView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun saveImageToAlbum(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = Glide.with(requireContext()).asBitmap().load(imageUrl).submit().get()
                val albumDir = File(requireContext().filesDir, "album").apply { mkdirs() }
                val imageFile = File(albumDir, "IMG_${System.currentTimeMillis()}.jpg")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                val albumRepository = com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl(
                    albumDatabaseProvider.getAlbumDao(),
                    albumDatabaseProvider.getPhotoDao()
                )
                val albumService = AlbumServiceImpl(albumRepository)
                albumService.addPhotoToAlbum("默认相册", imageFile.absolutePath)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "图片已保存至相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }


    private fun enterMultiSelectMode(initialMessage: ChatMessage? = null) {
        isMultiSelectMode = true
        chatAdapter.setMultiSelectMode(true)
        initialMessage?.let {
            chatAdapter.toggleSelection(it)
        }
        binding.bottomPanel.isVisible = false
        binding.multiSelectBottomBar.isVisible = true
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close_24)
        binding.actionAiProfileButton.isVisible = false
        binding.actionLongTermMemoryButton.isVisible = false
        binding.contactStatusTextView.isVisible = false // Hide status in multi-select mode
        binding.contactStatusIcon.isVisible = false // Hide status icon in multi-select mode
        activity?.invalidateOptionsMenu()

        binding.multiSelectBottomBar.post {
            val bottomBarHeight = binding.multiSelectBottomBar.height
            binding.chatRecyclerView.setPadding(0, 0, 0, bottomBarHeight)
        }
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        chatAdapter.setMultiSelectMode(false)
        binding.bottomPanel.isVisible = true
        binding.multiSelectBottomBar.isVisible = false
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_arrow_back_24)
        updateContactInfoInToolbar() // Restore contact info
        binding.actionAiProfileButton.isVisible = true
        binding.actionLongTermMemoryButton.isVisible = true
        activity?.invalidateOptionsMenu()
        binding.chatRecyclerView.setPadding(0, 0, 0, 0)
    }

    private fun setupMultiSelectBar() {
        binding.favoriteButton.setOnClickListener {
            val selectedItems = chatAdapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                val senderName = viewModel.qqContactManager.userProfile.value?.nickname ?: "我"
                val senderAvatar = viewModel.qqContactManager.userProfile.value?.avatarUri
                favoriteManager.addFavorites(selectedItems, senderName, senderAvatar)
                Toast.makeText(requireContext(), "已收藏 ${selectedItems.size} 条消息", Toast.LENGTH_SHORT).show()
                exitMultiSelectMode()
            }
        }

        binding.deleteButton.setOnClickListener {
            val selectedItems = chatAdapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                chatManager.deleteMessages(selectedItems)
                Toast.makeText(requireContext(), "已删除 ${selectedItems.size} 条消息", Toast.LENGTH_SHORT).show()
                exitMultiSelectMode()
            }
        }

        binding.forwardButton.setOnClickListener {
            Toast.makeText(requireContext(), "转发功能待实现", Toast.LENGTH_SHORT).show()
        }

        binding.longScreenshotButton.setOnClickListener {
            Toast.makeText(requireContext(), "长截图功能待实现", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (isMultiSelectMode) {
                    menuInflater.inflate(com.susking.ephone_s.qq.R.menu.menu_qq_chat_multiselect, menu)
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_select_all)?.let {
                    it.isVisible = isMultiSelectMode
                    if (isMultiSelectMode) {
                        val allItemsCount = chatAdapter.itemCount
                        val selectedItemsCount = chatAdapter.getSelectedItems().size
                        it.title = if (selectedItemsCount < allItemsCount) "全选" else "取消全选"
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_select_all -> {
                        val allItemsCount = chatAdapter.itemCount
                        val selectedItemsCount = chatAdapter.getSelectedItems().size
                        if (selectedItemsCount < allItemsCount) {
                            chatAdapter.selectAll()
                        } else {
                            chatAdapter.deselectAll()
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

   private fun startCooldownUpdates(contact: com.susking.ephone_s.aidata.domain.model.PersonProfile) {
       stopCooldownUpdates() // 先停止任何正在运行的更新
       cooldownRunnable = object : Runnable {
           override fun run() {
               updateBlockCooldownText(contact)
               cooldownHandler.postDelayed(this, 1000) // 每秒更新一次
           }
       }
       cooldownHandler.post(cooldownRunnable!!)
   }

   private fun stopCooldownUpdates() {
       cooldownRunnable?.let {
           cooldownHandler.removeCallbacks(it)
       }
       cooldownRunnable = null
   }

    private fun updateBlockCooldownText(contact: com.susking.ephone_s.aidata.domain.model.PersonProfile) {
        if (_binding == null) return

        val blockTimestamp = contact.blockTimestamp
        val cooldownHours = contact.blockCooldownHours

        // 更新角色状态
        binding.blockedContactPanel.contactStatusText.text = when {
            contact.isBlockedByContact -> "当前角色状态：被联系人拉黑"
            contact.isBlocked -> "当前角色状态：被用户拉黑"
            else -> "当前角色状态：正常"
        }

        // 更新需要冷静的时间
        binding.blockedContactPanel.cooldownPeriodText.text = String.format("需要冷静：%.1f 小时", cooldownHours)


        if (blockTimestamp == null) {
            binding.blockedContactPanel.cooldownTimerText.text = "冷静期是否结束：随时可以解除"
            binding.blockedContactPanel.triggerConditionText.text = "触发条件：已满足"
            return
        }

        val cooldownMillis = (cooldownHours * 60 * 60 * 1000).toLong()
        val unblockTime = blockTimestamp + cooldownMillis
        val remainingTime = unblockTime - System.currentTimeMillis()

        if (remainingTime <= 0) {
            binding.blockedContactPanel.cooldownTimerText.text = "冷静期是否结束：已结束"
            binding.blockedContactPanel.triggerConditionText.text = "触发条件：已满足"
            stopCooldownUpdates()
        } else {
            val hours = (remainingTime / (1000 * 60 * 60)).toInt()
            val minutes = ((remainingTime % (1000 * 60 * 60)) / (1000 * 60)).toInt()
            binding.blockedContactPanel.cooldownTimerText.text =
                String.format("冷静期是否结束：还剩约 %d 小时 %02d 分钟", hours, minutes)
            binding.blockedContactPanel.triggerConditionText.text = "触发条件：未满足"
        }
    }

    private fun startTypingAnimation() {
        typingAnimationJob?.cancel()
        setUiInteraction(false)
        typingAnimationJob = viewLifecycleOwner.lifecycleScope.launch {
            val typingTexts = listOf("对方正在输入中", "对方正在输入中.", "对方正在输入中..", "对方正在输入中...")
            var currentIndex = 0
            while (true) {
                binding.contactNameTextView.text = typingTexts[currentIndex]
                binding.contactStatusTextView.isVisible = false
                binding.contactStatusIcon.isVisible = false
                currentIndex = (currentIndex + 1) % typingTexts.size
                delay(500)
            }
        }
    }

    private fun stopTypingAnimation() {
        typingAnimationJob?.cancel()
        setUiInteraction(true)
        if (isMultiSelectMode) {
             val selectedCount = chatAdapter.getSelectedItems().size
             binding.contactNameTextView.text = if (selectedCount == 0) "未选择项目" else "已选择 $selectedCount 项"
             binding.contactStatusTextView.isVisible = false
             binding.contactStatusIcon.isVisible = false
        } else {
            updateContactInfoInToolbar()
        }
    }
 
    private fun setupImageSelector() {
        imageSelector = ImageSelector(this) { uri ->
            if (uri != null) {
                // 将返回的URI转换为持久化的文件路径
                val persistentPath = copyUriToInternalStorage(requireContext(), uri)
                if (persistentPath != null) {
                    selectedImagePath = persistentPath
                    binding.imagePreviewLayout.isVisible = true
                    Glide.with(this).load(selectedImagePath).into(binding.imagePreview)
                } else {
                    Toast.makeText(requireContext(), "加载图片失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 用户清除了图片选择
                selectedImagePath = null
                binding.imagePreviewLayout.isVisible = false
                binding.imagePreview.setImageDrawable(null)
            }
        }
    }
 
    private fun setUiInteraction(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        val canUseInputArea: Boolean = enabled || isChatAutoReplyEnabled
        val inputAreaAlpha: Float = if (canUseInputArea) 1.0f else 0.5f
        binding.sendButton.isEnabled = canUseInputArea
        binding.sendButton.alpha = inputAreaAlpha
        binding.acceptButton.isEnabled = enabled
        binding.acceptButton.alpha = alpha
        binding.voiceButton.isEnabled = canUseInputArea
        binding.voiceButton.alpha = inputAreaAlpha
        binding.photoButton.isEnabled = canUseInputArea
        binding.photoButton.alpha = inputAreaAlpha
        binding.cameraButton.isEnabled = canUseInputArea
        binding.cameraButton.alpha = inputAreaAlpha
        binding.emojiButton.isEnabled = canUseInputArea
        binding.emojiButton.alpha = inputAreaAlpha
        binding.repeatButton.isEnabled = enabled
        binding.repeatButton.alpha = alpha
        binding.moreButton.isEnabled = canUseInputArea
        binding.moreButton.alpha = inputAreaAlpha
    }

    /**
     * 重新生成图片
     */
    private fun regenerateImage(messageId: String, newPrompt: String) {
        lifecycleScope.launch {
            // 禁用UI交互,防止重复点击
            setUiInteraction(false)
            Toast.makeText(requireContext(), "正在重新生成图片...", Toast.LENGTH_SHORT).show()
            
            try {
                // 1. 获取原消息
                val messages = chatManager.getMessagesForContact(contactId).first()
                val originalMessage = messages.find { it.id == messageId }
                if (originalMessage == null || originalMessage.type != "naiimag") {
                    Toast.makeText(requireContext(), "消息不存在或不是图片消息", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 2. 获取联系人
                val contact = viewModel.qqContactManager.contacts.value?.find { it.id == contactId }
                if (contact == null) {
                    Toast.makeText(requireContext(), "联系人不存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 3. 生成图片
                val result = generateImageUseCase(newPrompt, contact)
                
                result.onSuccess { filePath ->
                    // 4. 更新消息
                    val updatedMessage = originalMessage.copy(
                        content = newPrompt,
                        imageUrl = filePath
                    )
                    chatManager.updateMessage(updatedMessage)
                    Toast.makeText(requireContext(), "图片重新生成成功", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "重新生成失败: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // 恢复UI交互
                setUiInteraction(true)
            }
        }
    }

    /**
     * 重roll图片(使用原提示词)
     */
    private fun rerollImage(messageId: String, originalPrompt: String) {
        regenerateImage(messageId, originalPrompt)
    }

    /**
     * AI重写提示词并重新生成
     */
    private fun rewriteAndRegenerateImage(
        messageId: String,
        specialRequirements: String?,
        includeOriginalPrompt: Boolean
    ) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "AI正在重写提示词...", Toast.LENGTH_SHORT).show()
            
            try {
                // 调用AI重写提示词
                rewriteImagePromptUseCase(messageId, contactId, specialRequirements, includeOriginalPrompt)
                    .onSuccess { newPrompt ->
                        Toast.makeText(requireContext(), "提示词重写成功,开始生成图片...", Toast.LENGTH_SHORT).show()
                        regenerateImage(messageId, newPrompt)
                    }
                    .onFailure { error ->
                        Toast.makeText(requireContext(), "AI重写失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "AI重写失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    
    
    /**
     * 隐藏输入法
     */
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageEditText.windowToken, 0)
        binding.messageEditText.clearFocus()
    }
    
    /**
     * 关闭所有面板
     */
    private fun closeAllPanels() {
        closeAllPanels(withAnimation = true)
    }

    /**
     * 按指定动画策略关闭所有面板。
     */
    private fun closeAllPanels(withAnimation: Boolean) {
        if (withAnimation) {
            if (isMoreOptionsOpen) closeMoreOptionsPanel()
            if (isStickerPanelOpen) closeStickerPanel()
            if (isVoicePanelOpen) closeVoicePanel()
            return
        }
        if (isMoreOptionsOpen) closeMoreOptionsPanelDirectly()
        if (isStickerPanelOpen) closeStickerPanelDirectly()
        if (isVoicePanelOpen) closeVoicePanelDirectly()
    }
    
    /**
     * 隐藏输入法并关闭所有面板
     */
    private fun hideKeyboardAndClosePanels() {
        hideKeyboard()
        closeAllPanels()
    }

    /**
     * 只在输入框或任意面板处于展开状态时收起界面，避免普通点击触发多余的焦点变更。
     */
    private fun hideKeyboardAndClosePanelsIfNeeded() {
        val shouldCollapse: Boolean = binding.messageEditText.hasFocus() || isMoreOptionsOpen || isStickerPanelOpen || isVoicePanelOpen
        if (!shouldCollapse) {
            return
        }
        hideKeyboard()
        closeAllPanels(withAnimation = false)
    }
    
    private fun scheduleAutoReplyAfterUserMessage() {
        autoReplyIdleJob?.cancel()
        val hasUnseenMessage: Boolean = hasUnseenUserMessageLocally()
        Log.d(TAG, "自动回复诊断: 尝试调度 idle enabled=$isChatAutoReplyEnabled isAdded=$isAdded active=${isUserActiveInChat()} hasUnseen=$hasUnseenMessage keyboard=$isKeyboardVisible")
        if (!isChatAutoReplyEnabled || !isAdded) {
            Log.d(TAG, "自动回复诊断: 不调度 idle，原因=开关关闭或Fragment未附加")
            return
        }
        if (isUserActiveInChat()) {
            Log.d(TAG, "自动回复诊断: 不调度 idle，原因=用户仍活跃")
            return
        }
        val delayMillis: Long = chatAutoReplyIntervalSeconds.coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS).toLong() * MILLIS_PER_SECOND
        autoReplyIdleJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(delayMillis)
            val activeAfterDelay: Boolean = isUserActiveInChat()
            Log.d(TAG, "自动回复诊断: idle 延迟结束 enabled=$isChatAutoReplyEnabled active=$activeAfterDelay hasUnseen=${hasUnseenUserMessageLocally()}")
            if (!isChatAutoReplyEnabled || activeAfterDelay) return@launch
            aiManager.requestAutoAiResponse(contactId)
        }
    }

    private fun updateAutoReplyTypingTimer() {
        autoReplyTypingJob?.cancel()
        if (!isChatAutoReplyEnabled || !isAdded) return
        if (isAutoReplyTypingEasterEggInCooldown()) return
        val hasTypingState: Boolean = isKeyboardVisible || binding.messageEditText.text?.isNotBlank() == true
        if (!hasTypingState) return
        autoReplyTypingJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(AUTO_REPLY_TYPING_EASTER_EGG_DELAY_MILLIS)
            val stillTyping: Boolean = isKeyboardVisible || binding.messageEditText.text?.isNotBlank() == true
            if (!isChatAutoReplyEnabled || !stillTyping || isAutoReplyTypingEasterEggInCooldown()) return@launch
            lastAutoReplyTypingEasterEggAtMillis = System.currentTimeMillis()
            aiManager.requestAutoAiResponse(contactId, AUTO_REPLY_TYPING_EASTER_EGG_PROMPT)
        }
    }

    private fun isAutoReplyTypingEasterEggInCooldown(): Boolean {
        val elapsedMillis: Long = System.currentTimeMillis() - lastAutoReplyTypingEasterEggAtMillis
        return elapsedMillis in 0 until AUTO_REPLY_TYPING_EASTER_EGG_COOLDOWN_MILLIS
    }

    private fun triggerAutoReplyWhenLeavingChat() {
        val hasUnseenMessage: Boolean = hasUnseenUserMessageLocally()
        Log.d(TAG, "自动回复诊断: 离开聊天触发 enabled=$isChatAutoReplyEnabled hasUnseen=$hasUnseenMessage")
        if (!isChatAutoReplyEnabled || !hasUnseenMessage) return
        cancelAutoReplyJobs()
        aiManager.requestAutoAiResponse(contactId)
    }

    private fun scheduleAutoReplyAfterInteractionCollapsed(source: String) {
        if (!hasUnseenUserMessageLocally()) return
        Log.d(TAG, "自动回复诊断: 交互界面收起后重新调度普通自动回复 source=$source contactId=$contactId")
        scheduleAutoReplyAfterUserMessage()
    }

    private fun hasUnseenUserMessageLocally(): Boolean {
        return allVisibleMessages.any { message: ChatMessage ->
            message.role == "user" && !message.hasBeenSeenByAi
        }
    }

    private fun isUserActiveInChat(): Boolean {
        val hasInputText: Boolean = binding.messageEditText.text?.isNotBlank() == true
        return hasInputText || isKeyboardVisible || isMoreOptionsOpen || isStickerPanelOpen || isVoicePanelOpen || isEditingMessage || voiceRecorder != null
    }

    private fun triggerAutoReplyWhenEnteringChildPage() {
        triggerAutoReplyWhenLeavingChat()
    }

    private fun registerEditingDialogLifecycleObserver() {
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fragmentManager: FragmentManager, fragment: Fragment) {
                    val tag: String = fragment.tag ?: return
                    if (isEditingDialogTag(tag)) {
                        isEditingMessage = false
                        scheduleAutoReplyAfterUserMessage()
                        updateAutoReplyTypingTimer()
                    }
                }
            },
            false
        )
    }

    private fun isEditingDialogTag(tag: String): Boolean {
        return tag == EditMessageDialogFragment.TAG ||
            tag == EditStickerDialogFragment.TAG ||
            tag == EditImagePromptDialogFragment.TAG ||
            tag == EditImageDescriptionDialogFragment.TAG
    }

    private fun observeFollowUpPolicyChanges(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EventBus.events.collectLatest { event: Any ->
                    when (event) {
                        is QqEvent.FollowUpPolicyChanged -> handleFollowUpPolicyChanged(event)
                        is QqEvent.AiResponseCompleted -> handleAiResponseCompletedForFollowUp(event)
                    }
                }
            }
        }
    }

    private fun handleFollowUpPolicyChanged(event: QqEvent.FollowUpPolicyChanged): Unit {
        if (event.contactId != contactId) return
        Log.d(TAG, "自动回复诊断: 收到追问策略更新事件，重新调度静默追问 contactId=$contactId")
        refreshAutoReplySettingsAndScheduleSilentFollowUp()
    }

    private fun handleAiResponseCompletedForFollowUp(event: QqEvent.AiResponseCompleted): Unit {
        if (event.contactId != contactId) return
        Log.d(TAG, "自动回复诊断: 收到AI回复完成事件，兜底重新调度静默追问 contactId=$contactId")
        refreshAutoReplySettingsAndScheduleSilentFollowUp()
    }

    private fun refreshAutoReplySettingsAndScheduleSilentFollowUp(): Unit {
        viewLifecycleOwner.lifecycleScope.launch {
            isChatAutoReplyEnabled = settingsRepository.getChatAutoReplyEnabledFlow().first()
            if (!isChatAutoReplyEnabled || !isAdded) return@launch
            chatAutoReplyIntervalSeconds = settingsRepository.getChatAutoReplyIntervalSeconds().coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
            chatFollowUpDelaySeconds = settingsRepository.getChatFollowUpDelaySeconds().coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
            scheduleAutoReplySilentFollowUpAfterAiResponse()
        }
    }

    private fun scheduleAutoReplySilentFollowUpAfterAiResponse(): Unit {
        autoReplySilentFollowUpJob?.cancel()
        if (!isChatAutoReplyEnabled || !isAdded || hasUnseenUserMessageLocally()) return

        val snapshot: FollowUpPolicySnapshot = followUpPolicyStore.getPolicySnapshot(contactId)
        if (!snapshot.canFollowUp) {
            Log.d(TAG, "自动回复诊断: 不调度静默追问，原因=追问策略不允许 contactId=$contactId")
            return
        }

        val configuredDelayMillis: Long = getChatFollowUpDelayMillis()
        val elapsedMillis: Long = System.currentTimeMillis() - snapshot.savedAtMillis
        val delayMillis: Long = (configuredDelayMillis - elapsedMillis).coerceAtLeast(0L)
        Log.d(TAG, "自动回复诊断: 调度静默追问 delayMillis=$delayMillis elapsedMillis=$elapsedMillis contactId=$contactId")
        autoReplySilentFollowUpJob = viewLifecycleOwner.lifecycleScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            Log.d(TAG, "自动回复诊断: 静默追问时间到 enabled=$isChatAutoReplyEnabled hasUnseen=${hasUnseenUserMessageLocally()}")
            if (!isChatAutoReplyEnabled || hasUnseenUserMessageLocally()) return@launch
            if (!followUpPolicyStore.canFollowUp(contactId)) return@launch
            aiManager.requestSilentFollowUpIfAllowed(contactId)
        }
    }

    private fun getChatFollowUpDelayMillis(): Long {
        return chatFollowUpDelaySeconds
            .coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
            .toLong() * MILLIS_PER_SECOND
    }

    private fun cancelAutoReplySilentFollowUpJob() {
        autoReplySilentFollowUpJob?.cancel()
        autoReplySilentFollowUpJob = null
    }

    private fun cancelAutoReplyJobs() {
        autoReplyIdleJob?.cancel()
        autoReplyIdleJob = null
        autoReplyTypingJob?.cancel()
        autoReplyTypingJob = null
        cancelAutoReplySilentFollowUpJob()
    }
    
    override fun onDestroyView() {
        cancelAutoReplyJobs()
        stopCooldownUpdates()
        stopVoiceRecorder()
        stopVoiceTimerUpdates()
        chatAdapter.stopActiveVoicePlayback()
        releaseTtsAutoPlayPlayer()
        releaseTtsStreamingAudioTrack()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_TARGET_TIMESTAMP = "target_timestamp"
        private const val ARG_IS_LAUNCHED_FROM_SEARCH = "is_launched_from_search"
        private const val TAG = "QqChatFragment"
        private const val VOICE_AUDIO_DIRECTORY_NAME: String = "voice_messages"
        private const val VOICE_AUDIO_MIME_TYPE: String = "audio/mp4"
        private const val VOICE_AUDIO_SAMPLE_RATE: Int = 16000
        private const val VOICE_AUDIO_BIT_RATE: Int = 64000
        private const val VOICE_TIMER_UPDATE_INTERVAL_MILLIS: Long = 200L
        private const val MIN_VOICE_DURATION_MILLIS: Long = 800L
        private const val DEFAULT_CHAT_INITIAL_LOAD_COUNT: Int = 30
        private const val MILLIS_PER_SECOND: Long = 1000L
        private const val MINIMUM_CHAT_TIMING_SECONDS: Int = 1
        private const val DEFAULT_CHAT_AUTO_REPLY_INTERVAL_SECONDS: Int = 5
        private const val DEFAULT_CHAT_FOLLOW_UP_DELAY_SECONDS: Int = 1200
        private const val AUTO_REPLY_TYPING_EASTER_EGG_DELAY_MILLIS: Long = 60000L
        private const val AUTO_REPLY_TYPING_EASTER_EGG_COOLDOWN_MILLIS: Long = 100 * 60 * 1000L
        private const val AUTO_REPLY_TYPING_EASTER_EGG_PROMPT: String = "用户已经持续处于正在输入状态超过一分钟，但尚未发送新内容。请你对此做出回应，也可不回应。"

        private const val MIMO_TTS_SAMPLE_RATE: Int = 24000
        private const val MIMO_TTS_STREAM_BUFFER_SIZE: Int = 48_000

        fun newInstance(
            contactId: String,
            targetTimestamp: Long? = null,
            isLaunchedFromSearch: Boolean = false
        ): QqChatFragment {
            return QqChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                    targetTimestamp?.let { putLong(ARG_TARGET_TIMESTAMP, it) }
                    if (isLaunchedFromSearch) {
                        putBoolean(ARG_IS_LAUNCHED_FROM_SEARCH, true)
                    }
                }
            }
        }
    }
}
