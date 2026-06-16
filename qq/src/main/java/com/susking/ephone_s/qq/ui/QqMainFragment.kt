package com.susking.ephone_s.qq.ui

import android.R
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.canhub.cropper.CropImageOptions
import com.google.android.material.imageview.ShapeableImageView
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.ImportMode
import com.susking.ephone_s.aidata.domain.model.import_export.ImportOutcome
import com.susking.ephone_s.aidata.domain.model.import_export.ImportPreview
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult
import com.susking.ephone_s.aidata.util.BackupSnapshotHelper
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.use_case.ExportDataUseCase
import com.susking.ephone_s.aidata.domain.use_case.ImportDataUseCase
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.core.util.EventObserver
import com.susking.ephone_s.qq.databinding.DialogExportFormatBinding
import com.susking.ephone_s.qq.databinding.FragmentQqMainBinding
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.backpack.BackpackFragment
import com.susking.ephone_s.qq.ui.chat.CreateContactDialogFragment
import com.susking.ephone_s.qq.ui.chat.QqChatFragment
import com.susking.ephone_s.qq.ui.chat.QqMessageListFragment
import com.susking.ephone_s.qq.ui.chat.StatusSelectionDialogFragment
import com.susking.ephone_s.qq.ui.contactList.QqContactListFragment
import com.susking.ephone_s.qq.ui.favorites.QqFavoritesFragment
import com.susking.ephone_s.qq.ui.memories.CreateAppointmentDialogFragment
import com.susking.ephone_s.qq.ui.memories.QqMemoriesFragment
import com.susking.ephone_s.qq.ui.persona.PersonaEditorFragment
import com.susking.ephone_s.qq.ui.qzone.QqFeedsFragment
import com.susking.ephone_s.qq.util.ImageSelector
import com.susking.ephone_s.settings.ui.conflict.ConflictResolutionDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

