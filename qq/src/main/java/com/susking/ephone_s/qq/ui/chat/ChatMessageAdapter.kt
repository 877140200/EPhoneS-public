package com.susking.ephone_s.qq.ui.chat

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.susking.ephone_s.qq.util.MarkdownRenderer
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.databinding.ContentMessageGiftBinding
import com.susking.ephone_s.qq.databinding.ContentMessageImageBinding
import com.susking.ephone_s.qq.databinding.ContentMessageLocationBinding
import com.susking.ephone_s.qq.databinding.ContentMessageStickerBinding
import com.susking.ephone_s.qq.databinding.ContentMessageTextBinding
import com.susking.ephone_s.qq.databinding.ContentMessageTransferBinding
import com.susking.ephone_s.qq.databinding.ContentMessageWaimaiBinding
import com.susking.ephone_s.qq.databinding.ContentMessageWaimaiOrderBinding
import com.susking.ephone_s.qq.databinding.ItemChatBubbleReceivedBinding
import com.susking.ephone_s.qq.databinding.ItemChatBubbleSentBinding
import com.susking.ephone_s.qq.databinding.ItemChatBubbleSystemBinding
import com.susking.ephone_s.qq.databinding.ItemChatMessageFriendApplicationBinding
import com.susking.ephone_s.qq.databinding.ItemMessageMenuBinding
import com.susking.ephone_s.qq.databinding.PopupQqMessageMenuBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface ChatMessageInteractionListener {
    fun onCopy(message: ChatMessage)
    fun onForward(message: ChatMessage)
    fun onFavorite(message: ChatMessage)
    fun onDelete(message: ChatMessage)
    fun onMultiSelect(message: ChatMessage)
    fun onQuote(message: ChatMessage)
    fun onEdit(message: ChatMessage)
    fun onScreenshot(message: ChatMessage)
    fun onSwitchResponse(message: ChatMessage, newIndex: Int)
    fun onContactAvatarClick(contactId: String)
    fun onContactAvatarDoubleClick(contactId: String) // 双击好友头像触发默认拍一拍
    fun onImageBubbleClick(message: ChatMessage) // 用于显示/隐藏图片操作按钮
    fun onZoomImageClick(message: ChatMessage) // 放大图片
    fun onRewritePromptClick(message: ChatMessage) // 重写提示词
    fun onRerollImageClick(message: ChatMessage) // 重roll图片
    fun onSaveImageToAlbum(message: ChatMessage)
    fun onSelectionChanged(selectedCount: Int)
    fun onWaimaiCardClick(message: ChatMessage) // 用于点击外卖卡片查看详情
    fun onTransferCardClick(message: ChatMessage)
    fun onAcceptFriendApplication(message: ChatMessage)
    fun onDeclineFriendApplication(message: ChatMessage)
    fun onQuoteJump(messageId: String)
    fun onMessageRecalled(message: ChatMessage) // 消息被撤回的回调
    fun onViewRecalledContent(message: ChatMessage) // 查看被撤回消息的原始内容
    fun onAcceptOfflineRequest(message: ChatMessage) // 同意线下见面请求
    fun onDeclineOfflineRequest(message: ChatMessage) // 拒绝线下见面请求
    fun onVideoCallRecordClick(message: ChatMessage) // 点击通话结束记录查看总结
    fun onRegenerateVoice(message: ChatMessage) // 重新生成语音缓存
    fun onPlayVoiceMessage(message: ChatMessage) // 播放或生成语音消息
    fun onRetryError(message: ChatMessage) // 点击错误气泡的重试按钮，删除该错误并重新发起请求
}

private fun Float.dpToPx(context: android.content.Context): Float {
    return this * context.resources.displayMetrics.density
}

