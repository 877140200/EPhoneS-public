package com.susking.ephone_s.cphone.ui.memo

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneMemoBinding
import com.susking.ephone_s.aidata.domain.model.Memo
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone备忘录App主界面
 * 显示备忘录列表
 */
@AndroidEntryPoint
class CPhoneMemoFragment : Fragment() {

    private var _binding: FragmentCphoneMemoBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneMemoAdapter
    private var contactId: String = ""
    private val memoList = mutableListOf<Memo>()
    
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
        _binding = FragmentCphoneMemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadMemos()
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
            title = "备忘录"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 刷新按钮
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
                refreshMemos()
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneMemoAdapter(
            onMemoClick = { memo ->
                openMemoDetail(memo)
            },
            onFavoriteClick = { memo ->
                toggleFavorite(memo)
            }
        )

        binding.rvMemos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneMemoFragment.adapter
        }
    }

    /**
     * 观察备忘录数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMemoData(contactId).collect { memos ->
                memoList.clear()
                memoList.addAll(memos)
                
                if (memoList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(memoList.toList()) {
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
                    if (state.appType == "memo") {
                        Snackbar.make(binding.root, "备忘录刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "memo") {
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
     * 加载备忘录
     */
    private fun loadMemos() {
        // 数据通过observeData自动加载
    }

    /**
     * 刷新备忘录
     * 调用AI接口生成12-20条备忘录
     */
    private fun refreshMemos() {
        viewModel.refreshAppData(contactId, "memo")
    }

    /**
     * 打开备忘录详情页
     */
    private fun openMemoDetail(memo: Memo) {
        val fragment = CPhoneMemoDetailFragment.newInstance(memo)
        
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
    private fun toggleFavorite(memo: Memo) {
        val index = memoList.indexOfFirst { it.id == memo.id }
        if (index != -1) {
            memoList[index] = memo.copy(isFavorite = !memo.isFavorite)
            adapter.submitList(memoList.toList())
            
            // TODO: 更新数据库中的收藏状态
        }
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvMemos.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text = "暂无备忘录"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvMemos.visibility = View.VISIBLE
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
            rvMemos.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 保存RecyclerView的滚动位置
     */
    private fun saveRecyclerViewState() {
        recyclerViewState = binding.rvMemos.layoutManager?.onSaveInstanceState()
    }
    
    /**
     * 恢复RecyclerView的滚动位置
     */
    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvMemos.layoutManager?.onRestoreInstanceState(state)
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

        fun newInstance(contactId: String) = CPhoneMemoFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}