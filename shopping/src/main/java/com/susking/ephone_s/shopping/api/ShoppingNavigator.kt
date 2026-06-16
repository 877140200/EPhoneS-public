package com.susking.ephone_s.shopping.api

import androidx.fragment.app.Fragment

/**
 * 商城导航器接口
 * 
 * 用于在shopping模块内部进行Fragment导航
 */
interface ShoppingNavigator {
    
    /**
     * 导航到商城容器Fragment（包含底部导航）
     * @return 商城容器Fragment实例
     */
    fun navigateToShoppingContainer(): Fragment
    
    /**
     * 导航到商城主页Fragment
     * @return 商城主页Fragment实例
     */
    fun navigateToShoppingMain(): Fragment
    
    /**
     * 导航到商品详情Fragment
     * @param productId 商品ID
     * @return 商品详情Fragment实例
     */
    fun navigateToProductDetail(productId: Long): Fragment
    
    /**
     * 导航到购物车Fragment
     * @return 购物车Fragment实例
     */
    fun navigateToShoppingCart(): Fragment
    
    /**
     * 导航到订单列表Fragment
     * @param chatId 聊天ID(可选)
     * @return 订单列表Fragment实例
     */
    fun navigateToOrderList(chatId: String? = null): Fragment
}