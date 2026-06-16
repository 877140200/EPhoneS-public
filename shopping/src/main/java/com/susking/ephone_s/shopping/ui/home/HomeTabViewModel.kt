package com.susking.ephone_s.shopping.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingCategoryRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import com.susking.ephone_s.aidata.domain.use_case.GenerateShoppingProductUseCase
import com.susking.ephone_s.aidata.domain.use_case.GenerateImagesForProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页Tab ViewModel
 *
 * 管理首页的状态和业务逻辑
 */
@HiltViewModel
class HomeTabViewModel @Inject constructor(
    private val categoryRepository: ShoppingCategoryRepository,
    private val productRepository: ShoppingProductRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val authorizedAccountRepository: com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository,
    private val chatRepository: com.susking.ephone_s.aidata.domain.repository.ChatRepository,
    private val generateShoppingProductUseCase: GenerateShoppingProductUseCase,
    private val generateImagesForProductsUseCase: GenerateImagesForProductsUseCase,
    private val imagePreloadService: com.susking.ephone_s.aidata.domain.service.ImagePreloadService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("shopping_prefs", Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val KEY_CURRENT_CONTACT_ID = "current_contact_id"
    }
    
    // 导航事件
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    // 分类列表（包含"全部"分类）
    private val _categories = MutableStateFlow<List<ShoppingCategory>>(emptyList())
    val categories: StateFlow<List<ShoppingCategory>> = _categories.asStateFlow()
    
    // 商品列表
    private val _products = MutableStateFlow<List<ShoppingProduct>>(emptyList())
    val products: StateFlow<List<ShoppingProduct>> = _products.asStateFlow()
    
    // 当前选中的分类ID（-1L表示"全部"）
    private val _selectedCategoryId = MutableStateFlow(-1L)
    val selectedCategoryId: StateFlow<Long> = _selectedCategoryId.asStateFlow()
    
    // 搜索关键词
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // UI消息
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    // 当前联系人ID（用于生成商品）- 从SharedPreferences加载初始值
    private val _currentContactId = MutableStateFlow<String?>(loadSavedContactId())
    val currentContactId: StateFlow<String?> = _currentContactId.asStateFlow()
    
    // 当前联系人信息（用于显示头像）
    private val _currentContact = MutableStateFlow<PersonProfile?>(null)
    val currentContact: StateFlow<PersonProfile?> = _currentContact.asStateFlow()
    
    // 所有商品列表（用于搜索）
    private var allProducts: List<ShoppingProduct> = emptyList()
    
    init {
        observeCategories()
        observeProducts()
        observeCurrentContact()
        
        // 如果有保存的联系人ID,则在启动时自动加载
        val savedContactId = _currentContactId.value
        if (savedContactId != null) {
            Log.d("HomeTabViewModel", "从SharedPreferences加载保存的联系人ID: $savedContactId")
        }
        
        // 自动为没有图片的商品生成图片
        autoGenerateProductImages()
    }
    
    /**
     * 从SharedPreferences加载保存的联系人ID
     */
    private fun loadSavedContactId(): String? {
        return sharedPrefs.getString(KEY_CURRENT_CONTACT_ID, null)
    }
    
    /**
     * 保存联系人ID到SharedPreferences
     */
    private fun saveContactId(contactId: String?) {
        sharedPrefs.edit().putString(KEY_CURRENT_CONTACT_ID, contactId).apply()
        Log.d("HomeTabViewModel", "保存联系人ID到SharedPreferences: $contactId")
    }
    
    /**
     * 观察分类列表变化
     * 监听contactId变化,自动切换分类Flow
     */
    private fun observeCategories() {
        viewModelScope.launch {
            _currentContactId
                .flatMapLatest { contactId ->
                    Log.d("HomeTabViewModel", "分类Flow切换: contactId=$contactId")
                    if (contactId != null) {
                        categoryRepository.getCategoriesByContactId(contactId)
                    } else {
                        categoryRepository.getAllCategories()
                    }
                }
                .collect { categoryList ->
                    val contactId = _currentContactId.value
                    Log.d("HomeTabViewModel", "收到分类列表: size=${categoryList.size}, contactId=$contactId")
                    
                    // 创建"全部"分类项
                    val allCategory = ShoppingCategory(
                        id = -1L,
                        name = "全部",
                        contactId = contactId ?: "",
                        timestamp = System.currentTimeMillis()
                    )
                    
                    // 将"全部"添加到列表最前面
                    _categories.value = listOf(allCategory) + categoryList
                }
        }
    }
    
    /**
     * 观察商品列表变化
     * 监听contactId和selectedCategoryId变化,自动切换商品Flow
     */
    private fun observeProducts() {
        viewModelScope.launch {
            // 组合监听contactId和selectedCategoryId
            kotlinx.coroutines.flow.combine(
                _currentContactId,
                _selectedCategoryId
            ) { contactId, categoryId ->
                Log.d("HomeTabViewModel", "商品Flow切换: contactId=$contactId, categoryId=$categoryId")
                Pair(contactId, categoryId)
            }.flatMapLatest { (contactId, categoryId) ->
                when {
                    // 情况1: 没有contactId,显示所有商品或按分类筛选
                    contactId == null -> {
                        if (categoryId == -1L) {
                            productRepository.getAllProducts()
                        } else {
                            productRepository.getProductsByCategoryId(categoryId)
                        }
                    }
                    // 情况2: 有contactId,显示该联系人的所有商品或按分类筛选
                    categoryId == -1L -> {
                        productRepository.getProductsByContactId(contactId)
                    }
                    // 情况3: 有contactId和分类,显示该联系人该分类的商品
                    else -> {
                        productRepository.getProductsByContactIdAndCategoryId(contactId, categoryId)
                    }
                }
            }.collect { productList ->
                Log.d("HomeTabViewModel", "收到商品列表: size=${productList.size}")
                allProducts = productList
                filterProducts()
            }
        }
    }
    
    /**
     * 根据分类加载商品（按当前联系人筛选）
     */
    private fun loadProductsByCategory(categoryId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val contactId = _currentContactId.value
            val flow = if (contactId != null) {
                productRepository.getProductsByContactIdAndCategoryId(contactId, categoryId)
            } else {
                productRepository.getProductsByCategoryId(categoryId)
            }
            flow.collect { productList ->
                allProducts = productList
                filterProducts()
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 选择分类
     */
    fun selectCategory(categoryId: Long) {
        _selectedCategoryId.value = categoryId
        // observeProducts()会自动响应selectedCategoryId的变化
    }
    
    /**
     * 搜索商品
     */
    fun searchProducts(query: String) {
        _searchQuery.value = query
        filterProducts()
    }
    
    /**
     * 过滤商品（根据搜索关键词）
     */
    private fun filterProducts() {
        val query = _searchQuery.value
        _products.value = if (query.isBlank()) {
            allProducts
        } else {
            allProducts.filter { product ->
                product.name.contains(query, ignoreCase = true) ||
                product.description?.contains(query, ignoreCase = true) == true
            }
        }
    }
    
    /**
     * 打开商品详情
     */
    fun openProductDetail(productId: Long) {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.ToProductDetail(productId))
        }
    }
    
    /**
     * 观察当前联系人数据变化
     * 使用flatMapLatest避免Flow嵌套问题
     */
    private fun observeCurrentContact() {
        viewModelScope.launch {
            _currentContactId
                .flatMapLatest { contactId ->
                    Log.d("HomeTabViewModel", "currentContactId changed: $contactId")
                    if (contactId != null) {
                        // 切换到新的联系人Flow
                        personProfileRepository.getPersonProfileByIdFlow(contactId)
                    } else {
                        Log.w("HomeTabViewModel", "contactId为null,清空currentContact")
                        // 返回一个发射null的Flow
                        flowOf(null)
                    }
                }
                .collect { profile ->
                    Log.d("HomeTabViewModel", "收到PersonProfile更新: $profile")
                    Log.d("HomeTabViewModel", "avatarUri: ${profile?.avatarUri}")
                    _currentContact.value = profile
                }
        }
    }
    
    /**
     * 设置当前联系人ID并加载联系人信息
     */
    fun setCurrentContactId(contactId: String) {
        viewModelScope.launch {
            Log.d("HomeTabViewModel", "setCurrentContactId called: $contactId")
            _currentContactId.value = contactId
            // 保存到SharedPreferences
            saveContactId(contactId)
            // 重置为"全部"分类
            _selectedCategoryId.value = -1L
            // 注意: 分类和商品会通过observeCategories()和observeProducts()自动重新加载
        }
    }
    
    /**
     * 清空当前联系人的所有商品和分类
     */
    fun clearCurrentContactData() {
        viewModelScope.launch {
            try {
                val contactId = _currentContactId.value
                if (contactId == null) {
                    _message.emit("请先选择联系人")
                    return@launch
                }
                
                // 先删除商品,再删除分类
                productRepository.deleteProductsByContactId(contactId)
                categoryRepository.deleteCategoriesByContactId(contactId)
                
                _message.emit("已清空该角色的商品和分类")
            } catch (e: Exception) {
                _message.emit("清空失败: ${e.message}")
            }
        }
    }
    
    /**
     * AI生成商品
     * @param context Android Context
     * @param contactId 角色ID
     */
    fun generateProductsWithAi(context: Context, contactId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 保存当前联系人ID
                _currentContactId.value = contactId
                
                val result = generateShoppingProductUseCase.generateWithAi(
                    context = context,
                    contactId = contactId,
                    categoryCount = 3,
                    productCountPerCategory = 5
                )
                result.onSuccess { count ->
                    _message.emit("AI成功生成了 $count 个商品!")
                }.onFailure { error ->
                    _message.emit("AI生成失败: ${error.message}")
                }
            } catch (e: Exception) {
                _message.emit("AI生成失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 打开商品编辑器
     */
    fun openProductEditor(productId: Long) {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.ToProductEditor(productId))
        }
    }
    
    /**
     * 删除商品
     */
    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
        }
    }
    
    /**
     * 添加分类
     * 绑定到当前联系人
     */
    fun addCategory(name: String) {
        viewModelScope.launch {
            try {
                val contactId = _currentContactId.value
                if (contactId == null) {
                    _message.emit("请先选择联系人")
                    return@launch
                }
                val result = categoryRepository.createCategory(name, contactId)
                if (result == -1L) {
                    _message.emit("分类名称已存在")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _message.emit("添加分类失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新分类
     */
    fun updateCategory(categoryId: Long, newName: String) {
        viewModelScope.launch {
            try {
                val category = categoryRepository.getCategoryById(categoryId)
                category?.let {
                    val updated = it.copy(name = newName)
                    categoryRepository.updateCategory(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 删除分类
     * 同时删除该分类下的所有商品
     */
    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                // 先获取该分类下的所有商品
                productRepository.getProductsByCategoryId(categoryId).collect { products ->
                    // 删除该分类下的所有商品
                    products.forEach { product ->
                        productRepository.deleteProduct(product.id)
                    }
                    // 删除分类
                    categoryRepository.deleteCategory(categoryId)
                    // 如果删除的是当前选中的分类，切换到"全部"
                    if (_selectedCategoryId.value == categoryId) {
                        selectCategory(-1L)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 创建新商品
     * @param categoryId 分类ID
     * @param contactId 联系人ID
     */
    fun createNewProduct(categoryId: Long, contactId: String) {
        viewModelScope.launch {
            try {
                // 使用repository的createProduct方法创建新商品
                val newId = productRepository.createProduct(
                    name = "新商品",
                    price = 0.0,
                    description = "请编辑商品信息",
                    imageUrl = "",
                    categoryId = categoryId,
                    variations = emptyList(),
                    contactId = contactId
                )
                
                // 打开编辑器
                if (newId > 0) {
                    _navigationEvent.emit(NavigationEvent.ToProductEditor(newId))
                } else {
                    _message.emit("创建商品失败")
                }
            } catch (e: Exception) {
                _message.emit("创建商品失败: ${e.message}")
            }
        }
    }
    
    /**
     * 自动为没有图片的商品生成图片
     * 在ViewModel初始化时自动调用
     */
    private fun autoGenerateProductImages() {
        viewModelScope.launch {
            try {
                Log.d("HomeTabViewModel", "开始自动生成商品图片...")
                val result = generateImagesForProductsUseCase()
                result.onSuccess { count ->
                    if (count > 0) {
                        Log.d("HomeTabViewModel", "自动为 $count 个商品生成了图片")
                        _message.emit("已自动为 $count 个商品生成图片")
                    } else {
                        Log.d("HomeTabViewModel", "所有商品都有图片,无需生成")
                    }
                }.onFailure { error ->
                    Log.e("HomeTabViewModel", "自动生成图片失败: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeTabViewModel", "自动生成图片异常", e)
            }
        }
    }
    
    /**
     * 添加已授权账号
     * @param contactId 联系人ID
     */
    fun addAuthorizedAccount(contactId: String) {
        viewModelScope.launch {
            try {
                authorizedAccountRepository.addAuthorizedAccount(contactId, note = "手动添加")
                _message.emit("已添加账号")
            } catch (e: Exception) {
                _message.emit("添加账号失败: ${e.message}")
            }
        }
    }
    
    /**
     * 发送购物访问申请
     * @param contactId 联系人ID
     */
    fun sendShoppingAccessRequest(contactId: String) {
        viewModelScope.launch {
            try {
                chatRepository.sendShoppingAccessRequest(contactId)
                _message.emit("已向该联系人发送购物访问申请")
            } catch (e: Exception) {
                _message.emit("发送申请失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取所有联系人列表（用于选择器）
     */
    fun getAllContacts(): kotlinx.coroutines.flow.Flow<List<PersonProfile>> {
        return personProfileRepository.getPersonProfilesFlow()
    }
    
    /**
     * 导航事件密封类
     */
    sealed class NavigationEvent {
        data class ToProductDetail(val productId: Long) : NavigationEvent()
        data class ToProductEditor(val productId: Long) : NavigationEvent()
    }
}