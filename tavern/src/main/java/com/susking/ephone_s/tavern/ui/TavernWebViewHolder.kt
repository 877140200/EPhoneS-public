package com.susking.ephone_s.tavern.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView

/**
 * 酒馆 WebView 的进程级缓存。
 *
 * 现有桌面导航采用 `replace` + 返回栈，离场会销毁 Fragment 视图，导致每次重进都要重新加载酒馆。
 * 为支持「挂后台不重载」，这里用单例持有唯一的 WebView 实例，跨 Fragment 重建存活：
 * - Fragment 进场时通过 [obtain] 取出（首次创建并加载，复用时直接挂回）。
 * - Fragment 离场时通过 [detach] 把 WebView 从父布局摘下保留，而非销毁。
 * - 用户选择真正退出时通过 [destroy] 释放。
 *
 * WebView 用 [Context.getApplicationContext] 创建，避免持有 Activity 造成内存泄漏。
 */
internal object TavernWebViewHolder {

    private var cachedWebView: WebView? = null

    /** 缓存中是否已有可复用的 WebView（决定进场时是否需要重新加载）。 */
    val hasCached: Boolean
        get() = cachedWebView != null

    /**
     * 获取缓存的 WebView；不存在时用 [configure] 创建并缓存。
     * @return 复用或新建的 WebView，以及它是否为本次新建（新建需要发起加载）。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(context: Context, configure: (WebView) -> Unit): Pair<WebView, Boolean> {
        cachedWebView?.let { return it to false }
        val webView = WebView(context.applicationContext)
        configure(webView)
        cachedWebView = webView
        return webView to true
    }

    /**
     * 把 WebView 从其父布局摘下并暂停，保留实例供下次复用。
     */
    fun detach() {
        cachedWebView?.let { webView ->
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.onPause()
        }
    }

    /**
     * 彻底销毁缓存的 WebView 并清空缓存（用户选择真正退出时调用）。
     */
    fun destroy() {
        cachedWebView?.let { webView ->
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
        cachedWebView = null
    }
}
