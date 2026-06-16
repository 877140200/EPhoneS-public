package com.susking.ephone_s.tavern.ui.jinnang

import android.content.Context
import android.util.Base64
import com.susking.ephone_s.core.util.ChatRecordMetadataKeys
import com.susking.ephone_s.core.util.ChatRecordsConstants
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务3 工具：解析 SillyTavern 上下文对话并写入酒馆记录目录。
 *
 * 流程：
 * 1. [parseExtractResult] 解析 evaluateJavascript 回传（JS 字符串需先反转义），得到
 *    用户名/角色名/创建日期/头像 base64/正则 id 列表 + 完整原始消息数组。
 * 2. [buildDefaultName] 生成「角色名_时间」默认文件名。
 * 3. [save] 把数据转成 SillyTavern 原生 .jsonl 写入应用外部文件目录的 chat_records 子目录
 *    （与 clouddreams 读取路径一致）：
 *    - 首行 = 元数据行（ST 原生 user_name/character_name/create_date/chat_metadata
 *      + 我方扩展 user_avatar/character_avatar/applied_regex_ids）
 *    - 后续每行 = 原始消息对象**逐字段保留**（含 swipes/extra 等，保证导出可被酒馆重新导入）
 *    - 头像 base64 落地到 chat_records/avatars/<hash>.png，jsonl 存相对路径
 */
object TavernChatSaver {

    /**
     * 提取结果。
     *
     * @property name2 角色名（兼容旧调用，等于 [characterName]）
     * @property chatArray 完整原始消息数组（逐字段保留，不砍字段）
     * @property messageCount 消息条数（不含元数据行）
     * @property userName 用户名（ST name1）
     * @property characterName 角色名（ST name2）
     * @property createDate 创建日期字符串（取首条消息 send_date 或当前时间）
     * @property chatMetadata 原始 chat_metadata 对象（可空）
     * @property userAvatarBase64 用户头像图片的 base64（data URL，可空）
     * @property characterAvatarBase64 角色头像图片的 base64（data URL，可空）
     * @property regexBodies 本次对话涉及的正则本体（ST 原生 camelCase 格式），保存时写入全局库
     */
    data class ExtractedChat(
        val name2: String,
        val chatArray: JSONArray,
        val messageCount: Int,
        val userName: String,
        val characterName: String,
        val createDate: String,
        val chatMetadata: JSONObject?,
        val userAvatarBase64: String?,
        val characterAvatarBase64: String?,
        val regexBodies: JSONArray
    )

    /** 提取任务在 window 上写入结果的全局变量名（Kotlin 端轮询读取）。 */
    const val EXTRACT_RESULT_VAR: String = "__ephoneChatExtract"

