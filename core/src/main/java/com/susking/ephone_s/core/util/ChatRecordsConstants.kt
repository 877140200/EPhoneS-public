package com.susking.ephone_s.core.util

/**
 * 酒馆记录（clouddreams）相关的共享常量。
 *
 * 酒馆记录模块本质是一个 SillyTavern 记录文件查看器：它扫描应用外部文件目录下的
 * [CHAT_RECORDS_DIR] 子目录，读取其中的 .jsonl 文件来列表与展示。
 *
 * 「锦囊」保存当前对话功能需要往同一目录写入 .jsonl，clouddreams 才能扫到。
 * 把目录名集中到 core 常量，避免 tavern 与 clouddreams 两处各自硬编码导致约定漂移。
 *
 * 注意：clouddreams 模块当前仍硬编码字面量 "chat_records"，二者必须保持一致；
 * 若日后修改目录名，请同步更新 clouddreams 的读取逻辑。
 */
object ChatRecordsConstants {
    const val CHAT_RECORDS_DIR: String = "chat_records"

    /**
     * 头像图片子目录（位于 [CHAT_RECORDS_DIR] 之内）。
     *
     * 「保存当前对话」时把 user/char 头像图片从酒馆服务器拉取后复制到本地，
     * 存于 `chat_records/avatars/<hash>.png`；jsonl 首行 metadata 用相对路径
     * `avatars/<hash>.png` 引用。clouddreams 渲染时按此相对路径在同目录下加载。
     */
    const val AVATARS_DIR: String = "avatars"
}

/**
 * 酒馆记录 .jsonl 首行 metadata 的字段名约定。
 *
 * SillyTavern 原生 .jsonl 首行是聊天元数据行，含 user_name/character_name/create_date/chat_metadata。
 * 「保存当前对话」在保留这些原生字段的同时，追加我方扩展字段（头像本地路径、应用的正则 id 列表）。
 * ST 导入时会忽略未知键，故不破坏与酒馆的双向兼容。
 *
 * 字段名集中到 core，避免 tavern 写入与 clouddreams 读取两处各自硬编码导致漂移。
 *
 * 注意：[KEY_USER_NAME]/[KEY_CHARACTER_NAME]/[KEY_CREATE_DATE]/[KEY_CHAT_METADATA] 为 ST 原生键，
 * 不可改名；其余为我方扩展键，改名时须同步 tavern 与 clouddreams 两端。
 */
object ChatRecordMetadataKeys {
    // ST 原生键（识别 metadata 行的依据 = 同时含 user_name 与 character_name）
    const val KEY_USER_NAME: String = "user_name"
    const val KEY_CHARACTER_NAME: String = "character_name"
    const val KEY_CREATE_DATE: String = "create_date"
    const val KEY_CHAT_METADATA: String = "chat_metadata"

    // 我方扩展键（ST 导入时忽略）
    const val KEY_USER_AVATAR: String = "user_avatar"
    const val KEY_CHARACTER_AVATAR: String = "character_avatar"
    const val KEY_APPLIED_REGEX_IDS: String = "applied_regex_ids"
}

/**
 * 酒馆记录（clouddreams）全局正则的存储契约。
 *
 * clouddreams 的 RegexRuleManager 把全局正则存在名为 [GLOBAL_RULES_PREFS] 的 SharedPreferences，
 * key 为 [RULES_KEY]，值是 `List<RegexRule>` 的 gson JSON 字符串。
 *
 * 「锦囊」一键拉取酒馆正则功能需往此 prefs 合并写入，故把两个约定常量集中到 core，
 * 两边引用同一处以降低「漂移」（双方各自硬编码导致对不上）。
 *
 * 注意：clouddreams 的 RegexRuleManager 当前仍硬编码这两个字面量，二者必须保持一致；
 * 若日后修改，请同步更新 clouddreams 的读取逻辑与本常量。
 */
object CloudDreamsRegexContract {
    const val GLOBAL_RULES_PREFS: String = "regex_global_rules"
    const val RULES_KEY: String = "rules"
}
