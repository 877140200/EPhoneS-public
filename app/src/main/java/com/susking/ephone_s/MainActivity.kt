package com.susking.ephone_s

import android.Manifest
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.alipay.ui.PaymentConfirmationDialogFragment
import com.susking.ephone_s.brain.ui.BrainFragment
import com.susking.ephone_s.core.api.QqApi
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.IncomingCallEvent
import com.susking.ephone_s.core.util.ShowPaymentDialogEvent
import com.susking.ephone_s.core.util.TavernForegroundEvent
import com.susking.ephone_s.databinding.ActivityMainBinding
import com.susking.ephone_s.license.ActivationActivity
import com.susking.ephone_s.license.LicenseManager
import com.susking.ephone_s.notification.IncomingCallNotificationHelper
import com.susking.ephone_s.aidata.worker.ScheduleReminderWorker
import com.susking.ephone_s.desktop.ui.DesktopContainerFragment
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallRecoveryManager
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallFragment
import com.susking.ephone_s.qq.ui.chat.videoCall.VideoCallState
import com.susking.ephone_s.schedule.ui.ScheduleAssignmentEditorFragment
import com.susking.ephone_s.schedule.ui.ScheduleCourseEditorFragment
import com.susking.ephone_s.schedule.ui.ScheduleExamEditorFragment
import com.susking.ephone_s.schedule.ui.ScheduleFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settingsRepository by lazy { EPhoneSApplication.settingsRepository }

    // 使用 Hilt 注入 ViewModel
    private val qqViewModel: QqViewModel by viewModels()
    
    // 注入视频通话恢复管理器
    @Inject
    lateinit var videoCallRecoveryManager: VideoCallRecoveryManager
    
    // 保存 BrainFragment 引用
    private var brainFragment: BrainFragment? = null

    // 酒馆前台闸门：用户进入「???」酒馆页面时为 true，期间强制隐藏 brain 伪悬浮窗，
    // 改由酒馆内的「锦囊」悬浮窗接管，离开酒馆后恢复 brain 按设置显示。
    private var isTavernForeground: Boolean = false

    // 通知权限（POST_NOTIFICATIONS）申请的结果回调器
    // 用户在系统弹窗中做出选择后回调，无论同意与否都继续检查其余两项权限
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndPromptSpecialPermissions()
        }

    // 定义 FragmentManager 生命周期回调,用于监听 Fragment 切换
    private val fragmentLifecycleCallback = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
            super.onFragmentResumed(fm, f)
            // 每当有 Fragment resumed 时,确保大脑悬浮窗容器在最上层
            if (binding.brainFragmentContainer.visibility == View.VISIBLE) {
                binding.brainFragmentContainer.bringToFront()
                binding.brainFragmentContainer.requestLayout()
                Log.d("BrainFloatingWindow", "Fragment resumed: ${f.javaClass.simpleName}, bringing brain to front")
            }
        }
    }

    // 定义 SharedPreferences 监听器
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "brain_floating_window_enabled") {
            Log.d("BrainFloatingWindow", "Preference changed: $key")
            updateBrainVisibility()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 激活闸门：未激活的设备直接跳转激活页并结束本页，阻断后续所有初始化。
        // 已激活后此判断仅为一次本地 prefs 读取，开销可忽略，平时启动不联网。
        if (!LicenseManager(this).isActivated()) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If the activity is being created for the first time,
        // add the main desktop container fragment.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, DesktopContainerFragment.Companion.newInstance())
                .commitNow()
        }

        // 添加 BrainFragment
        brainFragment = BrainFragment()
        val app = application as EPhoneSApplication
        app.configureBrainFragment(brainFragment!!)
        supportFragmentManager.beginTransaction()
            .replace(R.id.brain_fragment_container, brainFragment!!)
            .commitNow()

        // 初始化大脑悬浮窗可见性
        updateBrainVisibility()

        observeVideoCallState()
        // 注册监听器 (这里我们需要获取到底层的 SharedPreferences 实例来注册监听)
        // 由于 SettingsRepository 接口没有暴露 SharedPreferences，我们需要一种变通方法
        // 或者更好的是，我们在 SettingsRepository 中添加一个注册监听器的方法。
        // 但为了简单和不修改太多文件，我们假设 SettingsRepositoryImpl 使用的是默认的 SharedPreferences
        // 或者我们在 MainActivity 中轮询？不，轮询不好。

        // 注册 FragmentManager 生命周期回调
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallback, true)

        // 纠正：SettingsRepositoryImpl 使用了名为 "ephone_api_settings" 的 SharedPreferences
        val prefs = getSharedPreferences("ephone_api_settings", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        // 只在 Activity 首次创建时显示欢迎对话框，避免日夜间模式切换触发配置重建后重复显示。
        if (savedInstanceState == null) {
            showWelcomeDialog()
        }
        
        // 监听支付对话框事件
        observePaymentDialogEvent()

        // 监听全局来电事件（任意界面/后台拉起都能弹出来电界面）
        observeIncomingCallEvent()

        // 监听酒馆前台事件：进入酒馆隐藏 brain、离开恢复
        observeTavernForegroundEvent()

        // 检查是否有中断的视频通话
        checkForInterruptedVideoCall()
        handleScheduleIntent(intent)
        // 处理全屏来电通知拉起（后台来电点击通知/系统全屏拉起进入）
        handleIncomingCallIntent(intent)
        // 处理点击新消息通知拉起（定位到对应联系人聊天界面）
        handleOpenChatIntent(intent)

        // 首次启动时主动申请来电相关权限（仅首次，避免每次启动骚扰用户）
        if (savedInstanceState == null) {
            requestStartupPermissionsIfNeeded()
        }

        // 仅在首次创建时静默检查更新（isSilent = true）：
        // 有新版才弹窗，无新版或检查失败都安静忽略，免费 workers.dev 国内不稳时不打扰用户。
        // 放在权限申请之后，避免与首启权限弹窗争抢焦点。
        if (savedInstanceState == null) {
            com.susking.ephone_s.settings.api.SettingsApi.checkForUpdate(this, isSilent = true)
        }
    }

    /**
     * 首次启动时主动申请来电相关权限。
     *
     * 申请顺序：
     *  1. 先申请通知权限（POST_NOTIFICATIONS，Android 13+ 系统运行时弹窗），用于后台来电通知。
     *  2. 通知权限回调后，再检查全屏来电、悬浮窗这两项特殊权限，缺失则弹引导对话框跳转权限页。
     *
     * 用 SharedPreferences 标志位保证整套流程只在首次启动执行一次，用户后续可在「设置-权限管理」自行开启。
     */
    private fun requestStartupPermissionsIfNeeded() {
        val prefs = getSharedPreferences(STARTUP_PREFS_NAME, MODE_PRIVATE)
        val hasRequested: Boolean = prefs.getBoolean(KEY_HAS_REQUESTED_STARTUP_PERMISSIONS, false)
        if (hasRequested) {
            return
        }
        // 标记已申请，无论用户是否授权都不再自动弹出
        prefs.edit().putBoolean(KEY_HAS_REQUESTED_STARTUP_PERMISSIONS, true).apply()

        // Android 13+ 才需要运行时申请通知权限；低版本直接进入特殊权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isNotificationGranted: Boolean = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isNotificationGranted) {
                // 发起系统运行时弹窗，结果回调里继续检查特殊权限
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // 通知权限已具备或无需申请，直接检查特殊权限
        checkAndPromptSpecialPermissions()
    }

    /**
     * 检查全屏来电、悬浮窗两项特殊权限，缺失时弹引导对话框跳转权限管理页。
     * 这两项无法用运行时弹窗申请，只能引导用户去系统设置手动开启。
     */
    private fun checkAndPromptSpecialPermissions() {
        val isFullScreenMissing: Boolean = !hasFullScreenIntentPermission()
        val isOverlayMissing: Boolean = !android.provider.Settings.canDrawOverlays(this)
        if (!isFullScreenMissing && !isOverlayMissing) {
            return
        }
        val missingDescription: String = buildString {
            append("为了在锁屏或后台时也能正常接听联系人来电，建议开启以下权限：\n")
            if (isFullScreenMissing) {
                append("\n· 全屏通知：后台来电时直接弹出来电界面")
            }
            if (isOverlayMissing) {
                append("\n· 悬浮窗：通话可最小化为悬浮窗")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("开启来电权限")
            .setMessage(missingDescription)
            .setPositiveButton("去开启") { dialog, _ ->
                dialog.dismiss()
                com.susking.ephone_s.settings.api.SettingsApi.openPermissionActivity(this)
            }
            .setNegativeButton("暂不", null)
            .show()
    }

    /**
     * 检查全屏通知权限是否已开启。
     * Android 14（UPSIDE_DOWN_CAKE）起需用户显式授权；低版本默认拥有该能力。
     */
    private fun hasFullScreenIntentPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        return notificationManager.canUseFullScreenIntent()
    }
    
    /**
     * 检查是否有中断的视频通话
     */
    private fun checkForInterruptedVideoCall() {
        // 守卫：VideoCallManager 是 @Singleton，其内存状态 videoCallState 跟随 Application 生命周期，
        // 不随横竖屏 Activity 重建而销毁。若当前内存里仍是活跃通话态（非结束态），
        // 说明这只是配置变更导致的 Activity 重建，通话本身从未中断，绝不能弹"中断重连"对话框。
        // 只有进程被真正杀死后（内存状态复位为 Idle），库里残留的 in_progress 记录才代表真崩溃，才需要恢复。
        val currentCallState = qqViewModel.videoCallManager.videoCallState.value
        val isCallStillActive: Boolean = currentCallState is VideoCallState.Incoming ||
            currentCallState is VideoCallState.Outgoing ||
            currentCallState is VideoCallState.Connecting ||
            currentCallState is VideoCallState.InProgress ||
            currentCallState is VideoCallState.Minimized
        if (isCallStillActive) {
            Log.d("MainActivity", "检测到内存中通话仍活跃($currentCallState)，判定为 Activity 重建，跳过中断重连检查")
            return
        }
        lifecycleScope.launch {
            val interruptedCall = videoCallRecoveryManager.checkForInterruptedCall()
            if (interruptedCall != null) {
                // 获取联系人名称
                val contactName = videoCallRecoveryManager.getContactName(interruptedCall.contactId)
                val isWithinTimeout = videoCallRecoveryManager.isWithinTimeout(interruptedCall)
                if (isWithinTimeout) {
                    // 30分钟内，显示是否重连对话框
                    showCallRecoveryDialog(interruptedCall, contactName)
                } else {
                    // 超过30分钟，显示是否触发结束流程对话框
                    showTimeoutCallDialog(interruptedCall, contactName)
                }
            }
        }
    }
    
    /**
     * 显示通话恢复对话框（30分钟内）
     */
    private fun showCallRecoveryDialog(callHistory: com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity, contactName: String) {
        val interruptedMinutes = videoCallRecoveryManager.getInterruptedMinutes(callHistory)
        AlertDialog.Builder(this)
            .setTitle("视频通话中断")
            .setMessage("你与${contactName}的视频通话在${interruptedMinutes}分钟前异常中断。是否重新连接？")
            .setPositiveButton("重新连接") { dialog, _ ->
                dialog.dismiss()
                // 恢复通话
                qqViewModel.videoCallManager.restoreCallFromDatabase(callHistory)
            }
            .setNegativeButton("不了") { dialog, _ ->
                dialog.dismiss()
                // 触发通话结束流程
                videoCallRecoveryManager.declineCallRestoration(callHistory)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示超时通话对话框（超过30分钟）
     */
    private fun showTimeoutCallDialog(callHistory: com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity, contactName: String) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val interruptTime = dateFormat.format(Date(callHistory.lastUpdateTime))
        AlertDialog.Builder(this)
            .setTitle("通话异常中断")
            .setMessage("你与${contactName}的视频通话于${interruptTime}异常挂断。是否让对方知道通话已结束？")
            .setPositiveButton("是") { dialog, _ ->
                dialog.dismiss()
                // 触发通话结束流程
                videoCallRecoveryManager.triggerTimeoutCallEndFlow(callHistory)
            }
            .setNegativeButton("忽略") { dialog, _ ->
                dialog.dismiss()
                // 仅标记为中断，不触发AI回应
                lifecycleScope.launch {
                    videoCallRecoveryManager.handleTimeoutInterruptedCall(callHistory)
                }
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 监听支付对话框事件
     */
    private fun observePaymentDialogEvent() {
        EventBus.events
            .filterIsInstance<ShowPaymentDialogEvent>()
            .onEach { event ->
                showPaymentDialog(event.orderAmount, event.onConfirm)
            }
            .launchIn(lifecycleScope)
    }
    
    /**
     * 显示支付确认对话框
     */
    private fun showPaymentDialog(orderAmount: Double, onConfirm: () -> Unit) {
        val dialog = PaymentConfirmationDialogFragment.newInstance(
            orderAmount = orderAmount,
            onConfirm = onConfirm
        )
        dialog.show(supportFragmentManager, "payment_confirmation")
    }

    /**
     * 显示欢迎对话框
     */
    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setMessage("亲白枢一口,喜欢你哦！")
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScheduleIntent(intent)
        // 应用已在运行时，全屏来电通知再次拉起也要能进入来电界面
        handleIncomingCallIntent(intent)
        // 应用已在运行时，点击新消息通知也要能定位到对应聊天界面
        handleOpenChatIntent(intent)
    }

    private fun handleScheduleIntent(intent: Intent?): Unit {
        if (intent?.action != ScheduleReminderWorker.ACTION_OPEN_SCHEDULE_TARGET) return
        val targetType: String = intent.getStringExtra(ScheduleReminderWorker.EXTRA_TARGET_TYPE).orEmpty()
        val targetId: String = intent.getStringExtra(ScheduleReminderWorker.EXTRA_TARGET_ID).orEmpty()
        val scheduleFragment = ScheduleFragment.newInstance(targetType, targetId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, scheduleFragment)
            .addToBackStack(null)
            .commit()
        openScheduleTargetEditor(targetType, targetId)
    }

    private fun openScheduleTargetEditor(targetType: String, targetId: String): Unit {
        if (targetId.isBlank()) return
        val editorFragment = when (targetType) {
            "COURSE" -> ScheduleCourseEditorFragment.newInstance(targetId)
            "ASSIGNMENT" -> ScheduleAssignmentEditorFragment.newInstance(targetId)
            "EXAM" -> ScheduleExamEditorFragment.newInstance(targetId)
            else -> return
        }
        binding.mainFragmentContainer.post {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, editorFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销 FragmentManager 生命周期回调
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallback)
        // 注销监听器
        val prefs = getSharedPreferences("ephone_api_settings", MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun updateBrainVisibility() {
        // 酒馆前台时一律隐藏 brain：由酒馆内「锦囊」悬浮窗接管，盖过设置开关与置顶逻辑
        if (isTavernForeground) {
            binding.brainFragmentContainer.visibility = View.GONE
            brainFragment?.onContainerVisibilityChanged(false)
            return
        }
        val isEnabled = settingsRepository.isBrainFloatingWindowEnabled()
        Log.d("BrainFloatingWindow", "Updating visibility. Is enabled: $isEnabled")
        if (isEnabled) {
            binding.brainFragmentContainer.visibility = View.VISIBLE
            binding.brainFragmentContainer.bringToFront() // 确保悬浮窗在最顶层
            // 强制请求布局更新，确保悬浮窗立即显示
            binding.brainFragmentContainer.requestLayout()
            binding.brainFragmentContainer.invalidate()
            // 通知 BrainFragment 容器可见性已改变
            brainFragment?.onContainerVisibilityChanged(true)
        } else {
            binding.brainFragmentContainer.visibility = View.GONE
            brainFragment?.onContainerVisibilityChanged(false)
        }
    }

    /**
     * 全局视频通话状态观察者（Activity 级）。
     *
     * 设计：VideoCallFragment 的拉起/移除统一收归到此处的 video_call_fragment_container 顶层容器，
     * 不再分散在 QqChatFragment / QqMainFragment 各自的 FragmentManager。
     * 这样无论用户在桌面、QQ 还是任何其他界面，AI 来电/去电都能在最顶层弹出来电界面，
     * 且不会因多个 FragmentManager 各自判重失效而重复拉起多个 VideoCallFragment 实例。
     *
     * 状态与界面对应关系：
     * - Incoming/Outgoing/Connecting/InProgress：需要全屏通话界面 -> 确保 VideoCallFragment 已拉起
     * - Minimized：已最小化为系统悬浮窗，VideoCallFragment 自行移除，这里不主动拉起
     * - Idle/Terminated/TerminatedByAi：通话结束，VideoCallFragment 自行处理收尾/移除，这里不干预
     */
    private fun observeVideoCallState() {
        qqViewModel.videoCallManager.videoCallState.observe(this) { state ->
            // 后台来电通知(全屏 intent)只在 Incoming 阶段有意义。
            // 状态一旦离开 Incoming(接听 -> InProgress / 拒接 / 超时 -> Terminated / Idle 等),
            // 取消那条 setOngoing(用户划不掉) 的来电通知,避免它滞留在通知栏。
            // cancel 幂等,无来电通知时调用也无副作用,故"非 Incoming 即取消"最简洁。
            if (state !is VideoCallState.Incoming) {
                IncomingCallNotificationHelper.cancelIncomingCallNotification(this)
            }
            when (state) {
                is VideoCallState.Incoming,
                is VideoCallState.Outgoing,
                is VideoCallState.Connecting,
                is VideoCallState.InProgress -> {
                    showVideoCallFragmentIfNeeded()
                }
                else -> {
                    // Minimized：悬浮窗模式，界面由 VideoCallFragment 自行移除
                    // Idle/Terminated/TerminatedByAi：结束态，VideoCallFragment 自行收尾
                    // 这些状态下 VideoCallFragment 会被自身 popBackStack 移除，
                    // 此处负责把顶层来电容器收回 GONE，避免空的全屏容器残留在最顶层。
                    hideVideoCallContainerIfEmpty()
                }
            }

            // 通话激活时，确保大脑悬浮窗仍在最顶层（来电容器 elevation 低于 brain）
            if (state is VideoCallState.Outgoing ||
                state is VideoCallState.Incoming ||
                state is VideoCallState.InProgress) {
                if (binding.brainFragmentContainer.visibility == View.VISIBLE) {
                    binding.brainFragmentContainer.bringToFront()
                    Log.d("MainActivity", "Video call active, bringing brain fragment to front.")
                }
            }
        }
    }

    /**
     * 在顶层来电容器中拉起 VideoCallFragment（若尚未拉起）。
     * 用 video_call_fragment_container 顶层容器 + supportFragmentManager，
     * 盖住整个小手机界面；通过 TAG 判重避免重复 add 叠加多个实例。
     * VideoCallFragment 自身通过 activityViewModels 共享 QqViewModel，迁到顶层容器后功能不受影响。
     */
    private fun showVideoCallFragmentIfNeeded() {
        if (supportFragmentManager.isStateSaved) {
            // Activity 状态已保存（进入后台等），此时提交事务会抛 IllegalStateException，跳过。
            // 待 Activity 恢复后，全屏来电通知或状态恢复流程会重新触发拉起。
            return
        }
        if (supportFragmentManager.findFragmentByTag(VideoCallFragment.TAG) != null) {
            return // 已存在，避免重复拉起
        }
        binding.videoCallFragmentContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .add(
                R.id.video_call_fragment_container,
                VideoCallFragment.newInstance(),
                VideoCallFragment.TAG
            )
            .addToBackStack(VideoCallFragment.TAG)
            .commit()
    }

    /**
     * 当通话结束/最小化时，VideoCallFragment 会自行 popBackStack 移除自己。
     * 此方法负责在 Fragment 已不存在时，把顶层来电容器收回 GONE，
     * 避免一个空的全屏容器残留在最顶层，挡住下层桌面/聊天界面的交互。
     * 若 Fragment 仍在（极端时序下尚未移除完毕），则不动容器，交由其自身收尾。
     */
    private fun hideVideoCallContainerIfEmpty() {
        if (supportFragmentManager.findFragmentByTag(VideoCallFragment.TAG) != null) {
            return // Fragment 仍在，容器不能收，否则界面会被隐藏
        }
        binding.videoCallFragmentContainer.visibility = View.GONE
    }

    /**
     * 监听全局来电事件（EventBus.IncomingCallEvent）。
     *
     * 来源：QqChatManager 在 AI 响应中检测到来电指令时，除了发旧的 LiveData 事件（聊天页内消费），
     * 还会发此全局事件。MainActivity 用 Activity 级 lifecycleScope 监听，
     * 确保用户无论在哪个界面（桌面/其他 App 内页），AI 来电都能转交 VideoCallManager 弹出来电界面。
     *
     * 用 setIncomingCallById 而非直接传 contact 对象：因为事件只携带 contactId，
     * VideoCallManager 内部用 repository 直接查联系人，不依赖 UI 层 contacts 缓存是否已加载。
     */
    private fun observeIncomingCallEvent() {
        EventBus.events
            .filterIsInstance<IncomingCallEvent>()
            .onEach { event ->
                qqViewModel.videoCallManager.setIncomingCallById(event.contactId)
            }
            .launchIn(lifecycleScope)
    }

    /**
     * 监听酒馆前台事件。
     *
     * 进入酒馆（「???」）时隐藏 brain 大脑伪悬浮窗，改由酒馆内的「锦囊」悬浮窗接管；
     * 离开酒馆后按设置恢复 brain。用闸门字段 [isTavernForeground] 让 updateBrainVisibility()
     * 在酒馆前台时无条件隐藏 brain，避免 Fragment 切换/通话置顶逻辑把 brain 重新顶上来。
     */
    private fun observeTavernForegroundEvent() {
        EventBus.events
            .filterIsInstance<TavernForegroundEvent>()
            .onEach { event ->
                isTavernForeground = event.isForeground
                updateBrainVisibility()
            }
            .launchIn(lifecycleScope)
    }

    /**
     * 处理全屏来电通知点击/系统拉起的 Intent。
     *
     * 后台 AI 来电时，会发一条带 full-screen intent 的高优先级通知，
     * 该 Intent action=ACTION_INCOMING_CALL，extra 携带 contactId。
     * 系统拉起或用户点击通知进入本 Activity 后，解析 contactId 并交给 VideoCallManager 弹出来电界面。
     */
    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent?.action != ACTION_INCOMING_CALL) return
        val contactId: String = intent.getStringExtra(EXTRA_INCOMING_CALL_CONTACT_ID).orEmpty()
        if (contactId.isBlank()) return
        qqViewModel.videoCallManager.setIncomingCallById(contactId)
    }

    /**
     * 处理「点击新消息通知」拉起。
     *
     * 用户点击 NewMessageNotificationHelper 发出的通知后，解析 contactId，
     * 经 QqApi 获取该联系人的聊天 Fragment 并替换到主容器，定位到对应聊天界面。
     * 复用 handleScheduleIntent 同款 replace + addToBackStack，返回键回到原界面。
     */
    private fun handleOpenChatIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_CHAT) return
        val contactId: String = intent.getStringExtra(EXTRA_CHAT_CONTACT_ID).orEmpty()
        if (contactId.isBlank()) return
        val chatFragment = QqApi.getFragmentProvider().getQqChatFragment(contactId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, chatFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        // 全屏来电通知 Intent 约定：action 标识来电，extra 携带来电联系人ID。
        // brain 模块的来电通知构建处需使用同样的常量值。
        const val ACTION_INCOMING_CALL: String = "com.susking.ephone_s.action.INCOMING_CALL"
        const val EXTRA_INCOMING_CALL_CONTACT_ID: String = "extra_incoming_call_contact_id"

        // 新消息通知 Intent 约定：action 标识打开聊天，extra 携带目标联系人ID。
        // app 模块的 NewMessageNotificationHelper 构建处使用同样的常量值。
        const val ACTION_OPEN_CHAT: String = "com.susking.ephone_s.action.OPEN_CHAT"
        const val EXTRA_CHAT_CONTACT_ID: String = "extra_chat_contact_id"

        // 启动权限申请标志位：用独立的 SharedPreferences 记录是否已在首次启动申请过来电权限，
        // 保证整套申请流程只自动弹出一次，避免每次启动骚扰用户。
        private const val STARTUP_PREFS_NAME: String = "ephone_startup_prefs"
        private const val KEY_HAS_REQUESTED_STARTUP_PERMISSIONS: String = "has_requested_startup_permissions"
    }
}