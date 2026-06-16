package com.susking.ephone_s.cphone.ui.album

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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.ui.photo.PhotoDetailFragment
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneAlbumBinding
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone相册App主界面
 * 显示照片网格列表
 */
@AndroidEntryPoint
class CPhoneAlbumFragment : Fragment() {

    private var _binding: FragmentCphoneAlbumBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneAlbumAdapter
    private var contactId: String = ""
    private val photos = mutableListOf<AlbumPhoto>()
    
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
        _binding = FragmentCphoneAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadPhotos()
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
            title = "相册"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 刷新按钮
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
                refreshPhotos()
            }
            
            // 生成图片按钮（只在相册页面显示）
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_generate_images)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    generateImages()
                }
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneAlbumAdapter { photo, position ->
            openPhotoDetail(position)
        }

        binding.rvPhotos.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@CPhoneAlbumFragment.adapter
        }
    }

    /**
     * 观察相册数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAlbumData(contactId).collect { albumPhotos ->
                photos.clear()
                photos.addAll(albumPhotos)
                
                if (photos.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                    adapter.submitList(photos.toList()) {
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
                    // 空闲状态，不做处理
                }
                is CPhoneAppViewModel.RefreshState.Loading -> {
                    showLoading()
                }
                is CPhoneAppViewModel.RefreshState.Success -> {
                    if (state.appType == "album") {
                        Snackbar.make(binding.root, "相册刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "album") {
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
     * 加载照片数据
     */
    private fun loadPhotos() {
        // 数据通过observeData自动加载
        // 如果数据为空，显示空状态会在observeData中处理
    }

    /**
     * 刷新照片
     * 调用AI接口生成15-30张照片描述(不包含图片)
     */
    private fun refreshPhotos() {
        viewModel.refreshAppData(contactId, "album")
    }
    
    /**
     * 生成照片图片
     * 为所有没有图片的照片生成图片
     */
    private fun generateImages() {
        viewModel.generateAlbumImages(contactId)
        Snackbar.make(
            binding.root,
            "已开始生成照片图片，请在大脑中查看进度",
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * 打开照片详情页
     */
    private fun openPhotoDetail(position: Int) {
        val fragment = PhotoDetailFragment.newInstance(
            contactId = contactId,
            photos = photos,
            initialPosition = position
        )
        
        // 获取当前Fragment所在的容器ID
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvPhotos.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text =
                "暂无照片\n点击刷新生成AI相册"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvPhotos.visibility = View.VISIBLE
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
            rvPhotos.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 保存RecyclerView的滚动位置
     */
    private fun saveRecyclerViewState() {
        recyclerViewState = binding.rvPhotos.layoutManager?.onSaveInstanceState()
    }
    
    /**
     * 恢复RecyclerView的滚动位置
     */
    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvPhotos.layoutManager?.onRestoreInstanceState(state)
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

        fun newInstance(contactId: String) = CPhoneAlbumFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}