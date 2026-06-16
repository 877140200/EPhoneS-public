package com.susking.ephone_s.shopping.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.FragmentProductEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 商品编辑器Fragment
 * 
 * 用于创建或编辑商品
 */
@AndroidEntryPoint
class ProductEditorFragment : Fragment() {
    
    private var _binding: FragmentProductEditorBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProductEditorViewModel by viewModels()
    
    private lateinit var variationAdapter: VariationEditorAdapter
    
    private var categoryAdapter: ArrayAdapter<String>? = null
    private val categoryMap = mutableMapOf<String, ShoppingCategory>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductEditorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        
        // 检查是否是编辑模式
        val productId = arguments?.getLong(ARG_PRODUCT_ID, -1L) ?: -1L
        if (productId != -1L) {
            viewModel.initEditMode(productId)
        } else {
            viewModel.initCreateMode()
        }
    }
    
    /**
     * 设置Toolbar
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        
        // 根据模式设置标题
        viewModel.isEditMode.observe(viewLifecycleOwner) { isEdit ->
            binding.toolbar.title = if (isEdit) "编辑商品" else "创建商品"
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        variationAdapter = VariationEditorAdapter(
            onVariationChanged = { index, variation ->
                viewModel.updateVariation(index, variation)
            },
            onVariationDeleted = { index ->
                showDeleteVariationDialog(index)
            }
        )
        
        binding.recyclerViewVariations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = variationAdapter
        }
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 选择图片
        binding.fabSelectImage.setOnClickListener {
            showImageInputDialog()
        }
        
        // 添加款式
        binding.buttonAddVariation.setOnClickListener {
            showAddVariationDialog()
        }
        
        // 保存商品
        binding.buttonSave.setOnClickListener {
            viewModel.saveProduct()
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        // 商品名称
        viewModel.productName.observe(viewLifecycleOwner) { name ->
            if (binding.editTextName.text.toString() != name) {
                binding.editTextName.setText(name)
            }
        }
        
        // 商品价格
        viewModel.productPrice.observe(viewLifecycleOwner) { price ->
            if (binding.editTextPrice.text.toString() != price) {
                binding.editTextPrice.setText(price)
            }
        }
        
        // 商品描述
        viewModel.productDescription.observe(viewLifecycleOwner) { description ->
            if (binding.editTextDescription.text.toString() != description) {
                binding.editTextDescription.setText(description)
            }
        }
        
        // 商品图片
        viewModel.productImageUrl.observe(viewLifecycleOwner) { imageUrl ->
            if (!imageUrl.isNullOrBlank()) {
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.imageViewProduct)
            } else {
                binding.imageViewProduct.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
        
        // 分类列表
        lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                updateCategoryAdapter(categories)
            }
        }
        
        // 选中的分类
        viewModel.selectedCategory.observe(viewLifecycleOwner) { category ->
            if (category != null) {
                binding.autoCompleteCategory.setText(category.name, false)
            } else {
                binding.autoCompleteCategory.setText("", false)
            }
        }
        
        // 款式列表
        viewModel.variations.observe(viewLifecycleOwner) { variations ->
            variationAdapter.submitList(variations)
        }
        
        // UI事件
        viewModel.uiEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                is ProductEditorViewModel.UiEvent.ShowError -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
                is ProductEditorViewModel.UiEvent.SaveSuccess -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressed()
                }
            }
        }
        
        // 加载状态
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.buttonSave.isEnabled = !isLoading
            // TODO: 显示加载进度
        }
        
        // 监听输入变化
        binding.editTextName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateProductName(binding.editTextName.text.toString())
            }
        }
        
        binding.editTextPrice.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateProductPrice(binding.editTextPrice.text.toString())
            }
        }
        
        binding.editTextDescription.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateProductDescription(binding.editTextDescription.text.toString())
            }
        }
    }
    
    /**
     * 更新分类适配器
     */
    private fun updateCategoryAdapter(categories: List<ShoppingCategory>) {
        categoryMap.clear()
        val categoryNames = mutableListOf("未分类")
        
        categories.forEach { category ->
            categoryNames.add(category.name)
            categoryMap[category.name] = category
        }
        
        categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categoryNames
        )
        
        binding.autoCompleteCategory.setAdapter(categoryAdapter)
        binding.autoCompleteCategory.setOnItemClickListener { _, _, position, _ ->
            val selectedName = categoryNames[position]
            val category = categoryMap[selectedName]
            viewModel.selectCategory(category)
        }
    }
    
    /**
     * 显示图片输入对话框
     */
    private fun showImageInputDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "输入图片URL"
        input.setText(viewModel.productImageUrl.value)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("商品图片")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val url = input.text.toString().trim()
                viewModel.updateProductImageUrl(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示添加款式对话框
     */
    private fun showAddVariationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_variation, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextVariationName)
        val priceInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextVariationPrice)
        val imageUrlInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTextVariationImageUrl)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加款式")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.toString().trim()
                val priceStr = priceInput.text.toString().trim()
                val imageUrl = imageUrlInput.text.toString().trim()
                
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "请输入款式名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val price = priceStr.toDoubleOrNull()
                if (price == null || price <= 0) {
                    Toast.makeText(requireContext(), "请输入有效的价格", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val variation = ProductVariation(
                    name = name,
                    price = price,
                    imageUrl = imageUrl.ifBlank { null }
                )
                
                viewModel.addVariation(variation)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示删除款式确认对话框
     */
    private fun showDeleteVariationDialog(index: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除款式")
            .setMessage("确定要删除这个款式吗?")
            .setPositiveButton("删除") { _, _ ->
                viewModel.removeVariation(index)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        
        /**
         * 创建新实例(创建模式)
         */
        fun newInstance(): ProductEditorFragment {
            return ProductEditorFragment()
        }
        
        /**
         * 创建新实例(编辑模式)
         */
        fun newInstance(productId: Long): ProductEditorFragment {
            return ProductEditorFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PRODUCT_ID, productId)
                }
            }
        }
    }
}