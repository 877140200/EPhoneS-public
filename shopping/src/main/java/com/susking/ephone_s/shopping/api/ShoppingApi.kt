package com.susking.ephone_s.shopping.api

/**
 * 商城模块API接口
 * 
 * 提供给其他模块使用的商城功能入口
 */
interface ShoppingApi {
    
    /**
     * 打开商城主页
     */
    fun openShoppingMain()
    
    /**
     * 打开商品详情页
     * @param productId 商品ID
     */
    fun openProductDetail(productId: Long)
    
    /**
     * 打开购物车
     */
    fun openShoppingCart()
    
    /**
     * 打开订单列表
     * @param chatId 聊天ID(可选),如果提供则只显示该聊天的订单
     */
    fun openOrderList(chatId: String? = null)
    
    /**
     * 打开商品管理页面
     */
    fun openProductManagement()
}