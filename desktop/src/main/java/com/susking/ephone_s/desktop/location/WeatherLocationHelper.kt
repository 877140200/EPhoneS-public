package com.susking.ephone_s.desktop.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * 天气定位辅助类。
 *
 * 基于原生 [LocationManager] 读取系统最近一次已知位置（last known location），
 * 不主动发起单次定位请求——last known 已足够天气场景使用，且避免回调与超时的复杂度，符合简洁原则。
 *
 * 当无定位权限或拿不到任何已知位置时返回 null，由调用方决定是否走 IP 定位兜底。
 *
 * @property context 用于访问系统定位服务与权限检查的上下文
 */
class WeatherLocationHelper(
    private val context: Context
) {

    /**
     * 是否已授予粗定位权限。
     * 天气场景只需城市级精度，故仅检查 ACCESS_COARSE_LOCATION。
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 读取系统最近一次已知位置，遍历多个 provider 取时间戳最新的一个。
     *
     * @return 经纬度对（纬度, 经度），无权限或无任何已知位置时返回 null
     */
    fun getLastKnownLocation(): LocationPoint? {
        if (!hasLocationPermission()) {
            return null
        }
        val locationManager: LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val candidateProviders: List<String> = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider: String in candidateProviders) {
            val location: Location? = readProviderLocationSafely(locationManager, provider)
            if (location != null && isNewerThan(location, bestLocation)) {
                bestLocation = location
            }
        }
        return bestLocation?.let { safeLocation: Location ->
            LocationPoint(
                latitude = safeLocation.latitude,
                longitude = safeLocation.longitude
            )
        }
    }

    /**
     * 安全读取指定 provider 的已知位置，provider 不存在或抛 SecurityException 时返回 null。
     */
    private fun readProviderLocationSafely(
        locationManager: LocationManager,
        provider: String
    ): Location? {
        return try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.getLastKnownLocation(provider)
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * 判断 candidate 是否比 current 更新（时间戳更大）。current 为 null 时视为更新。
     */
    private fun isNewerThan(candidate: Location, current: Location?): Boolean {
        if (current == null) {
            return true
        }
        return candidate.time > current.time
    }

    /**
     * 定位结果坐标点。
     *
     * @property latitude 纬度
     * @property longitude 经度
     */
    data class LocationPoint(
        val latitude: Double,
        val longitude: Double
    )
}
