package com.susking.ephone_s.shopping.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.shopping.databinding.DialogVariationSelectionBinding

/**
 * 款式选择对话框
 * 
 * 用于在添加商品到购物车时选择款式
 */
class VariationSelectionDialogFragment : DialogFragment() {
    
    private var _binding: DialogVariationSelectionBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var product: ShoppingProduct
    private var onVariationSelected: ((ProductVariation?) -> Unit)? = null
    
    private lateinit var variationAdapter: VariationSelectionAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVariationSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDialog()
        setupRecyclerView()
        setupButtons()
        loadData()
    }
    
    /**
     * 设置对话框
     */
    private fun setupDialog() {
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        variationAdapter = VariationSelectionAdapter { variation ->
            // 选中款式后自动关闭对话框
            onVariationSelected?.invoke(variation)
            dismiss()
        }
        
        binding.recyclerViewVariations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = variationAdapter
        }
    }
    
    /**
     * 设置按钮
     */
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonSkip.setOnClickListener {
            // 不选择款式,直接添加商品
            onVariationSelected?.invoke(null)
            dismiss()
        }
    }
    
    /**
     * 加载数据
     */
    private fun loadData() {
        if (!::product.isInitialized) {
            dismiss()
            return
        }
        
        binding.textViewProductName.text = product.name
        
        if (product.variations.isEmpty()) {
            // 没有款式,直接添加商品
            onVariationSelected?.invoke(null)
            dismiss()
        } else {
            variationAdapter.submitList(product.variations)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val TAG = "VariationSelectionDialog"
        
        /**
         * 显示对话框
         */
        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            product: ShoppingProduct,
            onVariationSelected: (ProductVariation?) -> Unit
        ) {
            val dialog = VariationSelectionDialogFragment()
            dialog.product = product
            dialog.onVariationSelected = onVariationSelected
            dialog.show(fragmentManager, TAG)
        }
    }
}