    /**
     * 启动提取任务的 JS（异步）。
     *
     * 因 `evaluateJavascript` 无法等待 Promise，故本脚本启动一个异步任务，
     * 完成后把结果 JSON 字符串写入 `window.[EXTRACT_RESULT_VAR]`，Kotlin 端轮询 [POLL_RESULT_JS] 读取。
     * 启动前先把该变量置空，避免读到上一次的旧结果。
     *
     * 任务内容：
     * 1. 取 `SillyTavern.getContext()` 的 name1/name2/chat/chatMetadata。
     * 2. 正则：优先酒馆助手 `getTavernRegexes`（全局 + 当前角色），归一化成 ST 原生 camelCase 格式
     *    （复用 [TavernRegexImporter] 既有映射）；未装酒馆助手则回退 `extensionSettings.regex`（仅全局）。
     *    返回完整正则对象数组 `regexes`，Kotlin 端据此写入全局库并提取 id 列表。
     * 3. 头像：拼出 user/char 头像 URL，用 fetch 取图转 base64（失败则为 null，不阻断）。
     *
     * 注意：任何子步骤失败都被各自的 try/catch 兜住，保证最终一定写入结果（哪怕字段缺省），
     * 避免 Kotlin 端轮询超时。整体失败（getContext 不存在）时写入空字符串。
     */
    val EXTRACT_AND_SAVE_JS: String =
        """
        (function(){
          window.$EXTRACT_RESULT_VAR = '';
          function toDataUrl(url){
            return fetch(url).then(function(r){
              if(!r.ok) return null;
              return r.blob();
            }).then(function(b){
              if(!b) return null;
              return new Promise(function(res){
                var fr=new FileReader();
                fr.onloadend=function(){res(fr.result);};
                fr.onerror=function(){res(null);};
                fr.readAsDataURL(b);
              });
            }).catch(function(){return null;});
          }
          function helperToNative(s){
            var placement=[];
            if(s.source){
              if(s.source.user_input) placement.push(1);
              if(s.source.ai_output) placement.push(2);
              if(s.source.slash_command) placement.push(3);
              if(s.source.world_info) placement.push(5);
            }
            var trim=[];
            if(typeof s.trim_strings==='string'&&s.trim_strings.length)
              trim=s.trim_strings.split('\n');
            else if(Array.isArray(s.trim_strings)) trim=s.trim_strings;
            return {
              id:s.id, scriptName:s.script_name, findRegex:s.find_regex,
              replaceString:s.replace_string, trimStrings:trim, placement:placement,
              disabled:!s.enabled, runOnEdit:!!s.run_on_edit,
              markdownOnly:!!(s.destination&&s.destination.display),
              promptOnly:!!(s.destination&&s.destination.prompt),
              minDepth:(s.min_depth==null?null:s.min_depth),
              maxDepth:(s.max_depth==null?null:s.max_depth)
            };
          }
          function collectRegexes(ctx){
            try{
              if(typeof getTavernRegexes==='function'){
                var out=[];
                var g=getTavernRegexes({type:'global'})||[];
                var c=getTavernRegexes({type:'character',name:'current'})||[];
                g.concat(c).forEach(function(s){ if(s&&s.id) out.push(helperToNative(s)); });
                return out;
              }
            }catch(e){}
            try{
              var arr=(ctx.extensionSettings&&ctx.extensionSettings.regex)||
                      (ctx.extension_settings&&ctx.extension_settings.regex)||[];
              return arr.filter(function(s){return s&&s.id;});
            }catch(e){return [];}
          }
          function userAvatarUrl(ctx){
            try{
              var ua='';
              if(typeof user_avatar!=='undefined'&&user_avatar) ua=user_avatar;
              else if(ctx.userAvatar) ua=ctx.userAvatar;
              else if(ctx.user_avatar) ua=ctx.user_avatar;
              if(ua) return '/User Avatars/'+encodeURIComponent(ua);
            }catch(e){}
            return null;
          }
          function charAvatarUrl(ctx){
            try{
              var ch=ctx.characters&&ctx.characters[ctx.characterId];
              if(ch&&ch.avatar&&ch.avatar!=='none')
                return '/thumbnail?type=avatar&file='+encodeURIComponent(ch.avatar);
            }catch(e){}
            return null;
          }
          try{
            var ctx=SillyTavern.getContext();
            var out={
              name1:ctx.name1||'User',
              name2:ctx.name2||'对话',
              chat:ctx.chat||[],
              chat_metadata:ctx.chatMetadata||ctx.chat_metadata||{},
              regexes:collectRegexes(ctx)
            };
            var uUrl=userAvatarUrl(ctx);
            var cUrl=charAvatarUrl(ctx);
            Promise.all([
              uUrl?toDataUrl(uUrl):Promise.resolve(null),
              cUrl?toDataUrl(cUrl):Promise.resolve(null)
            ]).then(function(av){
              out.user_avatar_b64=av[0]||'';
              out.char_avatar_b64=av[1]||'';
              window.$EXTRACT_RESULT_VAR=JSON.stringify(out);
            }).catch(function(){
              out.user_avatar_b64='';
              out.char_avatar_b64='';
              window.$EXTRACT_RESULT_VAR=JSON.stringify(out);
            });
          }catch(e){
            window.$EXTRACT_RESULT_VAR='';
          }
        })();
        """.trimIndent()

