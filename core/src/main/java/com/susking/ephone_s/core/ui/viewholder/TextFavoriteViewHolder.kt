package com.susking.ephone_s.core.ui.viewholder

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.core.databinding.ContentFavoriteTextBinding

/**
 * 通用的文本收藏ViewHolder
 * 支持Markdown和HTML渲染，超过20行时限制高度为400dp并支持滚动
 */
class TextFavoriteViewHolder(
    private val contentBinding: ContentFavoriteTextBinding
) : RecyclerView.ViewHolder(contentBinding.root) {

    @SuppressLint("ClickableViewAccessibility")
    fun bind(text: String) {
        val lineCount = text.count { it == '\n' } + 1
        // 超过20行时限制高度为400dp，可滚动
        val maxHeightPx = if (lineCount > 20) {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                400f, 
                itemView.resources.displayMetrics
            ).toInt()
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        contentBinding.favoriteTextWebView.layoutParams.height = maxHeightPx
        
        // 设置触摸事件，允许WebView内部滚动
        contentBinding.favoriteTextWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // 请求父View不要拦截触摸事件
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        
        // 渲染Markdown到WebView
        renderMarkdownToWebView(contentBinding.favoriteTextWebView, text)
    }

    /**
     * 渲染Markdown或HTML内容到WebView
     * @param webView 目标WebView
     * @param content 内容文本（支持Markdown或HTML）
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun renderMarkdownToWebView(webView: WebView, content: String) {
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(0x00000000) // 确保WebView本身是透明的
        
        // 启用WebView控制台日志
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }

        // 使用Base64编码避免所有转义问题
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val base64Content = Base64.encodeToString(contentBytes, Base64.NO_WRAP)

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <style>
                    * {
                        box-sizing: border-box;
                    }
                    body {
                        font-family: sans-serif;
                        font-size: 14px;
                        line-height: 1.5;
                        margin: 0;
                        padding: 8px;
                        color: #000;
                        background: transparent;
                        word-wrap: break-word;
                    }
                    #content {
                        /* 让marked.js生成的HTML自行控制空白符处理 */
                    }
                    pre {
                        background: #f5f5f5 !important;
                        padding: 12px !important;
                        border-radius: 4px;
                        border: 1px solid #d0d0d0;
                        overflow-x: auto;
                        white-space: pre !important;
                        font-family: 'Courier New', monospace;
                        margin: 12px 0 !important;
                        display: block !important;
                        font-size: 13px;
                        line-height: 1.4;
                    }
                    code {
                        background: #f0f0f0;
                        padding: 2px 5px;
                        border-radius: 3px;
                        font-family: 'Courier New', monospace;
                        font-size: 13px;
                    }
                    pre code {
                        background: transparent !important;
                        padding: 0 !important;
                        border-radius: 0;
                    }
                    blockquote {
                        border-left: 3px solid #ccc;
                        margin: 8px 0;
                        padding-left: 12px;
                        color: #666;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 8px 0;
                    }
                    th, td {
                        border: 1px solid #ddd;
                        padding: 8px;
                        text-align: left;
                    }
                    img {
                        max-width: 100%;
                    }
                    details {
                        margin: 8px 0;
                    }
                    summary {
                        cursor: pointer;
                        font-weight: bold;
                    }
                    h1 {
                        font-size: 24px;
                        font-weight: bold;
                        margin: 16px 0 12px 0;
                        padding-bottom: 8px;
                        border-bottom: 2px solid #e0e0e0;
                    }
                    h2 {
                        font-size: 20px;
                        font-weight: bold;
                        margin: 14px 0 10px 0;
                        padding-bottom: 6px;
                        border-bottom: 1px solid #e0e0e0;
                    }
                    h3 {
                        font-size: 18px;
                        font-weight: bold;
                        margin: 12px 0 8px 0;
                    }
                    h4, h5, h6 {
                        font-size: 16px;
                        font-weight: bold;
                        margin: 12px 0 8px 0;
                    }
                    p {
                        margin: 0 0 8px 0;
                        white-space: pre-wrap;
                    }
                </style>
            </head>
            <body>
                <div id="content"></div>
                <script>
                    try {
                        // 从Base64解码内容
                        var base64Content = "$base64Content";
                        var rawContent = decodeURIComponent(escape(atob(base64Content)));
                        
                        console.log('Raw content length:', rawContent.length);
                        console.log('Content preview:', rawContent.substring(0, 200));
                        
                        // 第一步：手动处理三反引号代码块
                        var processedContent = rawContent.replace(/```(\w*)\n([\s\S]*?)```/g, function(match, lang, code) {
                            console.log('Found code block with lang:', lang);
                            // 转义HTML特殊字符
                            var escapedCode = code
                                .replace(/&/g, '&amp;')
                                .replace(/</g, '&lt;')
                                .replace(/>/g, '&gt;')
                                .replace(/"/g, '&quot;')
                                .replace(/'/g, '&#39;');
                            // 直接生成HTML代码块
                            return '<pre><code class="language-' + lang + '">' + escapedCode + '</code></pre>';
                        });
                        
                        // 第二步：手动处理Markdown标题（#符号，支持有无空格）
                        processedContent = processedContent.replace(/^(#{1,6})\s*(.+)$/gm, function(match, hashes, title) {
                            var level = hashes.length;
                            console.log('Found h' + level + ' heading:', title);
                            return '<h' + level + '>' + title.trim() + '</h' + level + '>';
                        });
                        
                        console.log('After preprocessing, length:', processedContent.length);
                        
                        // 检查marked是否加载
                        if (typeof marked === 'undefined') {
                            console.error('marked.js not loaded, using preprocessed HTML');
                            document.getElementById('content').innerHTML = processedContent;
                        } else {
                            // 配置marked选项
                            if (typeof marked.setOptions === 'function') {
                                marked.setOptions({
                                    breaks: true,        // 支持GitHub风格的换行
                                    gfm: true,          // 启用GitHub Flavored Markdown
                                    headerIds: false,   // 不生成header ID
                                    mangle: false,      // 不混淆邮箱地址
                                    pedantic: false,    // 不使用严格模式
                                    sanitize: false     // 不清理HTML
                                });
                            }
                            console.log('Rendering with marked.js (supports mixed Markdown/HTML)');
                            var rendered = marked.parse ? marked.parse(processedContent) : marked(processedContent);
                            console.log('Rendered HTML preview:', rendered.substring(0, 300));
                            document.getElementById('content').innerHTML = rendered;
                        }
                    } catch (e) {
                        console.error('Error rendering content:', e);
                        document.getElementById('content').textContent = 'Error: ' + e.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}