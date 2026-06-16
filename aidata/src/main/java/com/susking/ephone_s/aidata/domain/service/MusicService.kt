package com.susking.ephone_s.aidata.domain.service

import com.susking.ephone_s.aidata.domain.model.MusicSearchResult
import com.susking.ephone_s.aidata.domain.model.PlayableSong

/**
 * 音乐服务接口
 * 提供音乐搜索和获取详情功能
 */
interface MusicService {
    
    /**
     * 从网易云音乐搜索歌曲
     * @param name 歌名
     * @param singer 歌手名(可选)
     * @return 搜索结果列表
     */
    suspend fun searchNeteaseMusic(name: String, singer: String? = null): List<MusicSearchResult>
    
    /**
     * 从QQ音乐搜索歌曲
     * @param name 歌名
     * @return 搜索结果列表
     */
    suspend fun searchTencentMusic(name: String): List<MusicSearchResult>
    
    /**
     * 获取可播放的歌曲详情
     * 包含播放链接和歌词，支持备用源
     * @param songData 搜索得到的歌曲信息
     * @return 完整的可播放歌曲信息，失败返回null
     */
    suspend fun getPlayableSongDetails(songData: MusicSearchResult): PlayableSong?
    
    /**
     * 获取歌词
     * @param songId 歌曲ID
     * @param source 音乐源
     * @return 歌词内容
     */
    suspend fun getLyrics(songId: String, source: com.susking.ephone_s.aidata.domain.model.MusicSource): String
}