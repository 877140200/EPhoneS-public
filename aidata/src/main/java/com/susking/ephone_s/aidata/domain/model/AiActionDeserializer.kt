package com.susking.ephone_s.aidata.domain.model

import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

/**
 * 这是一个自定义的Gson反序列化器。
 * 它的作用是查看JSON对象中的 "type" 字段，然后根据该字段的值，
 * 决定将这个JSON对象转换成哪个具体的 `AiAction` 数据类。
 * 这使得我们能够优雅地处理包含多种不同行动指令的JSON数组。
 */
class AiActionDeserializer : JsonDeserializer<AiAction> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AiAction {
        val jsonObject = when {
            json?.isJsonObject == true -> json.asJsonObject
            json?.isJsonPrimitive == true && json.asJsonPrimitive.isString -> {
                val rawString = json.asString.trim()
                val contentToParse = if (rawString.startsWith("```json")) {
                    rawString.substringAfter("```json").substringBeforeLast("```").trim()
                } else {
                    rawString
                }

                if (contentToParse.isNotEmpty()) {
                    try {
                        JsonParser.parseString(contentToParse).asJsonObject
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        } ?: return AiAction.Unknown()

        // 从JSON中获取"type"字段的值
        val type = jsonObject.get("type")?.asString

        // 使用 when 表达式来根据 type 决定反序列化成哪个具体的类
        return when (type) {
            "thought_chain" -> context?.deserialize(jsonObject, AiAction.ThoughtChain::class.java)
            "text", "offline_text" -> context?.deserialize(jsonObject, AiAction.Text::class.java) // offline_text也当作text处理
            "error" -> context?.deserialize(jsonObject, AiAction.ErrorMessage::class.java) // error类型专门处理错误消息
            "sticker" -> context?.deserialize(jsonObject, AiAction.Sticker::class.java)
            "update_thoughts" -> context?.deserialize(jsonObject, AiAction.UpdateThoughts::class.java)
            "update_semantic_state" -> context?.deserialize(jsonObject, AiAction.UpdateSemanticState::class.java)
            "qzone_post" -> context?.deserialize(jsonObject, AiAction.QzonePost::class.java)
            "qzone_like" -> context?.deserialize(jsonObject, AiAction.QzoneLike::class.java)
            "qzone_comment" -> context?.deserialize(jsonObject, AiAction.QzoneComment::class.java)
            "qzone_delete_post" -> context?.deserialize(jsonObject, AiAction.QzoneDeletePost::class.java)
            "qzone_share_post" -> context?.deserialize(jsonObject, AiAction.QzoneSharePost::class.java)
            "naiimag", "ai_image" -> context?.deserialize(jsonObject, AiAction.NaiImage::class.java)
            "update_status" -> context?.deserialize(jsonObject, AiAction.UpdateStatus::class.java)
            "waimai_order" -> {
                val productInfo = if (jsonObject.has("productInfo") && !jsonObject.get("productInfo").isJsonNull) jsonObject.get("productInfo").asString else null
                val amountElement = jsonObject.get("amount")
                Log.d("AiActionDeserializer", "WaimaiOrder amount element: $amountElement")
                val amount = if (amountElement != null && !amountElement.isJsonNull && amountElement.isJsonPrimitive) {
                    amountElement.asJsonPrimitive.let {
                        if (it.isNumber) it.asNumber.toDouble()
                        else if (it.isString) it.asString.toDoubleOrNull()
                        else null
                    }
                } else {
                    null
                }
                val greeting = if (jsonObject.has("greeting") && !jsonObject.get("greeting").isJsonNull) jsonObject.get("greeting").asString else null
                val senderName = if (jsonObject.has("senderName") && !jsonObject.get("senderName").isJsonNull) jsonObject.get("senderName").asString else null
                val recipientName = if (jsonObject.has("recipientName") && !jsonObject.get("recipientName").isJsonNull) jsonObject.get("recipientName").asString else null
                AiAction.WaimaiOrder(
                    type = "waimai_order",
                    productInfo = productInfo,
                    amount = amount,
                    greeting = greeting,
                    senderName = senderName,
                    recipientName = recipientName
                )
            }
            "waimai_request" -> context?.deserialize(jsonObject, AiAction.WaimaiRequest::class.java)
            "transfer" -> context?.deserialize(jsonObject, AiAction.Transfer::class.java)
            "accept_transfer" -> context?.deserialize(jsonObject, AiAction.AcceptTransfer::class.java)
            "decline_transfer" -> context?.deserialize(jsonObject, AiAction.DeclineTransfer::class.java)
            "location_share" -> context?.deserialize(jsonObject, AiAction.LocationShare::class.java)
            "video_call_request" -> context?.deserialize(jsonObject, AiAction.VideoCallRequest::class.java)
            "accept_call" -> context?.deserialize(jsonObject, AiAction.AcceptCall::class.java)
            "decline_call" -> context?.deserialize(jsonObject, AiAction.DeclineCall::class.java)
            "end_call" -> context?.deserialize(jsonObject, AiAction.EndCall::class.java)
            "change_user_nickname" -> context?.deserialize(jsonObject, AiAction.ChangeUserNickname::class.java)
            "quote_reply" -> {
                // 手动解析，避免NumberFormatException
                val targetTimestampElement = jsonObject.get("target_timestamp")
                val targetTimestamp = if (targetTimestampElement != null && !targetTimestampElement.isJsonNull && targetTimestampElement.isJsonPrimitive) {
                    targetTimestampElement.asJsonPrimitive.let {
                        if (it.isNumber) {
                            it.asLong
                        } else if (it.isString) {
                            // 尝试将字符串解析为Long，如果失败返回0
                            it.asString.toLongOrNull() ?: run {
                                Log.w("AiActionDeserializer", "无法解析target_timestamp为Long: ${it.asString}，使用默认值0")
                                0L
                            }
                        } else {
                            0L
                        }
                    }
                } else {
                    0L
                }
                val replyContent = if (jsonObject.has("reply_content") && !jsonObject.get("reply_content").isJsonNull) {
                    jsonObject.get("reply_content").asString
                } else {
                    null
                }
                AiAction.QuoteReply(
                    type = "quote_reply",
                    targetTimestamp = targetTimestamp,
                    replyContent = replyContent
                )
            }
            "approve_shopping_access", "ApproveShoppingAccess" -> context?.deserialize(jsonObject, AiAction.ApproveShoppingAccess::class.java)
            "reject_shopping_access", "RejectShoppingAccess" -> context?.deserialize(jsonObject, AiAction.RejectShoppingAccess::class.java)
            "gift" -> context?.deserialize(jsonObject, AiAction.Gift::class.java)
            "voice_message" -> context?.deserialize(jsonObject, AiAction.VoiceMessage::class.java)
            "create_countdown" -> context?.deserialize(jsonObject, AiAction.CreateCountdown::class.java)
            "create_memory" -> context?.deserialize(jsonObject, AiAction.CreateMemory::class.java)
            "pat_user" -> context?.deserialize(jsonObject, AiAction.PatUser::class.java)
            "send_and_recall" -> context?.deserialize(jsonObject, AiAction.SendAndRecall::class.java)
            "offline_request" -> context?.deserialize(jsonObject, AiAction.OfflineRequest::class.java)
            "image_analysis" -> context?.deserialize(jsonObject, AiAction.ImageAnalysis::class.java)
            // 如果遇到未知的type，将原始JSON保存到Unknown中
            else -> AiAction.Unknown(
                type = type ?: "unknown",
                rawContent = jsonObject.toString()
            )
        } ?: AiAction.Unknown()
    }
}

/**
 * 这是一个自定义的Gson反序列化器，专门用于处理可能包含多种格式的AI行动指令列表。
 * AI返回的`content`字段可能是：
 * 1. 一个包含JSON数组的字符串（"[{...}, {...}]"）。
 * 2. 一个标准的JSON数组 ([{...}, {...}])。
 * 3. 一个代表单一行动的JSON对象 ({...})。
 * 4. 一个普通的文本字符串。
 * 此反序列化器能够稳健地处理以上所有情况，并返回一个统一的 `List<AiAction>`。
 */
class AiActionListDeserializer : JsonDeserializer<List<AiAction>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<AiAction> {
        if (json == null || json.isJsonNull || context == null) {
            return emptyList()
        }

        fun parseAction(element: JsonElement): AiAction? {
            return context.deserialize(element, AiAction::class.java)
        }

        // 情况1：JSON是一个字符串，需要先解析其内容
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            var contentString = json.asString
            if (contentString.isBlank()) return emptyList()

            contentString = contentString.trim()
            if (contentString.startsWith("```json")) {
                contentString = contentString.substringAfter("```json").substringBeforeLast("```").trim()
            }

            return try {
                val parsedElement = JsonParser.parseString(contentString)
                when {
                    parsedElement.isJsonArray -> // 字符串内容是一个JSON数组
                        parsedElement.asJsonArray.mapNotNull { parseAction(it) }
                    parsedElement.isJsonObject -> // 字符串内容是一个JSON对象
                        listOfNotNull(parseAction(parsedElement))
                    else -> // 字符串是普通文本
                        listOf(AiAction.Text(content = contentString))
                }
            } catch (e: JsonSyntaxException) {
                // 字符串不是有效的JSON，视为普通文本
                listOf(AiAction.Text(content = contentString))
            }
        }

        // 情况2：JSON本身就是一个JSON数组
        if (json.isJsonArray) {
            return json.asJsonArray.mapNotNull { parseAction(it) }
        }

        // 情况3：JSON本身就是一个JSON对象
        if (json.isJsonObject) {
            return listOfNotNull(parseAction(json))
        }

        return emptyList()
    }
}