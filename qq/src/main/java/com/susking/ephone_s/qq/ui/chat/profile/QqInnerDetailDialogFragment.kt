package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.data.local.entity.HeartbeatEntity
import com.susking.ephone_s.aidata.data.local.entity.JottingEntity
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.qq.databinding.FragmentQqInnerDetailDialogBinding
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicySnapshot
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import javax.inject.Inject

@AndroidEntryPoint
class QqInnerDetailDialogFragment : DialogFragment() {

    private var _binding: FragmentQqInnerDetailDialogBinding? = null
    private val binding get() = _binding!!

    private var currentHeartbeat: HeartbeatEntity? = null
    private var currentJotting: JottingEntity? = null
    private var currentSemanticState: ContactSemanticStateEntity? = null

    // 使用 Hilt 注入 ViewModel
    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 Manager
    @Inject lateinit var qqContentManager: QqContentManager
    @Inject lateinit var followUpPolicyStore: FollowUpPolicyStore
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var contactId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
            ?: throw IllegalArgumentException("QqInnerDetailDialogFragment requires a contactId")
        // 设置对话框样式为不占满全屏，且有邮票形状背景
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_StampDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqInnerDetailDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId } ?: return

        // 触发 InnerActivityManager 加载特定联系人的心声和散记
        qqContentManager.loadContactDetails(contactId)
        observeViewModel()
        setupClickListeners()
        setupFragmentResultListeners()
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            "editHeartfelt", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT)
            if (newContent != null && currentHeartbeat != null) {
                qqContentManager.updateHeartbeat(currentHeartbeat!!.copy(content = newContent), contactId)
            }
        }

        childFragmentManager.setFragmentResultListener(
            "editJotting", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT)
            if (newContent != null && currentJotting != null) {
                qqContentManager.updateJotting(currentJotting!!.copy(content = newContent), contactId)
            }
        }

        childFragmentManager.setFragmentResultListener(
            "editRecentSemantic", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT) ?: return@setFragmentResultListener
            updateSemanticState { semanticState: ContactSemanticStateEntity -> semanticState.copy(activeSemanticContext = newContent) }
        }

        childFragmentManager.setFragmentResultListener(
            "editUserCurrentState", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT) ?: return@setFragmentResultListener
            updateSemanticState { semanticState: ContactSemanticStateEntity -> semanticState.copy(historicalRecallAnchors = newContent) }
        }

        childFragmentManager.setFragmentResultListener(
            "editCharCurrentState", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT) ?: return@setFragmentResultListener
            updateSemanticState { semanticState: ContactSemanticStateEntity -> semanticState.copy(resolvedEventAnchors = newContent) }
        }

        childFragmentManager.setFragmentResultListener(
            "editSemanticKeywords", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT) ?: return@setFragmentResultListener
            updateSemanticState { semanticState: ContactSemanticStateEntity -> semanticState.copy(semanticKeywords = newContent) }
        }

        childFragmentManager.setFragmentResultListener(
            "editStateValidityNote", this
        ) { _, bundle ->
            val newContent = bundle.getString(EditInnerContentDialogFragment.RESULT_CONTENT) ?: return@setFragmentResultListener
            updateSemanticState { semanticState: ContactSemanticStateEntity -> semanticState.copy(lifecycleNotes = newContent) }
        }
    }

    private fun setupClickListeners() {
        binding.favoriteButton.setOnClickListener {
            // 调用 toggleInnerContentFavorite,传入心声和散记
            qqContentManager.toggleInnerContentFavorite(currentHeartbeat, currentJotting)
        }

        binding.historyButton.setOnClickListener {
            QqInnerHistoryFragment.newInstance(contactId)
                .show(parentFragmentManager, "inner_history")
        }

        binding.editHeartfeltButton.setOnClickListener {
            currentHeartbeat?.let {
                EditInnerContentDialogFragment.newInstance("编辑心声", it.content, "editHeartfelt")
                    .show(childFragmentManager, "editHeartfelt")
            }
        }

        binding.editCasualNotesButton.setOnClickListener {
            currentJotting?.let {
                EditInnerContentDialogFragment.newInstance("编辑散记", it.content, "editJotting")
                    .show(childFragmentManager, "editJotting")
            }
        }

        binding.editRecentSemanticButton.setOnClickListener {
            EditInnerContentDialogFragment.newInstance("编辑当前互动语义", currentSemanticState?.activeSemanticContext.orEmpty(), "editRecentSemantic")
                .show(childFragmentManager, "editRecentSemantic")
        }

        binding.editUserCurrentStateButton.setOnClickListener {
            EditInnerContentDialogFragment.newInstance("编辑历史召回锚点", currentSemanticState?.historicalRecallAnchors.orEmpty(), "editUserCurrentState")
                .show(childFragmentManager, "editUserCurrentState")
        }

        binding.editCharCurrentStateButton.setOnClickListener {
            EditInnerContentDialogFragment.newInstance("编辑已结束事件线索", currentSemanticState?.resolvedEventAnchors.orEmpty(), "editCharCurrentState")
                .show(childFragmentManager, "editCharCurrentState")
        }

        binding.editSemanticKeywordsButton.setOnClickListener {
            EditInnerContentDialogFragment.newInstance("编辑语义关键词", currentSemanticState?.semanticKeywords.orEmpty(), "editSemanticKeywords")
                .show(childFragmentManager, "editSemanticKeywords")
        }

        binding.editStateValidityNoteButton.setOnClickListener {
            EditInnerContentDialogFragment.newInstance("编辑生命周期说明", currentSemanticState?.lifecycleNotes.orEmpty(), "editStateValidityNote")
                .show(childFragmentManager, "editStateValidityNote")
        }

        binding.cancelFollowUpButton.setOnClickListener {
            followUpPolicyStore.cancelPolicy(contactId)
            populateFollowUpPolicy()
        }
    }

    private fun observeViewModel() {
        // 观察联系人基本信息
        viewModel.contactManager.contacts.observe(viewLifecycleOwner) { contacts ->
            val contact = contacts.find { it.id == contactId }
            contact?.let { populateContactInfo(it) }
        }

        // 观察最新的心声
        qqContentManager.latestHeartbeat.observe(viewLifecycleOwner) { heartbeat ->
            currentHeartbeat = heartbeat as? HeartbeatEntity
            binding.heartfeltWordsTextView.text = currentHeartbeat?.content ?: "暂无心声"
            binding.heartfeltTimestamp.text = currentHeartbeat?.timestamp?.let { formatTimestamp(it) } ?: ""
            updateFavoriteButtonState()
            populateFollowUpPolicy()
        }

        // 观察最新的散记
        qqContentManager.latestJotting.observe(viewLifecycleOwner) { jotting ->
            currentJotting = jotting as? JottingEntity
            binding.casualNotesTextView.text = currentJotting?.content ?: "暂无散记"
            binding.casualNotesTimestamp.text = currentJotting?.timestamp?.let { formatTimestamp(it) } ?: ""
            updateFavoriteButtonState()
            populateFollowUpPolicy()
        }

        // 观察当前语义状态
        qqContentManager.latestSemanticState.observe(viewLifecycleOwner) { semanticState ->
            currentSemanticState = semanticState
            populateSemanticState(semanticState)
        }

        // 观察收藏列表来更新按钮状态
        qqContentManager.favoriteMessages.observe(viewLifecycleOwner) {
            updateFavoriteButtonState()
        }
    }

    private fun updateSemanticState(transform: (ContactSemanticStateEntity) -> ContactSemanticStateEntity) {
        val baseSemanticState = currentSemanticState ?: ContactSemanticStateEntity(contactId = contactId)
        val updatedSemanticState = transform(baseSemanticState)
        currentSemanticState = updatedSemanticState
        populateSemanticState(updatedSemanticState)
        qqContentManager.updateSemanticState(updatedSemanticState, contactId)
    }

    private fun populateSemanticState(semanticState: ContactSemanticStateEntity?) {
        binding.recentSemanticTextView.text = semanticState?.activeSemanticContext?.ifBlank { "暂无当前互动语义" } ?: "暂无当前互动语义"
        binding.userCurrentStateTextView.text = semanticState?.historicalRecallAnchors?.ifBlank { "暂无历史召回锚点" } ?: "暂无历史召回锚点"
        binding.charCurrentStateTextView.text = semanticState?.resolvedEventAnchors?.ifBlank { "暂无已结束事件线索" } ?: "暂无已结束事件线索"
        binding.semanticKeywordsTextView.text = semanticState?.semanticKeywords?.ifBlank { "暂无语义关键词" } ?: "暂无语义关键词"
        binding.stateValidityNoteTextView.text = semanticState?.lifecycleNotes?.ifBlank { "暂无生命周期说明" } ?: "暂无生命周期说明"
        binding.semanticStateTimestamp.text = semanticState?.updatedAt?.let { formatTimestamp(it) } ?: ""
    }

    private fun populateFollowUpPolicy() {
        val snapshot: FollowUpPolicySnapshot = followUpPolicyStore.getPolicySnapshot(contactId)
        val statusText: String = if (snapshot.shouldFollowUp) "会追问" else "不追问"
        val countText: String = "已追问 ${snapshot.followUpCount}/2 次"
        val remainingText: String = buildFollowUpRemainingText(snapshot)
        val hintText: String = snapshot.followUpHint.ifBlank { "暂无追问原因" }
        binding.followUpPolicyStatusTextView.text = "本轮是否追问：$statusText（$countText）\n$remainingText"
        binding.followUpPolicyReasonTextView.text = "原因：$hintText"
        binding.cancelFollowUpButton.isEnabled = snapshot.shouldFollowUp
        binding.cancelFollowUpButton.alpha = if (snapshot.shouldFollowUp) 1.0f else 0.5f
    }

    private fun buildFollowUpRemainingText(snapshot: FollowUpPolicySnapshot): String {
        if (!snapshot.shouldFollowUp) return "距离追问：不会追问"
        if (!snapshot.canFollowUp) return "距离追问：已达到追问次数上限"
        val nextFollowUpAtMillis: Long = snapshot.savedAtMillis + getChatFollowUpDelayMillis()
        val remainingMillis: Long = nextFollowUpAtMillis - System.currentTimeMillis()
        if (remainingMillis <= 0L) return "距离追问：现在可以追问"
        return "距离追问还有 ${formatRemainingTime(remainingMillis)}"
    }

    private fun getChatFollowUpDelayMillis(): Long {
        return settingsRepository.getChatFollowUpDelaySeconds()
            .coerceAtLeast(MINIMUM_CHAT_TIMING_SECONDS)
            .toLong() * MILLIS_PER_SECOND
    }

    private fun formatRemainingTime(remainingMillis: Long): String {
        val totalMinutes: Long = ceil(remainingMillis.toDouble() / MILLIS_PER_MINUTE.toDouble()).toLong()
        val hours: Long = totalMinutes / MINUTES_PER_HOUR
        val minutes: Long = totalMinutes % MINUTES_PER_HOUR
        return when {
            hours > 0L && minutes > 0L -> "${hours}小时${minutes}分钟"
            hours > 0L -> "${hours}小时"
            else -> "${minutes.coerceAtLeast(1L)}分钟"
        }
    }

    private fun updateFavoriteButtonState() {
        if (currentHeartbeat == null && currentJotting == null) {
            binding.favoriteButton.setImageResource(R.drawable.ic_like_empty_24)
            return
        }

        val combinedId = "inner_${contactId}_${currentHeartbeat?.timestamp ?: "n"}_${currentJotting?.timestamp ?: "n"}"
        val isFavorited = qqContentManager.favoriteMessages.value?.any {
            it.messageId == combinedId
        } ?: false

        binding.favoriteButton.setImageResource(
            if (isFavorited) R.drawable.ic_like_filled_24 else R.drawable.ic_like_empty_24
        )
    }

    private fun populateContactInfo(contact: PersonProfile) {
        binding.contactNameTextView.text = contact.remarkName
        binding.realNameTextView.text = contact.realName

        Glide.with(this)
            .load(contact.avatarUri)
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(binding.avatarImageView)

        Glide.with(this)
            .load(contact.backgroundUri)
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(binding.backgroundImage)

        // 注意：这里不再从 contact 对象中获取心声和散记
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QqInnerDetailDialogFragment"
        private const val ARG_CONTACT_ID = "contact_id"
        private const val MILLIS_PER_SECOND: Long = 1_000L
        private const val MILLIS_PER_MINUTE: Long = 60_000L
        private const val MINUTES_PER_HOUR: Long = 60L
        private const val MINIMUM_CHAT_TIMING_SECONDS: Int = 1

        fun newInstance(contactId: String): QqInnerDetailDialogFragment {
            return QqInnerDetailDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}