package com.susking.ephone_s.cphone.ui.diary

import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneDiaryBinding
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import com.susking.ephone_s.aidata.worker.CPhoneAutoDiaryWorker
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone日记App主界面
 * 显示日记列表
 */
@AndroidEntryPoint
class CPhoneDiaryFragment : Fragment() {

    private var _binding: FragmentCphoneDiaryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneDiaryAdapter
    private var contactId: String = ""
    private val diaryList = mutableListOf<DiaryEntry>()
    
    // 保存RecyclerView的滚动位置
    private var recyclerViewState: Parcelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        loadDiaries()
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        // 获取Toolbar
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.toolbar
        )
        
        toolbar?.apply {
            title = "日记"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
        
        // 设置分层摘要入口
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_memory_summary)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                openMemorySummary()
            }
        }
        
        // 日记页面不再提供手动刷新AI日记入口
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.visibility = View.GONE

        // 设置入口：具体功能稍后接入
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_diary_settings)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                showDiarySettingsDialog()
            }
        }

        // 手动新增日记入口
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_diary)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                showAddDiaryDialog()
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneDiaryAdapter(
            onDiaryClick = { diary ->
                openDiaryDetail(diary)
            },
            onFavoriteClick = { diary ->
                toggleFavorite(diary)
            },
            onEditClick = { diary ->
                showEditDiaryDialog(diary)
            },
            onDeleteClick = { diary ->
                showDeleteDiaryDialog(diary)
            }
        )

        binding.rvDiaries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneDiaryFragment.adapter
        }
    }

    /**
     * 观察日记数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getDiaryData(contactId).collect { diaryEntries ->
                diaryList.clear()
                diaryList.addAll(diaryEntries)
                
                if (diaryList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(diaryList.toList()) {
                        // 数据更新完成后恢复滚动位置
                        restoreRecyclerViewState()
                    }
                }
            }
        }
    }
    
    /**
     * 加载日记
     */
    private fun loadDiaries() {
        // 数据通过observeData自动加载
    }

    /**
     * 显示日记设置弹窗
     */
    private fun showDiarySettingsDialog() {
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DIALOG_HORIZONTAL_PADDING, DIALOG_TOP_PADDING, DIALOG_HORIZONTAL_PADDING, 0)
        }
        val autoDiaryCheckBox: CheckBox = CheckBox(requireContext()).apply {
            text = "启用自动日记"
            isChecked = CPhoneAutoDiaryWorker.isAutoDiaryEnabled(requireContext(), contactId)
        }
        container.addView(autoDiaryCheckBox)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("日记设置")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                saveAutoDiarySetting(autoDiaryCheckBox.isChecked)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 保存自动日记设置
     */
    private fun saveAutoDiarySetting(isEnabled: Boolean): Unit {
        CPhoneAutoDiaryWorker.updateAutoDiaryEnabled(requireContext().applicationContext, contactId, isEnabled)
        Snackbar.make(
            binding.root,
            if (isEnabled) "当前联系人自动日记已启用" else "当前联系人自动日记已关闭",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * 显示新增日记弹窗
     */
    private fun showAddDiaryDialog() {
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DIALOG_HORIZONTAL_PADDING, DIALOG_TOP_PADDING, DIALOG_HORIZONTAL_PADDING, 0)
        }
        val titleInput: EditText = createDiaryEditText("标题", false)
        val contentInput: EditText = createDiaryEditText("日记内容", true)
        container.addView(titleInput)
        container.addView(contentInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增日记")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                saveNewDiary(titleInput.text.toString(), contentInput.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 创建日记输入框
     */
    private fun createDiaryEditText(hintText: String, isMultiline: Boolean): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            inputType = if (isMultiline) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            minLines = if (isMultiline) DIARY_CONTENT_MIN_LINES else DIARY_TITLE_MIN_LINES
        }
    }

    /**
     * 保存用户新增日记
     */
    private fun saveNewDiary(title: String, content: String) {
        val trimmedTitle: String = title.trim()
        val trimmedContent: String = content.trim()
        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            Snackbar.make(binding.root, "标题和内容不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }
        val currentTime: Long = System.currentTimeMillis()
        val diary: DiaryEntry = DiaryEntry(
            id = "manual_diary_$currentTime",
            title = trimmedTitle,
            content = trimmedContent,
            timestamp = currentTime
        )
        viewModel.addDiaryEntry(contactId, diary) { isSuccess: Boolean ->
            Snackbar.make(
                binding.root,
                if (isSuccess) "日记已保存" else "日记保存失败",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示编辑日记弹窗
     */
    private fun showEditDiaryDialog(diary: DiaryEntry) {
        val container: LinearLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DIALOG_HORIZONTAL_PADDING, DIALOG_TOP_PADDING, DIALOG_HORIZONTAL_PADDING, 0)
        }
        val titleInput: EditText = createDiaryEditText("标题", false).apply {
            setText(diary.title)
            setSelection(text.length)
        }
        val contentInput: EditText = createDiaryEditText("日记内容", true).apply {
            setText(diary.content)
        }
        container.addView(titleInput)
        container.addView(contentInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑日记")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                saveEditedDiary(diary, titleInput.text.toString(), contentInput.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 保存编辑后的日记
     */
    private fun saveEditedDiary(diary: DiaryEntry, title: String, content: String) {
        val trimmedTitle: String = title.trim()
        val trimmedContent: String = content.trim()
        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            Snackbar.make(binding.root, "标题和内容不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }
        val updatedDiary: DiaryEntry = diary.copy(
            title = trimmedTitle,
            content = trimmedContent
        )
        viewModel.updateDiaryEntry(contactId, updatedDiary) { isSuccess: Boolean ->
            Snackbar.make(
                binding.root,
                if (isSuccess) "日记已更新" else "日记更新失败",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示删除日记确认弹窗
     */
    private fun showDeleteDiaryDialog(diary: DiaryEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除日记")
            .setMessage("确定要删除《${diary.title}》吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteDiary(diary)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 删除日记
     */
    private fun deleteDiary(diary: DiaryEntry) {
        viewModel.deleteDiaryEntry(contactId, diary.id) { isSuccess: Boolean ->
            Snackbar.make(
                binding.root,
                if (isSuccess) "日记已删除" else "日记删除失败",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 打开分层摘要页面
     */
    private fun openMemorySummary() {
        val fragment = CPhoneMemorySummaryFragment.newInstance(contactId)
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * 打开日记详情页
     */
    private fun openDiaryDetail(diary: DiaryEntry) {
        val fragment = CPhoneDiaryDetailFragment.newInstance(diary)
        
        // 获取当前Fragment所在的容器ID
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * 切换收藏状态
     */
    private fun toggleFavorite(diary: DiaryEntry) {
        val index = diaryList.indexOfFirst { it.id == diary.id }
        if (index != -1) {
            diaryList[index] = diary.copy(isFavorite = !diary.isFavorite)
            adapter.submitList(diaryList.toList())
            
            // TODO: 更新数据库中的收藏状态
        }
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvDiaries.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text =
                "暂无日记\n点击右上角新增按钮记录一篇日记"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvDiaries.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 显示加载中
     */
    private fun showLoading() {
        binding.apply {
            rvDiaries.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 保存RecyclerView的滚动位置
     */
    private fun saveRecyclerViewState() {
        recyclerViewState = binding.rvDiaries.layoutManager?.onSaveInstanceState()
    }
    
    /**
     * 恢复RecyclerView的滚动位置
     */
    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvDiaries.layoutManager?.onRestoreInstanceState(state)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 在Fragment暂停时保存滚动位置
        saveRecyclerViewState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"
        private const val DIALOG_HORIZONTAL_PADDING = 48
        private const val DIALOG_TOP_PADDING = 24
        private const val DIARY_TITLE_MIN_LINES = 1
        private const val DIARY_CONTENT_MIN_LINES = 6

        fun newInstance(contactId: String) = CPhoneDiaryFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}