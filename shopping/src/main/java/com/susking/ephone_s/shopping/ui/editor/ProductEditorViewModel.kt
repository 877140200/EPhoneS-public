package com.susking.ephone_s.shopping.ui.editor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.aidata.domain.repository.ShoppingCategoryRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 商品编辑器ViewModel
 * 
 * 负责商品的创建和编辑逻辑
 */
@HiltViewModel
class ProductEditorViewModel @Inject constructor(
    private val productRepository: ShoppingProductRepository,
    private val categoryRepository: ShoppingCategoryRepository
) : ViewModel() {
    
    // 编辑模式(true=编辑现有商品, false=创建新商品)
    private val _isEditMode = MutableLiveData(false)
    val isEditMode: LiveData<Boolean> = _isEditMode
    
    // 当前编辑的商品ID(仅编辑模式有效)
    private var currentProductId: Long? = null
    
    // 商品名称
    private val _productName = MutableLiveData("")
    val productName: LiveData<String> = _productName
    
    // 商品价格
    private val _productPrice = MutableLiveData("")
    val productPrice: LiveData<String> = _productPrice
    
    // 商品描述
    private val _productDescription = MutableLiveData("")
    val productDescription: LiveData<String> = _productDescription
    
    // 商品图片URL
    private val _productImageUrl = MutableLiveData("")
    val productImageUrl: LiveData<String> = _productImageUrl
    
    // 选中的分类
    private val _selectedCategory = MutableLiveData<ShoppingCategory?>(null)
    val selectedCategory: LiveData<ShoppingCategory?> = _selectedCategory
    
    // 商品款式列表
    private val _variations = MutableLiveData<List<ProductVariation>>(emptyList())
    val variations: LiveData<List<ProductVariation>> = _variations
    
    // 所有分类列表
    private val _categories = MutableStateFlow<List<ShoppingCategory>>(emptyList())
    val categories: StateFlow<List<ShoppingCategory>> = _categories.asStateFlow()
    
    // UI事件
    private val _uiEvent = MutableLiveData<UiEvent>()
    val uiEvent: LiveData<UiEvent> = _uiEvent
    
    // 加载状态
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        loadCategories()
    }
    
    /**
     * 加载所有分类
     */
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categoriesList ->
                _categories.value = categoriesList
            }
        }
    }
    
    /**
     * 初始化为创建模式
     */
    fun initCreateMode() {
        _isEditMode.value = false
        currentProductId = null
        clearForm()
    }
    
    /**
     * 初始化为编辑模式
     * @param productId 要编辑的商品ID
     */
    fun initEditMode(productId: Long) {
        _isEditMode.value = true
        currentProductId = productId
        loadProduct(productId)
    }
    
    /**
     * 加载商品数据
     */
    private fun loadProduct(productId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val product = productRepository.getProductById(productId)
                if (product != null) {
                    _productName.value = product.name
                    _productPrice.value = product.price.toString()
                    _productDescription.value = product.description
                    _productImageUrl.value = product.imageUrl
                    _variations.value = product.variations
                    
                    // 加载分类
                    val catId = product.categoryId
                    if (catId != null) {
                        val category = categoryRepository.getCategoryById(catId)
                        _selectedCategory.value = category
                    }
                } else {
                    _uiEvent.value = UiEvent.ShowError("商品不存在")
                }
            } catch (e: Exception) {
                _uiEvent.value = UiEvent.ShowError("加载商品失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 更新商品名称
     */
    fun updateProductName(name: String) {
        _productName.value = name
    }
    
    /**
     * 更新商品价格
     */
    fun updateProductPrice(price: String) {
        _productPrice.value = price
    }
    
    /**
     * 更新商品描述
     */
    fun updateProductDescription(description: String) {
        _productDescription.value = description
    }
    
    /**
     * 更新商品图片URL
     */
    fun updateProductImageUrl(imageUrl: String) {
        _productImageUrl.value = imageUrl
    }
    
    /**
     * 选择分类
     */
    fun selectCategory(category: ShoppingCategory?) {
        _selectedCategory.value = category
    }
    
    /**
     * 添加款式
     */
    fun addVariation(variation: ProductVariation) {
        val currentList = _variations.value.orEmpty().toMutableList()
        currentList.add(variation)
        _variations.value = currentList
    }
    
    /**
     * 更新款式
     */
    fun updateVariation(index: Int, variation: ProductVariation) {
        val currentList = _variations.value.orEmpty().toMutableList()
        if (index in currentList.indices) {
            currentList[index] = variation
            _variations.value = currentList
        }
    }
    
    /**
     * 删除款式
     */
    fun removeVariation(index: Int) {
        val currentList = _variations.value.orEmpty().toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _variations.value = currentList
        }
    }
    
    /**
     * 保存商品
     */
    fun saveProduct() {
        // 验证输入
        val name = _productName.value?.trim()
        if (name.isNullOrBlank()) {
            _uiEvent.value = UiEvent.ShowError("请输入商品名称")
            return
        }
        
        val priceStr = _productPrice.value?.trim()
        if (priceStr.isNullOrBlank()) {
            _uiEvent.value = UiEvent.ShowError("请输入商品价格")
            return
        }
        
        val price = priceStr.toDoubleOrNull()
        if (price == null || price <= 0) {
            _uiEvent.value = UiEvent.ShowError("请输入有效的价格")
            return
        }
        
        val description = _productDescription.value?.trim() ?: ""
        val imageUrl = _productImageUrl.value?.trim() ?: ""
        val categoryId = _selectedCategory.value?.id
        val variationsList = _variations.value.orEmpty()
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (_isEditMode.value == true && currentProductId != null) {
                    // 编辑模式:更新商品
                    val product = ShoppingProduct(
                        id = currentProductId!!,
                        name = name,
                        price = price,
                        description = description,
                        imageUrl = imageUrl,
                        categoryId = categoryId,
                        variations = variationsList,
                        timestamp = System.currentTimeMillis()
                    )
                    productRepository.updateProduct(product)
                    _uiEvent.value = UiEvent.SaveSuccess("商品已更新")
                } else {
                    // 创建模式:新建商品
                    productRepository.createProduct(
                        name = name,
                        price = price,
                        description = description,
                        imageUrl = imageUrl,
                        categoryId = categoryId,
                        variations = variationsList
                    )
                    _uiEvent.value = UiEvent.SaveSuccess("商品已创建")
                }
            } catch (e: Exception) {
                _uiEvent.value = UiEvent.ShowError("保存失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 清空表单
     */
    private fun clearForm() {
        _productName.value = ""
        _productPrice.value = ""
        _productDescription.value = ""
        _productImageUrl.value = ""
        _selectedCategory.value = null
        _variations.value = emptyList()
    }
    
    /**
     * UI事件
     */
    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        data class SaveSuccess(val message: String) : UiEvent()
    }
}