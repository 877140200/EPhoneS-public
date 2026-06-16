package com.susking.ephone_s.shopping.ui.home

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.appcompat.widget.PopupMenu
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.FragmentHomeTabBinding
import com.susking.ephone_s.shopping.navigation.ShoppingNavigatorImpl
import com.susking.ephone_s.shopping.ui.main.CategoryListAdapter
import com.susking.ephone_s.shopping.ui.main.ProductListAdapter
import com.susking.ephone_s.core.api.QqApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 首页Tab Fragment
 *
 * 展示搜索框、商品分类和商品列表
 */
@AndroidEntryPoint
class HomeTabFragment : Fragment() {
    
    private var _binding: FragmentHomeTabBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeTabViewModel by viewModels()
    
    private lateinit var productAdapter: ProductListAdapter
    private lateinit var categoryAdapter: CategoryListAdapter
    
    private val navigator: ShoppingNavigatorImpl by lazy {
        ShoppingNavigatorImpl(this)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeTabBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupToolbar()
        setupContactAvatar()
        setupSearchBox()
        setupCategoryList()
        setupAddCategoryButton()
        setupProductList()
        observeViewModel()
    }
    
    /**
     * 设置窗口边距适配
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 给AppBarLayout添加顶部内边距,适配状态栏
            val layoutParams = binding.appBarLayout.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = systemBars.top
            binding.appBarLayout.layoutParams = layoutParams
            
            insets
        }
    }
    
    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        // Toolbar不再需要菜单,已被头像替代
    }
    
    /**
     * 设置联系人头像
     */
    private fun setupContactAvatar() {
        binding.imageViewContactAvatar.setOnClickListener { view ->
            showContactAvatarMenu(view)
        }
    }
    
