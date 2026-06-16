package com.susking.ephone_s.aidata.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * IP 定位接口响应 DTO。
 *
 * 作为原生 GPS 定位失败时的兜底，使用免费的 ip-api.com：
 * http://ip-api.com/json/?fields=status,lat,lon,city,regionName
 *
 * @property status 请求状态，成功为 "success"
 * @property latitude 纬度
 * @property longitude 经度
 * @property city 城市名
 * @property regionName 省/州名，城市为空时兜底展示
 */
data class IpLocationDto(
    @SerializedName("status")
    val status: String = "",
    @SerializedName("lat")
    val latitude: Double = 0.0,
    @SerializedName("lon")
    val longitude: Double = 0.0,
    @SerializedName("city")
    val city: String = "",
    @SerializedName("regionName")
    val regionName: String = ""
) {
    /**
     * 是否定位成功。
     */
    fun isSuccessful(): Boolean {
        return status.equals("success", ignoreCase = true)
    }

    /**
     * 返回展示用的位置名，优先城市，其次省/州。
     */
    fun resolveLocationName(): String {
        return city.ifBlank { regionName }
    }
}
