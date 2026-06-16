package com.susking.ephone_s.qq.ui.chat.profile

import android.net.Uri
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.textfield.TextInputEditText
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.import_export.ImportOutcome
import com.susking.ephone_s.aidata.domain.model.import_export.ImportPreview
import com.susking.ephone_s.aidata.util.BackupSnapshotHelper
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.core.util.ImagePickerHelper
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.FragmentQqAiProfileBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.chat.search.ChatHistorySearchFragment
import com.susking.ephone_s.qq.util.ImageSelector
import com.susking.ephone_s.settings.ui.novelai.CharacterNaiSettingsDialogFragment
import com.susking.ephone_s.settings.ui.conflict.ConflictResolutionDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.susking.ephone_s.settings.R as SettingsR

@AndroidEntryPoint
class QqAiProfileFragment : Fragment(), CharacterNaiSettingsDialogFragment.OnNaiSettingsSaveListener {

private var _binding: FragmentQqAiProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 Manager
    @Inject lateinit var contactManager: QqContactManager
    @Inject lateinit var innerActivityManager: QqContentManager
    @Inject lateinit var qqChatManager: com.susking.ephone_s.qq.domain.manager.QqChatManager
    
    // 导入导出状态
    private val _importPreviewEvent = androidx.lifecycle.MutableLiveData<Event<Any>>()
    private val _isImporting = androidx.lifecycle.MutableLiveData<Boolean>(false)
    private val _importConfirmation = androidx.lifecycle.MutableLiveData<Event<Any>>()
    private val _errorEvent = androidx.lifecycle.MutableLiveData<Event<String>>()
    private val _exportResult = kotlinx.coroutines.flow.MutableStateFlow<String>("")
    
    // 保存当前导入的URI,用于智能合并
    private var currentImportUri: Uri? = null

    private var contactId: String? = null

    private var currentContact: PersonProfile? = null
    private var isPhotoEditMode: Boolean = false

