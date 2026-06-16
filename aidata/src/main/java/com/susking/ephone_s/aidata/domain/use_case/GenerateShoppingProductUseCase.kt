package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.MemoriesRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingCategoryRepository
import com.susking.ephone_s.aidata.domain.repository.ShoppingProductRepository
import com.susking.ephone_s.aidata.prompt.ShoppingPromptBuilder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 商品生成UseCase
 *
 * 提供手动创建和AI生成两种方式
 */
class GenerateShoppingProductUseCase @Inject constructor(
    private val productRepository: ShoppingProductRepository,
    private val categoryRepository: ShoppingCategoryRepository,
    private val aiRequestService: AiRequestService,
    private val personProfileRepository: PersonProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val memoriesRepository: MemoriesRepository,
    private val chatRepository: ChatRepository
) {
    
    private val gson = Gson()
    
    /**
     * 生成商品数据
     *
     * @param name 商品名称
     * @param price 默认价格
     * @param description 商品描述
     * @param imageUrl 商品图片URL
     * @param contactId 联系人ID(角色ID)
     * @param categoryName 分类名称(可选)
     * @param variations 款式列表(可选)
     * @return 新商品ID
     */
    suspend operator fun invoke(
        name: String,
        price: Double,
        description: String,
        imageUrl: String,
        contactId: String,
        categoryName: String? = null,
        variations: List<ProductVariation> = emptyList()
    ): Result<Long> {
        return try {
            // 1. 处理分类
            val categoryId = if (categoryName != null) {
                // 尝试查找已存在的分类(仅当前联系人)
                val categories = mutableListOf<com.susking.ephone_s.aidata.domain.model.ShoppingCategory>()
                categoryRepository.getCategoriesByContactId(contactId).collect { list ->
                    categories.clear()
                    categories.addAll(list)
                }
                
                val existing = categories.firstOrNull {
                    it.name.equals(categoryName, ignoreCase = true)
                }
                
                if (existing != null) {
                    existing.id
                } else {
                    // 创建新分类,绑定到当前联系人
                    val newId = categoryRepository.createCategory(categoryName, contactId)
                    if (newId == -1L) {
                        // 创建失败,可能是并发导致,再次查找
                        categories.clear()
                        categoryRepository.getCategoriesByContactId(contactId).collect { list ->
                            categories.addAll(list)
                        }
                        categories.firstOrNull {
                            it.name.equals(categoryName, ignoreCase = true)
                        }?.id
                    } else {
                        newId
                    }
                }
            } else {
                null
            }
            
            // 2. 创建商品,绑定到当前联系人
            val productId = productRepository.createProduct(
                name = name,
                price = price,
                description = description,
                imageUrl = imageUrl,
                categoryId = categoryId,
                variations = variations,
                contactId = contactId
            )
            
            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 批量生成商品
     * 
     * @param products 商品数据列表
     * @return 成功生成的商品ID列表
     */
    suspend fun generateMultiple(
        products: List<ProductData>
    ): Result<List<Long>> {
        return try {
            val productIds = mutableListOf<Long>()
            
            products.forEach { data ->
                val result = invoke(
                    name = data.name,
                    price = data.price,
                    description = data.description,
                    imageUrl = data.imageUrl,
                    contactId = data.contactId,
                    categoryName = data.categoryName,
                    variations = data.variations
                )
                
                result.getOrNull()?.let { productIds.add(it) }
            }
            
            Result.success(productIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 使用AI生成商品
     *
     * @param context Android Context
     * @param contactId 角色ID(用于获取角色人设)
     * @param categoryCount 要生成的分类数量
     * @param productCountPerCategory 每个分类的商品数量
     * @return Result包含生成的商品数量,失败返回错误信息
     */
    suspend fun generateWithAi(
        context: Context,
        contactId: String,
        categoryCount: Int = 3,
        productCountPerCategory: Int = 5
    ): Result<Int> {
        return try {
            Log.d(TAG, "开始AI生成商品: contactId=$contactId, 分类数=$categoryCount, 每分类商品数=$productCountPerCategory")
            
            // 1. 获取角色人设
            Log.d(TAG, "正在获取角色人设...")
            val profile = personProfileRepository.getPersonProfileById(contactId)
            if (profile == null) {
                Log.e(TAG, "找不到角色: $contactId, AI商品生成中止")
                return Result.failure(Exception("找不到角色: $contactId"))
            }
            Log.d(TAG, "成功获取角色人设: ${profile.realName}")
            
            // 2. 获取已有分类(仅当前联系人的分类)
            Log.d(TAG, "正在获取已有分类...")
            val existingCategories = try {
                categoryRepository.getCategoriesByContactId(contactId)
                    .first()
                    .map { it.name }
            } catch (e: Exception) {
                Log.e(TAG, "获取分类失败", e)
                emptyList()
            }
            Log.d(TAG, "成功获取${existingCategories.size}个已有分类")
            
            // 3. 获取记忆数据
            Log.d(TAG, "正在获取记忆数据...")
            
            val appointments = try {
                memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
            } catch (e: Exception) {
                Log.e(TAG, "获取约定倒计时失败", e)
                emptyList()
            }
            
            val generalMemories = try {
                memoriesRepository.getMemoriesByContactIdSuspend(contactId)
            } catch (e: Exception) {
                Log.e(TAG, "获取回忆失败", e)
                emptyList()
            }
            
            val chatHistory = try {
                chatRepository.getMessagesForContactNonFlow(contactId)
            } catch (e: Exception) {
                Log.e(TAG, "获取对话历史失败", e)
                emptyList()
            }
            
            val userProfile = try {
                personProfileRepository.getUserProfile()
            } catch (e: Exception) {
                Log.e(TAG, "获取用户资料失败", e)
                null
            }
            
            Log.d(TAG, "记忆数据获取完成: 约定=${appointments.size}条, 回忆=${generalMemories.size}条, 对话历史=${chatHistory.size}条")
            
            // 4. 构建AI提示词
            val apiUrl = settingsRepository.getMainApiUrl()
            val model = settingsRepository.getMainModel()
            
            val promptRequest = ShoppingPromptBuilder.buildShoppingGenerationPrompt(
                profile = profile,
                existingCategories = existingCategories,
                categoryCount = categoryCount,
                productCountPerCategory = productCountPerCategory,
                apiUrl = apiUrl,
                model = model,
                appointments = appointments,
                generalMemories = generalMemories,
                chatHistory = chatHistory,
                userProfile = userProfile
            )
            
            Log.d(TAG, "准备发送AI请求 - contactName: ${promptRequest.contactName}, activityType: ${promptRequest.activityType}")
            
            // 5. 调用brain模块发送请求给AI
            val aiResponse = aiRequestService.getChatCompletion(context, promptRequest)
            
            if (aiResponse == null) {
                Log.e(TAG, "AI请求失败: 返回null")
                return Result.failure(Exception("AI请求失败: 未收到响应"))
            }
            
            Log.d(TAG, "收到AI回复,长度: ${aiResponse.length} 字符")
            Log.d(TAG, "AI回复前500字符: ${aiResponse.take(500)}")

            // 6. 解析AI返回的JSON
            Log.d(TAG, "开始解析AI返回的JSON...")
            val generatedData = parseAiResponse(aiResponse)
            if (generatedData == null) {
                Log.e(TAG, "解析失败! generatedData为null")
                return Result.failure(Exception("AI返回格式错误: 无法解析为商品数据"))
            }
            
            Log.d(TAG, "✓ 解析成功! 分类数量: ${generatedData.categories.size}")
            generatedData.categories.forEachIndexed { index, cat ->
                Log.d(TAG, "  分类${index + 1}: ${cat.name}, 商品数: ${cat.products.size}")
            }
            
            // 7. 保存到数据库
            Log.d(TAG, "开始保存商品到数据库...")
            var totalProductsCreated = 0
            generatedData.categories.forEach { categoryData ->
                Log.d(TAG, "处理分类: ${categoryData.name}")
                
                // 检查分类是否已存在(仅当前联系人的分类)
                val categories = categoryRepository.getCategoriesByContactId(contactId).first()
                
                val existingCategory = categories.firstOrNull {
                    it.name.equals(categoryData.name, ignoreCase = true)
                }
                
                val categoryId = existingCategory?.id ?: categoryRepository.createCategory(categoryData.name, contactId)
                Log.d(TAG, "  分类[${categoryData.name}] ID: $categoryId")
                
                // 创建该分类下的所有商品
                categoryData.products.forEach { productData ->
                    Log.d(TAG, "    正在创建商品: ${productData.name}")
                    try {
                        val variations = productData.variations.map { v ->
                            ProductVariation(
                                name = v.name,
                                price = v.price,
                                imageUrl = generateImageUrl(v.imagePrompt)
                            )
                        }
                        
                        productRepository.createProduct(
                            name = productData.name,
                            price = productData.price,
                            description = productData.description,
                            imageUrl = generateImageUrl(productData.imagePrompt),
                            categoryId = categoryId,
                            variations = variations,
                            contactId = contactId
                        )
                        
                        totalProductsCreated++
                        Log.d(TAG, "    ✓ 商品创建成功: ${productData.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "    ✗ 创建商品失败: ${productData.name}", e)
                    }
                }
            }
            
            Log.d(TAG, "========================================")
            Log.d(TAG, "AI生成商品完成! 共创建 $totalProductsCreated 个商品")
            Log.d(TAG, "========================================")
            Result.success(totalProductsCreated)
            
        } catch (e: Exception) {
            Log.e(TAG, "AI生成商品失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析AI返回的商品数据
     */
    private fun parseAiResponse(response: String): ShoppingGenerationResponse? {
        return try {
            Log.d(TAG, "parseAiResponse: 原始响应长度=${response.length}")
            
            // AI可能返回带代码块标记的JSON,需要清理
            val cleanJson = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            Log.d(TAG, "parseAiResponse: 清理后JSON长度=${cleanJson.length}")
            Log.d(TAG, "parseAiResponse: JSON前200字符=${cleanJson.take(200)}")
            Log.d(TAG, "parseAiResponse: JSON后200字符=${cleanJson.takeLast(200)}")
            
            val result = gson.fromJson(cleanJson, ShoppingGenerationResponse::class.java)
            Log.d(TAG, "parseAiResponse: Gson解析成功")
            result
        } catch (e: Exception) {
            Log.e(TAG, "parseAiResponse: 解析失败!", e)
            Log.e(TAG, "parseAiResponse: 错误类型=${e.javaClass.simpleName}")
            Log.e(TAG, "parseAiResponse: 错误消息=${e.message}")
            Log.e(TAG, "parseAiResponse: 响应前1000字符=${response.take(1000)}")
            null
        }
    }
    
    /**
     * 生成图片URL(使用Pollinations AI)
     */
    private fun generateImageUrl(prompt: String): String {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "https://image.pollinations.ai/prompt/$encodedPrompt"
    }
    
    /**
     * 商品数据传输对象
     */
    data class ProductData(
        val name: String,
        val price: Double,
        val description: String,
        val imageUrl: String,
        val contactId: String,
        val categoryName: String? = null,
        val variations: List<ProductVariation> = emptyList()
    )
    
    /**
     * AI返回的商品数据结构
     */
    private data class ShoppingGenerationResponse(
        @SerializedName("categories")
        val categories: List<CategoryData>
    )
    
    private data class CategoryData(
        @SerializedName("name")
        val name: String,
        @SerializedName("products")
        val products: List<AiProductData>
    )
    
    private data class AiProductData(
        @SerializedName("name")
        val name: String,
        @SerializedName("price")
        val price: Double,
        @SerializedName("description")
        val description: String,
        @SerializedName("imagePrompt")
        val imagePrompt: String,
        @SerializedName("variations")
        val variations: List<VariationData>
    )
    
    private data class VariationData(
        @SerializedName("name")
        val name: String,
        @SerializedName("price")
        val price: Double,
        @SerializedName("imagePrompt")
        val imagePrompt: String
    )
    
    companion object {
        private const val TAG = "GenerateShoppingProduct"
    }
}