    /**
     * 显示联系人头像菜单
     */
    private fun showContactAvatarMenu(anchorView: View) {
        PopupMenu(requireContext(), anchorView).apply {
            menu.add(0, 1, 0, "切换账号")
            menu.add(0, 2, 1, "刷新商品")
            menu.add(0, 3, 2, "清空商品")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showSwitchContactDialog()
                        true
                    }
                    2 -> {
                        refreshProductsWithCurrentContact()
                        true
                    }
                    3 -> {
                        showClearDataConfirmDialog()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
    
    /**
     * 显示切换账号对话框
     */
    private fun showSwitchContactDialog() {
        val dialog = com.susking.ephone_s.shopping.ui.account.SelectAccountDialogFragment.newInstance()
        
        // 设置账号选择回调
        dialog.onAccountSelected = { contactId ->
            viewModel.setCurrentContactId(contactId)
        }
        
        // 设置添加账号回调
        dialog.onAddAccountClick = {
            showAddAccountOptions()
        }
        
        dialog.show(childFragmentManager, com.susking.ephone_s.shopping.ui.account.SelectAccountDialogFragment.TAG)
    }
    
    /**
     * 显示添加账号选项
     * 使用通用转发页面选择联系人，选择后发送购物访问申请
     */
    private fun showAddAccountOptions() {
        // 通过QQ API获取转发选择器Fragment
        val forwardFragment = QqApi.getFragmentProvider().getForwardSelectorFragment(
            contentType = "shopping_request",
            contentId = null,
            onContactsSelected = { contactIds, _, _ ->
                // 处理所有选中的联系人（支持多选）
                if (contactIds.isNotEmpty()) {
                    // 显示确认对话框并发送给所有选中的联系人
                    showConfirmSendRequestDialogForMultiple(contactIds)
                }
            },
            onCancelled = {
                // 用户取消了选择，不做任何操作
            }
        )
        
        // 显示转发选择器Fragment
        // 使用 requireActivity() 的 FragmentManager 来确保能够访问到 Activity 的容器
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, forwardFragment)
            .addToBackStack(null)
            .commit()
    }
    
    /**
     * 显示确认发送申请对话框（多个联系人）
     */
    private fun showConfirmSendRequestDialogForMultiple(contactIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 获取所有联系人信息
            val allContacts = viewModel.getAllContacts().first()
            val selectedContacts = contactIds.mapNotNull { id ->
                allContacts.find { it.id == id }
            }
            
            // 构建联系人名称列表
            val contactNames = selectedContacts.joinToString("、") { contact ->
                contact.remarkName.ifEmpty { contact.realName }
            }
            
            val message = if (contactIds.size == 1) {
                "确定要向「$contactNames」发送购物访问申请吗？\n\n对方同意后，你将可以查看Ta的购物app。"
            } else {
                "确定要向以下 ${contactIds.size} 位联系人发送购物访问申请吗？\n\n$contactNames\n\n对方同意后，你将可以查看Ta们的购物app。"
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("发送购物访问申请")
                .setMessage(message)
                .setPositiveButton("发送申请") { _, _ ->
                    // 向所有选中的联系人发送申请
                    contactIds.forEach { contactId ->
                        viewModel.sendShoppingAccessRequest(contactId)
                    }
                    // 显示发送成功提示
                    Toast.makeText(
                        requireContext(),
                        "已向 ${contactIds.size} 位联系人发送申请",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    /**
     * 使用当前联系人刷新商品
     */
    private fun refreshProductsWithCurrentContact() {
        val contactId = viewModel.currentContactId.value
        if (contactId.isNullOrEmpty()) {
            // 如果还没有设置联系人ID,显示切换账号对话框
            showSwitchContactDialog()
        } else {
            // 显示确认对话框
            showRefreshConfirmDialog(contactId)
        }
    }
    
    /**
     * 显示刷新确认对话框
     */
    private fun showRefreshConfirmDialog(contactId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认刷新")
            .setMessage("刷新将清空该角色的所有商品和分类,然后重新生成。\n\n此操作不可撤销,确定要继续吗?")
            .setPositiveButton("确定刷新") { _, _ ->
                // 先清空数据
                viewModel.clearCurrentContactData()
                // 再生成新商品
                viewModel.generateProductsWithAi(requireContext(), contactId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示清空数据确认对话框
     */
    private fun showClearDataConfirmDialog() {
        val contactId = viewModel.currentContactId.value
        if (contactId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "请先选择角色", Toast.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清空商品")
            .setMessage("确定要清空该角色的所有商品和分类吗?\n此操作不可恢复!")
            .setPositiveButton("清空") { _, _ ->
                viewModel.clearCurrentContactData()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 设置搜索框
     */
    private fun setupSearchBox() {
        binding.searchEditText.apply {
            // 搜索框文本变化监听
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    viewModel.searchProducts(s?.toString() ?: "")
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
            
            // 搜索按钮点击
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    viewModel.searchProducts(text.toString())
                    clearFocus()
                    true
                } else {
                    false
                }
            }
        }
    }
    
    /**
     * 设置分类列表
     */
    private fun setupCategoryList() {
        categoryAdapter = CategoryListAdapter(
            onCategoryClick = { category ->
                val currentSelectedId = viewModel.selectedCategoryId.value
                // 如果点击的是当前选中的分类
                if (currentSelectedId == category.id) {
                    // 如果是"全部"分类,不做任何反应
                    if (category.id == -1L) {
                        return@CategoryListAdapter
                    }
                    // 如果是其他分类,则返回"全部"分类
                    viewModel.selectCategory(-1L)
                } else {
                    // 选择新的分类
                    viewModel.selectCategory(category.id)
                }
            },
            onCategoryLongClick = { category, view ->
                showCategoryPopupMenu(category, view)
            }
        )
        binding.recyclerViewCategories.apply {
            adapter = categoryAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                context,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
        }
    }
    
    /**
     * 设置添加分类按钮
     */
    private fun setupAddCategoryButton() {
        binding.fabAddCategoryInline.setOnClickListener {
            showAddCategoryDialog()
        }
    }
    
    /**
     * 显示添加分类对话框
     */
    private fun showAddCategoryDialog() {
        val input = android.widget.EditText(requireContext())
        input.hint = "分类名称"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加分类")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addCategory(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示分类操作气泡菜单
     */
    private fun showCategoryPopupMenu(category: ShoppingCategory, anchorView: View) {
        PopupMenu(requireContext(), anchorView).apply {
            menu.add(0, 1, 0, "编辑")
            menu.add(0, 2, 1, "删除")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showEditCategoryDialog(category.id, category.name)
                        true
                    }
                    2 -> {
                        showDeleteCategoryConfirmDialog(category.id, category.name)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
    
    /**
     * 显示编辑分类对话框
     */
    private fun showEditCategoryDialog(categoryId: Long, currentName: String) {
        val input = android.widget.EditText(requireContext())
        input.hint = "分类名称"
        input.setText(currentName)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑分类")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.updateCategory(categoryId, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示删除分类确认对话框
     */
    private fun showDeleteCategoryConfirmDialog(categoryId: Long, categoryName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分类")
            .setMessage("确定要删除「$categoryName」分类吗?\n该分类下的所有商品也将一并删除。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteCategory(categoryId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 设置商品列表
     */
    private fun setupProductList() {
        productAdapter = ProductListAdapter(
            onProductClick = { product ->
                viewModel.openProductDetail(product.id)
            },
            onEditClick = { product ->
                viewModel.openProductEditor(product.id)
            },
            onDeleteClick = { product ->
                showDeleteConfirmDialog(product.id, product.name)
            },
            onAddClick = null // 初始为null,后续根据分类动态设置
        )
        binding.recyclerViewProducts.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(context, 2)
            itemAnimator = null // 禁用动画，避免切换分类时闪烁
        }
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(productId: Long, productName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除商品")
            .setMessage("确定要删除「$productName」吗?")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteProduct(productId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示添加商品对话框
     */
    private fun showAddProductDialog() {
        val contactId = viewModel.currentContactId.value
        val categoryId = viewModel.selectedCategoryId.value
        
        if (contactId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "请先选择联系人", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (categoryId == -1L) {
            Toast.makeText(requireContext(), "请先选择一个分类", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 调用ViewModel创建新商品
        viewModel.createNewProduct(categoryId, contactId)
    }
    
    /**
     * 显示AI生成商品对话框
     */
    private fun showAiGenerateDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "请输入角色ID (联系人ID)"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("AI生成商品")
            .setMessage("AI将根据角色人设自动生成商品")
            .setView(editText)
            .setPositiveButton("生成") { _, _ ->
                val contactId = editText.text.toString().trim()
                if (contactId.isNotEmpty()) {
                    viewModel.generateProductsWithAi(requireContext(), contactId)
                } else {
                    Toast.makeText(requireContext(), "请输入角色ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 观察ViewModel状态
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察分类列表
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories) {
                            // 列表更新完成后，重新应用选中状态，解决时序问题
                            categoryAdapter.setSelectedCategory(viewModel.selectedCategoryId.value)
                        }
                    }
                }
                // 观察商品列表
                launch {
                    viewModel.products.collect { products ->
                        productAdapter.submitList(products)
                        binding.textViewEmpty.visibility = if (products.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                // 观察选中的分类（用于用户点击切换时的即时响应）
                launch {
                    viewModel.selectedCategoryId.collect { categoryId ->
                        categoryAdapter.setSelectedCategory(categoryId)
                        // 根据分类动态设置是否显示添加卡片
                        updateAddButtonVisibility(categoryId)
                    }
                }
               
                // 观察导航事件
                 launch {
                    viewModel.navigationEvent.collect { event ->
                        handleNavigationEvent(event)
                    }
                 }
                 
                 // 观察消息
                 launch {
                     viewModel.message.collect { message ->
                         Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                     }
                 }
                 
                 // 观察当前联系人信息,更新头像显示
                 launch {
                     viewModel.currentContact.collect { contact ->
                         updateContactAvatar(contact)
                     }
                 }
            }
        }
    }
    
    /**
     * 处理导航事件
     */
    private fun handleNavigationEvent(event: HomeTabViewModel.NavigationEvent) {
        val fragment = when (event) {
            is HomeTabViewModel.NavigationEvent.ToProductDetail ->
                navigator.navigateToProductDetail(event.productId)
            is HomeTabViewModel.NavigationEvent.ToProductEditor ->
                navigator.navigateToProductEditor(event.productId)
        }
        navigator.navigate(fragment)
    }
    
    /**
     * 根据选中的分类更新添加按钮的可见性
     * 只有选中具体分类时才显示添加卡片,"全部"分类不显示
     */
    private fun updateAddButtonVisibility(categoryId: Long) {
        productAdapter = ProductListAdapter(
            onProductClick = { product ->
                viewModel.openProductDetail(product.id)
            },
            onEditClick = { product ->
                viewModel.openProductEditor(product.id)
            },
            onDeleteClick = { product ->
                showDeleteConfirmDialog(product.id, product.name)
            },
            onAddClick = if (categoryId != -1L) {
                // 选中了具体分类,显示添加卡片
                { showAddProductDialog() }
            } else {
                // 选中"全部"分类,不显示添加卡片
                null
            }
        )
        binding.recyclerViewProducts.adapter = productAdapter
        // 重新提交当前的商品列表
        productAdapter.submitList(viewModel.products.value)
    }
    
    /**
     * 更新联系人头像
     */
    private fun updateContactAvatar(contact: PersonProfile?) {
        Log.d("HomeTabFragment", "updateContactAvatar called")
        Log.d("HomeTabFragment", "contact: $contact")
        Log.d("HomeTabFragment", "avatarUri: ${contact?.avatarUri}")
        
        if (contact == null || contact.avatarUri.isNullOrEmpty()) {
            // 没有联系人或没有头像,使用默认头像
            Log.w("HomeTabFragment", "使用默认头像 - contact为null或avatarUri为空")
            binding.imageViewContactAvatar.setImageResource(R.drawable.ic_person_24)
        } else {
            // 加载真实头像
            val avatarPath = contact.avatarUri
            Log.d("HomeTabFragment", "尝试加载头像: $avatarPath")
            
            if (avatarPath.isNullOrEmpty()) {
                // avatarUri为空,使用默认头像
                Log.w("HomeTabFragment", "avatarUri为空,使用默认头像")
                binding.imageViewContactAvatar.setImageResource(R.drawable.ic_person_24)
                return
            }
            
            try {
                // 构建正确的文件URI
                val fileUri = when {
                    avatarPath.startsWith("file://") -> {
                        Uri.parse(avatarPath)
                    }
                    avatarPath.startsWith("/") -> {
                        // 绝对路径,添加file://前缀
                        Uri.parse("file://$avatarPath")
                    }
                    else -> {
                        // 相对路径或其他格式
                        Uri.parse(avatarPath)
                    }
                }
                
                Log.d("HomeTabFragment", "转换后的URI: $fileUri")
                
                Glide.with(this)
                    .load(fileUri)
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("HomeTabFragment", "Glide加载头像失败", e)
                            return false // 让Glide处理错误
                        }
                        
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("HomeTabFragment", "Glide加载头像成功")
                            return false // 让Glide处理显示
                        }
                    })
                    .circleCrop()
                    .into(binding.imageViewContactAvatar)
            } catch (e: Exception) {
                // 加载失败,使用默认头像
                Log.e("HomeTabFragment", "Glide加载头像异常: ${e.message}", e)
                binding.imageViewContactAvatar.setImageResource(R.drawable.ic_person_24)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = HomeTabFragment()
    }
}