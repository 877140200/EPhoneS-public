package com.susking.ephone_s.clouddreams.ui

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

object MarkdownRenderer {
    private var markwon: Markwon? = null

    fun initialize(context: Context) {
        if (markwon == null) {
            val appContext = context.applicationContext

            markwon = Markwon.builder(appContext)
                .usePlugin(HtmlPlugin.create()) // 使用默认的HTML插件
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(GlideImagesPlugin.create(appContext))
                .usePlugin(TablePlugin.create(appContext))
                .usePlugin(TaskListPlugin.create(appContext))
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder
                            .codeTextColor(Color.BLACK)
                            .codeBackgroundColor(0xffeeeeee.toInt())
                    }
                })
                .build()
        }
    }
    fun renderHtmlWithMarkdown(textView: TextView, content: String) {
        renderMarkdown(textView, content)
    }

    private fun renderMarkdown(textView: TextView, content: String) {
        markwon?.setMarkdown(textView, content)
    }
}