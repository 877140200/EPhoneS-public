package com.susking.ephone_s.cphone.ui.amap

import android.os.Bundle
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
import com.susking.ephone_s.cphone.databinding.FragmentCphoneAmapBinding
import com.susking.ephone_s.aidata.domain.model.AmapFootprint
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone高德地图App主界面
 * 显示足迹时间轴列表
 */
@AndroidEntryPoint
class CPhoneAmapFragment : Fragment() {

    private var _binding: FragmentCphoneAmapBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneAmapAdapter
    private var contactId: String = ""
    private val footprintList = mutableListOf<AmapFootprint>()

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
        _binding = FragmentCphoneAmapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadFootprints()
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
            title = "高德地图"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 刷新按钮
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
                refreshFootprints()
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneAmapAdapter(
            onFootprintClick = { footprint ->
                // TODO: 点击足迹后可以显示详情或在地图上查看
                // 当前暂不实现详情页
            },
            onFootprintEdit = { footprint ->
                handleEditFootprint(footprint)
            },
            onFootprintDelete = { footprint ->
                handleDeleteFootprint(footprint)
            }
        )

        binding.rvFootprints.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneAmapFragment.adapter
        }
    }

    /**
     * 观察地图足迹数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAmapData(contactId).collect { amapFootprints ->
                footprintList.clear()
                footprintList.addAll(amapFootprints)
                
                if (footprintList.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(footprintList.toList())
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
                    if (state.appType == "amap") {
                        Snackbar.make(binding.root, "足迹记录刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "amap") {
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
     * 加载足迹
     */
    private fun loadFootprints() {
        // 数据通过observeData自动加载
    }

    /**
     * 刷新足迹
     * 调用AI接口生成10-20条足迹记录
     */
    private fun refreshFootprints() {
        viewModel.refreshAppData(contactId, "amap")
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvFootprints.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text = 
                "暂无足迹记录\n点击刷新生成AI足迹"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvFootprints.visibility = View.VISIBLE
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
            rvFootprints.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }
    
    /**
     * 处理编辑足迹
     */
    private fun handleEditFootprint(footprint: AmapFootprint) {
        // TODO: 实现编辑功能
        // 当前暂时显示提示信息
        Snackbar.make(
            binding.root,
            "编辑功能待实现：${footprint.locationName}",
            Snackbar.LENGTH_SHORT
        ).show()
    }
    
    /**
     * 处理删除足迹
     */
    private fun handleDeleteFootprint(footprint: AmapFootprint) {
        // 显示确认对话框
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除足迹「${footprint.locationName}」吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteFootprint(footprint)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行删除足迹
     */
    private fun deleteFootprint(footprint: AmapFootprint) {
        viewModel.deleteAmapFootprint(contactId, footprint.id)
        
        Snackbar.make(
            binding.root,
            "已删除：${footprint.locationName}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String) = CPhoneAmapFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}