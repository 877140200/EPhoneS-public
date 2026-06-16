package com.susking.ephone_s.settings.feedback

import com.google.gson.Gson
import com.google.gson.JsonArray
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
 * 意见反馈远程服务。
 *
 * 负责把用户填写的反馈（文字 + 图片 + 类别 + 激活码 + 设备码）POST 到 Cloudflare Worker
 * 的 /feedback 接口，由 Worker 在服务端转发到 Server酱（推送到作者微信），
 * 并在 KV 中留存一份完整备份（含 base64 图片）。
 *
 * 设计要点：
 *  - Server酱 的 SendKey 等密钥全部锁在 Worker 服务端，不进 APK，避免被反编译盗用刷消息。
 *  - 图片以 base64 数组形式上传，Worker 完整存 KV，微信推送只显示图片数量和 KV 键名。
 *  - 反馈是"发出去就完事"的一次性操作，不需要账本与一致性，任何静态/serverless 都能接。
 *  - 免费 workers.dev 国内访问不稳，失败时返回 [FeedbackResult.Error] 由界面提示重试。
 */
class FeedbackRemoteService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(RemoteConfig.NETWORK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    private val gson: Gson = Gson()

    /**
     * 提交一条反馈。
     *
     * @param content 用户填写的反馈内容
     * @param category 问题类别（功能异常/界面显示/数据丢失/性能卡顿/功能建议/其他）
     * @param contact 可选的联系方式，便于作者回复，可为空
     * @param appVersion 当前 app 版本名，便于定位问题
     * @param activationCode 激活码，用于识别用户身份
     * @param fingerprint 设备指纹，用于关联设备
     * @param images 图片 base64 列表，最多 10 张
     * @return 提交结果
     */
    suspend fun submit(
        content: String,
        category: String,
        contact: String,
        appVersion: String,
        activationCode: String,
        fingerprint: String,
        images: List<String>
    ): FeedbackResult = withContext(Dispatchers.IO) {
        val payload: JsonObject = JsonObject().apply {
            addProperty(FIELD_CONTENT, content)
            addProperty(FIELD_CATEGORY, category)
            addProperty(FIELD_CONTACT, contact)
            addProperty(FIELD_APP_VERSION, appVersion)
            addProperty(FIELD_ACTIVATION_CODE, activationCode)
            addProperty(FIELD_FINGERPRINT, fingerprint)
            val imagesArray = JsonArray()
            images.forEach { imagesArray.add(it) }
            add(FIELD_IMAGES, imagesArray)
        }
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request: Request = Request.Builder()
            .url(RemoteConfig.feedbackUrl)
            .post(body)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    FeedbackResult.Success
                } else {
                    FeedbackResult.Error("提交失败（HTTP ${response.code}）")
                }
            }
        } catch (e: IOException) {
            FeedbackResult.Error(e.message ?: "网络连接失败")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val FIELD_CONTENT: String = "content"
        private const val FIELD_CATEGORY: String = "category"
        private const val FIELD_CONTACT: String = "contact"
        private const val FIELD_APP_VERSION: String = "appVersion"
        private const val FIELD_ACTIVATION_CODE: String = "activationCode"
        private const val FIELD_FINGERPRINT: String = "fingerprint"
        private const val FIELD_IMAGES: String = "images"
    }
}

/**
 * 反馈提交结果。
 */
sealed class FeedbackResult {

    /** 提交成功。 */
    data object Success : FeedbackResult()

    /** 提交失败（网络异常或服务端错误），[message] 为简要原因。 */
    data class Error(val message: String) : FeedbackResult()
}
