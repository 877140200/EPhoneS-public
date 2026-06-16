package com.susking.ephone_s.aidata.domain.model

import com.google.gson.annotations.SerializedName

/**
 * CPhone（查手机）数据模型
 * 存储AI角色的模拟手机数据
 * V2.2 - 完全按照JS提示词重构
 */
data class CPhoneData(
    val contactId: String,
    
    // 相册数据
    val albumPhotos: List<AlbumPhoto> = emptyList(),
    
    // 浏览器历史
    val browserHistory: List<BrowserRecord> = emptyList(),
    
    // 淘宝购物数据(包含余额和购买记录)
    val taobaoData: TaobaoData? = null,
    
    // 备忘录
    val memos: List<Memo> = emptyList(),
    
    // 日记
    val diaryEntries: List<DiaryEntry> = emptyList(),
    
    // 高德地图足迹
    val amapFootprints: List<AmapFootprint> = emptyList(),
    
    // App使用记录
    val appUsageRecords: List<AppUsageRecord> = emptyList(),
    
    // 音乐歌曲
    val musicTracks: List<MusicTrack> = emptyList(),
    
    // QQ模拟对话
    val qqConversations: List<QQConversation> = emptyList(),
    
    // 最后更新时间
    val lastUpdated: Long = System.currentTimeMillis()
)

// ========== 相册 ==========
/**
 * 相册照片
 * 对应JS: { description, image_prompt }
 */
data class AlbumPhoto(
    val id: String = "",
    val description: String = "", // 照片背后的故事或角色的心情日记,第一人称"我"
    @SerializedName("image_prompt")
    val imagePrompt: String = "a photo", // 用于生成照片的英文关键词
    @SerializedName("image_url")
    val imageUrl: String? = null, // 生成后的图片URL
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("is_favorite")
    val isFavorite: Boolean = false
)

// ========== 浏览器 ==========
/**
 * 浏览器记录
 * 对应JS: { type: "text", title, url, content }
 */
data class BrowserRecord(
    val id: String = "",
    val type: String = "text", // 固定为"text"
    val title: String = "未知标题", // 文章或搜索标题
    val url: String = "https://example.com", // 虚构的网址
    val content: String = "", // 200-400字的文章或帖子正文
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

// ========== 淘宝 ==========
/**
 * 淘宝数据(单一对象)
 * 对应JS: { totalBalance, purchases: [...] }
 */
data class TaobaoData(
    val totalBalance: Double = 0.0, // 账户总余额
    val purchases: List<TaobaoPurchase> = emptyList() // 12-15个商品
)

/**
 * 淘宝购买记录
 * 对应JS: { itemName, price, status, reason, image_prompt }
 */
data class TaobaoPurchase(
    val id: String = "",
    val itemName: String = "未知商品", // 商品名称
    val price: Double = 0.0, // 商品价格
    val status: String = "已签收", // 订单状态: "已签收", "待发货", "运输中", "待评价"
    val reason: String = "", // 购买理由,第一人称"我"
    @SerializedName("image_prompt")
    val imagePrompt: String = "product photo, white background", // 生成商品图片的英文关键词
    @SerializedName("image_url")
    val imageUrl: String? = null // 生成后的图片URL
)

// ========== 备忘录 ==========
data class Memo(
    val id: String = "",
    val title: String = "未命名",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

// ========== 日记 ==========
data class DiaryEntry(
    val id: String = "",
    val title: String = "未命名",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

// ========== 高德地图 ==========
/**
 * 高德地图足迹
 * 对应JS: { locationName, address, comment, image_prompt, timestamp }
 */
data class AmapFootprint(
    val id: String = "",
    val locationName: String = "未知地点", // 地点名称
    val address: String = "", // 详细地址
    val comment: String = "", // 角色的内心独白或评论,第一人称"我"
    val imagePrompt: String? = null, // (可选)生成地点照片的英文关键词
    val imageUrl: String? = null, // 生成后的图片URL
    val timestamp: String = "" // ISO 8601格式: "2025-09-25T18:30:00Z"
)

// ========== App使用记录 ==========
/**
 * App使用记录
 * 对应JS: { appName, usageTimeMinutes, category, image_prompt }
 */
data class AppUsageRecord(
    val id: String = "",
    val appName: String = "未知应用", // App名称
    val usageTimeMinutes: Int = 0, // 使用时长(分钟)
    val category: String = "其他", // App分类: 社交, 游戏, 影音, 工具, 阅读, 购物
    val imagePrompt: String = "modern app icon, flat design, simple, clean background", // 生成App图标的英文关键词
    val imageUrl: String? = null // 生成后的图片URL
)

// ========== 音乐 ==========
/**
 * 音乐歌曲
 * 对应JS: { songName, artistName }
 */
data class MusicTrack(
    val id: String = "",
    val songName: String = "未知歌曲", // 歌曲名称
    val artistName: String = "未知艺术家", // 艺术家/歌手名
    val album: String = "未知专辑", // 专辑名(可选,用于本地扩展)
    val duration: Int = 0, // 单位：秒(可选,用于本地扩展)
    var coverUrl: String? = null, // 封面URL(可选,用于本地扩展)
    var playUrl: String? = null, // 播放URL(可选,用于本地扩展)
    var lrcContent: String = "", // 歌词内容(可选,用于本地扩展)
    val isFavorite: Boolean = false
)

// ========== QQ模拟对话 ==========
/**
 * QQ对话记录
 * 对应JS: { contactName, contactAvatar, lastMessage, lastMessageTime, messages: [...] }
 */
data class QQConversation(
    val id: String = "",
    val contactName: String = "未知联系人", // 联系人名字
    val contactAvatar: String = "默认头像", // 对方头像的简短描述
    val lastMessage: String = "", // 最后一条消息内容
    val lastMessageTime: String = "", // ISO 8601格式
    val messages: List<QQMessage> = emptyList() // 3-8条消息
)

/**
 * QQ消息
 * 对应JS: { senderName, content, timestamp, isSentByMe }
 */
data class QQMessage(
    val id: String = "",
    val senderName: String = "未知", // 发送者名字
    val content: String = "", // 消息内容
    val timestamp: String = "", // ISO 8601格式
    val isSentByMe: Boolean = false // true=角色发送, false=对方发送
)