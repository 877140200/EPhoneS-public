package com.susking.ephone_s.aidata.domain.use_case

import android.util.Log
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 为没有图片的商品自动生成图片UseCase
 * 
 * 进入商城时自动检测所有没有图片的商品并生成图片URL
 */
class GenerateImagesForProductsUseCase @Inject constructor(
    private val productRepository: ShoppingProductRepository
) {
    
    /**
     * 检测并为所有没有图片的商品生成图片
     * 
     * @return Result包含更新的商品数量
     */
    suspend operator fun invoke(): Result<Int> {
        return try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "开始自动生成商品图片")
            Log.d(TAG, "========================================")
            
            // 1. 获取所有商品
            Log.d(TAG, "步骤1: 获取所有商品...")
            val allProducts = productRepository.getAllProducts().first()
            Log.d(TAG, "✓ 商品总数: ${allProducts.size}")
            
            // 打印所有商品的imageUrl状态
            Log.d(TAG, "所有商品的图片URL状态:")
            allProducts.forEachIndexed { index, product ->
                Log.d(TAG, "  ${index + 1}. ${product.name}")
                Log.d(TAG, "     imageUrl: ${product.imageUrl}")
                Log.d(TAG, "     isBlank: ${product.imageUrl.isBlank()}")
                Log.d(TAG, "     isValidUrl: ${isValidImageUrl(product.imageUrl)}")
            }
            
            // 2. 筛选出没有图片的商品(imageUrl为空、空字符串或无效URL)
            Log.d(TAG, "步骤2: 筛选没有图片或图片无效的商品...")
            val productsWithoutImage = allProducts.filter { product ->
                val url = product.imageUrl
                // 检查是否为空、空字符串、或者不是有效的URL
                url.isBlank() || !isValidImageUrl(url)
            }
            Log.d(TAG, "✓ 发现 ${productsWithoutImage.size} 个商品需要生成图片")
            
            // 打印详细信息
            if (productsWithoutImage.isNotEmpty()) {
                Log.d(TAG, "需要处理的商品列表:")
                productsWithoutImage.forEachIndexed { index, product ->
                    Log.d(TAG, "  ${index + 1}. ${product.name} - 当前URL: ${product.imageUrl.ifBlank { "(空)" }}")
                }
            }
            
            if (productsWithoutImage.isEmpty()) {
                Log.d(TAG, "========================================")
                Log.d(TAG, "所有商品都有图片,无需生成")
                Log.d(TAG, "========================================")
                return Result.success(0)
            }
            
            // 3. 为每个没有图片的商品生成图片URL
            Log.d(TAG, "步骤3: 开始为商品生成图片URL...")
            Log.d(TAG, "----------------------------------------")
            var updatedCount = 0
            productsWithoutImage.forEachIndexed { index, product ->
                try {
                    Log.d(TAG, "[${index + 1}/${productsWithoutImage.size}] 正在处理: ${product.name}")
                    Log.d(TAG, "  商品ID: ${product.id}")
                    Log.d(TAG, "  联系人ID: ${product.contactId ?: "无"}")
                    Log.d(TAG, "  分类ID: ${product.categoryId ?: "未分类"}")
                    
                    // 使用商品名称作为提示词生成图片URL
                    val prompt = generateImagePrompt(product.name, product.description)
                    Log.d(TAG, "  生成提示词: $prompt")
                    
                    val imageUrl = generateImageUrl(prompt)
                    Log.d(TAG, "  生成图片URL: $imageUrl")
                    
                    // 更新商品图片URL
                    val updatedProduct = product.copy(imageUrl = imageUrl)
                    productRepository.updateProduct(updatedProduct)
                    
                    updatedCount++
                    Log.d(TAG, "  ✓ 图片生成成功!")
                    Log.d(TAG, "----------------------------------------")
                } catch (e: Exception) {
                    Log.e(TAG, "  ✗ 生成失败: ${e.message}", e)
                    Log.d(TAG, "----------------------------------------")
                }
            }
            
            Log.d(TAG, "========================================")
            Log.d(TAG, "图片生成完成!")
            Log.d(TAG, "成功: $updatedCount 个")
            Log.d(TAG, "失败: ${productsWithoutImage.size - updatedCount} 个")
            Log.d(TAG, "========================================")
            
            Result.success(updatedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "自动生成图片失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 生成图片提示词
     * 基于商品名称和描述生成英文提示词用于Unsplash搜索
     */
    private fun generateImagePrompt(name: String, description: String): String {
        // 使用商品名称和描述的组合
        // 注意: AI生成的商品可能已经有英文prompt,这里是为没有图片的旧商品生成
        return "$name, $description, product photo, high quality, professional lighting"
    }
    
    /**
     * 生成图片URL(使用Pollinations AI)
     */
    /**
     * 检查是否是有效的图片URL
     * 目前只检查是否以http/https开头
     */
    private fun isValidImageUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
               url.startsWith("https://", ignoreCase = true)
    }

    private fun generateImageUrl(prompt: String): String {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "https://image.pollinations.ai/prompt/$encodedPrompt"
    }
    
    companion object {
        private const val TAG = "GenerateImagesForProducts"
    }
}