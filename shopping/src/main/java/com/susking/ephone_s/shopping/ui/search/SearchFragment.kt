package com.susking.ephone_s.shopping.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.susking.ephone_s.shopping.databinding.FragmentSearchBinding
import com.susking.ephone_s.shopping.navigation.ShoppingNavigatorImpl
import com.susking.ephone_s.shopping.ui.main.ProductListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 搜索Fragment
 * 
 * 提供商品搜索功能
 */
@AndroidEntryPoint
class SearchFragment : Fragment() {
    
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SearchViewModel by viewModels()
    
    private lateinit var productAdapter: ProductListAdapter
    
    private val navigator: ShoppingNavigatorImpl by lazy {
        ShoppingNavigatorImpl(this)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupSearchView()
        setupRecyclerView()
        observeViewModel()
    }
    
    /**
     * 设置Toolbar
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }
    
    /**
     * 设置SearchView
     */
    private fun setupSearchView() {
        binding.searchView.apply {
            // 设置搜索框展开
            isIconified = false
            
            // 监听搜索文本变化
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { viewModel.updateSearchQuery(it) }
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { viewModel.updateSearchQuery(it) }
                    return true
                }
            })
            
            // 请求焦点
            requestFocus()
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        productAdapter = ProductListAdapter(
            onProductClick = { product ->
                viewModel.openProductDetail(product.id)
            }
        )
        
        binding.recyclerViewResults.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察搜索结果
                launch {
                    viewModel.searchResults.collect { products ->
                        productAdapter.submitList(products)
                        
                        // 显示/隐藏空状态
                        if (products.isEmpty()) {
                            val query = viewModel.searchQuery.value
                            if (query.isNullOrBlank()) {
                                binding.textViewEmpty.text = "请输入关键词搜索商品"
                            } else {
                                binding.textViewEmpty.text = "未找到相关商品"
                            }
                            binding.textViewEmpty.visibility = View.VISIBLE
                            binding.recyclerViewResults.visibility = View.GONE
                        } else {
                            binding.textViewEmpty.visibility = View.GONE
                            binding.recyclerViewResults.visibility = View.VISIBLE
                        }
                    }
                }
                
                // 观察加载状态
                launch {
                    viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
                        binding.progressBar.visibility = if (isLoading) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                
                // 观察导航事件
                launch {
                    viewModel.navigationEvent.collect { event ->
                        event?.let { handleNavigationEvent(it) }
                    }
                }
            }
        }
    }
    
    /**
     * 处理导航事件
     */
    private fun handleNavigationEvent(event: SearchViewModel.NavigationEvent) {
        when (event) {
            is SearchViewModel.NavigationEvent.ToProductDetail -> {
                val fragment = navigator.navigateToProductDetail(event.productId)
                navigator.navigate(fragment)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = SearchFragment()
    }
}