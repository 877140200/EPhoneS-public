package com.susking.ephone_s.qq.ui.forward

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.susking.ephone_s.qq.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 通用转发选择器Fragment
 * 
 * 用于选择联系人进行转发、分享、发送申请等操作
 * 
 * 使用方式：
 * ```
 * val fragment = ForwardSelectorFragment.newInstance(
 *     contentType = "shopping_request",
 *     contentId = "xxx",
 *     callback = object : ForwardCallback {
 *         override fun onContactsSelected(contactIds: List<String>, contentType: String?, contentId: String?) {
 *             // 处理选中的联系人
 *         }
 *     }
 * )
 * ```
 */
@AndroidEntryPoint
class ForwardSelectorFragment : Fragment() {

    private val viewModel: ForwardSelectorViewModel by viewModels()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonClose: TextView
    private lateinit var buttonMultiSelect: TextView
    private lateinit var editTextSearch: EditText
    private lateinit var recyclerViewContacts: RecyclerView
    private lateinit var textViewEmpty: TextView

    private lateinit var contactAdapter: ForwardContactAdapter

    /**
     * 回调接口
     */
    private var callback: ForwardCallback? = null

    /**
     * 内容类型（如：shopping_request, message_forward等）
     */
    private var contentType: String? = null

    /**
     * 内容ID
     */
    private var contentId: String? = null

    companion object {
        private const val ARG_CONTENT_TYPE = "content_type"
        private const val ARG_CONTENT_ID = "content_id"

        /**
         * 创建ForwardSelectorFragment实例
         * 
         * @param contentType 内容类型
         * @param contentId 内容ID
         * @param callback 回调接口
         */
        fun newInstance(
            contentType: String? = null,
            contentId: String? = null,
            callback: ForwardCallback
        ): ForwardSelectorFragment {
            return ForwardSelectorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTENT_TYPE, contentType)
                    putString(ARG_CONTENT_ID, contentId)
                }
                this.callback = callback
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contentType = it.getString(ARG_CONTENT_TYPE)
            contentId = it.getString(ARG_CONTENT_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_forward_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupWindowInsets(view)
        setupToolbar()
        setupSearchBox()
        setupRecyclerView()
        observeViewModel()
    }

    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        buttonClose = view.findViewById(R.id.buttonClose)
        buttonMultiSelect = view.findViewById(R.id.buttonMultiSelect)
        editTextSearch = view.findViewById(R.id.editTextSearch)
        recyclerViewContacts = view.findViewById(R.id.recyclerViewContacts)
        textViewEmpty = view.findViewById(R.id.textViewEmpty)
    }

    /**
     * 设置窗口边距适配
     */
    private fun setupWindowInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 给Toolbar添加顶部内边距，适配状态栏
            val layoutParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = systemBars.top
            toolbar.layoutParams = layoutParams
            
            insets
        }
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        // 关闭按钮
        buttonClose.setOnClickListener {
            callback?.onCancelled()
            parentFragmentManager.popBackStack()
        }

        // 多选/发送按钮
        buttonMultiSelect.setOnClickListener {
            if (viewModel.isMultiSelectMode.value) {
                // 当前是多选模式，点击后发送
                handleSendAction()
            } else {
                // 切换到多选模式
                viewModel.toggleMultiSelectMode()
            }
        }
    }

    /**
     * 设置搜索框
     */
    private fun setupSearchBox() {
        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchContacts(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        contactAdapter = ForwardContactAdapter { contact ->
            if (!viewModel.isMultiSelectMode.value) {
                // 单选模式：直接发送
                callback?.onContactsSelected(
                    contactIds = listOf(contact.id),
                    contentType = contentType,
                    contentId = contentId
                )
                parentFragmentManager.popBackStack()
            } else {
                // 多选模式：更新选中数量显示
                updateMultiSelectButton()
            }
        }

        recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
        }
    }

    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察联系人列表
                launch {
                    viewModel.displayedContacts.collect { contacts ->
                        contactAdapter.submitList(contacts)
                        // 显示/隐藏空状态提示
                        textViewEmpty.visibility = if (contacts.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }

                // 观察多选模式状态
                launch {
                    viewModel.isMultiSelectMode.collect { isMultiSelect ->
                        contactAdapter.isMultiSelectMode = isMultiSelect
                        updateMultiSelectButton()
                    }
                }
            }
        }
    }

    /**
     * 更新多选按钮文本
     */
    private fun updateMultiSelectButton() {
        if (viewModel.isMultiSelectMode.value) {
            val selectedCount = contactAdapter.getSelectedContactIds().size
            buttonMultiSelect.text = if (selectedCount > 0) {
                "发送($selectedCount)"
            } else {
                "发送"
            }
        } else {
            buttonMultiSelect.text = "多选"
        }
    }

    /**
     * 处理发送操作
     */
    private fun handleSendAction() {
        val selectedIds = contactAdapter.getSelectedContactIds()
        if (selectedIds.isEmpty()) {
            Toast.makeText(requireContext(), "请至少选择一个联系人", Toast.LENGTH_SHORT).show()
            return
        }

        // 调用回调
        callback?.onContactsSelected(
            contactIds = selectedIds,
            contentType = contentType,
            contentId = contentId
        )

        // 关闭页面
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        callback = null
    }
}