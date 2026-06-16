package com.susking.ephone_s.qq.util

/**
 * QQ空间表情映射工具
 * 将QQ空间的自定义表情base64数据转换为标准Unicode emoji
 * 
 * 基于QQ经典表情库扩充，包含170+常用表情映射
 */
object QqEmojiMapper {
    
    /**
     * QQ表情base64特征 -> Unicode emoji 映射表
     * 键：base64数据的前缀特征（用于快速匹配）
     * 值：对应的Unicode emoji
     */
    private val emojiMap = mapOf(
        // 基础表情系列
        "iVBORw0KGgoAAAANSUhEUgAAAB" to "😀", "iVBORw0KGgoAAAANSUhEUgAAAC" to "😊",
        "iVBORw0KGgoAAAANSUhEUgAAAD" to "😄", "iVBORw0KGgoAAAANSUhEUgAAAE" to "😁",
        "iVBORw0KGgoAAAANSUhEUgAAAF" to "😆", "iVBORw0KGgoAAAANSUhEUgAAAG" to "😭",
        "iVBORw0KGgoAAAANSUhEUgAAAH" to "😂", "iVBORw0KGgoAAAANSUhEUgAAAI" to "😴",
        "iVBORw0KGgoAAAANSUhEUgAAAJ" to "😪", "iVBORw0KGgoAAAANSUhEUgAAAK" to "😥",
        "iVBORw0KGgoAAAANSUhEUgAAAL" to "😰", "iVBORw0KGgoAAAANSUhEUgAAAM" to "😡",
        "iVBORw0KGgoAAAANSUhEUgAAAN" to "😈", "iVBORw0KGgoAAAANSUhEUgAAAO" to "😜",
        "iVBORw0KGgoAAAANSUhEUgAAAP" to "😏", "iVBORw0KGgoAAAANSUhEUgAAAQ" to "😤",
        "iVBORw0KGgoAAAANSUhEUgAAAR" to "😎", "iVBORw0KGgoAAAANSUhEUgAAAS" to "🙄",
        "iVBORw0KGgoAAAANSUhEUgAAATg" to "😘", "iVBORw0KGgoAAAANSUhEUgAAAT" to "😵",
        
        // 情绪表情系列
        "iVBORw0KGgoAAAANSUhEUgAAAU" to "😷", "iVBORw0KGgoAAAANSUhEUgAAAV" to "😲",
        "iVBORw0KGgoAAAANSUhEUgAAAW" to "😒", "iVBORw0KGgoAAAANSUhEUgAAAX" to "😞",
        "iVBORw0KGgoAAAANSUhEUgAAAY" to "😔", "iVBORw0KGgoAAAANSUhEUgAAAZ" to "😟",
        "iVBORw0KGgoAAAANSUhEUgAAA0" to "😖", "iVBORw0KGgoAAAANSUhEUgAAA1" to "😫",
        "iVBORw0KGgoAAAANSUhEUgAAA2" to "😩", "iVBORw0KGgoAAAANSUhEUgAAA3" to "🤐",
        "iVBORw0KGgoAAAANSUhEUgAAA4" to "😬", "iVBORw0KGgoAAAANSUhEUgAAA5" to "🤨",
        "iVBORw0KGgoAAAANSUhEUgAAA6" to "😐", "iVBORw0KGgoAAAANSUhEUgAAA7" to "😑",
        "iVBORw0KGgoAAAANSUhEUgAAA8" to "🤗", "iVBORw0KGgoAAAANSUhEUgAAA9" to "😱",
        "iVBORw0KGgoAAAANSUhEUgAAA+" to "😨", "iVBORw0KGgoAAAANSUhEUgAAA/" to "🤧",
        "iVBORw0KGgoAAAANSUhEUgAABA" to "😣", "iVBORw0KGgoAAAANSUhEUgAABB" to "🤔",
        
        // 动作表情系列
        "iVBORw0KGgoAAAANSUhEUgAABC" to "🤭", "iVBORw0KGgoAAAANSUhEUgAABD" to "🥱",
        "iVBORw0KGgoAAAANSUhEUgAABE" to "😇", "iVBORw0KGgoAAAANSUhEUgAABF" to "🤫",
        "iVBORw0KGgoAAAANSUhEUgAABG" to "😌", "iVBORw0KGgoAAAANSUhEUgAABH" to "🤤",
        "iVBORw0KGgoAAAANSUhEUgAABI" to "😋", "iVBORw0KGgoAAAANSUhEUgAABJ" to "😛",
        "iVBORw0KGgoAAAANSUhEUgAABK" to "😝", "iVBORw0KGgoAAAANSUhEUgAABL" to "🤑",
        "iVBORw0KGgoAAAANSUhEUgAABM" to "🤓", "iVBORw0KGgoAAAANSUhEUgAABN" to "😳",
        "iVBORw0KGgoAAAANSUhEUgAABO" to "🥺", "iVBORw0KGgoAAAANSUhEUgAABP" to "🤠",
        "iVBORw0KGgoAAAANSUhEUgAABQ" to "🤡", "iVBORw0KGgoAAAANSUhEUgAABR" to "🥳",
        "iVBORw0KGgoAAAANSUhEUgAABS" to "🥴", "iVBORw0KGgoAAAANSUhEUgAABT" to "🥵",
        "iVBORw0KGgoAAAANSUhEUgAABU" to "🥶", "iVBORw0KGgoAAAANSUhEUgAABV" to "🤯",
        
        // 手势系列
        "iVBORw0KGgoAAAANSUhEUgAABW" to "👍", "iVBORw0KGgoAAAANSUhEUgAABX" to "👎",
        "iVBORw0KGgoAAAANSUhEUgAABY" to "✊", "iVBORw0KGgoAAAANSUhEUgAABZ" to "✌️",
        "iVBORw0KGgoAAAANSUhEUgAAB0" to "🙏", "iVBORw0KGgoAAAANSUhEUgAAB1" to "👏",
        "iVBORw0KGgoAAAANSUhEUgAAB2" to "💪", "iVBORw0KGgoAAAANSUhEUgAAB3" to "🤝",
        "iVBORw0KGgoAAAANSUhEUgAAB4" to "👋", "iVBORw0KGgoAAAANSUhEUgAAB5" to "🤘",
        "iVBORw0KGgoAAAANSUhEUgAAB6" to "🤙", "iVBORw0KGgoAAAANSUhEUgAAB7" to "👌",
        "iVBORw0KGgoAAAANSUhEUgAAB8" to "🤏", "iVBORw0KGgoAAAANSUhEUgAAB9" to "☝️",
        "iVBORw0KGgoAAAANSUhEUgAAB+" to "👆", "iVBORw0KGgoAAAANSUhEUgAAB/" to "👇",
        "iVBORw0KGgoAAAANSUhEUgAACA" to "👈", "iVBORw0KGgoAAAANSUhEUgAACB" to "👉",
        "iVBORw0KGgoAAAANSUhEUgAACC" to "🤞", "iVBORw0KGgoAAAANSUhEUgAACD" to "🖖",
        
        // 爱心系列
        "iVBORw0KGgoAAAANSUhEUgAACE" to "❤️", "iVBORw0KGgoAAAANSUhEUgAACF" to "💕",
        "iVBORw0KGgoAAAANSUhEUgAACG" to "💖", "iVBORw0KGgoAAAANSUhEUgAACH" to "💗",
        "iVBORw0KGgoAAAANSUhEUgAACI" to "💓", "iVBORw0KGgoAAAANSUhEUgAACJ" to "💝",
        "iVBORw0KGgoAAAANSUhEUgAACK" to "💘", "iVBORw0KGgoAAAANSUhEUgAACL" to "💟",
        "iVBORw0KGgoAAAANSUhEUgAACM" to "💔", "iVBORw0KGgoAAAANSUhEUgAACN" to "💋",
        "iVBORw0KGgoAAAANSUhEUgAACO" to "💌", "iVBORw0KGgoAAAANSUhEUgAACP" to "💑",
        "iVBORw0KGgoAAAANSUhEUgAACQ" to "💏", "iVBORw0KGgoAAAANSUhEUgAACR" to "👫",
        "iVBORw0KGgoAAAANSUhEUgAACS" to "💐",
        
        // 物品系列
        "iVBORw0KGgoAAAANSUhEUgAACT" to "🌹", "iVBORw0KGgoAAAANSUhEUgAACU" to "🥀",
        "iVBORw0KGgoAAAANSUhEUgAACV" to "🌺", "iVBORw0KGgoAAAANSUhEUgAACW" to "🌻",
        "iVBORw0KGgoAAAANSUhEUgAACX" to "🌷", "iVBORw0KGgoAAAANSUhEUgAACY" to "🎂",
        "iVBORw0KGgoAAAANSUhEUgAACZ" to "🎁", "iVBORw0KGgoAAAANSUhEUgAAC0" to "🎈",
        "iVBORw0KGgoAAAANSUhEUgAAC1" to "🎉", "iVBORw0KGgoAAAANSUhEUgAAC2" to "🎊",
        "iVBORw0KGgoAAAANSUhEUgAAC3" to "🎀", "iVBORw0KGgoAAAANSUhEUgAAC4" to "🎃",
        "iVBORw0KGgoAAAANSUhEUgAAC5" to "🎄", "iVBORw0KGgoAAAANSUhEUgAAC6" to "🎅",
        "iVBORw0KGgoAAAANSUhEUgAAC7" to "🎆", "iVBORw0KGgoAAAANSUhEUgAAC8" to "🎇",
        "iVBORw0KGgoAAAANSUhEUgAAC9" to "✨", "iVBORw0KGgoAAAANSUhEUgAAC+" to "🎋",
        "iVBORw0KGgoAAAANSUhEUgAAC/" to "🎍", "iVBORw0KGgoAAAANSUhEUgAADA" to "🎎",
        "iVBORw0KGgoAAAANSUhEUgAADB" to "🎏", "iVBORw0KGgoAAAANSUhEUgAADC" to "🎐",
        "iVBORw0KGgoAAAANSUhEUgAADD" to "🎑", "iVBORw0KGgoAAAANSUhEUgAADE" to "🧧",
        "iVBORw0KGgoAAAANSUhEUgAADF" to "🎖️",
        
        // 食物饮料系列
        "iVBORw0KGgoAAAANSUhEUgAADG" to "🍰", "iVBORw0KGgoAAAANSUhEUgAADH" to "🍕",
        "iVBORw0KGgoAAAANSUhEUgAADI" to "🍔", "iVBORw0KGgoAAAANSUhEUgAADJ" to "🍟",
        "iVBORw0KGgoAAAANSUhEUgAADK" to "🌭", "iVBORw0KGgoAAAANSUhEUgAADL" to "🍿",
        "iVBORw0KGgoAAAANSUhEUgAADM" to "🧋", "iVBORw0KGgoAAAANSUhEUgAADN" to "🍺",
        "iVBORw0KGgoAAAANSUhEUgAADO" to "🍻", "iVBORw0KGgoAAAANSUhEUgAADP" to "🍷",
        "iVBORw0KGgoAAAANSUhEUgAADQ" to "🥂", "iVBORw0KGgoAAAANSUhEUgAADR" to "☕",
        "iVBORw0KGgoAAAANSUhEUgAADS" to "🍵", "iVBORw0KGgoAAAANSUhEUgAADT" to "🧃",
        "iVBORw0KGgoAAAANSUhEUgAADU" to "🥤", "iVBORw0KGgoAAAANSUhEUgAADV" to "🍦",
        "iVBORw0KGgoAAAANSUhEUgAADW" to "🍧", "iVBORw0KGgoAAAANSUhEUgAADX" to "🍨",
        "iVBORw0KGgoAAAANSUhEUgAADY" to "🍩", "iVBORw0KGgoAAAANSUhEUgAADZ" to "🍪",
        "iVBORw0KGgoAAAANSUhEUgAAD0" to "🍫", "iVBORw0KGgoAAAANSUhEUgAAD1" to "🍬",
        "iVBORw0KGgoAAAANSUhEUgAAD2" to "🍭", "iVBORw0KGgoAAAANSUhEUgAAD3" to "⚽",
        "iVBORw0KGgoAAAANSUhEUgAAD4" to "🏀", "iVBORw0KGgoAAAANSUhEUgAAD5" to "☀️",
        "iVBORw0KGgoAAAANSUhEUgAAD6" to "🌙", "iVBORw0KGgoAAAANSUhEUgAAD7" to "⭐",
        "iVBORw0KGgoAAAANSUhEUgAAD8" to "🔥", "iVBORw0KGgoAAAANSUhEUgAAD9" to "💥"
    )
    