@AndroidEntryPoint
class QqMainFragment : Fragment(), CreateContactDialogFragment.CreateContactListener,
    StatusSelectionDialogFragment.StatusSelectionListener {

    private var _binding: FragmentQqMainBinding? = null
    private val binding get() = _binding!!

    // 使用 Hilt 注入 ViewModel
    private val viewModel: QqViewModel by activityViewModels()

    // 注入各个 Manager 和 UseCase
    @Inject
    lateinit var contactManager: QqContactManager
    @Inject
    lateinit var chatManager: QqChatManager
    @Inject
    lateinit var importDataUseCase: ImportDataUseCase
    @Inject
    lateinit var exportDataUseCase: ExportDataUseCase
    @Inject
    lateinit var personProfileRepository: PersonProfileRepository
    @Inject
    lateinit var dataImportExportRepository: DataImportExportRepository
    @Inject
    lateinit var settingsRepository: SettingsRepository

    // 导入导出状态
    private val _importPreviewEvent = MutableLiveData<Event<Any>>()
    private val _isImporting = MutableLiveData<Boolean>(false)
    private val _importConfirmation = MutableLiveData<Event<Any>>()
    private val _errorEvent = MutableLiveData<Event<String>>()
    private val _exportResult = MutableStateFlow<String>("")

    // 保存当前导入的URI,用于智能合并
    private var currentImportUri: Uri? = null

    private lateinit var gestureDetector: GestureDetector
    private var globalTouchListener: View.OnTouchListener? = null
    private lateinit var drawerAvatarSelector: ImageSelector
    private lateinit var drawerBackgroundSelector: ImageSelector

    private val importDataLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { performImport(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 为 AppBarLayout 应用顶部内边距，以避开状态栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // 为 BottomNavigationView 应用底部内边距，以避开导航栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        setupViewPager()
        setupBottomNavigation()
        setupDrawer()
        setupToolbar()
        observeViewModel()
        setupBackStackListener()
        setupImageSelectors()
        synchronizeMainUiVisibility()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 5
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> QqMessageListFragment.Companion.newInstance()
                    1 -> QqContactListFragment()
                    2 -> QqMemoriesFragment.Companion.newInstance()
                    3 -> QqFavoritesFragment.Companion.newInstance()
                    4 -> QqFeedsFragment.Companion.newInstance()
                    else -> throw IllegalStateException("Invalid position $position")
                }
            }
        }

        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigationView.menu.getItem(position).isChecked = true
                if (position != 4 && childFragmentManager.backStackEntryCount == 0) { // 如果不是动态页面且没有子页面
                    binding.appBarLayout.visibility = View.VISIBLE
                }
                updateToolbarForPosition(position)
                updateDrawerGestureLockMode()
             }
         })
     }

    private fun setAppBarVisibilityAnimated(shouldShow: Boolean) {
        val currentIsVisible = binding.appBarLayout.visibility == View.VISIBLE
        if (currentIsVisible == shouldShow) return

        val targetAlpha = if (shouldShow) 1f else 0f
        val duration = resources.getInteger(R.integer.config_shortAnimTime).toLong()

        if (shouldShow) {
            binding.appBarLayout.alpha = 0f
            binding.appBarLayout.visibility = View.VISIBLE
        }

        binding.appBarLayout.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .withEndAction {
                if (!shouldShow) {
                    binding.appBarLayout.visibility = View.GONE
                }
            }
            .start()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                com.susking.ephone_s.qq.R.id.nav_messages -> 0
                com.susking.ephone_s.qq.R.id.nav_contacts -> 1
                com.susking.ephone_s.qq.R.id.nav_memories -> 2
                com.susking.ephone_s.qq.R.id.nav_favorites -> 3
                com.susking.ephone_s.qq.R.id.nav_updates -> 4
                else -> -1
            }
            if (position != -1) {
                binding.viewPager.setCurrentItem(position, false)
                if (position == 4) {
                    // 当用户点击动态选项卡时，清除计数
                    viewModel.clearNewFeedsCount()
                }
            }
            true
        }
    }

    private fun updateToolbarForPosition(position: Int) {
        binding.toolbar.menu.clear()
        val toolbarAvatar = binding.toolbar.findViewById<View>(com.susking.ephone_s.qq.R.id.toolbar_avatar)
        val userInfoContainer = binding.toolbar.findViewById<View>(com.susking.ephone_s.qq.R.id.toolbar_user_info_container)
        val contactsTitle = binding.toolbar.findViewById<View>(com.susking.ephone_s.qq.R.id.toolbar_title_contacts)
        val favoritesTitle = binding.toolbar.findViewById<View>(com.susking.ephone_s.qq.R.id.toolbar_title_favorites)
        val memoriesTitle = binding.toolbar.findViewById<View>(com.susking.ephone_s.qq.R.id.toolbar_title_memories)

        // 重置所有自定义视图的状态
        userInfoContainer.visibility = View.GONE
        contactsTitle.visibility = View.GONE
        favoritesTitle.visibility = View.GONE
        memoriesTitle.visibility = View.GONE
        binding.toolbar.title = ""

        when (position) {
            0 -> { // 消息
                toolbarAvatar.visibility = View.VISIBLE
                userInfoContainer.visibility = View.VISIBLE
                binding.toolbar.inflateMenu(com.susking.ephone_s.qq.R.menu.menu_qq_chat_list)
            }
            1 -> { // 联系人
                toolbarAvatar.visibility = View.VISIBLE
                contactsTitle.visibility = View.VISIBLE
                binding.toolbar.inflateMenu(com.susking.ephone_s.qq.R.menu.menu_qq_chat_list)
            }
            2 -> { // 回忆
                toolbarAvatar.visibility = View.VISIBLE
                memoriesTitle.visibility = View.VISIBLE
                binding.toolbar.inflateMenu(com.susking.ephone_s.qq.R.menu.menu_qq_memories)
            }
            3 -> { // 收藏
                toolbarAvatar.visibility = View.VISIBLE
                favoritesTitle.visibility = View.VISIBLE
                binding.toolbar.inflateMenu(com.susking.ephone_s.qq.R.menu.menu_qq_favorites)
            }
            4 -> { // 动态
                binding.appBarLayout.visibility = View.GONE
            }
        }
    }

    private fun setupDrawer() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        setupDrawerHeaderListeners()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                com.susking.ephone_s.qq.R.id.drawer_backpack -> {
                    openDrawerFragment(BackpackFragment.newInstance(), com.susking.ephone_s.qq.R.id.fragment_container_for_persona)
                    true
                }
                com.susking.ephone_s.qq.R.id.drawer_persona -> {
                    openDrawerFragment(PersonaEditorFragment.Companion.newInstance(), com.susking.ephone_s.qq.R.id.fragment_container_for_persona)
                    true
                }
                com.susking.ephone_s.qq.R.id.drawer_export -> {
                    showExportAllDataFormatDialog()
                    true
                }
                else -> {
                    Toast.makeText(requireContext(), "${menuItem.title} clicked", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.findViewById<ShapeableImageView>(com.susking.ephone_s.qq.R.id.toolbar_avatar)?.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.toolbar.findViewById<TextView>(com.susking.ephone_s.qq.R.id.toolbar_user_status)?.setOnClickListener {
            viewModel.qqContactManager.userProfile.value?.let { profile: UserProfile ->
                StatusSelectionDialogFragment.Companion.newInstance(profile.status ?: "在线", profile.statusIconUri)
                    .apply { setStatusSelectionListener(this@QqMainFragment) }
                    .show(childFragmentManager, StatusSelectionDialogFragment.Companion.TAG)
            }
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.susking.ephone_s.qq.R.id.action_settings_favorites -> {
                    // 找到 QqFavoritesFragment 的实例并调用其方法
                    val favoritesFragment = childFragmentManager.fragments
                        .find { it is QqFavoritesFragment } as? QqFavoritesFragment
                    favoritesFragment?.showDisplayModeDialog()
                    true
                }
                com.susking.ephone_s.qq.R.id.action_create_new_chat -> {
                    CreateContactDialogFragment().show(childFragmentManager, CreateContactDialogFragment.Companion.TAG)
                    true
                }
                com.susking.ephone_s.qq.R.id.action_create_appointment -> {
                    val contactId: String? = viewModel.selectedContactId.value
                    if (contactId.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "请先选择一个联系人", Toast.LENGTH_SHORT).show()
                        true
                    } else {
                        val memoriesFragment = childFragmentManager.fragments.find { it is QqMemoriesFragment } as? QqMemoriesFragment
                        memoriesFragment?.let {
                            val dialog = CreateAppointmentDialogFragment.newInstance(contactId)
                            dialog.listener = it
                            dialog.show(childFragmentManager, "CreateAppointmentDialog")
                        }
                        true
                    }
                }
                com.susking.ephone_s.qq.R.id.action_scan -> {
                    showImportOptionsDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        // 观察导入预览事件
        _importPreviewEvent.observe(viewLifecycleOwner, EventObserver { preview ->
            when (preview) {
                is ImportPreview.SingleChat -> showImportConfirmationDialog(preview)
                is ImportPreview.AllData -> showImportAllDataConfirmationDialog(preview)
            }
        })

        // 观察导入状态以显示进度条
        _isImporting.observe(viewLifecycleOwner) { isLoading ->
            binding.importProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.importOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                activity?.window?.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }

        _importConfirmation.observe(viewLifecycleOwner, EventObserver { success ->
            if (success as? Boolean == true) {
                Toast.makeText(requireContext(), "导入成功！", Toast.LENGTH_SHORT).show()
                // 刷新联系人列表和用户资料,UI会通过LiveData自动更新
                contactManager.loadContacts()
                viewModel.qqContactManager.refreshUserProfile()
            } else {
                Toast.makeText(requireContext(), "导入失败。", Toast.LENGTH_SHORT).show()
            }
        })

        _errorEvent.observe(viewLifecycleOwner, EventObserver { errorMessage ->
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                _exportResult.collect { successMessage ->
                    if (successMessage.isNotEmpty()) {
                        Toast.makeText(requireContext(), successMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewModel.qqContactManager.userProfile.observe(viewLifecycleOwner) { profile ->
            updateDrawerHeader(profile)
        }

        // 【新增】监听全局未读消息总数
        viewModel.contactManager.totalUnreadCount.observe(viewLifecycleOwner) { count ->
            val badge = binding.bottomNavigationView.getOrCreateBadge(com.susking.ephone_s.qq.R.id.nav_messages)
            if (count > 0) {
                badge.isVisible = true
                badge.number = count
            } else {
                badge.isVisible = false
            }
        }


        viewModel.navigateToChat.observe(viewLifecycleOwner, EventObserver { contact ->
            binding.fragmentContainerForChat.visibility = View.VISIBLE
            val fragment = QqChatFragment.Companion.newInstance(contact.id)
            childFragmentManager.beginTransaction()
                .add(com.susking.ephone_s.qq.R.id.fragment_container_for_chat, fragment)
                .addToBackStack(null)
                .commit()
            // showBars 已删除，UI 栏的显示/隐藏由 Fragment 自己管理
            binding.appBarLayout.visibility = View.GONE
            binding.bottomNavigationView.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        })

        // 注:从最小化恢复通话的拉起逻辑已统一收归 MainActivity 顶层容器。
        // restoreFromMinimized() 会 post InProgress/Outgoing 状态,
        // 由 MainActivity.observeVideoCallState 在 video_call_fragment_container 拉起 VideoCallFragment,
        // 盖住整个小手机界面,无需再在此隐藏 QQ 内部栏。

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getNewFeedsCountFlow().collect { count ->
                    val badge = binding.bottomNavigationView.getOrCreateBadge(com.susking.ephone_s.qq.R.id.nav_updates)
                    if (count > 0) {
                        badge.isVisible = true
                        badge.number = count
                    } else {
                        badge.isVisible = false
                    }
                }
            }
        }
    }

    private fun setupDrawerHeaderListeners() {
        val headerView = binding.navView.getHeaderView(0)
        val avatar = headerView.findViewById<ImageView>(com.susking.ephone_s.qq.R.id.iv_avatar)
        val background = headerView.findViewById<ImageView>(com.susking.ephone_s.qq.R.id.iv_header_bg)
        val userName = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_user_name)
        val signature = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_user_signature)
        val drawerStatus = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_status)

        avatar.setOnClickListener {
            val cropOptions =
                CropImageOptions(fixAspectRatio = true, aspectRatioX = 1, aspectRatioY = 1)
            drawerAvatarSelector.showSelectionDialog(
                title = "选择头像",
                cropOptions = cropOptions,
                showClearOption = false
            )
        }
        background.setOnClickListener {
            val cropOptions = CropImageOptions(fixAspectRatio = false)
            drawerBackgroundSelector.showSelectionDialog(
                title = "选择抽屉背景",
                cropOptions = cropOptions,
                showClearOption = true
            )
        }
        userName.setOnClickListener {
            showEditDialog("修改昵称", userName.text.toString()) { newName ->
                viewModel.qqContactManager.userProfile.value?.let {
                    viewModel.qqContactManager.updateUserProfile(it.copy(nickname = newName))
                }
            }
        }
        signature.setOnClickListener {
            showEditDialog("修改个性签名", signature.text.toString()) { newSignature ->
                viewModel.qqContactManager.userProfile.value?.let {
                    viewModel.qqContactManager.updateUserProfile(it.copy(signature = newSignature))
                }
            }
        }
        drawerStatus.setOnClickListener {
            viewModel.qqContactManager.userProfile.value?.let { profile ->
                StatusSelectionDialogFragment.Companion.newInstance(profile.status ?: "在线", profile.statusIconUri)
                    .apply { setStatusSelectionListener(this@QqMainFragment) }
                    .show(childFragmentManager, StatusSelectionDialogFragment.Companion.TAG)
            }
        }
    }

    private fun setupImageSelectors() {
        drawerAvatarSelector = ImageSelector(this, "main_drawer_avatar") { uri ->
            uri?.let {
                val currentProfile = viewModel.qqContactManager.userProfile.value ?: return@let
                val updatedProfile = currentProfile.copy(avatarUri = it.toString())
                viewModel.qqContactManager.updateUserProfile(updatedProfile)
            }
        }

        drawerBackgroundSelector = ImageSelector(this, "main_drawer_background") { uri ->
            val currentProfile =
                viewModel.qqContactManager.userProfile.value ?: return@ImageSelector
            val updatedProfile = currentProfile.copy(backgroundUri = uri?.toString())
            viewModel.qqContactManager.updateUserProfile(updatedProfile)
        }
    }

    private fun showEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        val context = requireContext()
        val editText = EditText(context).apply { setText(currentValue) }
        AlertDialog.Builder(context).setTitle(title).setView(editText)
            .setPositiveButton("保存") { dialog, _ ->
                onSave(editText.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateDrawerHeader(profile: UserProfile) {
        val headerView = binding.navView.getHeaderView(0)
        val avatar = headerView.findViewById<ImageView>(com.susking.ephone_s.qq.R.id.iv_avatar)
        val background = headerView.findViewById<ImageView>(com.susking.ephone_s.qq.R.id.iv_header_bg)
        val userName = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_user_name)
        val signature = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_user_signature)
        val drawerStatus = headerView.findViewById<TextView>(com.susking.ephone_s.qq.R.id.tv_status)
        val toolbarAvatar = binding.toolbar.findViewById<ShapeableImageView>(com.susking.ephone_s.qq.R.id.toolbar_avatar)
        val toolbarUserName = binding.toolbar.findViewById<TextView>(com.susking.ephone_s.qq.R.id.toolbar_user_name)
        val toolbarUserStatus = binding.toolbar.findViewById<TextView>(com.susking.ephone_s.qq.R.id.toolbar_user_status)

        userName.text = profile.nickname
        signature.text = profile.signature
        drawerStatus.text = profile.status
        toolbarUserName.text = profile.nickname
        toolbarUserStatus.text = profile.status

        // 更新抽屉头部状态图标
        val statusIconUri = profile.statusIconUri
        if (statusIconUri != null) {
            Glide.with(this)
                .load(statusIconUri.toUri())
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        drawerStatus.setCompoundDrawablesWithIntrinsicBounds(resource, null, null, null)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        drawerStatus.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                    }
                })
        } else {
            // 根据状态文本设置默认图标
            when (profile.status) {
                "在线" -> drawerStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_online, 0, 0, 0)
                "忙碌" -> drawerStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_busy, 0, 0, 0)
                else -> {
                    // 默认显示“在线”状态
                    drawerStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_online, 0, 0, 0)
                    drawerStatus.text = "在线"
                }
            }
        }

        // 更新工具栏状态图标
        if (statusIconUri != null) {
            Glide.with(this)
                .load(statusIconUri.toUri())
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        toolbarUserStatus.setCompoundDrawablesWithIntrinsicBounds(resource, null, null, null)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        toolbarUserStatus.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                    }
                })
        } else {
            when (profile.status) {
                "在线" -> toolbarUserStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_online, 0, 0, 0)
                "忙碌" -> toolbarUserStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_busy, 0, 0, 0)
                else -> {
                    // 默认显示“在线”状态
                    toolbarUserStatus.setCompoundDrawablesWithIntrinsicBounds(com.susking.ephone_s.qq.R.drawable.ic_status_online, 0, 0, 0)
                    toolbarUserStatus.text = "在线"
                }
            }
        }

        Glide.with(this)
            .load(profile.avatarUri)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(com.susking.ephone_s.qq.R.drawable.ic_default_avatar)
            .into(avatar)
        Glide.with(this)
            .load(profile.avatarUri)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(com.susking.ephone_s.qq.R.drawable.ic_default_avatar)
            .into(toolbarAvatar)
        Glide.with(this)
            .load(profile.backgroundUri)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(com.susking.ephone_s.qq.R.drawable.ic_launcher_background)
            .into(background)
    }

    override fun onStatusSelected(status: String, statusIconUri: String?) {
        viewModel.qqContactManager.userProfile.value?.let { profile ->
            viewModel.qqContactManager.updateUserProfile(profile.copy(status = status, statusIconUri = statusIconUri))
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        globalTouchListener?.let { requireActivity().window.decorView.setOnTouchListener(null) }
        _binding = null
    }

    override fun onContactCreated(contact: PersonProfile) {
        contactManager.addContact(contact)
        Log.d("QqMainFragment", "New contact added via QqContactManager: $contact")
    }

    private fun showImportOptionsDialog() {
        val options = arrayOf("导入单个角色聊天记录", "导入QQ所有数据")
        AlertDialog.Builder(requireContext())
            .setTitle("选择导入类型")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> importDataLauncher.launch("*/*")
                    1 -> importDataLauncher.launch("*/*")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImportAllDataConfirmationDialog(preview: ImportPreview.AllData) {
        // 显示导入模式选择对话框
        showImportModeSelectionDialog(preview)
    }

    private fun showImportConfirmationDialog(preview: ImportPreview.SingleChat) {
        // 显示导入模式选择对话框
        showImportModeSelectionDialog(preview)
    }

    /**
     * 显示导入模式选择对话框
     */
    private fun showImportModeSelectionDialog(preview: ImportPreview) {
        // 构建标题和消息
        val (title, message) = when (preview) {
            is ImportPreview.SingleChat -> {
                val titleText = if (preview.isNewCharacter) "导入新角色" else "角色已存在"
                val messageText = "角色名: ${preview.characterNickname}"
                Pair(titleText, messageText)
            }
            is ImportPreview.AllData -> {
                val titleText = "导入QQ所有数据"
                val messageText = buildString {
                    appendLine("联系人: ${preview.contactCount}个")
                    appendLine("消息: ${preview.messageCount}条")
                }
                Pair(titleText, messageText)
            }
            else -> Pair("选择导入模式", "")
        }

        // 使用自定义布局
        val dialogView = layoutInflater.inflate(com.susking.ephone_s.settings.R.layout.dialog_import_mode_selection, null)
        val tvMessage = dialogView.findViewById<TextView>(com.susking.ephone_s.settings.R.id.tvMessage)
        val radioGroup = dialogView.findViewById<RadioGroup>(com.susking.ephone_s.settings.R.id.radioGroupModes)

        tvMessage.text = message

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确认导入") { _: DialogInterface, _: Int ->
                when (radioGroup.checkedRadioButtonId) {
                    com.susking.ephone_s.settings.R.id.radioSmartMerge -> {
                        // 增量导入(智能合并) - 使用交互式导入
                        performInteractiveImport(preview)
                    }
                    com.susking.ephone_s.settings.R.id.radioOverwrite -> {
                        // 覆盖导入
                        val updatedPreview = when (preview) {
                            is ImportPreview.SingleChat -> preview.copy(mode = ImportMode.OVERWRITE)
                            is ImportPreview.AllData -> preview.copy(mode = ImportMode.OVERWRITE)
                            else -> preview
                        }
                        performConfirmImport(updatedPreview)
                    }
                    else -> {
                        // 默认使用智能合并
                        performInteractiveImport(preview)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("说明") { _: DialogInterface, _: Int ->
                showImportModeDescription {
                    showImportModeSelectionDialog(preview)
                }
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 显示导入模式说明对话框
     */
    private fun showImportModeDescription(onDismiss: (() -> Unit)? = null) {
        val description = """
            【增量导入(智能合并)】推荐
            选择文件后直接开始导入,遇到冲突时会询问您:
            • 联系人资料:逐字段对比,每个字段冲突都会询问您保留哪个
            • 聊天消息/记忆等:整体对比,冲突时询问您保留哪条
            • 相同数据:自动跳过,不重复导入
            • 新数据:自动添加,不会丢失
            • 导入完成后显示详细统计:新增几条、冲突几条
            
            适用场景:
            ✓ 想精确控制每个冲突的处理方式
            ✓ 需要合并多个数据源
            ✓ 不确定要保留哪些数据
            
            【覆盖导入】
            完全清空现有数据,导入全部新数据。
            注意:此操作会删除所有现有数据,不可撤销!
            
            适用场景:
            ✓ 想完全替换所有数据
            ✓ 确定导入的数据是最新最全的
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("导入模式说明")
            .setMessage(description)
            .setPositiveButton("我知道了") { _, _ ->
                onDismiss?.invoke()
            }
            .show()
    }

    /**
     * 执行交互式导入(智能合并)
     */
    private fun performInteractiveImport(preview: ImportPreview) {
        Log.d("QqMainFragment", "performInteractiveImport: 开始交互式导入")

        val uri = currentImportUri
        if (uri == null) {
            Toast.makeText(requireContext(), "导入失败:未找到文件", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)
                Log.d("QqMainFragment", "performInteractiveImport: 调用executeInteractiveImport")

                val result = importDataUseCase.executeInteractiveImport(
                    uri = uri,
                    onConflict = { conflictItem ->
                        // 在主线程显示冲突解决对话框
                        showConflictResolutionDialog(conflictItem)
                    }
                )

                _isImporting.postValue(false)
                when (result) {
                    is ImportOutcome.Success -> {
                        Log.d("QqMainFragment", "performInteractiveImport: 导入完成")
                        // 显示导入结果
                        showImportResultDialog(result.data)
                        // 刷新数据
                        contactManager.loadContacts()
                        viewModel.qqContactManager.refreshUserProfile()
                    }
                    is ImportOutcome.Failed -> {
                        Log.e("QqMainFragment", "performInteractiveImport: 导入失败", result.error)
                        _errorEvent.postValue(Event("导入失败: ${result.error.message}"))
                    }
                    is ImportOutcome.RolledBack -> {
                        Log.e("QqMainFragment", "performInteractiveImport: 导入失败已回滚", result.error)
                        showRolledBackDialogAndRestart(result)
                    }
                }
            } catch (e: Exception) {
                Log.e("QqMainFragment", "performInteractiveImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
            }
        }
    }

    /**
     * 显示「导入失败、数据已回滚」提示框，用户确认后重启 App。
     *
     * 数据已用快照恢复到导入前状态，但内存中仍持有旧的数据库连接 / 缓存，必须重启进程才能读到
     * 恢复后的数据。重启前用不可取消的弹框承接这一空档，避免用户在旧连接上误操作。
     */
    private fun showRolledBackDialogAndRestart(outcome: ImportOutcome.RolledBack) {
        val message: String = if (outcome.restoredCleanly) {
            "导入过程中出错，您的数据已恢复到导入前的状态，未发生丢失。\n\n" +
                "点击确定将重启应用以完成恢复。\n\n失败原因：${outcome.error.message}"
        } else {
            "导入过程中出错，正在尝试恢复数据时也遇到了问题，数据可能不完整。\n\n" +
                "点击确定将重启应用，请重启后检查数据。\n\n失败原因：${outcome.error.message}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("导入失败，已回滚")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("确定并重启") { _, _ ->
                BackupSnapshotHelper(requireContext().applicationContext).restartApp()
            }
            .show()
    }

    /**
     * 显示冲突解决对话框
     */
    private suspend fun showConflictResolutionDialog(conflictItem: ConflictItem): ConflictResolution {
        return suspendCancellableCoroutine { continuation ->
            requireActivity().runOnUiThread {
                val dialogFragment = ConflictResolutionDialogFragment.newInstance(conflictItem)
                dialogFragment.setOnResolutionSelectedListener { resolution ->
                    continuation.resume(resolution) {
                        // 取消时的清理操作
                    }
                }
                dialogFragment.show(childFragmentManager, "ConflictResolutionDialog")
            }
        }
    }

    /**
     * 显示导入结果对话框
     */
    private fun showImportResultDialog(result: ImportResult) {
        // 使用ImportResult提供的格式化报告
        val message = result.formatReport()

        AlertDialog.Builder(requireContext())
            .setTitle("导入成功")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    internal fun openDrawerFragment(fragment: Fragment, containerId: Int) {
        childFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()

        // 隐藏底部导航栏和其他UI元素
        binding.appBarLayout.visibility = View.GONE
        binding.bottomNavigationView.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fragmentContainerForChat.visibility = View.VISIBLE
    }

    private fun setupBackStackListener() {
        childFragmentManager.addOnBackStackChangedListener {
            synchronizeMainUiVisibility()
        }
    }

    private fun synchronizeMainUiVisibility() {
        val hasChildFragment = childFragmentManager.backStackEntryCount > 0

        if (!hasChildFragment) {
            // 没有子Fragment时,恢复主界面
            binding.fragmentContainerForChat.visibility = View.GONE

            // 恢复 UI 栏显示
            if (binding.viewPager.currentItem == 4) { // 动态页
                binding.appBarLayout.visibility = View.GONE
            } else {
                binding.appBarLayout.visibility = View.VISIBLE
            }
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
            updateDrawerGestureLockMode()
            return
        }

        // 有子Fragment时，主题切换恢复视图也需要立刻隐藏QQ主界面元素，避免底部导航栏露出。
        binding.appBarLayout.visibility = View.GONE
        binding.bottomNavigationView.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fragmentContainerForChat.visibility = View.VISIBLE
        updateDrawerGestureLockMode()
    }

    private fun updateDrawerGestureLockMode() {
        val hasChildFragment = childFragmentManager.backStackEntryCount > 0
        val shouldEnableGesture = canEnableDrawerGesture(binding.viewPager.currentItem, hasChildFragment)
        val lockMode = if (shouldEnableGesture) {
            DrawerLayout.LOCK_MODE_UNLOCKED
        } else {
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        }

        // 抽屉的手势开关必须和锁定模式同步，否则恢复主界面时会被解锁状态覆盖。
        binding.drawerLayout.isGestureEnabled = shouldEnableGesture
        binding.drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)
    }

    private fun canEnableDrawerGesture(position: Int, hasChildFragment: Boolean): Boolean {
        if (hasChildFragment) return false

        return when (position) {
            0, 1, 2, 3 -> true // 消息、联系人、回忆、收藏页允许从左侧滑出抽屉。
            else -> false // 动态页和其他未知页面禁用抽屉手势，避免和页面内横向手势冲突。
        }
    }

    companion object {
        fun newInstance() = QqMainFragment()
    }

    private fun showExportAllDataFormatDialog() {
        val dialogBinding = DialogExportFormatBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnExportZip.setOnClickListener {
            performExportAllData("zip")
            dialog.dismiss()
        }

        dialogBinding.btnExportJson.setOnClickListener {
            performExportAllData("json")
            dialog.dismiss()
        }

        dialog.show()
    }

    // 导入导出的实际执行方法
    private fun performImport(uri: Uri) {
        Log.d("QqMainFragment", "performImport: 开始导入,URI=$uri")
        // 保存URI供后续使用
        currentImportUri = uri

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)
                Log.d("QqMainFragment", "performImport: 调用 prepareImport")

                val result = importDataUseCase.prepareImport(uri)

                result.onSuccess { preview ->
                    Log.d("QqMainFragment", "performImport: 解析成功,预览类型=${preview::class.simpleName}")
                    _importPreviewEvent.postValue(Event(preview))
                }.onFailure { error ->
                    Log.e("QqMainFragment", "performImport: 解析失败", error)
                    _errorEvent.postValue(Event("导入失败: ${error.message}"))
                }

                _isImporting.postValue(false)
            } catch (e: Exception) {
                Log.e("QqMainFragment", "performImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
            }
        }
    }

    private fun performConfirmImport(data: Any) {
        Log.d("QqMainFragment", "performConfirmImport: 确认导入,数据类型=${data::class.simpleName}")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)

                val outcome = importDataUseCase.executeImport(data as ImportPreview)

                when (outcome) {
                    is ImportOutcome.Success -> {
                        Log.d("QqMainFragment", "performConfirmImport: 导入成功,${outcome.data}")
                        _importConfirmation.postValue(Event(true))
                        _isImporting.postValue(false)
                    }
                    is ImportOutcome.Failed -> {
                        Log.e("QqMainFragment", "performConfirmImport: 导入失败", outcome.error)
                        _errorEvent.postValue(Event("导入失败: ${outcome.error.message}"))
                        _importConfirmation.postValue(Event(false))
                        _isImporting.postValue(false)
                    }
                    is ImportOutcome.RolledBack -> {
                        Log.e("QqMainFragment", "performConfirmImport: 导入失败已回滚", outcome.error)
                        _isImporting.postValue(false)
                        showRolledBackDialogAndRestart(outcome)
                    }
                }
            } catch (e: Exception) {
                Log.e("QqMainFragment", "performConfirmImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
                _importConfirmation.postValue(Event(false))
            }
        }
    }

    private fun performExportAllData(outputFormat: String) {
        Log.d("QqMainFragment", "=== performExportAllData 被调用,format=$outputFormat ===")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("QqMainFragment", "开始收集联系人数据")
                val contacts = personProfileRepository.getPersonProfiles()
                val friendGroups = dataImportExportRepository.getFriendGroups()

                Log.d("QqMainFragment", "收集到 ${contacts.size} 个联系人,准备调用 exportDataUseCase")
                val result = exportDataUseCase(outputFormat, contacts, friendGroups)

                result.onSuccess { message ->
                    Log.d("QqMainFragment", "导出成功: $message")
                    _exportResult.value = message
                }.onFailure { error ->
                    Log.e("QqMainFragment", "导出失败", error)
                    _errorEvent.postValue(Event("导出失败: ${error.message}"))
                }
            } catch (e: Exception) {
                Log.e("QqMainFragment", "导出异常", e)
                _errorEvent.postValue(Event("导出失败: ${e.message}"))
            }
        }
    }
}