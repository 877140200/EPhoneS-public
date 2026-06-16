package com.susking.ephone_s.aidata.domain.service

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片预加载服务
 *
 * 用于在商品创建时预加载所有图片到本地缓存
 */
@Singleton
class ImagePreloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) {
    
    /**
     * 预加载单张图片（带重试机制）
     *
     * @param imageUrl 图片URL
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 是否加载成功
     */
    suspend fun preloadImage(
        imageUrl: String,
        maxRetries: Int = 3,
        retryDelayMs: Long = 2000L
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var success = false
            
            while (attempt < maxRetries && !success) {
                attempt++
                
                try {
                    if (attempt > 1) {
                        Log.d(TAG, "重试预加载图片 (${attempt}/${maxRetries}): $imageUrl")
                        delay(retryDelayMs)
                    } else {
                        Log.d(TAG, "开始预加载图片: $imageUrl")
                    }
                    
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .build()
                    
                    // 执行请求，图片会被缓存到Coil的磁盘缓存
                    val result = imageLoader.execute(request)
                    
                    success = result.drawable != null
                    
                    if (success) {
                        Log.d(TAG, "✓ 图片预加载成功 (尝试${attempt}次): $imageUrl")
                    } else if (attempt >= maxRetries) {
                        Log.w(TAG, "✗ 图片预加载失败（已达最大重试次数）: $imageUrl")
                    }
                    
                } catch (e: Exception) {
                    if (attempt >= maxRetries) {
                        Log.e(TAG, "图片预加载异常（已达最大重试次数）: $imageUrl", e)
                    }
                }
            }
            
            success
        }
    }
    
    /**
     * 批量预加载图片（带重试和初始延迟）
     *
     * @param imageUrls 图片URL列表
     * @param initialDelayMs 初始延迟（毫秒），给Pollinations AI时间生成图片
     * @param maxRetries 每张图片的最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 成功加载的图片数量
     */
    suspend fun preloadImages(
        imageUrls: List<String>,
        initialDelayMs: Long = 3000L,
        maxRetries: Int = 3,
        retryDelayMs: Long = 2000L
    ): Int {
        return withContext(Dispatchers.IO) {
            var successCount = 0
            
            Log.d(TAG, "开始批量预加载 ${imageUrls.size} 张图片（初始延迟${initialDelayMs}ms）")
            
            // 等待一段时间，让Pollinations AI有时间生成图片
            if (initialDelayMs > 0) {
                delay(initialDelayMs)
            }
            
            imageUrls.forEach { url ->
                if (url.isNotBlank() && preloadImage(url, maxRetries, retryDelayMs)) {
                    successCount++
                }
            }
            
            Log.d(TAG, "批量预加载完成: 成功 $successCount/${imageUrls.size} 张")
            successCount
        }
    }
    
    /**
     * 预加载商品的所有图片（主图 + 所有款式图片）
     * 带初始延迟和重试机制，适配Pollinations AI的生成速度
     *
     * @param mainImageUrl 商品主图URL
     * @param variationImageUrls 款式图片URL列表
     * @param initialDelayMs 初始延迟（毫秒），给Pollinations AI时间生成图片
     * @param maxRetries 每张图片的最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 成功加载的图片数量
     */
    suspend fun preloadProductImages(
        mainImageUrl: String,
        variationImageUrls: List<String>,
        initialDelayMs: Long = 3000L,
        maxRetries: Int = 3,
        retryDelayMs: Long = 2000L
    ): Int {
        val allUrls = mutableListOf<String>()
        
        // 添加主图
        if (mainImageUrl.isNotBlank()) {
            allUrls.add(mainImageUrl)
        }
        
        // 添加所有款式图片
        variationImageUrls.forEach { url ->
            if (url.isNotBlank()) {
                allUrls.add(url)
            }
        }
        
        Log.d(TAG, "准备预加载商品图片: 主图1张 + 款式${variationImageUrls.size}张 = 共${allUrls.size}张")
        
        return preloadImages(allUrls, initialDelayMs, maxRetries, retryDelayMs)
    }
    
    companion object {
        private const val TAG = "ImagePreloadService"
    }
}