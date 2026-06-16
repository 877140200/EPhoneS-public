package com.susking.ephone_s.license

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.susking.ephone_s.core.config.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 激活验证远程服务。
 *
 * 负责把「激活码 + 设备指纹」POST 到 Cloudflare Worker 的 /activate 接口，
 * 由 Worker 在 KV 账本中执行“一码绑一台设备、先到先得”的判定，本地只解析结果。
 *
 * 设计要点：
 *  - 真正的占用账本在服务端，客户端无任何密钥，泄露的码到了陌生人手里也会因
 *    设备指纹对不上而被服务端拒绝。
 *  - 免费 workers.dev 国内访问不稳，超时设短（见 [RemoteConfig.NETWORK_TIMEOUT_MILLIS]），
 *    失败由调用方提示重试。
 */
class LicenseRemoteService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    private val gson: Gson = Gson()

    /**
     * 向 Worker 提交激活请求。
     *
     * @param activationCode 用户输入的激活码
     * @param fingerprint 当前设备指纹
     * @return 结构化的激活结果，网络异常归入 [ActivationResult.NetworkError]
     */
    suspend fun activate(
        activationCode: String,
        fingerprint: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        val payload: JsonObject = JsonObject().apply {
            addProperty(FIELD_CODE, activationCode)
            addProperty(FIELD_FINGERPRINT, fingerprint)
        }
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request: Request = Request.Builder()
            .url(RemoteConfig.activateUrl)
            .post(body)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                parseResponse(response.code, response.body?.string())
            }
        } catch (e: IOException) {
            ActivationResult.NetworkError(e.message ?: "网络连接失败")
        }
    }

    /**
     * 解析 Worker 返回的 JSON。
     *
     * 约定响应格式：{ "success": true } 或 { "success": false, "reason": "occupied" }
     *
     * @param httpCode HTTP 状态码
     * @param rawBody 响应体文本
     * @return 对应的激活结果
     */
    private fun parseResponse(httpCode: Int, rawBody: String?): ActivationResult {
        if (rawBody.isNullOrBlank()) {
            return ActivationResult.NetworkError("服务器无响应（HTTP $httpCode）")
        }
        return try {
            val json: JsonObject = gson.fromJson(rawBody, JsonObject::class.java)
            val isSuccess: Boolean = json.get(FIELD_SUCCESS)?.asBoolean ?: false
            if (isSuccess) {
                ActivationResult.Success
            } else {
                val reason: String = json.get(FIELD_REASON)?.asString ?: REASON_INVALID
                ActivationResult.Rejected(mapReason(reason))
            }
        } catch (e: Exception) {
            ActivationResult.NetworkError("响应解析失败：${e.message}")
        }
    }

    /**
     * 将服务端 reason 代码映射为给用户看的中文文案。
     *
     * @param reason 服务端返回的失败原因代码
     * @return 面向用户的提示文案
     */
    private fun mapReason(reason: String): String = when (reason) {
        REASON_OCCUPIED -> "该激活码已绑定其他设备，无法在本机使用"
        REASON_INVALID -> "激活码无效，请检查是否输入正确"
        REASON_REVOKED -> "该激活码已被停用"
        else -> "激活失败：$reason"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val FIELD_CODE: String = "code"
        private const val FIELD_FINGERPRINT: String = "fingerprint"
        private const val FIELD_SUCCESS: String = "success"
        private const val FIELD_REASON: String = "reason"

        private const val REASON_OCCUPIED: String = "occupied"
        private const val REASON_INVALID: String = "invalid"
        private const val REASON_REVOKED: String = "revoked"
    }
}

/**
 * 激活结果密封类，覆盖成功、被服务端拒绝、网络异常三种情况。
 */
sealed class ActivationResult {

    /** 激活成功，可保存本地状态并放行。 */
    data object Success : ActivationResult()

    /** 服务端明确拒绝（码无效 / 已被占用 / 已停用），[message] 为面向用户的文案。 */
    data class Rejected(val message: String) : ActivationResult()

    /** 网络层面失败（超时、无网络、解析异常），[message] 为简要原因，调用方应提示重试。 */
    data class NetworkError(val message: String) : ActivationResult()
}
