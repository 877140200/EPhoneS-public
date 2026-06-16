package com.susking.ephone_s.qq.util

import android.content.Context
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Markdown 和 HTML 渲染工具类
 * 使用 Markwon 库来渲染 Markdown 和 HTML 格式的文本
 */
object MarkdownRenderer {
    
    private var markwon: Markwon? = null
    
    /**
     * 获取 Markwon 实例(单例模式)
     */
    private fun getMarkwon(context: Context): Markwon {
        if (markwon == null) {
            markwon = Markwon.builder(context)
                .usePlugin(HtmlPlugin.create()) // 启用 HTML 支持
                .usePlugin(ImagesPlugin.create()) // 启用图片支持
                .usePlugin(LinkifyPlugin.create()) // 启用自动链接识别
                .usePlugin(StrikethroughPlugin.create()) // 启用删除线支持
                .usePlugin(TablePlugin.create(context)) // 启用表格支持
                .build()
        }
        return markwon!!
    }
    
    /**
     * 将 Markdown 或 HTML 文本渲染到 TextView
     *
     * @param textView 目标 TextView
     * @param content 要渲染的 Markdown 或 HTML 内容
     */
    fun renderMarkdown(textView: TextView, content: String?) {
        if (content.isNullOrBlank()) {
            textView.text = ""
            return
        }
        
        val markwonInstance = getMarkwon(textView.context)
        markwonInstance.setMarkdown(textView, content)
        
        // 重要:不设置 movementMethod,避免影响长按事件
        // 如果需要链接可点击,应该在外层设置,而不是在这里
        textView.movementMethod = null
    }
    
    /**
     * 检查文本是否可能包含 Markdown 或 HTML 格式
     * 用于优化性能,对纯文本直接显示而不经过 Markwon 处理
     * 
     * @param text 要检查的文本
     * @return 如果文本可能包含格式则返回 true
     */
    fun mightContainMarkup(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        
        // 检查常见的 Markdown 和 HTML 标记
        return text.contains("**") || // 粗体
               text.contains("*") || // 斜体
               text.contains("__") || // 粗体
               text.contains("_") || // 斜体
               text.contains("~~") || // 删除线
               text.contains("[") && text.contains("]") && text.contains("(") || // 链接
               text.contains("```") || // 代码块
               text.contains("`") || // 行内代码
               text.contains("#") || // 标题
               text.contains("<") && text.contains(">") || // HTML 标签
               text.contains("|") // 表格
    }
    
    /**
     * 清理 Markwon 缓存(可选,在需要时调用)
     */
    fun clearCache() {
        markwon = null
    }
}