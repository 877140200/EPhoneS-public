package com.susking.ephone_s.core.api

/**
 * QQ 模块对外 API
 * 类似 AlbumApi 的设计,使用服务定位器模式
 * 
 * 使用方式:
 * 1. 在 EPhoneSApplication 中初始化: QqApi.init(fragmentProvider)
 * 2. 在其他模块中获取: QqApi.getFragmentProvider()
 */
object QqApi {
    private lateinit var fragmentProvider: QqFragmentProvider
    
    /**
     * 初始化 QQ API
     * 必须在 Application.onCreate() 中调用
     * 
     * @param provider Fragment 提供者的具体实现
     */
    fun init(provider: QqFragmentProvider) {
        this.fragmentProvider = provider
    }
    
    /**
     * 获取 Fragment 提供者
     * 
     * @return QqFragmentProvider 实例
     * @throws UninitializedPropertyAccessException 如果未初始化
     */
    fun getFragmentProvider(): QqFragmentProvider {
        if (!::fragmentProvider.isInitialized) {
            throw UninitializedPropertyAccessException(
                "QqApi 未初始化,请在 Application.onCreate() 中调用 QqApi.init()"
            )
        }
        return fragmentProvider
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = ::fragmentProvider.isInitialized
}