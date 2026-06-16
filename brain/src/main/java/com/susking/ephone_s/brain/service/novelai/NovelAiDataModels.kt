package com.susking.ephone_s.brain.service.novelai

import com.google.gson.annotations.SerializedName

/**
 * V3 版本的 NovelAI 请求体结构
 */
data class NovelAIRequestV3(
    val input: String,
    val model: String,
    val action: String = "generate",
    val parameters: Parameters
) {
    data class Parameters(
        val width: Int,
        val height: Int,
        val scale: Float,
        val sampler: String,
        val steps: Int,
        val seed: Long,
        @SerializedName("n_samples")
        val nSamples: Int = 1,
        val ucPreset: Int,
        val qualityToggle: Boolean,
        val sm: Boolean,
        @SerializedName("sm_dyn")
        val smDyn: Boolean,
        @SerializedName("negative_prompt")
        val negativePrompt: String
    )
}

/**
 * V4/V4.5 版本的 NovelAI 请求体结构
 */
data class NovelAIRequestV4(
    val input: String,
    val model: String,
    val action: String = "generate",
    val parameters: Parameters
) {
    data class Parameters(
        @SerializedName("params_version")
        val paramsVersion: Int,
        val width: Int,
        val height: Int,
        val scale: Float,
        val sampler: String,
        val steps: Int,
        val seed: Long,
        @SerializedName("n_samples")
        val nSamples: Int,
        val ucPreset: Int,
        val qualityToggle: Boolean,
        @SerializedName("v4_prompt")
        val v4Prompt: V4Prompt,
        @SerializedName("v4_negative_prompt")
        val v4NegativePrompt: V4NegativePrompt,
        @SerializedName("negative_prompt")
        val negativePrompt: String,
        @SerializedName("noise_schedule")
        val noiseSchedule: String,
        @SerializedName("legacy_v3_extend")
        val legacyV3Extend: Boolean,
        @SerializedName("add_original_image")
        val addOriginalImage: Boolean,
        val legacy: Boolean,
        @SerializedName("cfg_rescale")
        val cfgRescale: Float,
        @SerializedName("controlnet_strength")
        val controlnetStrength: Float,
        @SerializedName("dynamic_thresholding")
        val dynamicThresholding: Boolean
    )
    data class V4Prompt(val caption: V4Caption, @SerializedName("use_coords") val useCoords: Boolean = false, @SerializedName("use_order") val useOrder: Boolean = true)
    data class V4NegativePrompt(val caption: V4Caption, @SerializedName("legacy_uc") val legacyUc: Boolean = false)
    data class V4Caption(@SerializedName("base_caption") val baseCaption: String, @SerializedName("char_captions") val charCaptions: List<String> = emptyList())
}