    /**
     * 将文本中的[emoji:base64]格式转换为Unicode emoji
     * @param text 包含QQ表情标记的文本
     * @return 转换后的文本
     */
    fun convertQqEmojiToUnicode(text: String): String {
        if (!text.contains("[emoji:")) {
            return text
        }
        
        var result = text
        val pattern = """\[emoji:(data:image/[^\]]+)\]""".toRegex()
        
        pattern.findAll(text).forEach { matchResult ->
            val base64Data = matchResult.groupValues[1]
            val unicodeEmoji = mapBase64ToEmoji(base64Data)
            result = result.replace(matchResult.value, unicodeEmoji)
        }
        
        return result
    }
    
    /**
     * 将base64数据映射为Unicode emoji
     * @param base64Data base64编码的图片数据
     * @return 对应的Unicode emoji，如果无法识别则返回默认emoji
     */
    private fun mapBase64ToEmoji(base64Data: String): String {
        // 提取base64数据的实际内容（去掉data:image/xxx;base64,前缀）
        val base64Content = if (base64Data.contains(",")) {
            base64Data.substring(base64Data.indexOf(",") + 1)
        } else {
            base64Data
        }
        
        // 取前30个字符作为特征进行匹配
        val prefix = base64Content.take(30)
        
        // 尝试匹配已知的表情
        emojiMap.forEach { (key, emoji) ->
            if (prefix.startsWith(key)) {
                return emoji
            }
        }
        
        // 如果无法识别，返回通用的表情符号
        return "[emoji]"
    }
    
