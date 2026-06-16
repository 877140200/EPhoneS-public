package com.susking.ephone_s.qq.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.domain.model.AiAction
import com.susking.ephone_s.aidata.domain.model.AiActionDeserializer
import com.susking.ephone_s.aidata.domain.model.AiActionListDeserializer

/**
 * Gson实例提供者
 * 提供配置了特殊反序列化器的Gson实例,用于处理AiAction相关的序列化/反序列化
 */
object GsonProvider {
    
    /**
     * 获取配置了AiAction反序列化器的Gson实例
     */
    fun getGsonInstance(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(AiAction::class.java, AiActionDeserializer())
            .registerTypeAdapter(object : TypeToken<List<AiAction>>() {}.type, AiActionListDeserializer())
            .create()
    }
}