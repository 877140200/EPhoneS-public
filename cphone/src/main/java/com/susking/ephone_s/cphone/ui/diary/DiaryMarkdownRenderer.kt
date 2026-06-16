package com.susking.ephone_s.cphone.ui.diary

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView

/**
 * 日记专属 Markdown 渲染器。
 *
 * 标准 Markwon 只认 `**加粗**`，无法识别日记规范里的专属语法，
 * 导致 `!h{}`/`!u{}` 等标记原样当纯文本显示。
 * 这里用 [SpannableStringBuilder] 自行解析全部 8 种语法并应用对应样式：
 * - `**加粗**`        → 粗体
 * - `~~划掉~~`        → 删除线
 * - `!h{黄色高亮}`     → 黄色高亮背景
 * - `!u{粉色下划线}`   → 粉色文字 + 下划线
 * - `!e{粉色强调}`     → 粉色粗体
 * - `!w{手写体}`       → 衬线斜体
 * - `!m{凌乱手写体}`   → 衬线粗斜体（更浅）
 * - `||涂黑||`         → 黑底黑字，点击可揭示
 */
object DiaryMarkdownRenderer {

    /**
     * 将日记内容解析为富文本并渲染到 [textView]。
     */
    fun render(textView: TextView, content: String): Unit {
        val rendered: CharSequence = buildSpanned(content)
        textView.text = rendered
        // 涂黑文字依赖点击揭示，必须启用链接点击移动方法。
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * 解析日记原文，返回带样式的富文本。
     */
    fun buildSpanned(content: String): CharSequence {
        val builder = SpannableStringBuilder()
        var cursor = 0
        while (cursor < content.length) {
            val match: SyntaxMatch? = findEarliestMatch(content, cursor)
            if (match == null) {
                builder.append(content.substring(cursor))
                break
            }
            if (match.range.first > cursor) {
                builder.append(content.substring(cursor, match.range.first))
            }
            appendStyledSegment(builder, match)
            cursor = match.range.last + 1
        }
        return builder
    }

    /**
     * 从 [startIndex] 起，找出所有规则中最先命中的那一个。
     */
    private fun findEarliestMatch(content: String, startIndex: Int): SyntaxMatch? {
        var earliest: SyntaxMatch? = null
        SYNTAX_RULES.forEach { rule: SyntaxRule ->
            val matchResult: MatchResult? = rule.regex.find(content, startIndex)
            if (matchResult != null) {
                val innerText: String = matchResult.groupValues[1]
                val candidate = SyntaxMatch(rule.style, innerText, matchResult.range)
                if (earliest == null || candidate.range.first < earliest!!.range.first) {
                    earliest = candidate
                }
            }
        }
        return earliest
    }

    /**
     * 把命中的片段按其样式追加进富文本。
     */
    private fun appendStyledSegment(builder: SpannableStringBuilder, match: SyntaxMatch): Unit {
        val start: Int = builder.length
        builder.append(match.innerText)
        val end: Int = builder.length
        applySpansForStyle(builder, match.style, start, end)
    }

    private fun applySpansForStyle(builder: SpannableStringBuilder, style: DiaryStyle, start: Int, end: Int): Unit {
        val spans: List<Any> = when (style) {
            DiaryStyle.BOLD -> listOf(StyleSpan(Typeface.BOLD))
            DiaryStyle.STRIKETHROUGH -> listOf(StrikethroughSpan())
            DiaryStyle.HIGHLIGHT -> listOf(BackgroundColorSpan(COLOR_HIGHLIGHT), ForegroundColorSpan(COLOR_HIGHLIGHT_TEXT))
            DiaryStyle.UNDERLINE -> listOf(ForegroundColorSpan(COLOR_PINK), UnderlineSpan())
            DiaryStyle.EMPHASIS -> listOf(ForegroundColorSpan(COLOR_PINK), StyleSpan(Typeface.BOLD))
            DiaryStyle.HANDWRITING -> listOf(TypefaceSpan(FONT_FAMILY_SERIF), StyleSpan(Typeface.ITALIC))
            DiaryStyle.MESSY_HANDWRITING -> listOf(TypefaceSpan(FONT_FAMILY_SERIF), StyleSpan(Typeface.BOLD_ITALIC), ForegroundColorSpan(COLOR_MESSY))
            DiaryStyle.REDACTED -> listOf(RedactionSpan())
        }
        spans.forEach { span: Any ->
            builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * 涂黑文字：默认黑底黑字隐藏内容，点击后揭示原文。
     */
    private class RedactionSpan : ClickableSpan() {
        private var isRevealed: Boolean = false

        override fun onClick(widget: View) {
            isRevealed = !isRevealed
            widget.invalidate()
        }

        override fun updateDrawState(textPaint: android.text.TextPaint) {
            if (isRevealed) {
                textPaint.bgColor = Color.TRANSPARENT
                textPaint.color = COLOR_REDACTION_REVEALED
            } else {
                textPaint.bgColor = COLOR_REDACTION
                textPaint.color = COLOR_REDACTION
            }
            textPaint.isUnderlineText = false
        }
    }

    private data class SyntaxRule(val regex: Regex, val style: DiaryStyle)

    private data class SyntaxMatch(val style: DiaryStyle, val innerText: String, val range: IntRange)

    private enum class DiaryStyle {
        BOLD,
        STRIKETHROUGH,
        HIGHLIGHT,
        UNDERLINE,
        EMPHASIS,
        HANDWRITING,
        MESSY_HANDWRITING,
        REDACTED
    }

    private const val FONT_FAMILY_SERIF: String = "serif"
    private const val COLOR_PINK: Int = 0xFFE91E63.toInt()
    private const val COLOR_MESSY: Int = 0xFF9C7B8E.toInt()
    private const val COLOR_HIGHLIGHT: Int = 0x80FFEB3B.toInt()
    private const val COLOR_HIGHLIGHT_TEXT: Int = 0xFF3E2723.toInt()
    private const val COLOR_REDACTION: Int = 0xFF222222.toInt()
    private const val COLOR_REDACTION_REVEALED: Int = 0xFFE91E63.toInt()

    // 顺序无关，findEarliestMatch 会按命中位置择优；非贪婪匹配避免吞掉相邻标记。
    private val SYNTAX_RULES: List<SyntaxRule> = listOf(
        SyntaxRule(Regex("""\*\*(.+?)\*\*"""), DiaryStyle.BOLD),
        SyntaxRule(Regex("""~~(.+?)~~"""), DiaryStyle.STRIKETHROUGH),
        SyntaxRule(Regex("""!h\{([^}]*)\}"""), DiaryStyle.HIGHLIGHT),
        SyntaxRule(Regex("""!u\{([^}]*)\}"""), DiaryStyle.UNDERLINE),
        SyntaxRule(Regex("""!e\{([^}]*)\}"""), DiaryStyle.EMPHASIS),
        SyntaxRule(Regex("""!w\{([^}]*)\}"""), DiaryStyle.HANDWRITING),
        SyntaxRule(Regex("""!m\{([^}]*)\}"""), DiaryStyle.MESSY_HANDWRITING),
        SyntaxRule(Regex("""\|\|(.+?)\|\|"""), DiaryStyle.REDACTED)
    )
}
