package com.susking.ephone_s.aidata.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 音乐API响应数据模型
 * 对应JavaScript中的音乐源API响应
 */

/**
 * 网易云/QQ音乐API响应
 */
data class MusicApiResponse(
    val code: Int = 0,
    val data: List<MusicSearchResult>? = null,
    val message: String? = null
)

/**
 * 音乐搜索结果
 * 对应JS中的搜索结果格式
 */
data class MusicSearchResult(
    val id: String = "",
    val song: String = "", // 歌曲名
    val singer: String = "", // 歌手名
    val cover: String? = null, // 封面URL
    val source: MusicSource = MusicSource.NETEASE // 音乐源
)

/**
 * 音乐详情API响应
 * 用于获取播放链接和歌词
 */
data class MusicDetailResponse(
    val code: Int = 0,
    val data: MusicDetailData? = null,
    val message: String? = null
)

/**
 * 音乐详情数据
 */
data class MusicDetailData(
    val url: String? = null, // 播放链接
    val lrc: String? = null, // 歌词(网易云)
    val lyric: String? = null, // 歌词(QQ音乐)
    val trans: String? = null, // 翻译歌词(网易云)
    val tlyric: String? = null // 翻译歌词(QQ音乐)
)

/**
 * 音乐源枚举
 */
enum class MusicSource {
    @SerializedName("netease")
    NETEASE, // 网易云音乐
    
    @SerializedName("tencent")
    TENCENT // QQ音乐(腾讯)
}

/**
 * 完整的可播放歌曲信息
 * 对应JS中getPlayableSongDetails返回的对象
 */
data class PlayableSong(
    val name: String = "",
    val artist: String = "",
    val src: String = "", // 播放URL
    val cover: String = "", // 封面URL
    val isLocal: Boolean = false,
    val lrcContent: String = "" // 歌词内容
)