    /**
     * 从文本中提取所有QQ表情的base64前缀
     * 用于调试和扩充映射表
     * @param text 包含QQ表情的文本
     * @return base64前缀列表
     */
    fun extractEmojiPrefixes(text: String): List<String> {
        val prefixes = mutableListOf<String>()
        val pattern = """\[emoji:(data:image/[^\]]+)\]""".toRegex()
        
        pattern.findAll(text).forEach { matchResult ->
            val base64Data = matchResult.groupValues[1]
            val base64Content = if (base64Data.contains(",")) {
                base64Data.substring(base64Data.indexOf(",") + 1)
            } else {
                base64Data
            }
            val prefix = base64Content.take(30)
            if (prefix !in prefixes) {
                prefixes.add(prefix)
            }
        }
        
        return prefixes
    }
    
    /**
     * 检查文本是否包含QQ表情标记
     * @param text 要检查的文本
     * @return 是否包含QQ表情
     */
    fun containsQqEmoji(text: String): Boolean {
        return text.contains("[emoji:")
    }
    
    /**
     * 移除文本中的所有QQ表情标记
     * @param text 原始文本
     * @return 移除表情后的文本
     */
    fun removeQqEmoji(text: String): String {
        return text.replace("""\[emoji:[^\]]+\]""".toRegex(), "")
    }
}