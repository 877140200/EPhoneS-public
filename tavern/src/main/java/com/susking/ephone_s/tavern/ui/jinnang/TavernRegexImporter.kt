package com.susking.ephone_s.tavern.ui.jinnang

import android.content.Context
import com.susking.ephone_s.core.util.CloudDreamsRegexContract
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 一键把 SillyTavern 的全局正则拉取到酒馆记录（clouddreams）。
 *
 * 原理：clouddreams 渲染酒馆记录时用其全局正则（存于 SharedPreferences
 * [CloudDreamsRegexContract.GLOBAL_RULES_PREFS]，key [CloudDreamsRegexContract.RULES_KEY]，
 * 值为 `List<RegexRule>` 的 JSON）逐条处理消息。本工具把 ST 的 extension_settings.regex
 * 转成相同结构合并写入，clouddreams 下次打开任意记录即自动套用。
 *
 * 不依赖 clouddreams 模块：通过 core 的约定常量直写其 prefs。为降低「格式漂移」，
 * 下面 [buildCloudDreamsRule] 产出的字段**必须与 clouddreams 的 RegexRule 数据类逐一对齐**，
 * 映射规则照搬 clouddreams 的 importRuleFromJson（ST 原生字段 → RegexRule）。
 */
object TavernRegexImporter {

    /** 拉取结果：导入条数 + 是否成功解析。 */
    data class ImportResult(val importedCount: Int, val parsedOk: Boolean)

    /**
     * 读取 ST 全局正则的 JS 表达式。
     * 兼容驼峰 extensionSettings 与下划线 extension_settings 两种命名，取不到返回空数组。
     */
    const val EXTRACT_JS: String =
        "(function(){try{var c=SillyTavern.getContext();" +
            "var r=(c.extensionSettings&&c.extensionSettings.regex)||" +
            "(c.extension_settings&&c.extension_settings.regex)||[];" +
            "return JSON.stringify(r);}catch(e){return '';}})();"

    /**
     * 解析 evaluateJavascript 回传并合并写入 clouddreams 全局正则。
     *
     * @return 解析失败返回 parsedOk=false；成功返回本次新增/更新的规则数。
     */
    fun importFromExtractResult(context: Context, rawResult: String?): ImportResult {
        val stScripts: JSONArray = parseStScripts(rawResult) ?: return ImportResult(0, false)
        if (stScripts.length() == 0) return ImportResult(0, true)
        val ids: List<String> = mergeScriptsIntoGlobal(context, stScripts)
        return ImportResult(ids.size, true)
    }

    /**
     * 把一组 ST 原生（camelCase）正则脚本合并写入 clouddreams 全局正则库，返回写入的 id 列表。
     *
     * 供「保存当前对话」复用：保存时拉到的正则本体须落进全局库，clouddreams 才能按 id 查到本体应用。
     * 按 id 去重覆盖；id 为空的脚本生成兜底 id。顺序保留：库中已有的在前，新增的追加在后。
     *
     * @param scripts ST 原生格式（camelCase：scriptName/findRegex/replaceString/placement…）的脚本数组
     * @return 本次涉及（新增或更新）的全部脚本 id（按入参顺序，去空）
     */
    fun mergeScriptsIntoGlobal(context: Context, scripts: JSONArray): List<String> {
        val prefs = context.getSharedPreferences(
            CloudDreamsRegexContract.GLOBAL_RULES_PREFS,
            Context.MODE_PRIVATE
        )
        val existing: JSONArray = readExistingRules(prefs)
        val byId: LinkedHashMap<String, JSONObject> = LinkedHashMap()
        for (i in 0 until existing.length()) {
            val rule: JSONObject = existing.optJSONObject(i) ?: continue
            byId[rule.optString("id")] = rule
        }

        val touchedIds: MutableList<String> = mutableListOf()
        for (i in 0 until scripts.length()) {
            val script: JSONObject = scripts.optJSONObject(i) ?: continue
            val rule: JSONObject = buildCloudDreamsRule(script)
            val id: String = rule.getString("id")
            byId[id] = rule
            touchedIds.add(id)
        }

        val merged = JSONArray()
        byId.values.forEach { merged.put(it) }
        prefs.edit().putString(CloudDreamsRegexContract.RULES_KEY, merged.toString()).apply()
        return touchedIds
    }

    /** 反转义并解析 ST 正则数组；失败返回 null。 */
    private fun parseStScripts(rawResult: String?): JSONArray? {
        if (rawResult.isNullOrBlank() || rawResult == "null") return null
        return try {
            val inner: String = (JSONTokener(rawResult).nextValue() as? String) ?: return null
            if (inner.isBlank()) return null
            JSONArray(inner)
        } catch (e: Exception) {
            null
        }
    }

    /** 读取 clouddreams 现有全局规则；异常/无值返回空数组。 */
    private fun readExistingRules(prefs: android.content.SharedPreferences): JSONArray {
        val json: String = prefs.getString(CloudDreamsRegexContract.RULES_KEY, "[]") ?: "[]"
        return try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    /**
     * ST 正则脚本 → clouddreams RegexRule（字段须与 clouddreams.RegexRule 对齐）。
     *
     * 映射照搬 clouddreams importRuleFromJson：
     * scriptName→name、findRegex→findPattern、replaceString→replacePattern、
     * disabled→enabled(取反)、markdownOnly→onlyFormatDisplay、promptOnly→onlyFormatPrompt、
     * placement 数组(1/2/3/5)→affectsInput/Output/Commands/WorldInfo。
     */
    private fun buildCloudDreamsRule(script: JSONObject): JSONObject {
        val placement: JSONArray = script.optJSONArray("placement") ?: JSONArray()
        val placementSet: Set<Int> = (0 until placement.length()).map { placement.optInt(it) }.toSet()

        val trimStrings = JSONArray()
        script.optJSONArray("trimStrings")?.let { arr ->
            for (i in 0 until arr.length()) trimStrings.put(arr.optString(i))
        }

        return JSONObject().apply {
            put("id", script.optString("id").ifBlank { System.currentTimeMillis().toString() + "_" + script.optString("scriptName").hashCode() })
            put("name", script.optString("scriptName"))
            put("findPattern", script.optString("findRegex"))
            put("replacePattern", script.optString("replaceString"))
            put("trimStrings", trimStrings)
            put("enabled", !script.optBoolean("disabled", false))
            put("runOnEdit", script.optBoolean("runOnEdit", false))
            put("onlyFormatDisplay", script.optBoolean("markdownOnly", false))
            put("onlyFormatPrompt", script.optBoolean("promptOnly", false))
            put("affectsInput", placementSet.contains(1))
            put("affectsOutput", placementSet.contains(2))
            put("affectsCommands", placementSet.contains(3))
            put("affectsWorldInfo", placementSet.contains(5))
            // minDepth/maxDepth 为 Int?；ST 无值时不写该键，gson 反序列化得 null
            if (!script.isNull("minDepth")) put("minDepth", script.optInt("minDepth"))
            if (!script.isNull("maxDepth")) put("maxDepth", script.optInt("maxDepth"))
            put("isGlobal", true)
        }
    }
}
