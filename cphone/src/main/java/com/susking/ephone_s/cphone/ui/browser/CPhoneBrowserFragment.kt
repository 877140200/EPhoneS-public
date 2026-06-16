package com.susking.ephone_s.cphone.ui.browser

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneBrowserBinding
import com.susking.ephone_s.aidata.domain.model.BrowserRecord
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone浏览器App主界面
 * 显示浏览历史记录列表
 */
@AndroidEntryPoint
class CPhoneBrowserFragment : Fragment() {

    private var _binding: FragmentCphoneBrowserBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneBrowserAdapter
    private var contactId: String = ""
    private val historyList = mutableListOf<BrowserRecord>()
    
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
        _binding = FragmentCphoneBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadHistory()
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
            title = "浏览器"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 刷新按钮
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
                refreshHistory()
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneBrowserAdapter(
            onHistoryClick = { history ->
                openArticleDetail(history)
            },
            onFavoriteClick = { history ->
                toggleFavorite(history)
            },
            onHistoryEdit = { history ->
                handleEditHistory(history)
            },
            onHistoryDelete = { history ->
                handleDeleteHistory(history)
            }
        )

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneBrowserFragment.adapter
        }
    }

    /**
     * 观察浏览器数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getBrowserData(contactId).collect { browserRecords ->
                historyList.clear()
                historyList.addAll(browserRecords.map {
                    BrowserRecord(
                        id = it.id,
                        title = it.title,
                        url = it.url,
                        content = it.content,
                        isFavorite = it.isFavorite,
                        timestamp = it.timestamp
                    )
                })

                if (historyList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(historyList.toList()) {
                        // 数据更新完成后恢复滚动位置
                        restoreRecyclerViewState()
                    }
                }
            }
        }
    }

    /**
     * 观察刷新状态
     */
    private fun observeRefreshState() {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CPhoneAppViewModel.RefreshState.Idle -> {
                    // 空闲状态
                }
                is CPhoneAppViewModel.RefreshState.Loading -> {
                    showLoading()
                }
                is CPhoneAppViewModel.RefreshState.Success -> {
                    if (state.appType == "browser") {
                        Snackbar.make(binding.root, "浏览历史刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "browser") {
                        showContent()
                        Snackbar.make(
                            binding.root,
                            "刷新失败: ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                        viewModel.resetRefreshState()
                    }
                }
            }
        }
    }

    /**
     * 加载浏览历史
     */
    private fun loadHistory() {
        // 数据通过observeData自动加载
    }

    /**
     * 刷新浏览历史
     * 调用AI接口生成10-20条浏览记录
     */
    private fun refreshHistory() {
        viewModel.refreshAppData(contactId, "browser")
    }

    /**
     * 打开文章详情页
     */
    private fun openArticleDetail(history: BrowserRecord) {
        val fragment = CPhoneArticleDetailFragment.newInstance(history)

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
    private fun toggleFavorite(history: BrowserRecord) {
        val index = historyList.indexOfFirst { it.id == history.id }
        if (index != -1) {
            historyList[index] = history.copy(isFavorite = !history.isFavorite)
            adapter.submitList(historyList.toList())

            // TODO: 更新数据库中的收藏状态
        }
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvHistory.visibility = View.GONE
            progressBar.visibility = View.GONE

            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE

            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text = "暂无浏览记录"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvHistory.visibility = View.VISIBLE
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
            rvHistory.visibility = View.GONE
            progressBar.visibility = View.VISIBLE

            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 处理编辑历史记录
     */
    private fun handleEditHistory(history: BrowserRecord) {
        // TODO: 实现编辑功能
        // 当前暂时显示提示信息
        Snackbar.make(
            binding.root,
            "编辑功能待实现：${history.title}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * 处理删除历史记录
     */
    private fun handleDeleteHistory(history: BrowserRecord) {
        // 显示确认对话框
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除浏览记录「${history.title}」吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteHistory(history)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行删除历史记录
     */
    private fun deleteHistory(history: BrowserRecord) {
        viewModel.deleteBrowserHistory(contactId, history.id)
        
        Snackbar.make(
            binding.root,
            "已删除：${history.title}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * 保存RecyclerView的滚动位置
     */
    private fun saveRecyclerViewState() {
        recyclerViewState = binding.rvHistory.layoutManager?.onSaveInstanceState()
    }
    
    /**
     * 恢复RecyclerView的滚动位置
     */
    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvHistory.layoutManager?.onRestoreInstanceState(state)
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

        fun newInstance(contactId: String) = CPhoneBrowserFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}