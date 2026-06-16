package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CPhone数据库实体类
 * 用于存储指定角色的所有App数据
 * 
 * @property characterId 角色ID（主键）
 * @property albumDataJson 相册数据JSON字符串（List<AlbumPhoto>）
 * @property browserDataJson 浏览器历史数据JSON字符串（List<BrowserHistory>）
 * @property taobaoDataJson 淘宝购物数据JSON字符串（List<TaobaoHistory>）
 * @property memoDataJson 备忘录数据JSON字符串（List<Memo>）
 * @property diaryDataJson 日记数据JSON字符串（List<DiaryEntry>）
 * @property amapDataJson 高德地图足迹数据JSON字符串（List<AmapFootprint>）
 * @property appUsageDataJson App使用记录数据JSON字符串（List<AppUsageRecord>）
 * @property musicDataJson 音乐歌曲数据JSON字符串（List<MusicTrack>）
 * @property qqDataJson QQ模拟对话数据JSON字符串（List<SimulatedConversation>）
 * @property lastUpdated 最后更新时间戳
 */
@Entity(tableName = "cphone_data")
data class CPhoneDataEntity(
    @PrimaryKey
    val characterId: String,
    val albumDataJson: String? = null,
    val browserDataJson: String? = null,
    val taobaoDataJson: String? = null,
    val memoDataJson: String? = null,
    val diaryDataJson: String? = null,
    val amapDataJson: String? = null,
    val appUsageDataJson: String? = null,
    val musicDataJson: String? = null,
    val qqDataJson: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)