    /** 轮询读取提取结果：返回空串=未就绪，'null'=本次脚本判定失败，其它=结果 JSON。 */
    val POLL_RESULT_JS: String =
        "(function(){return window.$EXTRACT_RESULT_VAR;})();"

    /**
     * 解析 evaluateJavascript 的回传。
     *
     * WebView 回传的是「JS 返回值再 JSON 编码」后的字符串：JS 返回的 JSON 文本会被再次加引号转义，
     * 故需先用 [JSONTokener] 反转义出内层 JSON 文本，再解析。任何异常/空值都返回 null（视为提取失败）。
     *
     * 内层 JSON 结构（由 [EXTRACT_AND_SAVE_JS] 产出）：
     * `{name1, name2, chat:[...], chat_metadata:{}, regexes:[...], user_avatar_b64, char_avatar_b64}`
     */
    fun parseExtractResult(rawResult: String?): ExtractedChat? {
        if (rawResult.isNullOrBlank() || rawResult == "null") return null
        return try {
            // 反转义：外层是被引号包裹的 JSON 字符串
            val innerJson: String = (JSONTokener(rawResult).nextValue() as? String) ?: return null
            if (innerJson.isBlank()) return null
            val obj = JSONObject(innerJson)
            val chat: JSONArray = obj.optJSONArray("chat") ?: return null

            val characterName: String = obj.optString("name2").ifBlank { "对话" }
            val userName: String = obj.optString("name1").ifBlank { "User" }
            // 创建日期：优先取首条消息 send_date，无则当前时间
            val firstDate: String = chat.optJSONObject(0)?.optString("send_date").orEmpty()
            val createDate: String = firstDate.ifBlank {
                SimpleDateFormat("yyyy-MM-dd@HH'h'mm'm'ss's'", Locale.getDefault()).format(Date())
            }

            ExtractedChat(
                name2 = characterName,
                chatArray = chat,
                messageCount = chat.length(),
                userName = userName,
                characterName = characterName,
                createDate = createDate,
                chatMetadata = obj.optJSONObject("chat_metadata"),
                userAvatarBase64 = obj.optString("user_avatar_b64").ifBlank { null },
                characterAvatarBase64 = obj.optString("char_avatar_b64").ifBlank { null },
                regexBodies = obj.optJSONArray("regexes") ?: JSONArray()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建 SillyTavern 原生 .jsonl 文本。
     *
     * 首行 = 元数据行：ST 原生键（user_name/character_name/create_date/chat_metadata）
     * + 我方扩展键（user_avatar/character_avatar 本地相对路径、applied_regex_ids）。
     * 后续行 = 原始消息对象**逐字段保留**，保证导出可被酒馆重新导入。
     *
     * @param userAvatarRelPath 头像本地相对路径（avatars/x.png），无头像传 null
     * @param appliedRegexIds 写入全局库后回收的正则 id 列表
     */
    private fun buildJsonl(
        extracted: ExtractedChat,
        userAvatarRelPath: String?,
        characterAvatarRelPath: String?,
        appliedRegexIds: List<String>
    ): String {
        val builder = StringBuilder()

        // 首行：元数据
        val metaLine = JSONObject().apply {
            put(ChatRecordMetadataKeys.KEY_USER_NAME, extracted.userName)
            put(ChatRecordMetadataKeys.KEY_CHARACTER_NAME, extracted.characterName)
            put(ChatRecordMetadataKeys.KEY_CREATE_DATE, extracted.createDate)
            put(ChatRecordMetadataKeys.KEY_CHAT_METADATA, extracted.chatMetadata ?: JSONObject())
            if (userAvatarRelPath != null) {
                put(ChatRecordMetadataKeys.KEY_USER_AVATAR, userAvatarRelPath)
            }
            if (characterAvatarRelPath != null) {
                put(ChatRecordMetadataKeys.KEY_CHARACTER_AVATAR, characterAvatarRelPath)
            }
            put(ChatRecordMetadataKeys.KEY_APPLIED_REGEX_IDS, JSONArray(appliedRegexIds))
        }
        builder.append(metaLine.toString())

        // 后续行：原始消息对象逐字段保留
        val chat: JSONArray = extracted.chatArray
        for (i in 0 until chat.length()) {
            val item: JSONObject = chat.optJSONObject(i) ?: continue
            builder.append('\n')
            builder.append(item.toString())
        }
        return builder.toString()
    }

    /**
     * 把头像 base64（data URL 形式 `data:image/png;base64,xxx`）落地到 avatars 子目录。
     *
     * @return 写入文件的相对路径（avatars/<hash>.png）；base64 为空或写入失败返回 null（不阻断保存）。
     */
    private fun writeAvatar(avatarsDir: File, base64DataUrl: String?): String? {
        if (base64DataUrl.isNullOrBlank()) return null
        return try {
            // 去掉 data URL 前缀，仅保留 base64 主体
            val commaIndex: Int = base64DataUrl.indexOf(',')
            val pureBase64: String =
                if (commaIndex >= 0) base64DataUrl.substring(commaIndex + 1) else base64DataUrl
            val bytes: ByteArray = Base64.decode(pureBase64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            // 用内容 hash 命名，相同头像复用同一文件，避免重复堆积
            val hash: String = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
                .take(AVATAR_HASH_LENGTH)
            if (!avatarsDir.exists()) avatarsDir.mkdirs()
            val avatarFile = File(avatarsDir, "$hash.png")
            if (!avatarFile.exists()) avatarFile.writeBytes(bytes)
            "${ChatRecordsConstants.AVATARS_DIR}/$hash.png"
        } catch (e: Exception) {
            null
        }
    }

    /** 生成默认文件名「角色名_yyyy-MM-dd_HHmmss」。 */
    fun buildDefaultName(name2: String): String {
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        val safeName: String = name2.ifBlank { "对话" }
        return "${safeName}_$timestamp"
    }

    /**
     * 写入酒馆记录目录，返回写入的文件。
     *
     * 先把头像 base64 落地到 avatars 子目录，再构建 ST 原生 jsonl 写入。
     * 文件名清洗非法字符并强制 .jsonl 后缀；同名则追加序号确保唯一。
     */
    fun save(context: Context, fileName: String, extracted: ExtractedChat): File {
        val dir = File(context.getExternalFilesDir(null), ChatRecordsConstants.CHAT_RECORDS_DIR)
        if (!dir.exists()) dir.mkdirs()

        // 正则本体写入全局库，回收本次涉及的 id（clouddreams 按这些 id 查本体应用）
        val appliedRegexIds: List<String> =
            TavernRegexImporter.mergeScriptsIntoGlobal(context, extracted.regexBodies)

        // 头像落地（失败返回 null，不阻断保存）
        val avatarsDir = File(dir, ChatRecordsConstants.AVATARS_DIR)
        val userAvatarRel: String? = writeAvatar(avatarsDir, extracted.userAvatarBase64)
        val charAvatarRel: String? = writeAvatar(avatarsDir, extracted.characterAvatarBase64)

        val content: String = buildJsonl(extracted, userAvatarRel, charAvatarRel, appliedRegexIds)

        val base: String = sanitizeFileName(fileName.ifBlank { buildDefaultName(extracted.name2) })
        var candidate = File(dir, "$base.jsonl")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index.jsonl")
            index++
        }
        candidate.writeText(content)
        return candidate
    }

    /** 去掉文件名里的路径分隔符等非法字符。 */
    private fun sanitizeFileName(name: String): String {
        return name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "对话" }
    }

    /** 头像文件名取 SHA-256 前若干位，足够避免碰撞又不冗长。 */
    private const val AVATAR_HASH_LENGTH: Int = 32
}