class ChatMessageAdapter(
    private var userAvatarUrl: String?,
    private var contactsMap: Map<String, PersonProfile>,
    private var userNickname: String = "我" // 默认是"我"
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TAG = "ChatMessageAdapter"
        private const val DEFAULT_VOICE_DURATION_MILLIS: Long = 4000L
        private const val VOICE_WAVE_ANIMATION_DURATION_MILLIS: Long = 720L

        private var activeVoiceMessageIdStatic: String? = null

        private val DiffCallback = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                if (newItem.id == activeVoiceMessageIdStatic) return true
                return oldItem == newItem
            }
        }
    }

     private var isMultiSelectMode = false
     private val selectedItems = mutableSetOf<ChatMessage>()

    override fun submitList(list: List<ChatMessage>?) {
        super.submitList(list) {
            if (list?.isNotEmpty() == true && activeVoiceMessageId != list[0].id) {
                notifyItemChanged(0)
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    var listener: ChatMessageInteractionListener? = null
    private var activeVoicePlayer: MediaPlayer? = null
    private var activeVoiceMessageId: String? = null
    private var activeVoiceStopCallback: (() -> Unit)? = null
    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val VIEW_TYPE_SYSTEM = 3 // 新增：用于系统消息，如通话记录
    private val VIEW_TYPE_FRIEND_APPLICATION = 4 // 新增：用于好友申请
    private val VIEW_TYPE_SHOPPING_ACCESS_REQUEST = 5 // 新增：用于购物访问申请
    fun stopActiveVoicePlayback(): Unit {
        activeVoicePlayer?.runCatching { release() }
        activeVoicePlayer = null
        activeVoiceMessageId = null
        activeVoiceMessageIdStatic = null
        activeVoiceStopCallback?.invoke()
        activeVoiceStopCallback = null
    }

    fun hasActiveVoicePlayback(): Boolean {
        return activeVoicePlayer != null
    }

    fun playVoiceAudioFromFragment(message: ChatMessage): Unit {
        val audioFile: File = message.voiceAudioPath
            ?.takeIf { path: String -> path.isNotBlank() }
            ?.let { path: String -> File(path) }
            ?.takeIf { file: File -> file.exists() && file.isFile }
            ?: return
        executeVoicePlayback(
            message = message,
            audioFile = audioFile,
            onStartAnimation = null,
            onStopAnimation = { notifyVoiceMessageChanged(message.id) },
            shouldToggleSameMessage = false
        )
    }

    private fun notifyVoiceMessageChanged(messageId: String): Unit {
        if (messageId == activeVoiceMessageId && activeVoicePlayer != null) return
        val position: Int = currentList.indexOfFirst { message: ChatMessage -> message.id == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    private fun executeVoicePlayback(
        message: ChatMessage,
        audioFile: File,
        onStartAnimation: (() -> Unit)?,
        onStopAnimation: (() -> Unit)?,
        shouldToggleSameMessage: Boolean
    ): Unit {
        if (shouldToggleSameMessage && activeVoiceMessageId == message.id && activeVoicePlayer != null) {
            stopActiveVoicePlayback()
            return
        }
        stopActiveVoicePlayback()
        activeVoiceMessageId = message.id
        activeVoiceMessageIdStatic = message.id
        activeVoiceStopCallback = onStopAnimation
        activeVoicePlayer = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            setOnCompletionListener { completedPlayer: MediaPlayer ->
                completedPlayer.release()
                activeVoicePlayer = null
                activeVoiceMessageId = null
                activeVoiceMessageIdStatic = null
                activeVoiceStopCallback?.invoke()
                activeVoiceStopCallback = null
            }
            setOnErrorListener { failedPlayer: MediaPlayer, _: Int, _: Int ->
                failedPlayer.release()
                activeVoicePlayer = null
                activeVoiceMessageId = null
                activeVoiceMessageIdStatic = null
                activeVoiceStopCallback?.invoke()
                activeVoiceStopCallback = null
                true
            }
            prepare()
            start()
        }
        onStartAnimation?.invoke()
    }

    fun setMultiSelectMode(enabled: Boolean) {
        if (isMultiSelectMode != enabled) {
            isMultiSelectMode = enabled
            if (!enabled) {
                selectedItems.clear()
            }
            notifyDataSetChanged()
            listener?.onSelectionChanged(selectedItems.size)
        }
    }

    fun getSelectedItems(): List<ChatMessage> {
        return selectedItems.toList()
    }

    fun selectAll() {
        if (isMultiSelectMode) {
            selectedItems.addAll(currentList)
            notifyDataSetChanged()
            listener?.onSelectionChanged(selectedItems.size)
        }
    }

    fun deselectAll() {
        if (isMultiSelectMode) {
            selectedItems.clear()
            notifyDataSetChanged()
            listener?.onSelectionChanged(selectedItems.size)
        }
    }

    fun toggleSelection(message: ChatMessage) {
        if (isMultiSelectMode) {
            if (selectedItems.contains(message)) {
                selectedItems.remove(message)
            } else {
                selectedItems.add(message)
            }
            val position = currentList.indexOf(message)
            if (position != -1) {
                notifyItemChanged(position)
            }
            listener?.onSelectionChanged(selectedItems.size)
        }
    }

    fun updateUserAvatarUrl(newUrl: String?) {
        if (this.userAvatarUrl != newUrl) {
            this.userAvatarUrl = newUrl
        }
    }

    fun updateContactsMap(newContactsMap: Map<String, PersonProfile>) {
        if (this.contactsMap != newContactsMap) {
            this.contactsMap = newContactsMap
        }
    }

    fun updateUserNickname(newNickname: String) {
        if (this.userNickname != newNickname) {
            this.userNickname = newNickname
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        android.util.Log.d("ChatMessageAdapter", "getItemViewType: position=$position, role=${message.role}, type=${message.type}, content=${message.content?.take(50)}")
        return when {
            message.type == "shopping_access_request" -> VIEW_TYPE_SHOPPING_ACCESS_REQUEST
            message.isRecalled -> VIEW_TYPE_SYSTEM // 已撤回的消息显示为系统消息
            message.role == "error" -> VIEW_TYPE_SYSTEM // 错误消息显示为系统样式（淡红色背景）
            message.role == "system" || message.type == "system" || message.type == "video_call_record" || message.type == "pat_message" || message.type == "offline_response" -> VIEW_TYPE_SYSTEM
            message.type == "friend_application" -> VIEW_TYPE_FRIEND_APPLICATION
            message.role == "user" -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(
                ItemChatBubbleSentBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_RECEIVED -> ReceivedViewHolder(
                ItemChatBubbleReceivedBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_SYSTEM -> SystemMessageViewHolder(
                ItemChatBubbleSystemBinding.inflate(inflater, parent, false)
            )
           VIEW_TYPE_FRIEND_APPLICATION -> FriendApplicationViewHolder(
               ItemChatMessageFriendApplicationBinding.inflate(inflater, parent, false)
           )
           VIEW_TYPE_SHOPPING_ACCESS_REQUEST -> ShoppingAccessRequestViewHolder(
               com.susking.ephone_s.qq.databinding.ItemChatMessageShoppingAccessRequestBinding.inflate(inflater, parent, false)
           )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)

        // 【新增】时间分割线逻辑
        val showTimeDivider = if (position == 0) {
            true // 第一条消息总显示时间
        } else {
            val prevMessage = getItem(position - 1)
            // 如果两条消息之间的时间差大于5分钟（300,000毫秒），则显示时间
            message.timestamp - prevMessage.timestamp > 300000
        }

        when (holder) {
            is SentViewHolder -> holder.bind(message, showTimeDivider)
            is ReceivedViewHolder -> holder.bind(message, showTimeDivider)
            is SystemMessageViewHolder -> holder.bind(message, showTimeDivider)
            is FriendApplicationViewHolder -> holder.bind(message, showTimeDivider)
            is ShoppingAccessRequestViewHolder -> holder.bind(message, showTimeDivider)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }

    private fun formatTimeDivider(timestamp: Long): String {
        val messageDate = Date(timestamp)
        val today = Calendar.getInstance()
        val messageCal = Calendar.getInstance().apply { time = messageDate }

        return when {
            today.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR) -> {
                // 今天
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
            }
            today.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) - 1 == messageCal.get(Calendar.DAY_OF_YEAR) -> {
                // 昨天
                "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
            }
            else -> {
                // 更早
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(messageDate)
            }
        }
    }
    private fun formatMessageText(text: String?): String {
        if (text.isNullOrBlank()) {
            return ""
        }
        // 为了避免将已经是段落间距的 \n\n 替换为 \n\n\n\n，我们只替换单个的 \n
        // 使用正则表达式来查找没有被另一个 \n 跟随的 \n
        return text.replace(Regex("(?<!\\n)\\n(?!\\n)"), "\n\n")
    }

    private fun formatVoiceDuration(durationMillis: Long?): String {
        val totalSeconds: Long = ((durationMillis ?: DEFAULT_VOICE_DURATION_MILLIS) / 1000L).coerceAtLeast(1L)
        return "$totalSeconds\""
    }

    private fun resolveThemeColor(context: android.content.Context, attributeResourceId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attributeResourceId, typedValue, true)
        return typedValue.data
    }

    private fun bindVoiceMessageContent(
        contentBinding: com.susking.ephone_s.qq.databinding.ContentMessageVoiceBinding,
        message: ChatMessage
    ) {
        val voiceAudioPath: String? = message.voiceAudioPath
        val playableAudioFile: File? = voiceAudioPath
            ?.takeIf { path: String -> path.isNotBlank() }
            ?.let { path: String -> File(path) }
            ?.takeIf { file: File -> file.exists() && file.isFile }

        val voiceContentColor: Int = if (message.role == "user") {
            resolveThemeColor(contentBinding.root.context, com.google.android.material.R.attr.colorOnPrimary)
        } else {
            contentBinding.root.context.getColor(R.color.material_on_surface_emphasis_medium)
        }

        fun stopWaveAnimation() {
            contentBinding.voicePlayPauseIcon.setImageResource(R.drawable.ic_play_arrow_24)
            val viewAnimator: ValueAnimator? = contentBinding.voiceWaveAnimation.tag as? ValueAnimator
            viewAnimator?.cancel()
            contentBinding.voiceWaveAnimation.tag = null
            contentBinding.voiceWaveAnimation.scaleY = 1f
            val drawable = contentBinding.voiceWaveAnimation.drawable
            if (drawable is Animatable && drawable.isRunning) {
                drawable.stop()
            }
            // 停止动画后清理 ImageView，防止因视图复用导致状态问题
            contentBinding.voiceWaveAnimation.setImageDrawable(null)
            contentBinding.voiceWaveStatic.visibility = View.VISIBLE
            contentBinding.voiceWaveAnimation.visibility = View.GONE
        }

        fun startWaveAnimation() {
            contentBinding.voicePlayPauseIcon.setImageResource(R.drawable.ic_pause_24)
            contentBinding.voiceWaveStatic.visibility = View.GONE
            contentBinding.voiceWaveAnimation.visibility = View.VISIBLE

            val avd = AnimatedVectorDrawableCompat.create(contentBinding.root.context, com.susking.ephone_s.qq.R.drawable.anim_voice_wave)
            contentBinding.voiceWaveAnimation.setImageDrawable(avd)
            contentBinding.voiceWaveAnimation.imageTintList = android.content.res.ColorStateList.valueOf(voiceContentColor)

            val viewAnimator: ValueAnimator = ValueAnimator.ofFloat(0.72f, 1.28f).apply {
                duration = VOICE_WAVE_ANIMATION_DURATION_MILLIS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { contentBinding.voiceWaveAnimation.scaleY = it.animatedValue as Float }
                start()
            }
            contentBinding.voiceWaveAnimation.tag = viewAnimator

            contentBinding.voiceWaveAnimation.post {
                if (contentBinding.voiceWaveAnimation.visibility == View.VISIBLE) {
                    avd?.start()
                }
            }
        }

        contentBinding.voicePlayPauseIcon.imageTintList = android.content.res.ColorStateList.valueOf(voiceContentColor)
        contentBinding.voiceWaveStatic.imageTintList = android.content.res.ColorStateList.valueOf(voiceContentColor)
        contentBinding.voiceWaveAnimation.imageTintList = android.content.res.ColorStateList.valueOf(voiceContentColor)
        contentBinding.voiceDurationText.setTextColor(voiceContentColor)
        contentBinding.voiceContentText.setTextColor(voiceContentColor)

        val statusText: String? = when (message.ttsGenerationStatus) {
            "generating" -> "生成中..."
            "failed" -> "生成失败，长按可重新生成"
            "success" -> null
            else -> null
        }
        contentBinding.voiceDurationText.text = statusText ?: formatVoiceDuration(message.voiceDurationMillis)
        contentBinding.voiceContentText.text = message.content.orEmpty()
        contentBinding.voiceContentText.visibility = if (!message.content.isNullOrBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (activeVoiceMessageId == message.id && activeVoicePlayer != null) {
            startWaveAnimation()
        } else {
            stopWaveAnimation()
        }

        contentBinding.voiceMessageLayout.setOnClickListener { view: View ->
            val audioFile: File = playableAudioFile ?: run {
                if (message.type == "voice_message" && message.role == "assistant") {
                    listener?.onPlayVoiceMessage(message)
                } else {
                    android.widget.Toast.makeText(view.context, "这条语音没有可播放的录音", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            executeVoicePlayback(
                message = message,
                audioFile = audioFile,
                onStartAnimation = ::startWaveAnimation,
                onStopAnimation = ::stopWaveAnimation,
                shouldToggleSameMessage = true
            )
        }
    }

    private fun bindQuotedMessage(quoteContainer: ViewGroup, message: ChatMessage) {
        if (message.quotedMessage != null) {
            val quoteBinding = com.susking.ephone_s.qq.databinding.ContentMessageQuoteBinding.inflate(
                LayoutInflater.from(quoteContainer.context),
                quoteContainer,
                false
            )
            val quotedMessage = message.quotedMessage!!
            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

            quoteBinding.quoteSenderAndTime.text = "${quotedMessage.senderName} $timestamp"
            quoteBinding.quoteContent.text = quotedMessage.content
            quoteBinding.quoteRootView.setOnClickListener {
                listener?.onQuoteJump(quotedMessage.messageId)
            }
            quoteContainer.addView(quoteBinding.root)
            quoteContainer.visibility = View.VISIBLE
        } else {
            quoteContainer.visibility = View.GONE
        }
    }

    private fun showCustomMenu(anchorView: View, message: ChatMessage, onDismiss: (() -> Unit)? = null) {
        val context = anchorView.context
        val inflater = LayoutInflater.from(context)
        val menuBinding = PopupQqMessageMenuBinding.inflate(inflater)

        val popupWindow = PopupWindow(
            menuBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isTouchable = true
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                onDismiss?.invoke()
            }
        }

        fun setupMenuItem(binding: ItemMessageMenuBinding, iconRes: Int, text: String, action: () -> Unit) {
            binding.menuItemIcon.setImageResource(iconRes)
            binding.menuItemText.text = text
            binding.root.setOnClickListener {
                android.util.Log.d("ChatMessageAdapter", "Menu item clicked: ${binding.menuItemText.text}")
                action()
                popupWindow.dismiss()
            }
        }

        setupMenuItem(menuBinding.actionCopy, R.drawable.ic_content_copy, "复制") { listener?.onCopy(message) }
        setupMenuItem(menuBinding.actionForward, R.drawable.ic_forward, "转发") { listener?.onForward(message) }
        if (message.isFavorited) {
            setupMenuItem(menuBinding.actionFavorite, R.drawable.ic_star_full, "取消收藏") { listener?.onFavorite(message) }
        } else {
            setupMenuItem(menuBinding.actionFavorite, R.drawable.ic_star_outline, "收藏") { listener?.onFavorite(message) }
        }
        setupMenuItem(menuBinding.actionDelete, R.drawable.ic_delete_outline, "删除") { listener?.onDelete(message) }
        setupMenuItem(menuBinding.actionMultiSelect, R.drawable.ic_multi_select, "多选") { listener?.onMultiSelect(message) }
        setupMenuItem(menuBinding.actionQuote, R.drawable.ic_format_quote, "引用") { listener?.onQuote(message) }
        val editAction = if (message.type == "naiimag" && message.imageUrl == "[图片生成失败]") {
            fun() { listener?.onRewritePromptClick(message) }
        } else {
            fun() { listener?.onEdit(message) }
        }
        setupMenuItem(menuBinding.actionRemind, R.drawable.ic_edit_24, "编辑", editAction)
        if (message.type == "voice_message") {
            setupMenuItem(menuBinding.actionRegenerateTts, R.drawable.ic_refresh_24, "重新生成") { listener?.onRegenerateVoice(message) }
            menuBinding.actionRegenerateTts.root.visibility = View.VISIBLE
        } else {
            menuBinding.actionRegenerateTts.root.visibility = View.GONE
        }
        setupMenuItem(menuBinding.actionScreenshot, R.drawable.ic_crop, "截图") { listener?.onScreenshot(message) }

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val menuView = menuBinding.root
        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuHeight = menuView.measuredHeight
        val menuWidth = menuView.measuredWidth

        val x = location[0] + (anchorView.width / 2) - (menuWidth / 2)
        val y = location[1] - menuHeight - 16

        // 打印PopupWindow的显示位置，方便调试
        android.util.Log.d("ChatMessageAdapter", "Showing PopupWindow at x: $x, y: $y, width: $menuWidth, height: $menuHeight")

        popupWindow.showAtLocation(anchorView.rootView, Gravity.NO_GRAVITY, x, y)
    }

    inner class SentViewHolder(private val binding: ItemChatBubbleSentBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(message: ChatMessage, showTimeDivider: Boolean) {
            val context = itemView.context
            val inflater = LayoutInflater.from(context)

            // 默认设置气泡背景
            binding.contentContainer.setBackgroundResource(R.drawable.bg_chat_bubble_sent)

            // 1. 绑定通用视图 (时间分割线, 头像, 时间戳, 多选框)
            binding.timeDividerTextView.isVisible = showTimeDivider
            if (showTimeDivider) {
                binding.timeDividerTextView.text = formatTimeDivider(message.timestamp)
            }

            Glide.with(context)
                .load(userAvatarUrl ?: R.drawable.ic_default_avatar)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.avatarImageView)

            binding.timestampTextView.text = formatTimestamp(message.timestamp)
            binding.readReceiptCheckTextView.isVisible = message.hasBeenSeenByAi
            binding.multiSelectCheckbox.isVisible = isMultiSelectMode
            binding.multiSelectCheckbox.isChecked = selectedItems.contains(message)
            binding.multiSelectCheckbox.setOnClickListener { toggleSelection(message) }

            // 2. 清空并动态填充内容容器
            binding.contentContainer.removeAllViews()
            bindQuotedMessage(binding.quoteContainer, message)

            when (message.type) {
                "text" -> {
                    if (!message.content.isNullOrBlank()) {
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        // 使用 Markwon 渲染 Markdown 和 HTML 格式
                        MarkdownRenderer.renderMarkdown(contentBinding.messageTextView, formatMessageText(message.content))
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }
                "sticker" -> {
                    if (!message.stickerUrl.isNullOrBlank()) {
                        // 表情不需要气泡背景
                        binding.contentContainer.background = null
                        binding.contentContainer.setPadding(0, 0, 0, 0)
                        val contentBinding = ContentMessageStickerBinding.inflate(inflater)
                        Glide.with(context)
                            .load(message.stickerUrl)
                            .placeholder(R.drawable.bg_image_placeholder)
                            .error(R.drawable.ic_image_load_failed)
                            .into(contentBinding.stickerImageView)
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }
                "image_url", "naiimag", "ai_image" -> {
                    if (!message.imageUrl.isNullOrBlank()) {
                        // 图片不需要气泡背景
                        binding.contentContainer.background = null
                        binding.contentContainer.setPadding(0, 0, 0, 0)
                        val contentBinding = ContentMessageImageBinding.inflate(inflater)
                        val imageUrl = message.imageUrl
                        val imageLoader = Glide.with(context)

                        if (!imageUrl.isNullOrBlank()) {
                            val imageFile = File(imageUrl)
                            val loadTarget = if (imageFile.exists()) {
                                Log.d(TAG, "Loading image from file path: $imageUrl")
                                imageFile
                            } else {
                                Log.d(TAG, "Loading image from URL/Base64: $imageUrl")
                                imageUrl
                            }
                            imageLoader.load(loadTarget)
                                .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                                .placeholder(R.drawable.bg_image_placeholder)
                                .error(R.drawable.ic_image_load_failed)
                                .into(contentBinding.messageImageView)
                        }

                        contentBinding.root.setOnClickListener {
                            if (!isMultiSelectMode) {
                                listener?.onZoomImageClick(message)
                            } else {
                                toggleSelection(message)
                            }
                        }
                        contentBinding.root.setOnLongClickListener {
                            if (!isMultiSelectMode) {
                                showCustomMenu(it, message)
                            }
                            true
                        }
                        binding.contentContainer.addView(contentBinding.root)
                    } else if (!message.content.isNullOrBlank()) {
                        // Fallback to text for prompts or failed images
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        contentBinding.messageTextView.text = formatMessageText(message.content)
                        binding.contentContainer.addView(contentBinding.root)
                    } else {
                        // 当图片URL和文本都为空时，显示错误信息
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        contentBinding.messageTextView.text = "[图片发送失败]"
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }
                "location_share" -> {
                    val contentBinding = ContentMessageLocationBinding.inflate(inflater)
                    contentBinding.locationNameTextView.text = message.content ?: message.content
                    binding.contentContainer.addView(contentBinding.root)
                }
                "waimai_request" -> {
                    val contentBinding = ContentMessageWaimaiBinding.inflate(inflater)
                    contentBinding.root.setOnClickListener { listener?.onWaimaiCardClick(message) }
                    contentBinding.waimaiTitle.text = "代付请求"
                    contentBinding.waimaiSubtitle.text = message.productInfo
                    contentBinding.waimaiAmountText.text = String.format(Locale.CHINA, "¥%.2f", message.amount)
                    contentBinding.viewDetailsButton.text = "点击查看详情"
                    binding.contentContainer.addView(contentBinding.root)
                }
                "waimai_order" -> {
                    val contentBinding = ContentMessageWaimaiOrderBinding.inflate(inflater)
                    contentBinding.viewDetailsButton.setOnClickListener {
                        listener?.onWaimaiCardClick(message)
                    }
                    contentBinding.waimaiOrderTitle.text = "外卖订单"
                    contentBinding.waimaiOrderProductInfo.text = message.productInfo
                    contentBinding.waimaiOrderAmount.text = String.format(Locale.CHINA, "¥%.2f", message.amount ?: 0.0)
                    contentBinding.viewDetailsButton.text = "点击查看详情"
                    binding.contentContainer.addView(contentBinding.root)
                }
                "transfer", "accept_transfer", "decline_transfer" -> {
                    val contentBinding = ContentMessageTransferBinding.inflate(inflater)
                    val contact = contactsMap[message.contactId]
                    val recipientName = contact?.remarkName ?: contact?.realName ?: "对方"
                    contentBinding.transferTitle.text = "转账给 $recipientName"
                    contentBinding.transferAmount.text = String.format(Locale.CHINA, "¥%.2f", message.amount)
                    contentBinding.transferNotes.text = message.notes ?: ""
                    binding.contentContainer.addView(contentBinding.root)
                }
                "gift" -> {
                    val contentBinding = ContentMessageGiftBinding.inflate(inflater)
                    contentBinding.giftName.text = message.giftName ?: "礼物"
                    contentBinding.giftValue.text = String.format(Locale.CHINA, "价值 ¥%.2f", message.giftValue ?: 0.0)

                    // 显示备注,如果没有备注则显示默认文本
                    contentBinding.giftMessage.text = if (!message.giftNote.isNullOrBlank()) {
                        "❤️ ${message.giftNote}"
                    } else {
                        "❤️ 送给你的小礼物"
                    }

                    // 加载礼物图片
                    if (!message.giftImageUrl.isNullOrBlank()) {
                        Glide.with(context)
                            .load(message.giftImageUrl)
                            .placeholder(R.drawable.bg_image_placeholder)
                            .error(R.drawable.bg_image_placeholder)
                            .into(contentBinding.giftImage)
                    }

                    binding.contentContainer.addView(contentBinding.root)
                }
                "voice_message" -> {
                    val contentBinding = com.susking.ephone_s.qq.databinding.ContentMessageVoiceBinding.inflate(inflater)
                    bindVoiceMessageContent(contentBinding, message)

                    contentBinding.voiceMessageLayout.setOnLongClickListener {
                        if (!isMultiSelectMode) {
                            showCustomMenu(it, message)
                        }
                        true
                    }

                    binding.contentContainer.addView(contentBinding.root)
                }

                else -> { // 默认为文本
                     if (!message.content.isNullOrBlank()) {
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        // 使用 Markwon 渲染 Markdown 和 HTML 格式
                        MarkdownRenderer.renderMarkdown(contentBinding.messageTextView, formatMessageText(message.content))
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }
            }

            // 【新增】如果没有任何内容被添加到气泡中，显示一条提示
            if (binding.contentContainer.childCount == 0) {
                val contentBinding = ContentMessageTextBinding.inflate(inflater)
                contentBinding.messageTextView.text = "[消息内容为空]"
                binding.contentContainer.addView(contentBinding.root)
            }

            // 3. 绑定通用交互事件 (长按菜单, 多选等)
            binding.root.setOnClickListener {
                if (isMultiSelectMode) toggleSelection(message)
            }
             binding.bubbleContentWrapper.setOnLongClickListener { view ->
                if (!isMultiSelectMode) {
                    showCustomMenu(view, message)
                }
                true
            }
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemChatBubbleReceivedBinding) : RecyclerView.ViewHolder(binding.root) {
        private val recallHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var recallRunnable: Runnable? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(message: ChatMessage, showTimeDivider: Boolean) {
            val context = itemView.context
            val inflater = LayoutInflater.from(context)
            var isLongClickEnabled = true // 默认启用长按

            // 清理之前的延迟任务,防止内存泄漏
            recallRunnable?.let { recallHandler.removeCallbacks(it) }
            recallRunnable = null

            // 默认设置气泡背景
            binding.contentContainer.setBackgroundResource(R.drawable.bg_chat_bubble_received)

            // 1. 绑定通用视图
            binding.timeDividerTextView.isVisible = showTimeDivider
            if (showTimeDivider) {
                binding.timeDividerTextView.text = formatTimeDivider(message.timestamp)
            }

            val contact = message.contactId?.let { contactsMap[it] }
            Glide.with(context)
                .load(contact?.avatarUri ?: R.drawable.ic_default_avatar)
                .into(binding.avatarImageView)

            binding.timestampTextView.text = formatTimestamp(message.timestamp)
            binding.multiSelectCheckbox.isVisible = isMultiSelectMode
            binding.multiSelectCheckbox.isChecked = selectedItems.contains(message)
            binding.multiSelectCheckbox.setOnClickListener { toggleSelection(message) }

            // 绑定AI回复切换器
            val versions = message.aiResponseVersions

            // 调试日志:检查版本历史
            android.util.Log.d(
                "ChatMessageAdapter",
                "消息ID: ${message.id}, 版本数量: ${versions.size}, turnId: ${message.aiTurnId}"
            )

            if (versions.size > 1) {
                binding.aiResponseSwitcherLayout.visibility = View.VISIBLE
                val currentIndex = message.displayedResponseIndex
                val totalVersions = versions.size
                binding.responseVersionTextView.text = "${currentIndex + 1} / $totalVersions"

                // 循环切换，按钮始终可用
                binding.prevResponseButton.isEnabled = true
                binding.nextResponseButton.isEnabled = true

                binding.prevResponseButton.setOnClickListener {
                    val newIndex = (currentIndex - 1 + totalVersions) % totalVersions
                    listener?.onSwitchResponse(message, newIndex)
                }
                binding.nextResponseButton.setOnClickListener {
                    val newIndex = (currentIndex + 1) % totalVersions
                    listener?.onSwitchResponse(message, newIndex)
                }
            } else {
                binding.aiResponseSwitcherLayout.visibility = View.GONE
            }

            // 2. 清空并动态填充内容容器
            binding.contentContainer.removeAllViews()
            binding.quoteContainer.removeAllViews()
            bindQuotedMessage(binding.quoteContainer, message)

            // 处理 send_and_recall 类型消息
            if (message.type == "send_and_recall" && !message.isRecalled) {
                // 显示原始内容作为普通文本气泡
                val contentBinding = ContentMessageTextBinding.inflate(inflater)
                if (!message.content.isNullOrBlank()) {
                    contentBinding.messageTextView.text = formatMessageText(message.content)
                } else {
                    contentBinding.messageTextView.text = "[空消息]"
                }
                contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                binding.contentContainer.addView(contentBinding.root)

                // 设置随机延迟(0.5-1.5秒)后触发撤回
                val delayMillis = (500..1500).random().toLong()
                recallRunnable = Runnable {
                    listener?.onMessageRecalled(message)
                }
                recallHandler.postDelayed(recallRunnable!!, delayMillis)
                return
            }

            when (message.type) {
                "text", "offline_text" -> {
                    val contentBinding = ContentMessageTextBinding.inflate(inflater)
                    if (!message.content.isNullOrBlank()) {
                        // 使用 Markwon 渲染 Markdown 和 HTML 格式
                        MarkdownRenderer.renderMarkdown(
                            contentBinding.messageTextView,
                            formatMessageText(message.content)
                        )
                    } else {
                        contentBinding.messageTextView.text = "[空消息]"
                    }
                    // 接收方文本颜色不同
                    contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                    binding.contentContainer.addView(contentBinding.root)
                }

                "sticker" -> {
                    if (!message.stickerUrl.isNullOrBlank()) {
                        // 表情不需要气泡背景
                        binding.contentContainer.background = null
                        binding.contentContainer.setPadding(0, 0, 0, 0)
                        val contentBinding = ContentMessageStickerBinding.inflate(inflater)
                        Glide.with(context)
                            .load(message.stickerUrl)
                            .placeholder(R.drawable.bg_image_placeholder)
                            .error(R.drawable.ic_image_load_failed)
                            .into(contentBinding.stickerImageView)
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }

                "image_url", "naiimag", "ai_image" -> {
                    val isNaiImage = message.type == "naiimag"
                    val isGenerating = isNaiImage && message.imageUrl.isNullOrBlank()
                    val hasFailed = isNaiImage && message.imageUrl == "[图片生成失败]"

                    if (isGenerating) {
                        // NAI图片正在生成
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        contentBinding.messageTextView.text = "正在生图中..."
                        contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                        binding.contentContainer.addView(contentBinding.root)
                        isLongClickEnabled = false // 正在生成时禁用长按
                    } else if (hasFailed) {
                        // NAI图片生成失败，显示错误信息
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        contentBinding.messageTextView.text = formatMessageText(message.content)
                        contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                        binding.contentContainer.addView(contentBinding.root)
                        // isLongClickEnabled 保持 true, 允许对失败信息进行操作
                    } else if (!message.imageUrl.isNullOrBlank()) {
                        // 图片加载成功
                        isLongClickEnabled = false // 图片自身有触摸处理，禁用外层包装的长按
                        binding.contentContainer.background = null
                        binding.contentContainer.setPadding(0, 0, 0, 0)
                        val contentBinding = ContentMessageImageBinding.inflate(inflater)
                        val imageUrl = message.imageUrl
                        val imageLoader = Glide.with(context)

                        if (!imageUrl.isNullOrBlank()) {
                            if (imageUrl == "generating_placeholder") {
                                // 显示加载中的圈圈动画占位图
                                Log.d(TAG, "显示图片生成中占位图")
                                // 创建包装的加载动画,使其居中显示并周围有留白
                                val wrappedProgress =
                                    createCenteredProgressDrawable(context, 600, 600)
                                // 设置scaleType确保显示完整的drawable包括留白
                                contentBinding.messageImageView.scaleType =
                                    android.widget.ImageView.ScaleType.FIT_CENTER
                                imageLoader.load(wrappedProgress)
                                    .override(600, 600)
                                    .into(contentBinding.messageImageView)
                                contentBinding.messageImageView.alpha = 1.0f
                            } else if (imageUrl == "error_placeholder") {
                                // 显示大的错误占位图
                                Log.d(TAG, "显示图片生成失败占位图")
                                imageLoader.load(R.drawable.ic_image_load_failed)
                                    .override(600, 600) // 设置大尺寸占位图
                                    .into(contentBinding.messageImageView)
                                contentBinding.messageImageView.alpha = 1.0f
                            } else {
                                val imageFile = File(imageUrl)
                                val loadTarget = if (imageFile.exists()) {
                                    Log.d(TAG, "Loading image from file path: $imageUrl")
                                    imageFile
                                } else {
                                    Log.d(TAG, "Loading image from URL/Base64: $imageUrl")
                                    imageUrl
                                }
                                imageLoader.load(loadTarget)
                                    .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                                    .placeholder(R.drawable.bg_image_placeholder)
                                    .error(R.drawable.ic_image_load_failed)
                                    .into(contentBinding.messageImageView)
                                contentBinding.messageImageView.alpha = 1.0f
                            }
                        }

                        val pressDownAnim =
                            AnimationUtils.loadAnimation(itemView.context, R.anim.bubble_press_down)
                        val pressUpAnim =
                            AnimationUtils.loadAnimation(itemView.context, R.anim.bubble_press_up)
                        var lastClickTime = 0L
                        var clickCount = 0
                        var pendingClickRunnable: Runnable? = null

                        contentBinding.imageBubbleLayout.setOnTouchListener(object :
                            View.OnTouchListener {
                            private val longPressHandler =
                                android.os.Handler(android.os.Looper.getMainLooper())
                            private var isLongPressTriggered = false
                            private val longPressRunnable = Runnable {
                                isLongPressTriggered = true
                                pendingClickRunnable?.let { itemView.removeCallbacks(it) }
                                pendingClickRunnable = null
                                clickCount = 0

                                if (isMultiSelectMode) {
                                    toggleSelection(message)
                                } else {
                                    itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    itemView.startAnimation(pressDownAnim)
                                    showCustomMenu(itemView, message) {
                                        itemView.startAnimation(pressUpAnim)
                                    }
                                }
                            }

                            @SuppressLint("ClickableViewAccessibility")
                            override fun onTouch(v: View, event: MotionEvent): Boolean {
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isLongPressTriggered = false
                                        longPressHandler.postDelayed(
                                            longPressRunnable,
                                            android.view.ViewConfiguration.getLongPressTimeout()
                                                .toLong()
                                        )
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastClickTime < 300) {
                                            clickCount++
                                        } else {
                                            clickCount = 1
                                            pendingClickRunnable?.let { v.removeCallbacks(it) }
                                            pendingClickRunnable = null
                                        }
                                        lastClickTime = currentTime
                                        if (clickCount == 3) {
                                            longPressHandler.removeCallbacks(longPressRunnable)
                                            pendingClickRunnable?.let { v.removeCallbacks(it) }
                                            pendingClickRunnable = null
                                            listener?.onSaveImageToAlbum(message)
                                        }
                                        return true
                                    }

                                    MotionEvent.ACTION_UP -> {
                                        longPressHandler.removeCallbacks(longPressRunnable)
                                        if (!isLongPressTriggered && clickCount == 1 && pendingClickRunnable == null) {
                                            pendingClickRunnable = Runnable {
                                                if (message.type == "naiimag") {
                                                    val isVisible =
                                                        contentBinding.zoomImageButton.isVisible
                                                    contentBinding.zoomImageButton.isVisible =
                                                        !isVisible
                                                    contentBinding.rewritePromptButton.isVisible =
                                                        !isVisible
                                                    contentBinding.rerollImageButton.isVisible =
                                                        !isVisible
                                                    listener?.onImageBubbleClick(message)
                                                } else {
                                                    listener?.onZoomImageClick(message)
                                                }
                                                pendingClickRunnable = null
                                            }
                                            v.postDelayed(pendingClickRunnable!!, 300)
                                        } else if (isMultiSelectMode && !isLongPressTriggered) {
                                            toggleSelection(message)
                                        }
                                        return true
                                    }

                                    MotionEvent.ACTION_CANCEL -> {
                                        longPressHandler.removeCallbacks(longPressRunnable)
                                        return true
                                    }
                                }
                                return false
                            }
                        })

                        contentBinding.zoomImageButton.setOnClickListener {
                            listener?.onZoomImageClick(
                                message
                            )
                        }
                        contentBinding.rewritePromptButton.setOnClickListener {
                            listener?.onRewritePromptClick(
                                message
                            )
                        }
                        contentBinding.rerollImageButton.setOnClickListener {
                            listener?.onRerollImageClick(
                                message
                            )
                        }

                        binding.contentContainer.addView(contentBinding.root)
                    } else if (!message.content.isNullOrBlank()) {
                        // Fallback to text for prompts
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        contentBinding.messageTextView.text = formatMessageText(message.content)
                        contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }

                "location_share" -> {
                    val contentBinding = ContentMessageLocationBinding.inflate(inflater)
                    contentBinding.locationNameTextView.text = message.content ?: message.content
                    binding.contentContainer.addView(contentBinding.root)
                }

                "waimai_request" -> {
                    val contentBinding = ContentMessageWaimaiBinding.inflate(inflater)
                    contentBinding.root.setOnClickListener {
                        listener?.onWaimaiCardClick(message)
                    }
                    contentBinding.waimaiTitle.text = "代付请求"
                    contentBinding.waimaiSubtitle.text = message.productInfo
                    contentBinding.waimaiAmountText.text =
                        String.format(Locale.CHINA, "¥%.2f", message.amount)
                    val statusText = when (message.status) {
                        "pending" -> "等待你处理"
                        "paid" -> "你已同意代付"
                        "rejected" -> "你已拒绝代付"
                        else -> "点击查看详情"
                    }
                    contentBinding.viewDetailsButton.text = statusText
                    binding.contentContainer.addView(contentBinding.root)
                }

                "waimai_order" -> {
                    val contentBinding = ContentMessageWaimaiOrderBinding.inflate(inflater)
                    contentBinding.viewDetailsButton.setOnClickListener {
                        listener?.onWaimaiCardClick(message)
                    }
                    contentBinding.waimaiOrderTitle.text = "外卖订单"
                    contentBinding.waimaiOrderProductInfo.text = message.productInfo
                    contentBinding.waimaiOrderAmount.text =
                        String.format(Locale.CHINA, "¥%.2f", message.amount ?: 0.0)
                    contentBinding.viewDetailsButton.text = "点击查看详情"
                    binding.contentContainer.addView(contentBinding.root)
                }

                "transfer", "accept_transfer", "decline_transfer" -> {
                    val contentBinding = ContentMessageTransferBinding.inflate(inflater)
                    if (message.status == "pending") {
                        contentBinding.root.setOnClickListener {
                            listener?.onTransferCardClick(message)
                        }
                    }
                    val contact = contactsMap[message.contactId]
                    val senderName = contact?.remarkName ?: contact?.realName ?: "对方"
                    contentBinding.transferTitle.text = "来自 $senderName 的转账"
                    contentBinding.transferAmount.text =
                        String.format(Locale.CHINA, "¥%.2f", message.amount)
                    val statusText = when (message.status) {
                        "pending" -> "待查收"
                        "accepted" -> "已收款"
                        "declined" -> "已退还"
                        else -> "转账"
                    }
                    contentBinding.transferNotes.text = statusText
                    binding.contentContainer.addView(contentBinding.root)
                }

                "gift" -> {
                    val contentBinding = ContentMessageGiftBinding.inflate(inflater)
                    contentBinding.giftName.text = message.giftName ?: "礼物"
                    contentBinding.giftValue.text =
                        String.format(Locale.CHINA, "价值 ¥%.2f", message.giftValue ?: 0.0)

                    // 显示备注,如果没有备注则显示默认文本
                    contentBinding.giftMessage.text = if (!message.giftNote.isNullOrBlank()) {
                        "❤️ ${message.giftNote}"
                    } else {
                        "❤️ 送给你的小礼物"
                    }

                    // 加载礼物图片
                    if (!message.giftImageUrl.isNullOrBlank()) {
                        Glide.with(context)
                            .load(message.giftImageUrl)
                            .placeholder(R.drawable.bg_image_placeholder)
                            .error(R.drawable.bg_image_placeholder)
                            .into(contentBinding.giftImage)
                    }

                    binding.contentContainer.addView(contentBinding.root)
                }

                "voice_message" -> {
                    val contentBinding =
                        com.susking.ephone_s.qq.databinding.ContentMessageVoiceBinding.inflate(
                            inflater
                        )
                    bindVoiceMessageContent(contentBinding, message)

                    contentBinding.voiceMessageLayout.setOnLongClickListener {
                        if (!isMultiSelectMode) {
                            showCustomMenu(it, message)
                        }
                        true
                    }

                    binding.contentContainer.addView(contentBinding.root)
                }

                "offline_request" -> {
                    val contentBinding =
                        com.susking.ephone_s.qq.databinding.ContentMessageOfflineRequestBinding.inflate(
                            inflater
                        )

                    // 设置地点和理由
                    contentBinding.offlineLocation.text = message.offlineLocation ?: "未指定地点"
                    contentBinding.offlineReason.text = message.offlineReason ?: "想见面聊聊"
                    
                    // 根据状态显示不同的UI
                    when (message.status) {
                        "pending" -> {
                            contentBinding.actionButtonsContainer.visibility = View.VISIBLE
                            contentBinding.offlineStatusText.visibility = View.GONE
                        }
                        "accepted" -> {
                            contentBinding.actionButtonsContainer.visibility = View.GONE
                            contentBinding.offlineStatusText.visibility = View.VISIBLE
                            contentBinding.offlineStatusText.text = "已同意"
                        }
                        "rejected" -> {
                            contentBinding.actionButtonsContainer.visibility = View.GONE
                            contentBinding.offlineStatusText.visibility = View.VISIBLE
                            contentBinding.offlineStatusText.text = "已拒绝"
                        }
                        else -> {
                            contentBinding.actionButtonsContainer.visibility = View.VISIBLE
                            contentBinding.offlineStatusText.visibility = View.GONE
                        }
                    }
                    
                    // 设置按钮点击事件
                    contentBinding.acceptButton.setOnClickListener {
                        listener?.onAcceptOfflineRequest(message)
                    }
                    contentBinding.declineButton.setOnClickListener {
                        listener?.onDeclineOfflineRequest(message)
                    }
                    
                    binding.contentContainer.addView(contentBinding.root)
                }

                else -> { // 默认为文本
                    if (!message.content.isNullOrBlank()) {
                        val contentBinding = ContentMessageTextBinding.inflate(inflater)
                        // 使用 Markwon 渲染 Markdown 和 HTML 格式
                        MarkdownRenderer.renderMarkdown(
                            contentBinding.messageTextView,
                            formatMessageText(message.content)
                        )
                        contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                        binding.contentContainer.addView(contentBinding.root)
                    }
                }
            }

            // 【新增】如果没有任何内容被添加到气泡中，显示一条提示
            if (binding.contentContainer.childCount == 0) {
                val contentBinding = ContentMessageTextBinding.inflate(inflater)
                contentBinding.messageTextView.text = "[消息内容为空]"
                contentBinding.messageTextView.setTextColor(context.getColor(R.color.material_on_surface_emphasis_medium))
                binding.contentContainer.addView(contentBinding.root)
            }
            
            // 3. 绑定通用交互事件
             binding.root.setOnClickListener {
                if (isMultiSelectMode) toggleSelection(message)
            }
            if (isLongClickEnabled) {
                binding.bubbleContentWrapper.setOnLongClickListener { view ->
                    if (!isMultiSelectMode) {
                        showCustomMenu(view, message)
                    }
                    true
                }
            } else {
                binding.bubbleContentWrapper.setOnLongClickListener(null)
            }
            // 头像手势：单击打开资料详情，双击触发默认拍一拍
            val avatarGestureDetector = android.view.GestureDetector(
                itemView.context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        message.contactId?.let { listener?.onContactAvatarClick(it) }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        message.contactId?.let { listener?.onContactAvatarDoubleClick(it) }
                        return true
                    }
                }
            )
            binding.avatarImageView.setOnTouchListener { view, event ->
                avatarGestureDetector.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                true
            }
        }
    }

    inner class SystemMessageViewHolder(private val binding: ItemChatBubbleSystemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, showTimeDivider: Boolean) {
            binding.timeDividerTextView.isVisible = showTimeDivider
            if (showTimeDivider) {
                binding.timeDividerTextView.text = formatTimeDivider(message.timestamp)
            }

            // 先重置为默认背景，防止 ViewHolder 复用时残留上一条错误消息的红色背景
            binding.systemMessageTextView.setBackgroundResource(R.drawable.bg_system_message)
            // 默认隐藏重试按钮，仅错误消息显示
            binding.errorRetryButton.isVisible = false
            binding.errorRetryButton.setOnClickListener(null)

            // 错误消息使用淡红色背景
            if (message.role == "error") {
                binding.systemMessageTextView.setBackgroundResource(R.drawable.bg_error_message)
                binding.systemMessageTextView.text = message.content
                // 显示重试按钮：点击后删除该错误气泡并重新向模型发起请求（走 brain）
                binding.errorRetryButton.isVisible = !isMultiSelectMode
                binding.errorRetryButton.setOnClickListener {
                    listener?.onRetryError(message)
                }
            } else if (message.isRecalled) {
                binding.systemMessageTextView.text = "对方撤回了一条消息"
                binding.root.setOnClickListener {
                    if (!isMultiSelectMode) {
                        listener?.onViewRecalledContent(message)
                    } else {
                        toggleSelection(message)
                    }
                }
            } else if (message.type == "video_call_record") {
                // 通话结束记录，添加点击查看视频通话历史详情的功能。
                // 解析JSON格式的content，只显示text部分。
                val displayText = try {
                    val contentMap = com.google.gson.Gson().fromJson(message.content, Map::class.java)
                    contentMap["text"] as? String ?: message.content
                } catch (e: Exception) {
                    message.content // 兼容旧格式，直接显示
                }
                binding.systemMessageTextView.text = displayText
                binding.root.setOnClickListener {
                    if (!isMultiSelectMode) {
                        listener?.onVideoCallRecordClick(message)
                    } else {
                        toggleSelection(message)
                    }
                }
            } else {
                binding.systemMessageTextView.text = message.content
                binding.root.setOnClickListener {
                    if (isMultiSelectMode) {
                        toggleSelection(message)
                    }
                }
            }

            val multiSelectCheckbox = itemView.findViewById<android.widget.CheckBox>(com.susking.ephone_s.qq.R.id.multi_select_checkbox)
            multiSelectCheckbox?.let {
                it.isVisible = isMultiSelectMode
                it.isChecked = selectedItems.contains(message)
                it.setOnClickListener { toggleSelection(message) }
            }

            // 为系统消息添加长按监听器
            binding.root.setOnLongClickListener { view ->
                if (!isMultiSelectMode) {
                    showCustomMenu(view, message)
                }
                true
            }
        }
    }

    inner class FriendApplicationViewHolder(private val binding: ItemChatMessageFriendApplicationBinding) : RecyclerView.ViewHolder(binding.root) {
       fun bind(message: ChatMessage, showTimeDivider: Boolean) {
           val contact = contactsMap[message.contactId]
           binding.applicationTitleText.text = "${contact?.remarkName ?: "对方"} 请求添加你为好友"
           binding.applicationReasonText.text = message.content ?: "请求添加好友"

           when (message.status) {
               "pending" -> {
                   binding.actionButtonsContainer.visibility = View.VISIBLE
                   binding.applicationStatusText.visibility = View.GONE
               }
               "accepted" -> {
                   binding.actionButtonsContainer.visibility = View.GONE
                   binding.applicationStatusText.visibility = View.VISIBLE
                   binding.applicationStatusText.text = "已同意"
               }
               "declined" -> {
                   binding.actionButtonsContainer.visibility = View.GONE
                   binding.applicationStatusText.visibility = View.VISIBLE
                   binding.applicationStatusText.text = "已拒绝"
               }
               else -> {
                   binding.actionButtonsContainer.visibility = View.GONE
                   binding.applicationStatusText.visibility = View.GONE
               }

           }

           binding.acceptButton.setOnClickListener { listener?.onAcceptFriendApplication(message) }
           binding.declineButton.setOnClickListener { listener?.onDeclineFriendApplication(message) }

           // 为好友申请卡片添加长按监听器
           itemView.setOnLongClickListener { view ->
               if (!isMultiSelectMode) {
                   showCustomMenu(view, message)
               }
               true
           }
       }
    }

    inner class ShoppingAccessRequestViewHolder(private val binding: com.susking.ephone_s.qq.databinding.ItemChatMessageShoppingAccessRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, showTimeDivider: Boolean) {
            binding.timeDividerTextView.isVisible = showTimeDivider
            if (showTimeDivider) {
                binding.timeDividerTextView.text = formatTimeDivider(message.timestamp)
            }

            // 显示申请内容
            binding.requestMessageText.text = "[系统提示：用户申请查看你的购物app页面]"

            // 根据状态显示不同的提示
            when (message.status) {
                "pending" -> {
                    binding.statusHintText.visibility = View.VISIBLE
                    binding.statusHintText.text = "请选择同意、拒绝或无视此申请"
                }
                "approved" -> {
                    binding.statusHintText.visibility = View.VISIBLE
                    binding.statusHintText.text = "已同意"
                }
                "rejected" -> {
                    binding.statusHintText.visibility = View.VISIBLE
                    binding.statusHintText.text = "已拒绝"
                }
                else -> {
                    binding.statusHintText.visibility = View.GONE
                }
            }

            // 为卡片添加长按监听器
            itemView.setOnLongClickListener { view ->
                if (!isMultiSelectMode) {
                    showCustomMenu(view, message)
                }
                true
            }
        }
    }


    // 创建一个居中的进度动画Drawable,周围有留白
    private fun createCenteredProgressDrawable(context: android.content.Context, width: Int, height: Int): Drawable {
        val circularProgress = androidx.swiperefreshlayout.widget.CircularProgressDrawable(context)
        // 圆圈尺寸适中,确保周围有足够留白
        circularProgress.strokeWidth = 10f.dpToPx(context)
        circularProgress.centerRadius = 50f.dpToPx(context) // 半径50dp,直径约100dp,在600px的区域中会有明显留白
        circularProgress.setColorSchemeColors(
            context.getColor(R.color.purple_700),
            context.getColor(R.color.purple_500)
        )
        circularProgress.start()

        // 创建一个包装Drawable,将CircularProgressDrawable居中放置
        return object : Drawable() {
            init {
                val progressDiameter = ((circularProgress.centerRadius * 2 + circularProgress.strokeWidth).toInt())
                val left = (width - progressDiameter) / 2
                val top = (height - progressDiameter) / 2
                circularProgress.setBounds(left, top, left + progressDiameter, top + progressDiameter)

                // 设置回调,让CircularProgressDrawable能够触发重绘
                circularProgress.callback = object : Callback {
                    override fun invalidateDrawable(who: Drawable) {
                        invalidateSelf()
                    }

                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                        scheduleSelf(what, `when`)
                    }

                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                        unscheduleSelf(what)
                    }
                }
            }

            override fun draw(canvas: Canvas) {
                circularProgress.draw(canvas)
            }

            override fun setAlpha(alpha: Int) {
                circularProgress.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                circularProgress.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

            override fun getIntrinsicWidth(): Int = width

            override fun getIntrinsicHeight(): Int = height
        }
    }
}
