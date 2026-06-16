package com.susking.ephone_s.shopping.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.FragmentProductDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 商品详情Fragment
 *
 * 展示商品详细信息,支持选择款式和加入购物车
 */
@AndroidEntryPoint
class ProductDetailFragment : Fragment() {
    
    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProductDetailViewModel by viewModels()
    
    private val productId: Long by lazy {
        arguments?.getLong(ARG_PRODUCT_ID) ?: throw IllegalArgumentException("Product ID required")
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadProduct(productId)
        
        setupButtons()
        observeViewModel()
    }

    /**
     * 设置按钮
     */
    private fun setupButtons() {
        // 加入购物车按钮
        binding.buttonAddToCart.setOnClickListener {
            viewModel.addToCart()
        }
    }
    
    /**
     * 观察ViewModel状态
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察商品信息
                launch {
                    viewModel.product.collect { product ->
                        product?.let { displayProduct(it) }
                    }
                }
                
                // 观察选中的款式
                launch {
                    viewModel.selectedVariationIndex.collect { index ->
                        updateVariationSelection(index)
                    }
                }
                
                // 观察数量
                launch {
                    viewModel.quantity.collect { quantity ->
                        binding.textViewQuantity.text = quantity.toString()
                    }
                }
                
                // 观察加入购物车结果
                launch {
                    viewModel.addToCartResult.collect { result ->
                        result?.let {
                            if (it) {
                                Snackbar.make(
                                    binding.root,
                                    "已加入购物车",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            } else {
                                Snackbar.make(
                                    binding.root,
                                    "加入购物车失败",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 显示商品信息
     */
    private fun displayProduct(product: com.susking.ephone_s.aidata.domain.model.ShoppingProduct) {
        binding.apply {
            // 商品图片
            imageViewProduct.load(product.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_error)
            }
            
            // 商品名称
            textViewProductName.text = product.name
            
            // 商品价格
            textViewPrice.text = String.format("¥%.2f", product.price)
            
            // 商品描述
            textViewDescription.text = product.description
            
            // 款式选择
            if (product.variations.isNotEmpty()) {
                chipGroupVariations.removeAllViews()
                product.variations.forEachIndexed { index, variation ->
                    val chip = Chip(requireContext()).apply {
                        text = "${variation.name}: ¥${String.format("%.2f", variation.price)}"
                        isCheckable = true
                        setOnClickListener {
                            // 如果点击的是已选中的款式，取消选择；否则选择该款式
                            val currentSelected = viewModel.selectedVariationIndex.value
                            if (currentSelected == index) {
                                viewModel.selectVariation(null)
                            } else {
                                viewModel.selectVariation(index)
                            }
                        }
                    }
                    chipGroupVariations.addView(chip)
                }
                layoutVariations.visibility = View.VISIBLE
            } else {
                layoutVariations.visibility = View.GONE
            }
            
            // 数量调整按钮
            buttonDecrease.setOnClickListener {
                viewModel.decreaseQuantity()
            }
            
            buttonIncrease.setOnClickListener {
                viewModel.increaseQuantity()
            }
        }
    }
    
    /**
     * 更新款式选择状态
     */
    private fun updateVariationSelection(selectedIndex: Int?) {
        // 更新Chip选中状态
        for (i in 0 until binding.chipGroupVariations.childCount) {
            val chip = binding.chipGroupVariations.getChildAt(i) as? Chip
            chip?.isChecked = (i == selectedIndex)
        }
        
        // 更新商品图片
        val currentProduct = viewModel.product.value ?: return
        val imageUrl = if (selectedIndex != null && selectedIndex < currentProduct.variations.size) {
            // 如果款式有自己的图片，使用款式图片，否则使用商品主图
            currentProduct.variations[selectedIndex].imageUrl ?: currentProduct.imageUrl
        } else {
            // 没有选择款式时，使用商品主图
            currentProduct.imageUrl
        }
        
        // 加载图片
        binding.imageViewProduct.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_error)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        
        fun newInstance(productId: Long) = ProductDetailFragment().apply {
            arguments = bundleOf(ARG_PRODUCT_ID to productId)
        }
    }
}