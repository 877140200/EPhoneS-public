package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.model.AmapFootprint
import com.susking.ephone_s.aidata.domain.model.AppUsageRecord
import com.susking.ephone_s.aidata.domain.model.BrowserRecord
import com.susking.ephone_s.aidata.domain.model.CPhoneData
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import com.susking.ephone_s.aidata.domain.model.Memo
import com.susking.ephone_s.aidata.domain.model.MusicTrack
import com.susking.ephone_s.aidata.domain.model.QQConversation
import com.susking.ephone_s.aidata.domain.model.TaobaoData
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.prompt.AiPromptService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 生成CPhone数据的UseCase
 * 调用AI生成指定类型的手机数据并保存到数据库
 *
 * 职责分离:
 * - aidata模块: 构建提示词和数据存储
 * - brain模块: 执行AI请求
 */
class GenerateCPhoneDataUseCase(
    private val context: Context,
    private val aiPromptService: AiPromptService,
    private val cphoneRepository: CPhoneRepository,
    private val aiRequestService: com.susking.ephone_s.aidata.api.AiRequestService,
    private val personProfileRepository: PersonProfileRepository,
    private val saveImageUseCase: SaveImageFromBase64UseCase,
    private val memorySummaryDao: MemorySummaryDao
) {
    
    private val gson = Gson()
    
    /**
     * 生成指定类型的CPhone数据
     * @param contactId 联系人ID
     * @param appType App类型（album/browser/taobao/memo/diary/amap/appUsage/music/qq）
     * @return Result<Unit> 成功返回Unit，失败返回错误信息
     */
    suspend fun execute(
        contactId: String,
        appType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("GenerateCPhoneData", "开始生成CPhone数据，contactId=$contactId, appType=$appType")
            
            // 1. 构建AI提示词（由aidata模块负责）
            val promptRequest = aiPromptService.buildCPhoneDataPrompt(contactId, appType)
            Log.d("GenerateCPhoneData", "提示词构建完成")
            
            // 2. 调用brain模块的AI请求服务
            val aiResponse = callAiApi(promptRequest)
            Log.d("GenerateCPhoneData", "AI请求完成，响应长度: ${aiResponse?.length ?: 0}")
            
            // 3. 解析AI返回的JSON
            val parsedData = parseAiResponse(aiResponse, appType)
            Log.d("GenerateCPhoneData", "数据解析完成")
            
            // 4. 确保数据库中有该联系人的记录
            ensureCPhoneRecordExists(contactId)
            
            // 5. 更新数据库
            updateDatabase(contactId, appType, parsedData)
            Log.d("GenerateCPhoneData", "数据库更新完成")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("GenerateCPhoneData", "生成失败", e)
            Result.failure(e)
        }
    }

    /**
     * 每日自动日记专用路径。
     * 该方法一次模型请求同时生成日记和每日分层摘要，避免额外调用手动摘要服务。
     *
     * 返回值为本次真正落库的每日摘要；若摘要为空或窗口已存在则为 null。
     * 调用方（Worker）拿到非空摘要后负责补建向量索引，保证自动摘要与手动摘要一样可被语义召回。
     */
    suspend fun executeAutomaticDailyDiary(
        contactId: String,
        windowStart: Long,
        windowEnd: Long,
        windowLabel: String
    ): Result<MemorySummary?> = withContext(Dispatchers.IO) {
        try {
            Log.d("GenerateCPhoneData", "开始自动生成每日日记和摘要，contactId=$contactId, window=$windowLabel")
            val promptRequest = aiPromptService.buildAutomaticDailyDiaryPrompt(contactId, windowStart, windowEnd, windowLabel)
            val aiResponse: String = callAiApi(promptRequest)
            val parsedData: AutomaticDailyDiaryResponse = parseAutomaticDailyDiaryResponse(aiResponse)
            ensureCPhoneRecordExists(contactId)
            appendDiaryEntries(contactId, parsedData.diaryEntries)
            val savedSummary: MemorySummary? = saveDailySummary(contactId, windowStart, windowEnd, parsedData)
            Log.d("GenerateCPhoneData", "自动每日日记和摘要追加完成，contactId=$contactId, window=$windowLabel")
            Result.success(savedSummary)
        } catch (exception: Exception) {
            Log.e("GenerateCPhoneData", "自动每日日记和摘要生成失败", exception)
            Result.failure(exception)
        }
    }

    /**
     * 调用brain模块的AI API服务
     * 直接调用suspend函数，不再使用反射
     */
    private suspend fun callAiApi(promptRequest: com.susking.ephone_s.aidata.prompt.AiPromptRequest): String {
        val response = aiRequestService.getChatCompletion(context, promptRequest)
        return response ?: throw Exception("AI请求返回空响应")
    }
    
    /**
     * 确保数据库中有该联系人的CPhone记录
     */
    private suspend fun ensureCPhoneRecordExists(contactId: String) {
        val existing = cphoneRepository.getCPhoneDataSuspend(contactId)
        if (existing == null) {
            // 创建空记录
            cphoneRepository.saveCPhoneData(
                CPhoneData(
                    contactId = contactId,
                    albumPhotos = emptyList(),
                    browserHistory = emptyList(),
                    taobaoData = null,
                    memos = emptyList(),
                    diaryEntries = emptyList(),
                    amapFootprints = emptyList(),
                    appUsageRecords = emptyList(),
                    musicTracks = emptyList(),
                    qqConversations = emptyList()
                )
            )
        }
    }
    
    /**
     * 解析AI响应
     * 支持两种格式:
     * 1. 对象格式(旧): {"photos": [...]}
     * 2. 数组格式(新): [...]
     */
    private fun parseAiResponse(aiResponse: String, appType: String): Any {
        return try {
            // 判断是数组还是对象格式
            val trimmed = aiResponse.trim()
            val isArrayFormat = trimmed.startsWith("[")
            
            when (appType) {
                "album" -> {
                    val photos = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<AlbumPhoto>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, AlbumResponse::class.java)
                        response.photos
                    }
                    Log.d("GenerateCPhoneData", "解析到${photos.size}条相册照片")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedPhotos = photos.mapIndexed { index, photo ->
                        if (photo.id.isBlank()) {
                            photo.copy(id = (baseTimestamp + index).toString())
                        } else {
                            photo
                        }
                    }
                    // 过滤无效数据：description或imagePrompt为空
                    // 保留这些字段以便日后重新生成图片
                    val filteredPhotos = processedPhotos.filter {
                        it.description.isNotBlank() && it.imagePrompt.isNotBlank()
                    }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredPhotos.size}条记录")
                    filteredPhotos
                }
                "browser" -> {
                    val records = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<BrowserRecord>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, BrowserResponse::class.java)
                        response.history
                    }
                    Log.d("GenerateCPhoneData", "解析到${records.size}条浏览器记录")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedRecords = records.mapIndexed { index, record ->
                        if (record.id.isBlank()) {
                            record.copy(id = (baseTimestamp + index).toString())
                        } else {
                            record
                        }
                    }
                    // 过滤无效数据：title为空
                    val filteredRecords = processedRecords.filter { it.title.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredRecords.size}条记录")
                    filteredRecords
                }
                "taobao" -> {
                    // 淘宝数据是单一对象格式: {totalBalance, purchases:[...]}
                    val taobaoData = gson.fromJson(aiResponse, TaobaoData::class.java)
                    Log.d("GenerateCPhoneData", "解析到${taobaoData.purchases.size}条淘宝订单")
                    
                    taobaoData.purchases.forEachIndexed { index, purchase ->
                        Log.d("GenerateCPhoneData", "商品[$index] ${purchase.itemName} -> image_prompt: ${purchase.imagePrompt}")
                    }
                    
                    // 如果订单没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedPurchases = taobaoData.purchases.mapIndexed { index, purchase ->
                        if (purchase.id.isBlank()) {
                            purchase.copy(id = (baseTimestamp + index).toString())
                        } else {
                            purchase
                        }
                    }
                    
                    // 过滤无效商品：itemName为空
                    val filteredPurchases = processedPurchases.filter { it.itemName.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredPurchases.size}条订单")
                    
                    taobaoData.copy(purchases = filteredPurchases)
                }
                "memo" -> {
                    val memos = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<Memo>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, MemoResponse::class.java)
                        response.memos
                    }
                    Log.d("GenerateCPhoneData", "解析到${memos.size}条备忘录")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedMemos = memos.mapIndexed { index, memo ->
                        if (memo.id.isBlank()) {
                            memo.copy(id = (baseTimestamp + index).toString())
                        } else {
                            memo
                        }
                    }
                    // 过滤无效数据：title为空
                    val filteredMemos = processedMemos.filter { it.title.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredMemos.size}条记录")
                    filteredMemos
                }
                "diary" -> {
                    val entries = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<DiaryEntry>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, DiaryResponse::class.java)
                        response.entries
                    }
                    Log.d("GenerateCPhoneData", "解析到${entries.size}条日记")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedEntries = entries.mapIndexed { index, entry ->
                        if (entry.id.isBlank()) {
                            entry.copy(id = (baseTimestamp + index).toString())
                        } else {
                            entry
                        }
                    }
                    // 过滤无效数据：title为空
                    val filteredEntries = processedEntries.filter { it.title.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredEntries.size}条记录")
                    filteredEntries
                }
                "amap" -> {
                    val footprints = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<AmapFootprint>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, AmapResponse::class.java)
                        response.footprints
                    }
                    Log.d("GenerateCPhoneData", "解析到${footprints.size}条高德地图足迹")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedFootprints = footprints.mapIndexed { index, footprint ->
                        if (footprint.id.isBlank()) {
                            footprint.copy(id = (baseTimestamp + index).toString())
                        } else {
                            footprint
                        }
                    }
                    // 过滤无效数据：locationName为空
                    val filteredFootprints = processedFootprints.filter { it.locationName.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredFootprints.size}条记录")
                    filteredFootprints
                }
                "appUsage" -> {
                    val records = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<AppUsageRecord>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, AppUsageResponse::class.java)
                        response.records
                    }
                    Log.d("GenerateCPhoneData", "解析到${records.size}条App使用记录")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedRecords = records.mapIndexed { index, record ->
                        if (record.id.isBlank()) {
                            record.copy(id = (baseTimestamp + index).toString())
                        } else {
                            record
                        }
                    }
                    // 过滤掉无效数据：appName为空的记录
                    val filteredRecords = processedRecords.filter { it.appName.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredRecords.size}条记录")
                    filteredRecords
                }
                "music" -> {
                    val tracks = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<MusicTrack>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, MusicResponse::class.java)
                        response.tracks
                    }
                    Log.d("GenerateCPhoneData", "解析到${tracks.size}条音乐歌曲")
                    // 如果记录没有id，使用时间戳作为id
                    val baseTimestamp = System.currentTimeMillis()
                    val processedTracks = tracks.mapIndexed { index, track ->
                        if (track.id.isBlank()) {
                            track.copy(id = (baseTimestamp + index).toString())
                        } else {
                            track
                        }
                    }
                    // 过滤无效数据：songName为空
                    val filteredTracks = processedTracks.filter { it.songName.isNotBlank() }
                    Log.d("GenerateCPhoneData", "过滤后剩余${filteredTracks.size}条记录")
                    filteredTracks
                }
                "qq" -> {
                    val conversations = if (isArrayFormat) {
                        gson.fromJson(aiResponse, Array<QQConversation>::class.java).toList()
                    } else {
                        val response = gson.fromJson(aiResponse, QQResponse::class.java)
                        response.conversations
                    }
                    // 过滤无效对话：id或contactName为空，同时过滤对话中的无效消息
                    conversations.filter { it.id.isNotBlank() && it.contactName.isNotBlank() }
                        .map { conv ->
                            conv.copy(
                                messages = conv.messages.filter {
                                    it.id.isNotBlank() && it.content.isNotBlank()
                                }
                            )
                        }
                        .filter { it.messages.isNotEmpty() } // 过滤掉没有有效消息的对话
                }
                else -> throw IllegalArgumentException("不支持的App类型: $appType")
            }
        } catch (e: JsonSyntaxException) {
            Log.e("GenerateCPhoneData", "JSON解析失败，原始响应: $aiResponse", e)
            throw Exception("AI返回的JSON格式错误: ${e.message}\n原始响应: ${aiResponse.take(200)}")
        }
    }
    
    /**
     * 解析每日自动联合生成响应。
     */
    private fun parseAutomaticDailyDiaryResponse(aiResponse: String): AutomaticDailyDiaryResponse {
        return try {
            val cleanedResponse: String = aiResponse.replace("```json", "").replace("```", "").trim()
            val jsonObject: JsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val baseTimestamp: Long = System.currentTimeMillis()
            val diaryElements: List<JsonElement> = getJsonArrayElements(jsonObject, "diaryEntries", "entries", "diary_entries")
            val processedEntries: List<DiaryEntry> = diaryElements.mapIndexedNotNull { index: Int, element: JsonElement ->
                val entry: DiaryEntry = gson.fromJson(element, DiaryEntry::class.java)
                val entryWithId: DiaryEntry = if (entry.id.isBlank()) {
                    entry.copy(id = (baseTimestamp + index).toString())
                } else {
                    entry
                }
                entryWithId.takeIf { diaryEntry: DiaryEntry -> diaryEntry.title.isNotBlank() && diaryEntry.content.isNotBlank() }
            }
            val dailySummary: String = getStringValue(jsonObject, "dailySummary", "summary", "summaryText", "daily_summary").trim()
            val sourceMemoryCount: Int = getIntValue(jsonObject, "sourceMemoryCount", "sourceCount", "source_memory_count")
            val importanceScore: Int = getIntValue(jsonObject, "importanceScore", "importance_score")
                .coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE)
            if (processedEntries.isEmpty() && dailySummary.isBlank()) {
                throw JsonSyntaxException("每日自动日记响应没有可保存的日记或摘要字段")
            }
            AutomaticDailyDiaryResponse(
                diaryEntries = processedEntries,
                dailySummary = dailySummary,
                sourceMemoryCount = sourceMemoryCount,
                importanceScore = importanceScore,
                importanceReason = getStringValue(jsonObject, "importanceReason", "importance_reason"),
                confidenceScore = getFloatValue(jsonObject, "confidenceScore", "confidence_score")
            )
        } catch (exception: JsonSyntaxException) {
            Log.e("GenerateCPhoneData", "每日自动日记JSON解析失败，原始响应: $aiResponse", exception)
            throw Exception("AI返回的每日自动日记JSON格式错误: ${exception.message}\n原始响应: ${aiResponse.take(200)}")
        }
    }

    private fun getJsonArrayElements(jsonObject: JsonObject, vararg fieldNames: String): List<JsonElement> {
        val jsonElement: JsonElement = fieldNames.firstNotNullOfOrNull { fieldName: String ->
            jsonObject.get(fieldName)?.takeIf { element: JsonElement -> element.isJsonArray }
        } ?: return emptyList()
        return jsonElement.asJsonArray.toList()
    }

    private fun getStringValue(jsonObject: JsonObject, vararg fieldNames: String): String {
        return fieldNames.firstNotNullOfOrNull { fieldName: String ->
            jsonObject.get(fieldName)?.takeIf { element: JsonElement -> !element.isJsonNull }?.asString
        }.orEmpty()
    }

    private fun getIntValue(jsonObject: JsonObject, vararg fieldNames: String): Int {
        return fieldNames.firstNotNullOfOrNull { fieldName: String ->
            jsonObject.get(fieldName)?.takeIf { element: JsonElement -> !element.isJsonNull }?.asInt
        } ?: DEFAULT_IMPORTANCE_SCORE
    }

    private fun getFloatValue(jsonObject: JsonObject, vararg fieldNames: String): Float {
        return fieldNames.firstNotNullOfOrNull { fieldName: String ->
            jsonObject.get(fieldName)?.takeIf { element: JsonElement -> !element.isJsonNull }?.asFloat
        } ?: DEFAULT_CONFIDENCE_SCORE
    }

    /**
     * 保存每日自动联合请求返回的分层摘要。
     *
     * @return 真正写入数据库的摘要；摘要为空或窗口已存在时返回 null。
     */
    private suspend fun saveDailySummary(
        contactId: String,
        windowStart: Long,
        windowEnd: Long,
        response: AutomaticDailyDiaryResponse
    ): MemorySummary? {
        if (response.dailySummary.isBlank()) return null
        val existingSummary: MemorySummary? = memorySummaryDao.getSummaryForWindow(contactId, SummaryLevel.DAILY, windowStart, windowEnd)
        if (existingSummary != null) return null
        val summary = MemorySummary(
            contactId = contactId,
            summaryLevel = SummaryLevel.DAILY,
            startTimestamp = windowStart,
            endTimestamp = windowEnd,
            summaryText = response.dailySummary,
            sourceMemoryCount = response.sourceMemoryCount.coerceAtLeast(MIN_SOURCE_MEMORY_COUNT),
            importanceScore = response.importanceScore.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
            modelVersion = AUTOMATIC_DIARY_MODEL_VERSION
        )
        memorySummaryDao.insert(summary)
        return summary
    }

    /**
     * 追加自动生成的日记。
     * 标题直接沿用 AI 返回的原文，不再附加任何前缀标识。
     */
    private suspend fun appendDiaryEntries(contactId: String, newEntries: List<DiaryEntry>) {
        val currentData: CPhoneData? = cphoneRepository.getCPhoneDataSuspend(contactId)
        val currentEntries: List<DiaryEntry> = currentData?.diaryEntries ?: emptyList()
        val mergedEntries: List<DiaryEntry> = (newEntries + currentEntries).distinctBy { entry: DiaryEntry -> entry.id }
        cphoneRepository.updateDiaryEntries(contactId, mergedEntries)
    }

    /**
     * 更新数据库
     */
    private suspend fun updateDatabase(contactId: String, appType: String, data: Any) {
        when (appType) {
            "album" -> {
                val photos = data as List<AlbumPhoto>
                // 只保存照片数据到数据库(不含图片)
                // 不再自动调度图片生成,用户需要手动点击生成图片按钮
                cphoneRepository.updateAlbumPhotos(contactId, photos)
            }
            "browser" -> cphoneRepository.updateBrowserHistory(contactId, data as List<BrowserRecord>)
            "taobao" -> cphoneRepository.updateTaobaoData(contactId, data as TaobaoData)
            "memo" -> cphoneRepository.updateMemos(contactId, data as List<Memo>)
            "diary" -> cphoneRepository.updateDiaryEntries(contactId, data as List<DiaryEntry>)
            "amap" -> cphoneRepository.updateAmapFootprints(contactId, data as List<AmapFootprint>)
            "appUsage" -> cphoneRepository.updateAppUsageRecords(contactId, data as List<AppUsageRecord>)
            "music" -> cphoneRepository.updateMusicTracks(contactId, data as List<MusicTrack>)
            "qq" -> cphoneRepository.updateQQConversations(contactId, data as List<QQConversation>)
        }
    }
    
    // ========== 响应数据类 ==========
    
    private data class AutomaticDailyDiaryResponse(
        val diaryEntries: List<DiaryEntry> = emptyList(),
        val dailySummary: String = "",
        val sourceMemoryCount: Int = 0,
        val importanceScore: Int = DEFAULT_IMPORTANCE_SCORE,
        val importanceReason: String = "",
        val confidenceScore: Float = DEFAULT_CONFIDENCE_SCORE
    )

    private data class AlbumResponse(val photos: List<AlbumPhoto>)
    private data class BrowserResponse(val history: List<BrowserRecord>)
    private data class MemoResponse(val memos: List<Memo>)
    private data class DiaryResponse(val entries: List<DiaryEntry>)
    private data class AmapResponse(val footprints: List<AmapFootprint>)
    private data class AppUsageResponse(val records: List<AppUsageRecord>)
    private data class MusicResponse(val tracks: List<MusicTrack>)
    private data class QQResponse(val conversations: List<QQConversation>)

    private companion object {
        private const val MIN_SOURCE_MEMORY_COUNT: Int = 1
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.8f
        private const val AUTOMATIC_DIARY_MODEL_VERSION: String = "automatic_cphone_diary"
    }
}