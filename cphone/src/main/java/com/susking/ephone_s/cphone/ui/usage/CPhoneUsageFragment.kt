package com.susking.ephone_s.cphone.ui.usage

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.aidata.domain.model.AppUsageRecord
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone App使用记录Fragment
 * 显示小手机中各个App的使用时长统计
 */
@AndroidEntryPoint
class CPhoneUsageFragment : Fragment() {

    private lateinit var rvAppUsage: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    
    private val viewModel: CPhoneAppViewModel by viewModels()
    private lateinit var adapter: CPhoneUsageAdapter
    private var contactId: String = ""
    private val usageRecords = mutableListOf<AppUsageRecord>()
    
    companion object {
        private const val TAG = "CPhoneUsageFragment"
        private const val ARG_CONTACT_ID = "contact_id"
        
        /**
         * 创建Fragment实例
         */
        fun newInstance(contactId: String): CPhoneUsageFragment {
            return CPhoneUsageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }

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
    ): View? {
        return inflater.inflate(R.layout.fragment_cphone_app_usage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
    }

    /**
     * 初始化视图
     */
    private fun initializeViews(view: View) {
        rvAppUsage = view.findViewById(R.id.rv_app_usage)
        emptyState = view.findViewById(R.id.empty_state)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        val toolbar = view?.findViewById<MaterialToolbar>(R.id.toolbar)
        
        toolbar?.apply {
            title = "使用记录"
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        
        // 设置刷新按钮点击事件
        view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
            loadUsageData()
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneUsageAdapter(
            onItemClick = { record ->
                onUsageRecordClick(record)
            },
            onItemEdit = { record ->
                handleEditRecord(record)
            },
            onItemDelete = { record ->
                handleDeleteRecord(record)
            }
        )
        
        rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        rvAppUsage.adapter = adapter
    }

    /**
     * 观察使用记录数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAppUsageData(contactId).collect { appUsageRecords ->
                val startTime = SystemClock.elapsedRealtime()
                Log.d(TAG, "收到使用记录数据: ${appUsageRecords.size}条")
                
                usageRecords.clear()
                usageRecords.addAll(appUsageRecords)
                
                val processTime = SystemClock.elapsedRealtime()
                Log.d(TAG, "数据处理耗时: ${processTime - startTime}ms")
                
                if (usageRecords.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent(usageRecords)
                }
                
                val totalTime = SystemClock.elapsedRealtime()
                Log.d(TAG, "总耗时: ${totalTime - startTime}ms")
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
                    if (state.appType == "appUsage") {
                        view?.let {
                            Snackbar.make(it, "使用记录刷新成功", Snackbar.LENGTH_SHORT).show()
                        }
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "appUsage") {
                        view?.let {
                            Snackbar.make(
                                it,
                                "刷新失败: ${state.message}",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                        viewModel.resetRefreshState()
                    }
                }
            }
        }
    }
    
    /**
     * 加载使用数据
     */
    private fun loadUsageData() {
        // 触发刷新来生成数据
        viewModel.refreshAppData(contactId, "appUsage")
    }

    /**
     * 点击使用记录项
     */
    private fun onUsageRecordClick(record: AppUsageRecord) {
        // TODO: 可以跳转到详细使用统计页面
        // 或者直接打开对应的App
    }

    /**
     * 显示加载中状态
     */
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        rvAppUsage.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    /**
     * 显示内容
     */
    private fun showContent(data: List<AppUsageRecord>) {
        val startTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "开始显示内容: ${data.size}条记录")
        
        progressBar.visibility = View.GONE
        rvAppUsage.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        // 创建新的列表副本，让DiffUtil能够正确检测到变化
        adapter.submitList(data.toList())
        
        val endTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "submitList耗时: ${endTime - startTime}ms")
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        progressBar.visibility = View.GONE
        rvAppUsage.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        
        // 设置空状态文本
        emptyState.findViewById<TextView>(R.id.tv_empty_message)?.text = "暂无使用记录"
    }
    
    /**
     * 处理编辑使用记录
     */
    private fun handleEditRecord(record: AppUsageRecord) {
        // TODO: 实现编辑功能
        // 当前暂时显示提示信息
        view?.let {
            Snackbar.make(
                it,
                "编辑功能待实现：${record.appName}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * 处理删除使用记录
     */
    private fun handleDeleteRecord(record: AppUsageRecord) {
        // 显示确认对话框
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除「${record.appName}」的使用记录吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteRecord(record)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行删除使用记录
     */
    private fun deleteRecord(record: AppUsageRecord) {
        viewModel.deleteAppUsageRecord(contactId, record.id)
        
        view?.let {
            Snackbar.make(
                it,
                "已删除：${record.appName}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

}