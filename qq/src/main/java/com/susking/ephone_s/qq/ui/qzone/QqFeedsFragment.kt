package com.susking.ephone_s.qq.ui.qzone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.ui.photo.PhotoDetailFragment
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.EventObserver
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.FragmentQqFeedsBinding
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.QqMainFragment
import com.susking.ephone_s.qq.ui.notifications.QqNotificationsFragment
import com.susking.ephone_s.qq.util.ImageSelector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QqFeedsFragment : Fragment(), FeedItemListener {

    private var _binding: FragmentQqFeedsBinding? = null
    private val binding get() = _binding!!

    private val feedsViewModel: QqFeedsViewModel by viewModels()

    private val sharedViewModel: QqViewModel by activityViewModels()
    
    // 注入 FeedManager
    @Inject lateinit var feedManager: QqChatManager

    private lateinit var feedsAdapter: QqFeedsAdapter

    private lateinit var headerBackgroundSelector: ImageSelector
    
    // HTML文件选择器
    private val htmlFilePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleHtmlFileSelected(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQqFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
        setupImageSelectors()
    }

    private fun setupImageSelectors() {
        headerBackgroundSelector = ImageSelector(this, "feeds_header_background") { uri ->
            feedsViewModel.updateFeedsBackground(uri)
        }
    }

    private fun setupClickListeners() {
        binding.createFeedCard.setOnClickListener {
            startActivity(Intent(requireContext(), CreateFeedActivity::class.java))
        }

        binding.headerBackgroundImage.setOnClickListener {
            val cropOptions = CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                fixAspectRatio = false // 允许自由裁剪
            }
            headerBackgroundSelector.showSelectionDialog(
                title = "选择空间背景",
                cropOptions = cropOptions,
                showClearOption = true
            )
        }
        
        // 导入链接按钮点击事件
        binding.actionImportFromUrl.setOnClickListener {
            showImportFromUrlDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // 此处应为空，或只包含与 Toolbar 无关的逻辑
    }

    override fun onPause() {
        super.onPause()
        // 此处留空
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_qq_feeds_toolbar)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_notifications -> {
                    (parentFragment as? QqMainFragment)?.openDrawerFragment(
                        QqNotificationsFragment(),
                        R.id.fragment_container_for_notifications
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        feedsAdapter = QqFeedsAdapter(this)
        binding.feedsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = feedsAdapter
        }
    }

    private fun observeViewModel() {
        sharedViewModel.qqContactManager.userProfile.observe(viewLifecycleOwner) { userProfile ->
            binding.userNickname.text = userProfile.nickname
            Glide.with(this).load(userProfile.avatarUri).placeholder(R.drawable.ic_default_avatar).into(binding.userAvatar)
            // 【新增】当UserProfile更新时，也更新动态页面的背景
            userProfile.feedsHeaderBackgroundUri?.let {
                Glide.with(this).load(it).placeholder(R.drawable.bg_qq_space_placeholder).error(R.drawable.bg_qq_space_placeholder).into(binding.headerBackgroundImage)
            }
        }


        feedsViewModel.feedItems.observe(viewLifecycleOwner) { uiModels ->
            feedsAdapter.submitList(uiModels)
        }

        binding.visitorStats.text = "访客总量 84501 · 今日访客 +31"

        // 【新增】监听全局AI活动事件
        EventBus.newAiActivityEvent.observe(viewLifecycleOwner, EventObserver {
            // 当收到事件时，调用ViewModel的刷新方法
            feedsViewModel.refreshFeeds()
        })

        // TODO: 通知数量功能待实现
        // 暂时移除 feedManager.unreadNotificationCount 的监听
        // 因为 QqChatManager 中没有此属性
    }
 
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDeleteClicked(feedId: Int) {
        val feedToDelete = feedsViewModel.feedItems.value?.find { it.feed.id == feedId }?.feed
        if (feedToDelete != null) {
            feedsViewModel.deleteFeed(feedToDelete)
            Toast.makeText(context, "动态已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "删除失败，未找到该动态", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFavoriteClicked(feedId: Int) {
        val feedToFavorite = feedsViewModel.feedItems.value?.find { it.feed.id == feedId }?.feed
        if (feedToFavorite != null) {
            feedsViewModel.favoriteFeed(feedToFavorite)
            Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "收藏失败，未找到该动态", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLikeClicked(feedId: Int) {
        feedsViewModel.toggleLike(feedId)
    }

    override fun onCommentClicked(feedId: Int) {
        showCommentDialog(feedId)
    }

    private fun showCommentDialog(feedId: Int) {
        val editText = EditText(requireContext())
        editText.hint = "请输入评论..."

        AlertDialog.Builder(requireContext())
            .setTitle("发表评论")
            .setView(editText)
            .setPositiveButton("发送") { dialog, _ ->
                val commentText = editText.text.toString()
                if (commentText.isNotBlank()) {
                    feedsViewModel.addComment(feedId, commentText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onShareClicked(feedId: Int) {
        val feedToShare = feedsViewModel.feedItems.value?.find { it.feed.id == feedId }?.feed
        if (feedToShare != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("转发动态")
                .setMessage("确定要转发这条动态吗？")
                .setPositiveButton("转发") { dialog, _ ->
                    feedsViewModel.shareFeed(feedToShare)
                    Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            Toast.makeText(context, "转发失败，未找到该动态", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCommentDeleteClicked(feedId: Int, commentId: Long) {
        feedsViewModel.deleteComment(feedId, commentId)
        Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
    }

    override fun onCommentEditClicked(feedId: Int, commentId: Long) {
        val commentToEdit = feedsViewModel.feedItems.value
            ?.find { it.feed.id == feedId }
            ?.feed?.comments?.find { it.id == commentId }

        if (commentToEdit != null) {
            showEditCommentDialog(feedId, commentId, commentToEdit.commentText ?: "")
        } else {
            Toast.makeText(context, "编辑失败，未找到该评论", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditCommentDialog(feedId: Int, commentId: Long, currentText: String) {
        val editText = EditText(requireContext())
        editText.setText(currentText)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑评论")
            .setView(editText)
            .setPositiveButton("保存") { dialog, _ ->
                val newCommentText = editText.text.toString()
                if (newCommentText.isNotBlank()) {
                    feedsViewModel.editComment(feedId, commentId, newCommentText)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onEditClicked(feedId: Int) {
        val feedToEdit = feedsViewModel.feedItems.value?.find { it.feed.id == feedId }?.feed
        if (feedToEdit != null) {
            showEditFeedDialog(feedToEdit)
        } else {
            Toast.makeText(context, "编辑失败，未找到该动态", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditFeedDialog(feed: FeedEntity) {
        val editText = EditText(requireContext())
        editText.setText(feed.content)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑动态")
            .setView(editText)
            .setPositiveButton("保存") { dialog, _ ->
                val newContent = editText.text.toString()
                if (newContent.isNotBlank()) {
                    feedsViewModel.editFeed(feed.id, newContent)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onImageClicked(feedId: Int, imagePosition: Int) {
        val feed = feedsViewModel.feedItems.value?.find { it.feed.id == feedId }?.feed
        if (feed != null && feed.imageUrls.isNotEmpty()) {
            // 将Feed的图片转换为AlbumPhoto列表
            val albumPhotos = feed.imageUrls.mapIndexed { index, imageUrl ->
                AlbumPhoto(
                    id = "${feed.id}_img_$index",
                    imageUrl = imageUrl,
                    imagePrompt = feed.imagePrompts.getOrNull(index) ?: "",
                    description = feed.imageDescriptions.getOrNull(index) ?: feed.content,
                    timestamp = feed.timestamp
                )
            }
            
            // 创建PhotoDetailFragment并显示
            val photoDetailFragment = PhotoDetailFragment.newInstance(
                contactId = feed.contactId,
                photos = albumPhotos,
                initialPosition = imagePosition
            )
            
            // 设置照片更新监听器
            photoDetailFragment.setPhotoUpdateListener(object : PhotoDetailFragment.PhotoUpdateListener {
                override fun onPhotoUpdated(contactId: String, updatedPhoto: AlbumPhoto) {
                    // 更新Feed中的图片URL和提示词
                    val newImageUrl = updatedPhoto.imageUrl ?: return
                    if (newImageUrl.isEmpty()) return
                    
                    val updatedImageUrls = feed.imageUrls.toMutableList()
                    val photoIndex = updatedPhoto.id.substringAfterLast("_").toIntOrNull() ?: 0
                    if (photoIndex in updatedImageUrls.indices) {
                        updatedImageUrls[photoIndex] = newImageUrl
                    }
                    
                    // 更新Feed的imagePrompts列表
                    val updatedImagePrompts = feed.imagePrompts.toMutableList()
                    if (photoIndex in updatedImagePrompts.indices) {
                        updatedImagePrompts[photoIndex] = updatedPhoto.imagePrompt
                    } else {
                        // 如果列表不够长,填充到足够长度
                        while (updatedImagePrompts.size <= photoIndex) {
                            updatedImagePrompts.add("")
                        }
                        updatedImagePrompts[photoIndex] = updatedPhoto.imagePrompt
                    }
                    
                    // 更新Feed
                    feedsViewModel.updateFeedImages(feed.id, updatedImageUrls, updatedImagePrompts)
                    Toast.makeText(requireContext(), "动态图片已更新", Toast.LENGTH_SHORT).show()
                }
            })
            
            // 显示Fragment - 使用activity的根容器
            requireActivity().supportFragmentManager.beginTransaction()
                .add(android.R.id.content, photoDetailFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * 显示从HTML文件导入的对话框
     */
    private fun showImportFromUrlDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_qzone_url, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // 获取对话框中的控件
        val btnSelectFile = dialogView.findViewById<Button>(R.id.btn_select_file)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val loadingLayout = dialogView.findViewById<LinearLayout>(R.id.loading_layout)
        val loadingText = dialogView.findViewById<TextView>(R.id.loading_text)
        val previewLayout = dialogView.findViewById<LinearLayout>(R.id.preview_layout)
        val previewSummary = dialogView.findViewById<TextView>(R.id.preview_summary)
        val btnConfirmImport = dialogView.findViewById<Button>(R.id.btn_confirm_import)
        val selectedFileText = dialogView.findViewById<TextView>(R.id.selected_file_text)

        // 存储解析结果
        var parsedFeeds = listOf<QzoneFeed>()

        // 选择文件按钮
        btnSelectFile.setOnClickListener {
            htmlFilePicker.launch("text/html")
            dialog.dismiss()
        }

        // 取消按钮
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    
    /**
     * 处理选中的HTML文件
     */
    private fun handleHtmlFileSelected(uri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_qzone_url, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val btnSelectFile = dialogView.findViewById<Button>(R.id.btn_select_file)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val loadingLayout = dialogView.findViewById<LinearLayout>(R.id.loading_layout)
        val loadingText = dialogView.findViewById<TextView>(R.id.loading_text)
        val previewLayout = dialogView.findViewById<LinearLayout>(R.id.preview_layout)
        val previewSummary = dialogView.findViewById<TextView>(R.id.preview_summary)
        val btnConfirmImport = dialogView.findViewById<Button>(R.id.btn_confirm_import)
        val btnPreviewFeeds = dialogView.findViewById<Button>(R.id.btn_preview_feeds)
        val selectedFileText = dialogView.findViewById<TextView>(R.id.selected_file_text)

        // 存储解析结果
        var parsedFeeds = listOf<QzoneFeed>()

        // 显示文件名
        val fileName = uri.lastPathSegment ?: "未知文件"
        selectedFileText.text = "已选择：$fileName"
        selectedFileText.visibility = View.VISIBLE

        // 隐藏选择按钮，显示加载状态
        btnSelectFile.visibility = View.GONE
        loadingLayout.visibility = View.VISIBLE
        previewLayout.visibility = View.GONE

        // 开始解析
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadingText.text = "正在读取HTML文件..."
                
                // 读取文件内容
                val htmlContent = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: run {
                    Toast.makeText(requireContext(), "无法读取文件", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@launch
                }
                
                loadingText.text = "正在解析说说内容..."
                val result = feedsViewModel.parseQzoneHtml(htmlContent)
                
                when (result) {
                    is ParseResult.Success -> {
                        // 解析成功，显示预览
                        parsedFeeds = result.feeds
                        
                        val totalFeeds = parsedFeeds.size
                        val totalImages = parsedFeeds.sumOf { it.imageUrls.size + (it.originalImageUrls?.size ?: 0) }
                        val forwardCount = parsedFeeds.count { it.isForward }
                        val originalCount = totalFeeds - forwardCount
                        
                        previewSummary.text = """
                            成功解析 $totalFeeds 条说说
                            · 原创说说：$originalCount 条
                            · 转发说说：$forwardCount 条
                            · 图片总数：$totalImages 张
                            
                            点击"确认导入"将这些说说添加到您的动态中
                        """.trimIndent()

                        loadingLayout.visibility = View.GONE
                        previewLayout.visibility = View.VISIBLE
                    }
                    is ParseResult.Error -> {
                        // 解析失败，显示错误信息
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "处理文件时出错：${e.message}", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }

        // 预览按钮
        btnPreviewFeeds.setOnClickListener {
            if (parsedFeeds.isEmpty()) {
                Toast.makeText(requireContext(), "没有可预览的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 关闭当前对话框
            dialog.dismiss()
            
            // 显示预览对话框
            showPreviewDialog(parsedFeeds)
        }

        // 确认导入按钮
        btnConfirmImport.setOnClickListener {
            if (parsedFeeds.isEmpty()) {
                Toast.makeText(requireContext(), "没有可导入的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 执行导入
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    loadingLayout.visibility = View.VISIBLE
                    previewLayout.visibility = View.GONE
                    loadingText.text = "正在导入说说..."
                    
                    feedsViewModel.importFeedsFromHtml(parsedFeeds)
                    
                    Toast.makeText(requireContext(), "成功导入 ${parsedFeeds.size} 条说说", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // 刷新动态列表
                    feedsViewModel.refreshFeeds()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        // 取消按钮
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 显示说说预览对话框（支持分页）
     */
    private fun showPreviewDialog(allFeeds: List<QzoneFeed>) {
        val previewDialogView = layoutInflater.inflate(R.layout.dialog_preview_qzone_feeds, null)
        val previewDialog = AlertDialog.Builder(requireContext())
            .setView(previewDialogView)
            .create()

        // 获取预览对话框中的控件
        val previewSummary = previewDialogView.findViewById<TextView>(R.id.preview_summary)
        val pageInfo = previewDialogView.findViewById<TextView>(R.id.page_info)
        val previewRecyclerView = previewDialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.preview_recycler_view)
        val btnPreviousPage = previewDialogView.findViewById<Button>(R.id.btn_previous_page)
        val btnNextPage = previewDialogView.findViewById<Button>(R.id.btn_next_page)
        val currentPageText = previewDialogView.findViewById<TextView>(R.id.current_page_text)
        val btnConfirmImportFromPreview = previewDialogView.findViewById<Button>(R.id.btn_confirm_import_from_preview)
        val btnClosePreview = previewDialogView.findViewById<Button>(R.id.btn_close_preview)

        // 设置RecyclerView
        val adapter = QzoneFeedPreviewAdapter()
        previewRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        previewRecyclerView.adapter = adapter

        // 分页相关变量
        val pageSize = 10
        var currentPage = 0
        val totalPages = feedsViewModel.getTotalPages(allFeeds.size, pageSize)

        // 统计信息
        val totalFeeds = allFeeds.size
        val totalImages = allFeeds.sumOf { it.imageUrls.size + (it.originalImageUrls?.size ?: 0) }
        val forwardCount = allFeeds.count { it.isForward }
        val originalCount = totalFeeds - forwardCount

        previewSummary.text = """
            成功解析 $totalFeeds 条说说
            · 原创说说：$originalCount 条
            · 转发说说：$forwardCount 条
            · 图片总数：$totalImages 张
        """.trimIndent()

        // 更新页面内容的函数
        fun updatePage() {
            val pagedFeeds = feedsViewModel.getPagedFeeds(allFeeds, currentPage, pageSize)
            adapter.pageStartIndex = currentPage * pageSize
            adapter.submitList(pagedFeeds)

            currentPageText.text = "第 ${currentPage + 1} 页"
            pageInfo.text = "共 $totalPages 页，每页 $pageSize 条"

            btnPreviousPage.isEnabled = currentPage > 0
            btnNextPage.isEnabled = currentPage < totalPages - 1

            // 滚动到顶部
            previewRecyclerView.scrollToPosition(0)
        }

        // 初始化第一页
        updatePage()

        // 上一页按钮
        btnPreviousPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updatePage()
            }
        }

        // 下一页按钮
        btnNextPage.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePage()
            }
        }

        // 确认导入按钮
        btnConfirmImportFromPreview.setOnClickListener {
            previewDialog.dismiss()

            // 显示导入确认对话框
            AlertDialog.Builder(requireContext())
                .setTitle("确认导入")
                .setMessage("确定要导入全部 $totalFeeds 条说说吗？")
                .setPositiveButton("确认") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            feedsViewModel.importFeedsFromHtml(allFeeds)
                            Toast.makeText(requireContext(), "成功导入 $totalFeeds 条说说", Toast.LENGTH_SHORT).show()
                            feedsViewModel.refreshFeeds()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 关闭按钮
        btnClosePreview.setOnClickListener {
            previewDialog.dismiss()
        }

        previewDialog.show()
    }

    companion object {
        fun newInstance() = QqFeedsFragment()
    }
}