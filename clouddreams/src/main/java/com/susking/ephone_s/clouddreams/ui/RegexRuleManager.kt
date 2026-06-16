package com.susking.ephone_s.clouddreams.ui

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import java.util.regex.Pattern
import androidx.core.content.edit

class RegexRuleManager(private val context: Context) {
    private val gson = Gson()
    private val globalPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("regex_global_rules", Context.MODE_PRIVATE)
    }

    // 存储全局规则
    fun saveGlobalRule(rule: RegexRule) {
        val rules = getGlobalRules().toMutableList()
        val existingIndex = rules.indexOfFirst { it.id == rule.id }

        if (existingIndex >= 0) {
            rules[existingIndex] = rule
        } else {
            rules.add(rule)
        }

        saveGlobalRules(rules)
    }

    fun getGlobalRules(): List<RegexRule> {
        val json = globalPrefs.getString("rules", "[]") ?: "[]"
        val type = object : TypeToken<List<RegexRule>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveGlobalRules(rules: List<RegexRule>) {
        val json = gson.toJson(rules)
        globalPrefs.edit { putString("rules", json) }
    }

    fun deleteGlobalRule(ruleId: String) {
        val rules = getGlobalRules().filter { it.id != ruleId }
        saveGlobalRules(rules)
    }

    // 对于局部规则，需要与特定角色卡片关联
    fun saveScopedRule(rule: RegexRule, cardId: String) {
        val rules = getScopedRules(cardId).toMutableList()
        val existingIndex = rules.indexOfFirst { it.id == rule.id }

        if (existingIndex >= 0) {
            rules[existingIndex] = rule
        } else {
            rules.add(rule)
        }

        saveScopedRules(rules, cardId)
    }

    fun getScopedRules(cardId: String): List<RegexRule> {
        val prefs = context.getSharedPreferences("regex_scoped_$cardId", Context.MODE_PRIVATE)
        val json = prefs.getString("rules", "[]") ?: "[]"
        val type = object : TypeToken<List<RegexRule>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveScopedRules(rules: List<RegexRule>, cardId: String) {
        val prefs = context.getSharedPreferences("regex_scoped_$cardId", Context.MODE_PRIVATE)
        val json = gson.toJson(rules)
        prefs.edit { putString("rules", json) }
    }

    fun deleteScopedRule(ruleId: String, cardId: String) {
        val rules = getScopedRules(cardId).filter { it.id != ruleId }
        saveScopedRules(rules, cardId)
    }

    // 添加处理消息文本的方法
    fun processMessage(message: String, isUserMessage: Boolean): String {
        // 无指定 id 列表：回退「应用全部启用的全局规则」（兼容外部导入的旧文件）
        return processMessage(message, isUserMessage, null)
    }

    /**
     * 按指定正则 id 列表处理消息文本。
     *
     * @param appliedRegexIds 该聊天记录声明应用的正则 id 列表：
     *   - 非 null：只应用全局库中 id 命中的规则（id 查不到则静默跳过，不做名字兜底）；
     *     顺序按 [appliedRegexIds] 给定顺序，保证与酒馆一致的应用次序。
     *   - null：回退应用全部启用的全局规则（兼容无 metadata 的外部导入文件）。
     */
    fun processMessage(
        message: String,
        isUserMessage: Boolean,
        appliedRegexIds: List<String>?
    ): String {
        var processedMessage = message

        val allGlobal: List<RegexRule> = getGlobalRules()
        val rules: List<RegexRule> = if (appliedRegexIds == null) {
            allGlobal
        } else {
            // 按 id 列表顺序取本体，查不到的 id 静默跳过
            val byId: Map<String, RegexRule> = allGlobal.associateBy { it.id }
            appliedRegexIds.mapNotNull { id -> byId[id] }
        }

        rules.filter { rule ->
            rule.enabled && (
                    (isUserMessage && rule.affectsInput) ||
                            (!isUserMessage && rule.affectsOutput)
                    )
        }.forEach { rule ->
            try {
                // 应用trim操作
                rule.trimStrings.forEach { trimStr ->
                    processedMessage = processedMessage.replace(trimStr, "")
                }

                // 应用正则替换
                if (rule.findPattern.isNotEmpty()) {
                    val pattern = Pattern.compile(rule.findPattern)
                    val matcher = pattern.matcher(processedMessage)

                    processedMessage = if (rule.replacePattern.contains("{{match}}")) {
                        // 使用{{match}}占位符 - 兼容旧版本API
                        val sb = StringBuffer()
                        while (matcher.find()) {
                            matcher.appendReplacement(sb, rule.replacePattern.replace("{{match}}", matcher.group()))
                        }
                        matcher.appendTail(sb)
                        sb.toString()
                    } else {
                        matcher.replaceAll(rule.replacePattern)
                    }
                }
            } catch (e: Exception) {
                Log.e("Regex", "Error applying rule ${rule.name}: ${e.message}")
            }
        }

        return processedMessage
    }
}

