package com.susking.ephone_s.aidata.data.service

import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.MusicApiResponse
import com.susking.ephone_s.aidata.domain.model.MusicDetailResponse
import com.susking.ephone_s.aidata.domain.model.MusicSearchResult
import com.susking.ephone_s.aidata.domain.model.MusicSource
import com.susking.ephone_s.aidata.domain.model.PlayableSong
import com.susking.ephone_s.aidata.domain.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐服务实现类
 * 对应JavaScript中的音乐源获取逻辑
 */
@Singleton
class MusicServiceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : MusicService {

    companion object {
        private const val API_BASE_URL = "https://api.vkeys.cn/v2/music"
        private const val DEFAULT_COVER = "https://i.postimg.cc/pT2xKzPz/album-cover-placeholder.png"
        private const val MAX_RESULTS = 30
    }

    /**
     * 从网易云音乐搜索歌曲
     * 对应JS: searchNeteaseMusic(name, singer)
     */
    override suspend fun searchNeteaseMusic(
        name: String,
        singer: String?
    ): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            // 移除空格并组合搜索词
            var searchTerm = name.replace("\\s".toRegex(), "")
            if (!singer.isNullOrEmpty()) {
                searchTerm += " ${singer.replace("\\s".toRegex(), "")}"
            }

            val encodedTerm = URLEncoder.encode(searchTerm, "UTF-8")
            val apiUrl = "$API_BASE_URL/netease?word=$encodedTerm"

            val request = Request.Builder()
                .url(apiUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val apiResponse = gson.fromJson(responseBody, MusicApiResponse::class.java)

            if (apiResponse.code != 200 || apiResponse.data.isNullOrEmpty()) {
                return@withContext emptyList()
            }

            // 转换为统一格式
            apiResponse.data.map { song ->
                MusicSearchResult(
                    id = song.id,
                    song = song.song,
                    singer = song.singer,
                    cover = song.cover?.ifEmpty { DEFAULT_COVER } ?: DEFAULT_COVER,
                    source = MusicSource.NETEASE
                )
            }.take(MAX_RESULTS)

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 从QQ音乐搜索歌曲
     * 对应JS: searchTencentMusic(name)
     */
    override suspend fun searchTencentMusic(name: String): List<MusicSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val searchTerm = name.replace("\\s".toRegex(), "")
                val encodedTerm = URLEncoder.encode(searchTerm, "UTF-8")
                val apiUrl = "$API_BASE_URL/tencent?word=$encodedTerm"

                val request = Request.Builder()
                    .url(apiUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }
    
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                val apiResponse = gson.fromJson(responseBody, MusicApiResponse::class.java)

                if (apiResponse.code != 200 || apiResponse.data.isNullOrEmpty()) {
                    return@withContext emptyList()
                }

                // 转换为统一格式
                apiResponse.data.map { song ->
                    MusicSearchResult(
                        id = song.id,
                        song = song.song,
                        singer = song.singer,
                        cover = song.cover?.ifEmpty { DEFAULT_COVER } ?: DEFAULT_COVER,
                        source = MusicSource.TENCENT
                    )
                }.take(MAX_RESULTS)

            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * 获取可播放的歌曲详情
     * 对应JS: getPlayableSongDetails(songData)
     * 支持主源失败时自动尝试备用源
     */
    override suspend fun getPlayableSongDetails(songData: MusicSearchResult): PlayableSong? =
        withContext(Dispatchers.IO) {
            var playUrl: String? = null
            var finalSource = songData.source
            var finalId = songData.id

            // 尝试从主源获取播放链接
            playUrl = fetchPlayUrl(songData.id, songData.source)

            // 如果主源失败，尝试备用源
            if (playUrl == null) {
                val fallbackSource = if (songData.source == MusicSource.NETEASE) {
                    MusicSource.TENCENT
                } else {
                    MusicSource.NETEASE
                }

                val fallbackResults = if (fallbackSource == MusicSource.TENCENT) {
                    searchTencentMusic(songData.song)
                } else {
                    searchNeteaseMusic(songData.song, songData.singer)
                }

                if (fallbackResults.isNotEmpty()) {
                    playUrl = fetchPlayUrl(fallbackResults[0].id, fallbackSource)
                    if (playUrl != null) {
                        finalSource = fallbackSource
                        finalId = fallbackResults[0].id
                    }
                }
            }

            // 如果获取到了播放链接，返回完整信息
            if (playUrl != null) {
                // 确保URL使用HTTPS
                val securePlayUrl = playUrl.replace("^http://".toRegex(RegexOption.IGNORE_CASE), "https://")
                val coverToUse = if (songData.cover.isNullOrEmpty()) DEFAULT_COVER else songData.cover
                val secureCoverUrl = coverToUse.replace("^http://".toRegex(RegexOption.IGNORE_CASE), "https://")

                // 获取歌词
                val lyrics = getLyrics(finalId, finalSource)

                PlayableSong(
                    name = songData.song,
                    artist = songData.singer,
                    src = securePlayUrl,
                    cover = secureCoverUrl,
                    isLocal = false,
                    lrcContent = lyrics
                )
            } else {
                null
            }
        }

    /**
     * 获取歌词
     * 对应JS: getLyricsForSong(songId, source)
     */
    override suspend fun getLyrics(
        songId: String,
        source: MusicSource
    ): String = withContext(Dispatchers.IO) {
        try {
            val sourceStr = if (source == MusicSource.NETEASE) "netease" else "tencent"
            val apiUrl = "$API_BASE_URL/$sourceStr/lyric?id=$songId"

            val request = Request.Builder()
                .url(apiUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext ""
            }

            val responseBody = response.body?.string() ?: return@withContext ""
            val detailResponse = gson.fromJson(responseBody, MusicDetailResponse::class.java)

            if (detailResponse.data == null) {
                return@withContext ""
            }

            val data = detailResponse.data
            val lrc = data.lrc ?: data.lyric ?: ""
            val tlyric = data.trans ?: data.tlyric ?: ""

            // 合并原文和翻译歌词
            if (tlyric.isNotEmpty()) {
                "$lrc\n$tlyric"
            } else {
                lrc
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 从指定源获取播放URL
     * @param songId 歌曲ID
     * @param source 音乐源
     * @return 播放URL，失败返回null
     */
    private suspend fun fetchPlayUrl(songId: String, source: MusicSource): String? =
        withContext(Dispatchers.IO) {
            try {
                val sourceStr = if (source == MusicSource.NETEASE) "netease" else "tencent"
                val apiUrl = "$API_BASE_URL/$sourceStr?id=$songId"

                val request = Request.Builder()
                    .url(apiUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext null
                }
    
                val responseBody = response.body?.string() ?: return@withContext null
                val detailResponse = gson.fromJson(responseBody, MusicDetailResponse::class.java)

                detailResponse.data?.url

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}