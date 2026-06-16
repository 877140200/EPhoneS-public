package com.susking.ephone_s.shopping.navigation

import androidx.fragment.app.Fragment
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.shopping.api.ShoppingNavigator
import com.susking.ephone_s.shopping.ui.cart.ShoppingCartFragment
import com.susking.ephone_s.shopping.ui.container.ShoppingContainerFragment
import com.susking.ephone_s.shopping.ui.detail.ProductDetailFragment
import com.susking.ephone_s.shopping.ui.home.HomeTabFragment

/**
 * Shopping导航器实现类
 *
 * 负责shopping模块内部的Fragment导航
 * 使用Activity的supportFragmentManager和main_fragment_container，确保全屏显示和正确的返回行为
 */
class ShoppingNavigatorImpl(
    private val hostFragment: Fragment
) : ShoppingNavigator {
    
    // 使用Activity的supportFragmentManager，确保导航到的Fragment全屏显示
    private val fragmentManager get() = (hostFragment.requireActivity() as BaseActivity).supportFragmentManager
    
    // 使用main_fragment_container作为容器ID
    private val containerId: Int by lazy {
        hostFragment.requireActivity().resources.getIdentifier(
            "main_fragment_container", "id", hostFragment.requireActivity().packageName
        )
    }
    
    override fun navigateToShoppingContainer(): Fragment {
        return ShoppingContainerFragment.newInstance()
    }
    
    override fun navigateToShoppingMain(): Fragment {
        // 保持兼容性，返回Container（现在是主入口）
        return ShoppingContainerFragment.newInstance()
    }
    
    /**
     * 导航到首页Tab（HomeTab）
     */
    fun navigateToHomeTab(): Fragment {
        return HomeTabFragment.newInstance()
    }
    
    override fun navigateToProductDetail(productId: Long): Fragment {
        return ProductDetailFragment.newInstance(productId)
    }
    
    override fun navigateToShoppingCart(): Fragment {
        return ShoppingCartFragment.newInstance()
    }
    
    override fun navigateToOrderList(chatId: String?): Fragment {
        return com.susking.ephone_s.shopping.ui.order.OrderListFragment.newInstance()
    }
    
    /**
     * 导航到商品编辑器
     * @param productId 商品ID,如果为null则是创建新商品
     */
    fun navigateToProductEditor(productId: Long?): Fragment {
        return if (productId != null) {
            com.susking.ephone_s.shopping.ui.editor.ProductEditorFragment.newInstance(productId)
        } else {
            com.susking.ephone_s.shopping.ui.editor.ProductEditorFragment.newInstance()
        }
    }
    
    /**
     * 导航到搜索页面
     */
    fun navigateToSearch(): Fragment {
        return com.susking.ephone_s.shopping.ui.search.SearchFragment.newInstance()
    }
    
    /**
     * 执行Fragment导航
     * 使用add+hide而不是replace，保持原Fragment状态（包括滚动位置）
     */
    fun navigate(fragment: Fragment) {
        val currentFragment = fragmentManager.findFragmentById(containerId)
        fragmentManager.beginTransaction().apply {
            currentFragment?.let { hide(it) }
            add(containerId, fragment)
            addToBackStack(null)
        }.commit()
    }
}