    private lateinit var photosAdapter: FeaturedPhotosAdapter
    private lateinit var imagePickerHelper: ImagePickerHelper
    // 将ImageSelector移到成员变量,在onCreate中初始化,避免view重建时重新创建
    private var chatBackgroundSelector: ImageSelector? = null
    private var avatarSelector: ImageSelector? = null
    private var backgroundSelector: ImageSelector? = null
    private var featuredPhotoSelector: ImageSelector? = null
    // --- 导入/导出 相关 ---

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { performImport(it) }
    }

    private fun observeViewModelEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                _exportResult.collect { resultMessage ->
                    if (resultMessage.isNotEmpty()) {
                        Toast.makeText(requireContext(), resultMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        _importPreviewEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { preview ->
                when (preview) {
                    is ImportPreview.SingleChat -> showImportConfirmationDialog(preview)
                    is ImportPreview.AllData -> {
                        Toast.makeText(requireContext(), "请在主界面的“扫一扫”中导入全部数据。", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

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

        _importConfirmation.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success as? Boolean == true) {
                    Toast.makeText(requireContext(), "导入成功！", Toast.LENGTH_SHORT).show()
                    // 刷新联系人列表和当前页面数据
                    contactManager.loadContacts()
                    contactId?.let { innerActivityManager.loadContactDetails(it) }
                } else {
                    Toast.makeText(requireContext(), "导入失败。", Toast.LENGTH_SHORT).show()
                }
            }
        }

        _errorEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorMessage ->
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun showImportConfirmationDialog(preview: ImportPreview.SingleChat) {
        // 显示导入模式选择对话框
        showImportModeSelectionDialog(preview)
    }
    
    /**
     * 显示导入模式选择对话框
     */
    private fun showImportModeSelectionDialog(preview: ImportPreview.SingleChat) {
        val title = if (preview.isNewCharacter) "导入新角色" else "角色已存在"
        val message = "角色名: ${preview.characterNickname}"
        
        // 使用自定义布局
        val dialogView = layoutInflater.inflate(SettingsR.layout.dialog_import_mode_selection, null)
        val tvMessage = dialogView.findViewById<TextView>(SettingsR.id.tvMessage)
        val radioGroup = dialogView.findViewById<RadioGroup>(SettingsR.id.radioGroupModes)
        
        tvMessage.text = message
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确认导入") { dialog, which ->
                when (radioGroup.checkedRadioButtonId) {
                    SettingsR.id.radioSmartMerge -> {
                        // 增量导入(智能合并)
                        performInteractiveImport(preview)
                    }
                    SettingsR.id.radioOverwrite -> {
                        // 覆盖导入
                        val updatedPreview = preview.copy(mode = com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE)
                        performConfirmImport(updatedPreview)
                    }
                    else -> {
                        // 默认使用智能合并
                        performInteractiveImport(preview)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("说明") { dialog, which ->
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
    private fun performInteractiveImport(preview: ImportPreview.SingleChat) {
        android.util.Log.d("QqAiProfileFragment", "performInteractiveImport: 开始交互式导入")
        
        val uri = currentImportUri
        if (uri == null) {
            Toast.makeText(requireContext(), "导入失败:未找到文件", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)
                android.util.Log.d("QqAiProfileFragment", "performInteractiveImport: 调用executeInteractiveImport")
                
                // 通过AiDataApi获取ImportDataUseCase
                val importDataUseCase = AiDataApi.getImportDataUseCase()
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
                        android.util.Log.d("QqAiProfileFragment", "performInteractiveImport: 导入完成")
                        // 显示导入结果
                        showImportResultDialog(result.data)
                        // 刷新数据
                        contactManager.loadContacts()
                        contactId?.let { innerActivityManager.loadContactDetails(it) }
                    }
                    is ImportOutcome.Failed -> {
                        android.util.Log.e("QqAiProfileFragment", "performInteractiveImport: 导入失败", result.error)
                        _errorEvent.postValue(Event("导入失败: ${result.error.message}"))
                    }
                    is ImportOutcome.RolledBack -> {
                        android.util.Log.e("QqAiProfileFragment", "performInteractiveImport: 导入失败已回滚", result.error)
                        showRolledBackDialogAndRestart(result)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("QqAiProfileFragment", "performInteractiveImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 显示冲突解决对话框
     */
    private suspend fun showConflictResolutionDialog(conflictItem: com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem): com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
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
    private fun showImportResultDialog(result: com.susking.ephone_s.aidata.domain.model.import_export.ImportResult) {
        // 使用ImportResult提供的格式化报告
        val message = result.formatReport()
        
        AlertDialog.Builder(requireContext())
            .setTitle("导入成功")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
        if (contactId == null) {
            Toast.makeText(context, "错误：未找到联系人ID", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
        // 在onCreate中初始化ImageSelector,避免view重建时重新创建
        setupImageSelectors()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqAiProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagePickerHelper = ImagePickerHelper(this)
        // setupImageSelectors() 已在onCreate中调用,不要重复调用
        setupToolbar()
        setupRecyclerView()
        observeContact()
        setupClickListeners()
        setupChangeListeners()
        observeViewModelEvents()
    }

    private fun setupImageSelectors() {
        // 只在首次创建时初始化
        if (chatBackgroundSelector == null) {
            chatBackgroundSelector = ImageSelector(this, "chat_background_selection") { uri ->
            val newChatBackgroundUrl = uri?.toString()
            currentContact = currentContact?.copy(chatBackgroundUri = newChatBackgroundUrl)
            saveContact()
            val message = if (newChatBackgroundUrl != null) "聊天背景已更新" else "聊天背景已清除"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        if (avatarSelector == null) {
            avatarSelector = ImageSelector(this, "avatar_selection") { uri ->
            uri?.let {
                val newAvatarUrl = it.toString()
                currentContact = currentContact?.copy(avatarUri = newAvatarUrl)
                Glide.with(this).load(newAvatarUrl).into(binding.avatarImage)
                saveContact()
            }
            }
        }

        if (backgroundSelector == null) {
            backgroundSelector = ImageSelector(this, "background_selection") { uri ->
            uri?.let {
                val newBackgroundUrl = it.toString()
                currentContact = currentContact?.copy(backgroundUri = newBackgroundUrl)
                Glide.with(this).load(newBackgroundUrl).into(binding.backgroundImage)
                saveContact()
            }
            }
        }

        if (featuredPhotoSelector == null) {
            featuredPhotoSelector = ImageSelector(this, "featured_photo_selection") { uri ->
                val currentPhotos =
                    currentContact?.selectedPhotos?.toMutableList() ?: mutableListOf()
                if (uri != null && currentPhotos.size < MAX_PHOTOS) {
                    currentPhotos.add(uri.toString())
                    currentContact = currentContact?.copy(selectedPhotos = currentPhotos)
                    bindContactData(currentContact!!)
                    saveContact()
                } else if (uri == null) {
                    // 用户取消选择或清除了，这里不做任何操作，因为没有“清除单张精选照片”的逻辑
                } else {
                    Toast.makeText(
                        requireContext(),
                        "最多只能上传 ${MAX_PHOTOS} 张照片",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
 
    private fun setupRecyclerView() {
        photosAdapter = FeaturedPhotosAdapter(
            onAddClick = {
                if ((currentContact?.selectedPhotos?.size ?: 0) < MAX_PHOTOS) {
                    val cropOptions = CropImageOptions().apply {
                        guidelines = CropImageView.Guidelines.ON
                        aspectRatioX = 1
                        aspectRatioY = 1
                        fixAspectRatio = true
                    }
                    featuredPhotoSelector?.showSelectionDialog(
                        title = "选择精选照片",
                        cropOptions = cropOptions,
                        showClearOption = false // 精选照片没有“清除”单张的选项，只有删除
                    )
                } else {
                    Toast.makeText(context, "最多只能上传 ${MAX_PHOTOS} 张照片", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { photoUri ->
                val currentPhotos = currentContact?.selectedPhotos?.toMutableList() ?: mutableListOf()
                currentPhotos.remove(photoUri)
                currentContact = currentContact?.copy(selectedPhotos = currentPhotos)
                bindContactData(currentContact!!) // Re-bind to update UI
                saveContact()
            },
            onPhotoClick = { photoUri ->
                // 导航到 PhotoViewerFragment
                val fragment = PhotoViewerFragment.newInstance(photoUri)
                parentFragmentManager.beginTransaction()
                    .replace(this.id, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )
        binding.photosRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 4) // 4列网格布局
            adapter = photosAdapter
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.editPhotosButton.setOnClickListener {
            togglePhotoEditMode()
        }
    }

    private fun observeContact() {
        viewModel.contactManager.contacts.observe(viewLifecycleOwner) { contacts ->
            contacts.find { it.id == contactId }?.let { contact ->
                currentContact = contact
                bindContactData(contact)
            }
        }
    }

    private fun bindContactData(contact: PersonProfile) {
        binding.collapsingToolbar.title = contact.remarkName
        binding.remarkNameText.text = contact.remarkName
        binding.realNameText.text = "真实姓名: ${contact.realName}"
        binding.qqNumberText.text = "QQ号: ${contact.id}"

        // Dynamically build the info line from the contact data
        val infoItems = mutableListOf<String>()
        contact.gender?.let { infoItems.add("♀ $it") }
        contact.age?.let { infoItems.add("${it}岁") }
        contact.birthday?.let { infoItems.add(it) }
        contact.zodiacSign?.let { infoItems.add(it) }
        contact.location?.let { infoItems.add("现居$it") }
        contact.companyOrSchool?.let { infoItems.add(it) }
        contact.profession?.let { infoItems.add(it) }

        binding.infoLineText.text = if (infoItems.isEmpty()) "ta的个人资料……" else infoItems.joinToString(" | ")
        binding.signatureText.text = contact.signature.takeIf { !it.isNullOrBlank() } ?: "ta的个性签名……"
        binding.actionCooldownContainer.visibility = if (contact.backgroundActivityEnabled) View.VISIBLE else View.GONE
        binding.lastBackgroundActionTimeText.text = formatLastBackgroundActionTimestamp(contact.lastBackgroundActionTimestamp)

        // 更新拉黑按钮的文本
       binding.blockContactButton.text = if (contact.isBlocked) "取消拉黑" else "拉黑联系人"

        // 设置开关状态
        binding.offlineModeSwitch.isChecked = contact.offlineModeEnabled
        binding.injectThoughtsSwitch.isChecked = contact.injectThoughts
        binding.backgroundActivitySwitch.isChecked = contact.backgroundActivityEnabled
        binding.timeAwarenessSwitch.isChecked = contact.timeAwarenessEnabled


        // Display selected photos
        photosAdapter.submitList(contact.selectedPhotos)

        // Load images
        Glide.with(this)
            .load(contact.avatarUri)
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(binding.avatarImage)

        Glide.with(this)
            .load(contact.backgroundUri)
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(binding.backgroundImage)

       // 重新设置点击事件，确保使用的是最新的contact状态
       binding.blockContactButton.setOnClickListener {
           val title = if (contact.isBlocked) "确认取消拉黑" else "确认拉黑"
           val message = if (contact.isBlocked) {
               "确定要将对方解除拉黑吗？"
           } else {
               "确定要拉黑 ${contact.remarkName} 吗？\n\n拉黑后, 在冷静期结束前您将无法与对方聊天。"
           }

           AlertDialog.Builder(requireContext())
               .setTitle(title)
               .setMessage(message)
               .setPositiveButton("确定") { _, _ ->
                   contactManager.toggleBlock(contact.id)
               }
               .setNegativeButton("取消", null)
               .show()
       }
    }

    private fun setupClickListeners() {
        binding.settingsButton.setOnClickListener {
            currentContact?.let { contact ->
                val fragment = QqAiProfileSettingsFragment.newInstance(contact.id)
                parentFragmentManager.beginTransaction()
                    .replace(this.id, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        binding.avatarImage.setOnClickListener {
            val cropOptions = CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                aspectRatioX = 1
                aspectRatioY = 1
                fixAspectRatio = true
                cropShape = CropImageView.CropShape.OVAL
            }
            avatarSelector?.showSelectionDialog(
                title = "选择头像",
                cropOptions = cropOptions,
                showClearOption = false
            )
        }
        binding.backgroundImage.setOnClickListener {
            val cropOptions = CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                fixAspectRatio = false // 允许自由裁剪
            }
            backgroundSelector?.showSelectionDialog(
                title = "选择资料卡背景",
                cropOptions = cropOptions,
                showClearOption = false
            )
        }
        binding.infoLineContainer.setOnClickListener {
            currentContact?.let { contact ->
                val fragment = QqAiProfileEditDetailsFragment.newInstance(contact.id)
                parentFragmentManager.beginTransaction()
                    .replace(this.id, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
        binding.qqSpaceLayout.setOnClickListener {
            val fragment = QqSpaceFragment()
            parentFragmentManager.beginTransaction()
                .replace(this.id, fragment)
                .addToBackStack(null)
                .commit()
        }
        binding.signatureContainer.setOnClickListener {
            showEditSignatureDialog()
        }
        binding.offlineModeInfoIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("线下模式说明")
                .setMessage("开启后, AI的回复将变为包含「对话」和动作/环境描写的剧情模式。")
                .setPositiveButton("确定", null)
                .show()
        }
        binding.naiSettingsButton.setOnClickListener {
            currentContact?.let { contact ->
                val dialog = CharacterNaiSettingsDialogFragment.newInstance(contact)
                dialog.listener = this
                dialog.show(childFragmentManager, CharacterNaiSettingsDialogFragment.TAG)
            }
        }
        binding.injectThoughtsInfoIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("注入最新心声说明")
                .setMessage("开启后, AI在回应前会“看到”自己上一轮的内心独白。这能增强连贯性, 但可能轻微增加Token消耗。")
                .setPositiveButton("确定", null)
                .show()
        }
        binding.backgroundActivityInfoIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("启用独立后台活动说明")
                .setMessage("允许该角色在后台独立发消息或动态。需同时开启API设置中的总开关。")
                .setPositiveButton("确定", null)
                .show()
        }
        binding.timeAwarenessInfoIcon.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("启用时间感知说明")
                .setMessage("关闭后, AI将不会接收到当前时间信息, 对话中不再体现时间变化。")
                .setPositiveButton("确定", null)
                .show()
        }
        binding.deleteContactButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }


        binding.clearChatHistoryButton.setOnClickListener {
            showClearHistoryConfirmationDialog()
        }

        binding.exportChatHistoryButton.setOnClickListener {
            showExportFormatDialog()
        }

        binding.importChatHistoryButton.setOnClickListener {
            importFileLauncher.launch("*/*") // 允许选择所有文件类型，包括 .zip 和 .json
        }

        binding.changeChatBackgroundButton.setOnClickListener {
            val displayMetrics = requireContext().resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val cropOptions = CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                fixAspectRatio = true
                aspectRatioX = screenWidth
                aspectRatioY = screenHeight
            }
            chatBackgroundSelector?.showSelectionDialog(
                title = "选择聊天背景",
                cropOptions = cropOptions,
                showClearOption = true
            )
        }
        
        binding.searchHistoryButton.setOnClickListener {
            contactId?.let {
                val fragment = ChatHistorySearchFragment.newInstance(it)
                parentFragmentManager.beginTransaction()
                    .replace(this.id, fragment) // 使用 this.id 获取当前容器
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
 
    private fun showExportFormatDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_export_format, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_export_zip).setOnClickListener {
            contactId?.let { performExportChatHistory(it, "zip") }
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_export_json).setOnClickListener {
            contactId?.let { performExportChatHistory(it, "json") }
            dialog.dismiss()
        }

        dialog.show()
    }
 
    private fun togglePhotoEditMode() {
        isPhotoEditMode = !isPhotoEditMode
        photosAdapter.setEditMode(isPhotoEditMode)

        if (isPhotoEditMode) {
            binding.editPhotosButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red_500)) // 假设你有一个 red_500 颜色
        } else {
            binding.editPhotosButton.clearColorFilter()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要永久删除此联系人及其所有聊天记录吗？原子事件纪念记录会保留。此操作无法撤销。")
            .setPositiveButton("删除") { _, _ ->
                contactId?.let {
                    contactManager.deleteContact(it)
                    Toast.makeText(context, "联系人已删除", Toast.LENGTH_SHORT).show()
                    // 直接返回到联系人列表，而不是聊天页面
                    // 这会弹出回退栈中直到（并包括）具有该名称的片段的所有状态
                    parentFragmentManager.popBackStack("chat_fragment_tag", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearHistoryConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("确认清除")
            .setMessage("确定要清除此联系人的所有聊天记录、心声和散记吗？原子事件纪念记录会保留。此操作无法撤销。")
            .setPositiveButton("清除") { _, _ ->
                contactId?.let {
                    qqChatManager.clearHistory(it)
                    Toast.makeText(context, "聊天记录已清除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupChangeListeners() {
        // Change listeners now only update the in-memory 'currentContact'.
        // The actual save operation is moved to onDestroyView to prevent race conditions
        // and to fulfill the requirement of saving only on exit.

        binding.backgroundActivitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (currentContact?.backgroundActivityEnabled != isChecked) {
                binding.actionCooldownContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                currentContact = currentContact?.copy(backgroundActivityEnabled = isChecked)
            }
        }

        binding.injectThoughtsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (currentContact?.injectThoughts != isChecked) {
                currentContact = currentContact?.copy(injectThoughts = isChecked)
            }
        }

        binding.timeAwarenessSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (currentContact?.timeAwarenessEnabled != isChecked) {
                currentContact = currentContact?.copy(timeAwarenessEnabled = isChecked)
            }
        }

        binding.offlineModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (currentContact?.offlineModeEnabled != isChecked) {
                currentContact = currentContact?.copy(offlineModeEnabled = isChecked)
            }
        }

        // TextWatcher is no longer needed as saving is deferred to onDestroyView.
        // The final value of actionCooldownEditText will be read at that point.
    }

    private fun formatLastBackgroundActionTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) {
            return "上次独立后台行动：从未"
        }

        val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val formattedTime: String = formatter.format(Date(timestamp))
        return "上次独立后台行动：$formattedTime"
    }

    override fun onNaiSettingsSave(promptSource: String, positivePrompt: String, negativePrompt: String) {
        currentContact = currentContact?.copy(
            naiPromptSource = promptSource,
            naiPositivePrompt = positivePrompt,
            naiNegativePrompt = negativePrompt
        )
        saveContact()
        Toast.makeText(context, "NAI提示词已保存", Toast.LENGTH_SHORT).show()
    }

    private fun saveContact() {
        currentContact?.let {
            val updatedContact = it.copy(
                signature = binding.signatureText.text.toString().takeIf { it.isNotBlank() },
                actionCooldownMinutes = binding.actionCooldownEditText.text.toString().toIntOrNull() ?: 10,
                backgroundActivityEnabled = binding.backgroundActivitySwitch.isChecked,
                injectThoughts = binding.injectThoughtsSwitch.isChecked,
                timeAwarenessEnabled = binding.timeAwarenessSwitch.isChecked,
                offlineModeEnabled = binding.offlineModeSwitch.isChecked,
                selectedPhotos = currentContact?.selectedPhotos ?: emptyList(),
                chatBackgroundUri = currentContact?.chatBackgroundUri
            )
            contactManager.updateContact(updatedContact)
        }
    }

    override fun onDestroyView() {
        // 在视图销毁前保存所有累积的更改。
        // 这是解决数据覆盖问题和实现“退出时保存”需求的核心。
        saveContact()
        super.onDestroyView()
        _binding = null
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun showEditSignatureDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("编辑个性签名")

        val input = TextInputEditText(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText(currentContact?.signature)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 4
        }

        builder.setView(input)

        builder.setPositiveButton("保存") { dialog, _ ->
            val newSignature = input.text.toString()
            currentContact = currentContact?.copy(signature = newSignature)
            bindContactData(currentContact!!)
            saveContact()
            dialog.dismiss()
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val MAX_PHOTOS = 4

        fun newInstance(contactId: String): QqAiProfileFragment {
            return QqAiProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
    
    // 导入导出的实际执行方法
    private fun performImport(uri: Uri) {
        android.util.Log.d("QqAiProfileFragment", "performImport: 开始导入,URI=$uri")
        // 保存URI供后续使用
        currentImportUri = uri
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)
                android.util.Log.d("QqAiProfileFragment", "performImport: 调用 prepareImport")

                // 通过AiDataApi获取ImportDataUseCase
                val importDataUseCase = AiDataApi.getImportDataUseCase()
                val result = importDataUseCase.prepareImport(uri)

                result.onSuccess { preview ->
                    android.util.Log.d("QqAiProfileFragment", "performImport: 解析成功,预览类型=${preview::class.simpleName}")
                    _importPreviewEvent.postValue(Event(preview))
                }.onFailure { error ->
                    android.util.Log.e("QqAiProfileFragment", "performImport: 解析失败", error)
                    _errorEvent.postValue(Event("导入失败: ${error.message}"))
                }

                _isImporting.postValue(false)
            } catch (e: Exception) {
                android.util.Log.e("QqAiProfileFragment", "performImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
            }
        }
    }
    
    private fun performConfirmImport(data: Any) {
        android.util.Log.d("QqAiProfileFragment", "performConfirmImport: 确认导入,数据类型=${data::class.simpleName}")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                _isImporting.postValue(true)

                // 通过AiDataApi获取ImportDataUseCase
                val importDataUseCase = AiDataApi.getImportDataUseCase()
                val outcome = importDataUseCase.executeImport(data as ImportPreview)

                _isImporting.postValue(false)
                when (outcome) {
                    is ImportOutcome.Success -> {
                        android.util.Log.d("QqAiProfileFragment", "performConfirmImport: 导入成功,${outcome.data}")
                        _importConfirmation.postValue(Event(true))
                    }
                    is ImportOutcome.Failed -> {
                        android.util.Log.e("QqAiProfileFragment", "performConfirmImport: 导入失败", outcome.error)
                        _errorEvent.postValue(Event("导入失败: ${outcome.error.message}"))
                        _importConfirmation.postValue(Event(false))
                    }
                    is ImportOutcome.RolledBack -> {
                        android.util.Log.e("QqAiProfileFragment", "performConfirmImport: 导入失败已回滚", outcome.error)
                        showRolledBackDialogAndRestart(outcome)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("QqAiProfileFragment", "performConfirmImport: 异常", e)
                _isImporting.postValue(false)
                _errorEvent.postValue(Event("导入失败: ${e.message}"))
                _importConfirmation.postValue(Event(false))
            }
        }
    }

    /**
     * 显示「导入失败、数据已回滚」提示框，用户确认后重启 App。
     *
     * 数据已用快照恢复到导入前状态，但内存中仍持有旧的数据库连接 / 缓存，必须重启进程才能读到
     * 恢复后的数据。重启前用不可取消的弹框承接这一空档，阻止用户进行其他操作。
     */
    private fun showRolledBackDialogAndRestart(outcome: ImportOutcome.RolledBack) {
        val message: String = if (outcome.restoredCleanly) {
            "导入过程中出错，您的数据已恢复到导入前的状态，未发生丢失。\n\n" +
                "点击确定将重启应用以完成恢复。\n\n失败原因：${outcome.error.message}"
        } else {
            "导入过程中出错，正在尝试恢复数据时也遇到了问题，数据可能不完整。\n\n" +
                "点击确定将重启应用，请重启后检查数据。\n\n失败原因：${outcome.error.message}"
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("导入失败，已回滚")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("确定并重启") { _, _ ->
                BackupSnapshotHelper(requireContext().applicationContext).restartApp()
            }
            .show()
    }

    private fun performExportChatHistory(contactId: String, outputFormat: String) {
        android.util.Log.d("QqAiProfileFragment", "=== performExportChatHistory 被调用,contactId=$contactId,format=$outputFormat ===")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                android.util.Log.d("QqAiProfileFragment", "准备导出联系人 $contactId 的聊天记录")
                
                // 通过AiDataApi获取ExportDataUseCase
                val exportDataUseCase = AiDataApi.getExportDataUseCase()
                val result = exportDataUseCase.exportSingleChat(contactId, outputFormat)
                
                result.onSuccess { message ->
                    android.util.Log.d("QqAiProfileFragment", "导出聊天记录成功: $message")
                    _exportResult.value = message
                }.onFailure { error ->
                    android.util.Log.e("QqAiProfileFragment", "导出聊天记录失败", error)
                    _errorEvent.postValue(Event("导出失败: ${error.message}"))
                }
            } catch (e: Exception) {
                android.util.Log.e("QqAiProfileFragment", "导出聊天记录异常", e)
                _errorEvent.postValue(Event("导出失败: ${e.message}"))
            }
        }
    }
}