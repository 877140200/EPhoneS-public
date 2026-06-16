package com.susking.ephone_s.qq.ui.chat.videoCall

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.qq.databinding.FragmentVideoCallBinding
import com.susking.ephone_s.qq.ui.QqViewModel

class VideoCallFragment : Fragment() {

    private var _binding: FragmentVideoCallBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    private lateinit var videoCallMessageAdapter: VideoCallMessageAdapter
    private var isHangUpByUser = false
    private var callEndDialog: AlertDialog? = null
    private var lastHangupReason: String? = null
    
    // 悬浮窗管理器(单例,确保只有一个悬浮窗)
    private var floatingWindow: VideoCallFloatingWindow? = null

    // 标记是否正在最小化，避免生命周期方法重复处理
    private var isMinimizing = false
    
    // 定时更新悬浮窗时长的Handler
    private val durationUpdateHandler = Handler(Looper.getMainLooper())
    private val durationUpdateRunnable = object : Runnable {
        override fun run() {
            updateFloatingWindowDuration()
            durationUpdateHandler.postDelayed(this, 1000) // 每秒更新一次
        }
    }
    
    // 返回键处理回调
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackPressed()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        setupBackPressedHandler()
        
        // 检查是否需要从最小化状态恢复
        checkAndRestoreFromMinimized()
    }

    private fun setupRecyclerView() {
        videoCallMessageAdapter = VideoCallMessageAdapter { message ->
            showEditMessageDialog(message)
        }
        binding.videoCallRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = videoCallMessageAdapter
        }
    }

    private fun setupClickListeners() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            handleBackPressed()
        }
        
        // 呼出时的挂断按钮
        binding.endCallButtonOutgoing.setOnClickListener {
            isHangUpByUser = true
            viewModel.videoCallManager.cancelOutgoingCall()
        }
        
        // 通话中控制
        binding.endCallButtonInCall.setOnClickListener {
            isHangUpByUser = true
            showHangupReasonDialog()
        }

        binding.speakButton.setOnClickListener {
            showTextInputDialog()
        }

        binding.rerollButtonInCall.setOnClickListener {
            viewModel.videoCallManager.rerollLastInCallResponse()
        }

        binding.redialButtonInCall.setOnClickListener {
            // 直接取 VideoCallManager 缓存的当前通话联系人,避免靠备注名字符串反查(重名/改名会匹配错)。
            val contact = viewModel.videoCallManager.currentCallContact
            contact?.let {
                viewModel.videoCallManager.startVideoCall(it.id, lastHangupReason)
            }
        }

        // 来电控制
        binding.acceptCallButton.setOnClickListener {
            viewModel.videoCallManager.acceptVideoCall()
        }

        binding.declineCallButton.setOnClickListener {
            showDeclineReasonDialog()
        }
    }

    private fun setupObservers() {
        viewModel.videoCallManager.videoCallState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is VideoCallState.Outgoing -> {
                    updateUiForOutgoingCall(state)
                }
                is VideoCallState.Incoming -> {
                    updateUiForIncomingCall(state)
                }
                is VideoCallState.Connecting -> {
                    updateUiForConnectingCall(state)
                }
                is VideoCallState.InProgress -> {
                    updateUiForInProgressCall(state)
                }
                is VideoCallState.Minimized -> {
                    // 最小化状态,Fragment应该已经被移除,这里不需要处理UI
                    // 悬浮窗由minimizeToFloatingWindow()方法管理
                }
                is VideoCallState.Terminated -> {
                    binding.callTimerChronometer.stop()
                    hideFloatingWindow() // 通话结束时隐藏悬浮窗
                    if (state.byUser) {
                        if (!safeIsStateSaved()) {
                            try {
                                parentFragmentManager.popBackStack()
                            } catch (e: IllegalStateException) {
                                // 状态已保存，忽略
                            }
                        }
                    } else {
                        showCallEndDialog(state.reason)
                    }
                }
                is VideoCallState.TerminatedByAi -> {
                    binding.callTimerChronometer.stop()
                    hideFloatingWindow() // AI挂断时隐藏悬浮窗
                    handleAiHangUp(state.reason)
                }
                is VideoCallState.Idle -> {
                    hideFloatingWindow() // 空闲状态隐藏悬浮窗
                    if (!safeIsStateSaved()) {
                        try {
                            parentFragmentManager.popBackStack()
                        } catch (e: IllegalStateException) {
                            // 状态已保存，忽略
                        }
                    }
                }
                else -> {
                    // Ignored
                }
            }
        })

        viewModel.videoCallManager.inCallHistory.observe(viewLifecycleOwner, Observer { messages ->
            videoCallMessageAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.videoCallRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        })

        viewModel.videoCallManager.showCallEndDialogEvent.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { reason ->
                if (!isHangUpByUser) {
                    showCallEndDialog(reason)
                }
            }
        })

        // 观察等待AI回复状态，控制说话按钮的启用/禁用
        viewModel.videoCallManager.isWaitingForAiResponse.observe(viewLifecycleOwner, Observer { isWaiting ->
            binding.speakButton.isEnabled = !isWaiting
            // 可选：更改按钮透明度以视觉上表示禁用状态
            binding.speakButton.alpha = if (isWaiting) 0.5f else 1.0f
        })
    }

    private fun showTextInputDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null)
        val textInput = dialogView.findViewById<TextInputEditText>(R.id.textInputDialog)

        AlertDialog.Builder(requireContext())
            .setTitle("你说")
            .setView(dialogView)
            .setPositiveButton("发送") { dialog, _ ->
                val inputText = textInput.text.toString()
                if (inputText.isNotBlank()) {
                    viewModel.videoCallManager.sendInCallMessage(inputText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCallEndDialog(reason: String) {
        if (callEndDialog?.isShowing == true) {
            return
        }
        // 直接取 VideoCallManager 缓存的当前通话联系人,避免靠 contactNameTextView 备注名反查(重名/改名会匹配错)。
        val contact = viewModel.videoCallManager.currentCallContact

        val title = when {
            reason.contains("拒绝") -> "对方未接听"
            reason.contains("取消") -> "通话已取消"
            else -> "通话结束"
        }

        callEndDialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(reason)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("重拨") { dialog, _ ->
                contact?.let { viewModel.videoCallManager.startVideoCall(it.id) }
                dialog.dismiss()
            }
            .setOnDismissListener {
                callEndDialog = null // 当对话框消失时，重置引用
                if (isAdded && parentFragmentManager.findFragmentByTag(TAG) != null && !safeIsStateSaved()) {
                    try {
                        parentFragmentManager.popBackStack()
                    } catch (e: IllegalStateException) {
                        // 状态已保存，忽略
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun handleAiHangUp(reason: String) {
        lastHangupReason = reason // 保存挂断原因
        
        val isRejected = reason.contains("拒绝")
        
        // 设置状态文本和显示状态
        binding.callStatusTextView.visibility = View.VISIBLE
        binding.callStatusTextView.text = if (isRejected) "对方未接听" else "通话结束"
        
        // 显示重拨按钮（对于拒接的情况）
        binding.redialButtonInCall.visibility = if (isRejected) View.VISIBLE else View.GONE
        
        // 隐藏通话中的控制按钮
        binding.speakButton.visibility = View.GONE
        binding.rerollButtonInCall.visibility = View.GONE
        
        // 确保显示挂断按钮容器
        binding.inCallControls.visibility = View.VISIBLE
        binding.videoCallRecyclerView.visibility = View.VISIBLE
        binding.callTimerChronometer.visibility = View.GONE

        // 修改挂断按钮为关闭按钮
        binding.endCallButtonInCall.setImageResource(R.drawable.ic_end_call)
        binding.endCallButtonInCall.setOnClickListener {
            if (!safeIsStateSaved()) {
                try {
                    parentFragmentManager.popBackStack()
                } catch (e: IllegalStateException) {
                    // 状态已保存，忽略
                }
            }
        }
    }

    private fun updateUiForOutgoingCall(state: VideoCallState.Outgoing) {
        binding.contactNameTextView.text = state.contact.remarkName
        binding.callStatusTextView.text = "正在呼叫..."
        Glide.with(this).load(state.contact.avatarUri).into(binding.contactAvatarImageView)

        binding.incomingCallControls.visibility = View.GONE
        binding.outgoingCallControls.visibility = View.VISIBLE
        binding.inCallControls.visibility = View.GONE
        binding.videoCallRecyclerView.visibility = View.GONE
        binding.callTimerChronometer.visibility = View.GONE
        binding.callStatusTextView.visibility = View.VISIBLE
    }

    private fun updateUiForConnectingCall(state: VideoCallState.Connecting) {
        binding.contactNameTextView.text = state.contact.remarkName
        binding.callStatusTextView.text = "正在连接中..."
        Glide.with(this).load(state.contact.avatarUri).into(binding.contactAvatarImageView)

        binding.incomingCallControls.visibility = View.GONE
        binding.outgoingCallControls.visibility = View.GONE
        binding.inCallControls.visibility = View.VISIBLE
        binding.videoCallRecyclerView.visibility = View.VISIBLE
        binding.callTimerChronometer.visibility = View.GONE
        binding.callStatusTextView.visibility = View.VISIBLE
        
        // 隐藏说话和重说按钮，只显示挂断按钮
        binding.speakButton.visibility = View.GONE
        binding.rerollButtonInCall.visibility = View.GONE
    }

    private fun updateUiForIncomingCall(state: VideoCallState.Incoming) {
        binding.contactNameTextView.text = state.contact.remarkName
        binding.callStatusTextView.text = "邀请你进行视频通话"
        Glide.with(this).load(state.contact.avatarUri).into(binding.contactAvatarImageView)

        binding.incomingCallControls.visibility = View.VISIBLE
        binding.inCallControls.visibility = View.GONE
        binding.videoCallRecyclerView.visibility = View.GONE
        binding.callTimerChronometer.visibility = View.GONE
        binding.callStatusTextView.visibility = View.VISIBLE
    }

    private fun updateUiForInProgressCall(state: VideoCallState.InProgress) {
        binding.contactNameTextView.text = state.contact.remarkName
        Glide.with(this).load(state.contact.avatarUri).into(binding.contactAvatarImageView)

        binding.incomingCallControls.visibility = View.GONE
        binding.outgoingCallControls.visibility = View.GONE
        binding.inCallControls.visibility = View.VISIBLE
        binding.videoCallRecyclerView.visibility = View.VISIBLE
        binding.callStatusTextView.visibility = View.GONE

        binding.callTimerChronometer.visibility = View.VISIBLE

        // 用墙钟开始时间反推 Chronometer.base,统一处理首次显示/最小化恢复/崩溃恢复:
        // Chronometer 以 SystemClock.elapsedRealtime()(相对开机时间)为基准,
        // 而通话开始时间是墙钟时间戳,二者基准不同,需换算:
        //   已用时长 = now(墙钟) - startTime(墙钟)
        //   base = 当前 elapsedRealtime - 已用时长 -> Chronometer 即从已用时长继续往上走
        // 这样恢复时能正确接续已计时长,无需再持久化 chronometerBase。
        val startTimeMillis: Long? = viewModel.videoCallManager.getVideoCallStartTimeMillis()
        binding.callTimerChronometer.base = if (startTimeMillis != null) {
            val elapsedMillis: Long = System.currentTimeMillis() - startTimeMillis
            SystemClock.elapsedRealtime() - elapsedMillis
        } else {
            SystemClock.elapsedRealtime()
        }
        binding.callTimerChronometer.start()
        
        // 显示所有通话中的按钮
        binding.speakButton.visibility = View.VISIBLE
        binding.rerollButtonInCall.visibility = View.VISIBLE
    }

    private fun showDeclineReasonDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null)
        val textInput = dialogView.findViewById<TextInputEditText>(R.id.textInputDialog)

        AlertDialog.Builder(requireContext())
            .setTitle("拒接理由 (可选)")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, _ ->
                val reason = textInput.text.toString().ifBlank { "对方未说明理由" }
                viewModel.videoCallManager.declineVideoCall(reason)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHangupReasonDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null)
        val textInput = dialogView.findViewById<TextInputEditText>(R.id.textInputDialog)

        AlertDialog.Builder(requireContext())
            .setTitle("挂断理由 (可选)")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, _ ->
                val reason = textInput.text.toString().ifBlank { null }
                viewModel.videoCallManager.endVideoCall(reason)
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 用户取消挂断，不做任何操作
                isHangUpByUser = false
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditMessageDialog(message: ChatMessage) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null)
        val textInput = dialogView.findViewById<TextInputEditText>(R.id.textInputDialog)
        textInput.setText(message.content)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑消息")
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val newText = textInput.text.toString()
                if (newText.isNotBlank()) {
                    viewModel.videoCallManager.updateInCallMessage(message.copy(content = newText))
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 安全检查FragmentManager状态是否已保存
     */
    private fun safeIsStateSaved(): Boolean {
        return try {
            parentFragmentManager.isStateSaved
        } catch (e: Exception) {
            true // 如果无法访问，假设状态已保存
        }
    }
    
    /**
     * 设置返回键处理
     */
    private fun setupBackPressedHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }
    
    /**
     * 处理返回键逻辑
     */
    private fun handleBackPressed() {
        val state = viewModel.videoCallManager.videoCallState.value
        Log.d(TAG, "【DEBUG】handleBackPressed() 被调用, 当前状态: ${state?.javaClass?.simpleName}")
        when (state) {
            is VideoCallState.Outgoing,
            is VideoCallState.InProgress,
            is VideoCallState.Connecting -> {
                // 呼叫中、通话进行中或连接中,最小化为悬浮窗
                Log.d(TAG, "【DEBUG】状态匹配,准备调用 minimizeToFloatingWindow()")
                minimizeToFloatingWindow()
            }
            else -> {
                // 其他状态直接返回
                if (!safeIsStateSaved()) {
                    try {
                        parentFragmentManager.popBackStack()
                    } catch (e: IllegalStateException) {
                        // 状态已保存，忽略
                    }
                }
            }
        }
    }
    
    /**
     * 最小化通话为悬浮窗
     */
    private fun minimizeToFloatingWindow() {
        Log.d(TAG, "【DEBUG】minimizeToFloatingWindow() 开始执行")
        val state = viewModel.videoCallManager.videoCallState.value
        Log.d(TAG, "【DEBUG】当前状态: ${state?.javaClass?.simpleName}")
        
        val contact = when (state) {
            is VideoCallState.Outgoing -> {
                Log.d(TAG, "【DEBUG】状态为 Outgoing, 联系人: ${state.contact.remarkName}")
                state.contact
            }
            is VideoCallState.InProgress -> {
                Log.d(TAG, "【DEBUG】状态为 InProgress, 联系人: ${state.contact.remarkName}")
                state.contact
            }
            is VideoCallState.Connecting -> {
                Log.d(TAG, "【DEBUG】状态为 Connecting, 联系人: ${state.contact.remarkName}")
                state.contact
            }
            else -> {
                Log.e(TAG, "【DEBUG】状态不匹配,提前返回! 状态类型: ${state?.javaClass?.simpleName}")
                return
            }
        }
        
        Log.d(TAG, "【DEBUG】联系人获取成功: ${contact.remarkName}, ID: ${contact.id}")
        
        // Android 6.0+ 检查系统级悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(requireContext())
            Log.d(TAG, "【DEBUG】悬浮窗权限检查: $hasPermission")
            if (!hasPermission) {
                // 没有权限，请求用户授权
                Log.w(TAG, "【DEBUG】没有悬浮窗权限,请求授权")
                requestFloatingWindowPermission()
                return
            }
        } else {
            Log.d(TAG, "【DEBUG】Android 6.0以下,无需权限检查")
        }
        
        // 创建悬浮窗(如果不存在) - 使用ApplicationContext确保在Fragment销毁后仍然有效
        if (floatingWindow == null) {
            Log.d(TAG, "【DEBUG】悬浮窗对象为null,创建新实例")
            floatingWindow = VideoCallFloatingWindow(requireContext().applicationContext) {
                // 点击悬浮窗恢复通话界面
                restoreFromFloatingWindow()
            }
            Log.d(TAG, "【DEBUG】悬浮窗对象创建完成,使用ApplicationContext")
        } else {
            Log.d(TAG, "【DEBUG】悬浮窗对象已存在,复用")
        }
        
        // 获取当前通话时长，根据不同状态显示不同文本
        val duration = when (state) {
            is VideoCallState.Outgoing -> {
                Log.d(TAG, "【DEBUG】Outgoing状态,显示文本: 正在呼叫...")
                "正在呼叫..."
            }
            is VideoCallState.Connecting -> {
                Log.d(TAG, "【DEBUG】Connecting状态,显示文本: 连接中...")
                "连接中..."
            }
            is VideoCallState.InProgress -> {
                val dur = viewModel.videoCallManager.getCurrentCallDuration() ?: "00:00"
                Log.d(TAG, "【DEBUG】InProgress状态,显示时长: $dur")
                dur
            }
            else -> {
                Log.d(TAG, "【DEBUG】其他状态,默认显示: 00:00")
                "00:00"
            }
        }
        
        // 计时器 base 不再单独保存:已统一由 VideoCallManager 的通话开始时间(墙钟)反推,
        // 最小化恢复时 updateUiForInProgressCall 会自动重算,无需在此持久化。

        // 显示悬浮窗
        Log.d(TAG, "【DEBUG】准备调用 floatingWindow.show(), 联系人: ${contact.remarkName}, 时长文本: $duration")
        floatingWindow?.show(contact, duration)
        Log.d(TAG, "【DEBUG】floatingWindow.show() 调用完成")
        
        // 检查悬浮窗是否真的显示了
        val isShowing = floatingWindow?.isShowing() ?: false
        Log.d(TAG, "【DEBUG】悬浮窗显示状态检查: isShowing = $isShowing")
        
        // 只在通话进行中状态才开始定时更新悬浮窗时长
        // Outgoing和Connecting状态显示的是固定文本,不需要定时更新
        if (state is VideoCallState.InProgress) {
            Log.d(TAG, "【DEBUG】InProgress状态,启动定时更新器")
            startDurationUpdateTimer()
        } else {
            Log.d(TAG, "【DEBUG】非InProgress状态(${state?.javaClass?.simpleName}),不启动定时更新器")
        }
        
        // 更新VideoCallManager状态为最小化
        Log.d(TAG, "【DEBUG】调用 videoCallManager.setMinimizedState()")
        viewModel.videoCallManager.setMinimizedState(contact)
        Log.d(TAG, "【DEBUG】setMinimizedState() 调用完成")
        
        // 标记正在最小化
        isMinimizing = true
        Log.d(TAG, "【DEBUG】设置 isMinimizing = true")
        
        // 移除当前Fragment（使用post延迟执行，避免与系统手势冲突）
        Log.d(TAG, "【DEBUG】准备移除Fragment")
        binding.root.post {
            Log.d(TAG, "【DEBUG】post回调执行, isAdded: $isAdded, isStateSaved: ${safeIsStateSaved()}")
            if (isAdded && !safeIsStateSaved()) {
                try {
                    Log.d(TAG, "【DEBUG】调用 popBackStack() 移除Fragment")
                    parentFragmentManager.popBackStack()
                    Log.d(TAG, "【DEBUG】popBackStack() 调用完成")
                } catch (e: IllegalStateException) {
                    // 状态已保存，忽略此异常
                    Log.e(TAG, "【DEBUG】popBackStack() 异常: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "【DEBUG】Fragment状态不允许移除, isAdded: $isAdded, isStateSaved: ${safeIsStateSaved()}")
            }
        }
        
        Log.d(TAG, "【DEBUG】minimizeToFloatingWindow() 执行完毕")
    }
    
    /**
     * 从悬浮窗恢复通话界面
     * 注意:此方法会在Fragment已经detached后被调用,因此不能使用parentFragmentManager
     */
    private fun restoreFromFloatingWindow() {
        // 隐藏悬浮窗
        hideFloatingWindow()
        
        // 恢复VideoCallManager状态(这会触发Activity重新显示VideoCallFragment)
        viewModel.videoCallManager.restoreFromMinimized()
        
        // 不需要在这里手动添加Fragment,因为:
        // 1. 当前Fragment实例已经detached,无法访问parentFragmentManager
        // 2. VideoCallManager的状态改变会通知Activity重新打开VideoCallFragment
        // 3. Activity会通过观察VideoCallManager的状态来决定何时显示VideoCallFragment
    }
    
    /**
     * 检查并从最小化状态恢复
     */
    private fun checkAndRestoreFromMinimized() {
        val state = viewModel.videoCallManager.videoCallState.value
        if (state is VideoCallState.Minimized) {
            // 如果当前是最小化状态,说明是从悬浮窗恢复的
            // 恢复为通话进行中状态
            viewModel.videoCallManager.restoreFromMinimized()
            // 隐藏悬浮窗
            hideFloatingWindow()
            // 重置最小化标记
            isMinimizing = false
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatingWindow() {
        stopDurationUpdateTimer()
        floatingWindow?.hide()
        floatingWindow = null
    }
    
    /**
     * 开始定时更新悬浮窗时长
     */
    private fun startDurationUpdateTimer() {
        durationUpdateHandler.post(durationUpdateRunnable)
    }
    
    /**
     * 停止定时更新悬浮窗时长
     */
    private fun stopDurationUpdateTimer() {
        durationUpdateHandler.removeCallbacks(durationUpdateRunnable)
    }
    
    /**
     * 更新悬浮窗显示的通话时长
     */
    private fun updateFloatingWindowDuration() {
        // 只在通话进行中状态才更新时长
        // Outgoing和Connecting状态显示的是固定文本
        val state = viewModel.videoCallManager.videoCallState.value
        if (state !is VideoCallState.InProgress) {
            return
        }
        
        val duration = viewModel.videoCallManager.getCurrentCallDuration()
        if (duration != null && floatingWindow?.isShowing() == true) {
            floatingWindow?.updateDuration(duration)
        }
    }
    
    /**
     * 请求系统级悬浮窗权限
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestFloatingWindowPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle("需要悬浮窗权限")
            .setMessage("为了在其他应用上显示通话悬浮窗，需要授予悬浮窗权限。\n\n这样您可以在使用其他应用时继续视频通话。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
                } catch (e: Exception) {
                    // 如果跳转失败，直接使用应用内模式（降级方案）
                    showPermissionErrorDialog()
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                // 用户拒绝权限，直接返回不最小化
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示权限错误对话框
     */
    private fun showPermissionErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("无法获取权限")
            .setMessage("无法跳转到权限设置页面。通话将保持在应用内。")
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 处理权限请求结果
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(requireContext())) {
                    // 权限已授予，重新尝试最小化
                    minimizeToFloatingWindow()
                } else {
                    // 权限被拒绝
                    AlertDialog.Builder(requireContext())
                        .setTitle("权限被拒绝")
                        .setMessage("没有悬浮窗权限，无法在其他应用上显示通话窗口。")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 当Fragment进入后台时（例如用户返回桌面），如果正在通话相关状态，显示悬浮窗
        val state = viewModel.videoCallManager.videoCallState.value
        if (!isMinimizing && (state is VideoCallState.Outgoing || state is VideoCallState.InProgress || state is VideoCallState.Connecting)) {
            // 检查是否真的进入后台（而不是因为对话框等原因）
            if (!requireActivity().isChangingConfigurations && !isRemoving) {
                minimizeToFloatingWindow()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "【DEBUG】onDestroyView() 被调用, isMinimizing=$isMinimizing, state=${viewModel.videoCallManager.videoCallState.value?.javaClass?.simpleName}")
        
        // 清理定时器
        stopDurationUpdateTimer()
        
        // 如果Fragment被销毁但不是因为最小化,则清理悬浮窗
        // 关键修复：检查isMinimizing标记，避免在最小化过程中误删悬浮窗
        if (!isMinimizing) {
            val state = viewModel.videoCallManager.videoCallState.value
            if (state !is VideoCallState.Minimized) {
                Log.d(TAG, "【DEBUG】非最小化销毁,隐藏悬浮窗")
                hideFloatingWindow()
            } else {
                Log.d(TAG, "【DEBUG】状态已是Minimized,保留悬浮窗")
            }
        } else {
            Log.d(TAG, "【DEBUG】正在最小化,保留悬浮窗")
        }
        
        // 重置最小化标记
        isMinimizing = false
        _binding = null
    }

    companion object {
        const val TAG = "VideoCallFragment"
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
        
        fun newInstance() = VideoCallFragment()
    }
}