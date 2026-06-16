package com.susking.ephone_s.qq.ui.qzone

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Test
import java.io.File

/**
 * QQ空间个人主页HTML解析器测试类
 * 针对从 user.qzone.qq.com/xxxxx/main 保存的完整HTML文件进行解析
 */
class QzoneParserTest {

    // CSS变量映射表，用于存储解析出的CSS变量
    private val cssVariables = mutableMapOf<String, String>()

    // 定义一个数据类来封装单条说说所有提取的信息
    data class QzoneFeed(
        val isForward: Boolean,
        val content: String,
        val forwarderText: String? = null,
        val originalAuthor: String? = null,
        val imageUrls: List<String>,
        val originalImageUrls: List<String>? = null,
        val videoUrls: List<String>,
        val timeText: String,
        val username: String,
        val device: String,
        val comments: List<Comment>,
        val likes: String,
        val views: String
    )

    data class Comment(
        val username: String,
        val text: String,
        val time: String
    )

    /**
     * 测试从本地HTML文件获取并解析所有说说的结构
     * HTML文件路径：C:/Users/Susking/Downloads/个人.html
     */
    @Test
    fun testFetchAndParseAllFeeds() = runBlocking {
        // 注意：请将 "geren" 文件复制到此路径并重命名为 "个人.html"
        val htmlFilePath = "C:/Users/Susking/Downloads/个人.html"
        try {
            val htmlFile = File(htmlFilePath)
            if (!htmlFile.exists()) {
                println("错误：HTML文件不存在：$htmlFilePath")
                return@runBlocking
            }

            val document: Document = Jsoup.parse(htmlFile, "UTF-8")

            // 关键步骤：桌面版空间的内容在iframe的srcdoc属性里
            val iframeDocString = document.select("iframe#QM_Feeds_Iframe").attr("srcdoc")
            if (iframeDocString.isBlank()) {
                println("错误：未找到id为 'QM_Feeds_Iframe' 的iframe或其 'srcdoc' 内容为空。")
                return@runBlocking
            }

            // 将iframe的内容作为新的文档进行解析
            val feedsDocument = Jsoup.parse(iframeDocString)

            // 解析CSS变量（在解析说说之前）
            parseCssVariables(feedsDocument)
            println("=== 已解析 ${cssVariables.size} 个CSS变量 ===")

            // 选取所有说说的li元素
            val feedElements = feedsDocument.select("li.f-single")
            println("=== 检测到 ${feedElements.size} 条说说 ===")

            if (feedElements.isEmpty()) {
                println("在iframe中未找到任何说说元素（li.f-single）。")
                return@runBlocking
            }

            // 遍历并解析每一条说说
            feedElements.forEachIndexed { index, feedElement ->
                println("\n\n---【正在解析第 ${index + 1} 条说说】---")
                val feed = parseFeed(feedElement)
                printFeedDetails(feed)
            }

            println("\n\n=== 所有说说提取完成 ===")

        } catch (e: Exception) {
            println("解析HTML时发生错误：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 解析单条说说元素
     */
    private fun parseFeed(feedElement: Element): QzoneFeed {
        // 判断是否为转发说说的关键：检查 .txt-box 是否有内容
        val txtBox = feedElement.select(".f-ct-txtimg .txt-box").first()
        val isForward = txtBox != null && !txtBox.text().trim().isEmpty()

        var content = ""
        var forwarderText: String? = null
        var originalAuthor: String? = null
        var originalImageUrls: List<String>? = null

        if (isForward && txtBox != null) {
            // 转发说说逻辑
            // 转发者的评论在 .f-info
            forwarderText = extractTextWithEmoji(feedElement.select(".f-info").first())
            
            // 原作者是 .txt-box 中第一个 a.nickname
            originalAuthor = txtBox.select("a.nickname").first()?.text()?.trim()
            
            // 提取原内容（排除原作者昵称链接）
            content = extractOriginalContent(txtBox)
            
            // 原图片在 .img-box 中
            val imgBox = feedElement.select(".f-ct-txtimg .img-box").first()
            if (imgBox != null) {
                originalImageUrls = extractImageUrlsFromBox(imgBox)
            }
        } else {
            // 原创说说逻辑
            // 内容直接在 .f-info
            content = extractTextWithEmoji(feedElement.select(".f-info").first())
        }

        return QzoneFeed(
            isForward = isForward,
            content = content,
            forwarderText = forwarderText,
            originalAuthor = originalAuthor,
            imageUrls = if (!isForward) extractImageUrls(feedElement, false) else emptyList(),
            originalImageUrls = originalImageUrls,
            videoUrls = extractVideoUrls(feedElement),
            timeText = extractTimeText(feedElement),
            username = extractUsername(feedElement),
            device = extractDeviceWithEmoji(feedElement),
            comments = extractComments(feedElement),
            likes = extractLikes(feedElement),
            views = extractViews(feedElement)
        )
    }

    /**
     * 提取转发说说中的原内容
     * 排除原作者昵称链接，保留"："后的文本和表情
     */
    private fun extractOriginalContent(txtBox: Element): String {
        val clone = txtBox.clone()
        // 移除第一个昵称链接（原作者）
        clone.select("a.nickname").first()?.remove()
        
        // 移除开头的 .state 元素（可能包含"："）
        clone.select(".state").remove()
        
        // 提取剩余文本和表情
        val text = extractTextWithEmoji(clone)
        
        // 清理开头的冒号和空格
        return text.removePrefix(":").trim()
    }

    /**
     * 从 .img-box 元素中提取图片URLs
     */
    private fun extractImageUrlsFromBox(imgBox: Element): List<String> {
        val imageUrls = mutableListOf<String>()
        
        imgBox.select("a.img-item img").forEach { img ->
            val src = img.attr("src")
            if (src.startsWith("data:image")) {
                // 提取base64数据的前100个字符作为预览
                imageUrls.add("data:image/...;base64,... (共${src.length}字符)")
            } else if (src.isNotBlank()) {
                imageUrls.add(src)
            }
        }
        
        return imageUrls.distinct()
    }

    /**
     * 打印单条说说的详细信息
     */
    private fun printFeedDetails(feed: QzoneFeed) {
        println("【用户名】: ${feed.username}")
        println("【发布时间】: ${feed.timeText}")
        println("【发布设备】: ${feed.device}")

        if (feed.isForward) {
            println("【类型】: 转发说说")
            println("【转发评论】: ${feed.forwarderText ?: "（无）"}")
            println("【原作者】: ${feed.originalAuthor ?: "（未知）"}")
            println("【原内容】: ${feed.content}") // 在转发逻辑中，content应为原内容
            println("【原图片URLs】(共${feed.originalImageUrls?.size ?: 0}张):")
            feed.originalImageUrls?.takeIf { it.isNotEmpty() }?.forEach { println("  - $it") } ?: println("  （无）")
        } else {
            println("【类型】: 原创说说")
            println("【内容】: ${feed.content}")
            println("【图片URLs】(共${feed.imageUrls.size}张):")
            feed.imageUrls.takeIf { it.isNotEmpty() }?.forEach { println("  - $it") } ?: println("  （无）")
        }

        println("【视频URLs】(共${feed.videoUrls.size}个):")
        feed.videoUrls.takeIf { it.isNotEmpty() }?.forEach { println("  - $it") } ?: println("  （无）")

        println("【浏览量】: ${feed.views}")
        println("【点赞】: ${feed.likes}")
        println("【评论】(共${feed.comments.size}条):")
        feed.comments.takeIf { it.isNotEmpty() }?.forEach { comment ->
            // 如果text包含"回复"关系，直接打印text（已包含完整信息）
            if (comment.text.contains(" 回复 ")) {
                println("  - ${comment.time} - ${comment.text}")
            } else {
                // 普通评论，打印username和text
                println("  - ${comment.time} - ${comment.username}: ${comment.text}")
            }
        } ?: println("  （无）")
    }


    // === 针对新HTML结构的辅助提取方法 ===

    private fun extractUsername(feedElement: Element): String {
        // 用户名在 .f-nick > a.f-name 元素中
        return feedElement.select(".f-nick a.f-name").first()?.text()?.trim() ?: "（未知用户）"
    }

    private fun extractTimeText(feedElement: Element): String {
        // 时间信息在 .info-detail > .state 元素中
        return feedElement.select(".info-detail .state").first()?.text()?.trim() ?: "（未知时间）"
    }

    private fun extractDeviceWithEmoji(feedElement: Element): String {
        // 发布设备信息，包含表情
        val deviceElement = feedElement.select(".f-reprint .phone-style").first()
        return extractTextWithEmoji(deviceElement)
    }

    private fun extractImageUrls(element: Element, isForward: Boolean): List<String> {
        val imageUrls = mutableListOf<String>()
        val targetElement = if (isForward) {
            // 如果是转发，原创的图片在 .f-repost 区域
            element.select(".f-repost").first() ?: element
        } else {
            // 如果是原创，图片在 .f-ct-txtimg 区域
            element.select(".f-ct-txtimg").first() ?: element
        }

        // 方法1: 提取 .img-box 中的传统图片（src属性）
        targetElement.select(".img-box a.img-item img").forEach { img ->
            // 忽略用户头像
            val imgClass = img.attr("class")
            if (imgClass.contains("user-avatar")) {
                return@forEach
            }
            
            val src = img.attr("src")
            if (src.startsWith("data:image")) {
                // 提取base64数据的前100个字符作为预览
                imageUrls.add("data:image/...;base64,... (共${src.length}字符)")
            } else if (src.isNotBlank()) {
                imageUrls.add(src)
            }
        }


        return imageUrls.distinct()
    }

    private fun extractVideoUrls(feedElement: Element): List<String> {
        val videoUrls = mutableListOf<String>()
        // 视频在 video 标签中
        feedElement.select("video").forEach { video ->
            val src = video.attr("src").takeIf { it.isNotBlank() }
                ?: video.select("source").attr("src")
            if (src.isNotBlank()) {
                videoUrls.add(src)
            }
        }
        return videoUrls.distinct()
    }

    private fun extractLikes(feedElement: Element): String {
        // 点赞列表在 .f-like-list .user-list 中
        val likeList = feedElement.select(".f-like-list .user-list").first()?.text()?.trim()
        return if (likeList.isNullOrEmpty()) "0人点赞" else likeList
    }

    private fun extractViews(feedElement: Element): String {
        // 浏览量在 .f-op-detail .state 元素中
        return feedElement.select(".f-op-detail .state[data-role=Visitor]").first()?.text()?.trim() ?: "（未知）"
    }

    private fun extractComments(feedElement: Element): List<Comment> {
        val comments = mutableListOf<Comment>()

        // 只选择一级评论（data-type=commentroot）
        feedElement.select(".comments-list > ul > li.comments-item[data-type=commentroot]").forEach { commentItem ->
            // 一级评论
            val singleReply = commentItem.select(".single-reply").first()
            if (singleReply != null) {
                // 评论者用户名
                val username = singleReply.select(".comments-content a.nickname").first()?.text()?.trim() ?: "（匿名）"
                // 评论内容（包含表情和图片）
                val contentElement = singleReply.select(".comments-content").first()
                val text = extractCommentTextWithEmoji(contentElement)
                // 评论时间
                val time = singleReply.select(".comments-op .state").first()?.text()?.trim() ?: ""

                if (text.isNotEmpty()) {
                    comments.add(Comment(username, text, time))
                }
            }

            // 提取二级回复（回复中的回复）
            commentItem.select(".mod-comments-sub li.comments-item[data-type=replyroot]").forEach { subCommentItem ->
                val singleSubReply = subCommentItem.select(".single-reply").first()
                if (singleSubReply != null) {
                    val contentElement = singleSubReply.select(".comments-content").first()
                    if (contentElement != null) {
                        // 提取回复关系："A 回复 B"
                        val nicknames = contentElement.select("a.nickname")
                        val replyFromUser = nicknames.getOrNull(0)?.text()?.trim() ?: "（匿名）"
                        val replyToUser = nicknames.getOrNull(1)?.text()?.trim() ?: ""

                        // 提取回复内容
                        val subText = extractCommentTextWithEmoji(contentElement)
                        val subTime = singleSubReply.select(".comments-op .state").first()?.text()?.trim() ?: ""

                        if (subText.isNotEmpty()) {
                            // 格式化为 "A 回复 B: 内容"（注意：不显示username字段，只在text中显示完整回复关系）
                            val formattedText = if (replyToUser.isNotEmpty()) {
                                "$replyFromUser 回复 $replyToUser: $subText"
                            } else {
                                subText
                            }
                            comments.add(Comment(replyFromUser, formattedText, subTime))
                        }
                    }
                }
            }
        }
        return comments
    }

    /**
     * 解析HTML中的CSS变量定义
     * CSS变量格式: :root{--sf-img-0: url("data:image/...;base64,..."); ...}
     */
    private fun parseCssVariables(document: Document) {
        cssVariables.clear()

        // 查找所有style标签
        document.select("style").forEach { styleElement ->
            val cssContent = styleElement.html()

            // 匹配CSS变量定义: --sf-img-X: url("...")
            val pattern = """--(sf-img-\d+):\s*url\("([^"]+)"\)""".toRegex()
            pattern.findAll(cssContent).forEach { matchResult ->
                val varName = matchResult.groupValues[1] // 例如: sf-img-8
                val url = matchResult.groupValues[2]     // data:image/...
                cssVariables[varName] = url
            }
        }
    }

    /**
     * 从CSS style属性中提取background-image的CSS变量
     * 例如: style="background-image:var(--sf-img-8)!important"
     * 返回: data:image/...
     */
    private fun extractCssVariableUrl(style: String): String? {
        // 匹配 background-image: var(--sf-img-X)
        val pattern = """background-image:\s*var\(--([^)]+)\)""".toRegex()
        val matchResult = pattern.find(style) ?: return null

        val varName = matchResult.groupValues[1] // 例如: sf-img-8
        return cssVariables[varName]
    }

    /**
     * 从Element中提取文本，将表情图标嵌入到文本中
     * 表情图标用 [emoji:data:image/...] 格式表示
     */
    private fun extractTextWithEmoji(element: Element?): String {
        if (element == null) return ""

        val result = StringBuilder()

        // 遍历所有子节点
        element.childNodes().forEach { node ->
            when {
                // 文本节点
                node is org.jsoup.nodes.TextNode -> {
                    result.append(node.text())
                }
                // 元素节点
                node is Element -> {
                    if (node.tagName() == "img") {
                        // 检查是否是表情图标（使用CSS变量）
                        val style = node.attr("style")
                        if (style.contains("background-image") && style.contains("var(--sf-img-")) {
                            extractCssVariableUrl(style)?.let { emojiUrl ->
                                // 提取前50个字符作为标识
                                val emojiPreview = emojiUrl.substring(0, minOf(50, emojiUrl.length))
                                result.append("[emoji:$emojiPreview...]")
                            }
                        } else {
                            // 普通img标签，保留alt文本
                            val alt = node.attr("alt")
                            if (alt.isNotBlank()) {
                                result.append(alt)
                            }
                        }
                    } else {
                        // 递归处理其他元素
                        result.append(extractTextWithEmoji(node))
                    }
                }
            }
        }

        return result.toString().trim()
    }

    /**
     * 从评论Element中提取文本（排除用户名链接和时间）
     * 包含表情和图片的提取
     */
    private fun extractCommentTextWithEmoji(element: Element?): String {
        if (element == null) return ""

        val clone = element.clone()
        // 移除用户名链接
        clone.select("a.nickname").remove()
        // 移除评论操作栏（包含时间）
        clone.select(".comments-op").remove()

        val result = StringBuilder()

        // 遍历所有子节点
        clone.childNodes().forEach { node ->
            when {
                // 文本节点
                node is org.jsoup.nodes.TextNode -> {
                    result.append(node.text())
                }
                // 元素节点
                node is Element -> {
                    if (node.tagName() == "img") {
                        // 检查是否是表情图标（使用CSS变量）
                        val style = node.attr("style")
                        if (style.contains("background-image") && style.contains("var(--sf-img-")) {
                            extractCssVariableUrl(style)?.let { emojiUrl ->
                                // 表情图标简化显示
                                result.append("[emoji:......]")
                            }
                        } else {
                            // 普通img标签，可能是评论中的图片
                            val src = node.attr("src")
                            if (src.startsWith("data:image")) {
                                // 这是base64图片
                                result.append("（带图片评论：${src.substring(0, minOf(20, src.length))}...）")
                            } else if (src.isNotBlank()) {
                                result.append("（带图片评论：$src）")
                            } else {
                                // 保留alt文本
                                val alt = node.attr("alt")
                                if (alt.isNotBlank()) {
                                    result.append(alt)
                                }
                            }
                        }
                    } else if (node.className().contains("comments-thumbnails")) {
                        // 评论中的图片缩略图区域
                        node.select("img").forEach { img ->
                            val src = img.attr("src")
                            if (src.startsWith("data:image")) {
                                result.append("（带图片评论：${src.substring(0, minOf(20, src.length))}...）")
                            }
                        }
                    } else {
                        // 递归处理其他元素
                        result.append(extractCommentTextWithEmoji(node))
                    }
                }
            }
        }

        // 清理文本：去掉开头的冒号、"回复"文本、多余空格
        return result.toString()
            .removePrefix(":")
            .removePrefix(" 回复 ")
            .removePrefix("回复")
            .trim()
            .replace(Regex("^:\\s*"), "") // 再次确保开头没有冒号
    }

    // ==================================================================================
    // 以下是旧的、用于不同HTML结构的解析逻辑，保留作为参考，但在新测试中不直接使用
    // ==================================================================================

    /**
     * 旧版提取内容方法
     */
    private fun extractContent_OLD(document: Document): String {
        // 策略1: QQ空间移动版最准确的选择器
        val tweetTxt = document.select("div.tweet-txt").firstOrNull()?.text()?.trim()
        if (!tweetTxt.isNullOrEmpty()) return tweetTxt

        // 策略2: 尝试feed-bd中的文本内容
        val feedBdText = document.select("div.feed-bd").firstOrNull()
            ?.ownText()?.trim()
        if (!feedBdText.isNullOrEmpty()) return feedBdText

        // 策略3: 通过meta标签获取（备用方案）
        val metaContent = document.select("meta[property=og:description]")
            .attr("content").trim()
        if (metaContent.isNotEmpty()) return metaContent

        // 策略4: 其他可能的选择器
        val divSelectors = listOf(
            "div.f-info",
            "div.content",
            "div.txt-box-title",
            "p.txt-box-title",
            "div.feed-content",
            "div.shuoshuo-content",
            "div.msgcnt"
        )

        for (selector in divSelectors) {
            val text = document.select(selector).firstOrNull()?.text()?.trim()
            if (!text.isNullOrEmpty()) return text
        }

        val dataContent = document.select("[data-content]")
            .attr("data-content").trim()
        if (dataContent.isNotEmpty()) return dataContent

        return ""
    }

    /**
     * 旧版转发说说结果数据类
     */
    data class ForwardResult_OLD(
        val forwarderText: String,
        val originalAuthor: String,
        val originalText: String,
        val originalImageUrls: List<String>
    )

    /**
     * 旧版提取转发说说内容
     */
    private fun extractForwardContent_OLD(document: Document): ForwardResult_OLD {
        val blockquote = document.select("blockquote.source").firstOrNull()

        // 转发人的文字：feed-bd下直接的p.txt（不在blockquote内）
        val forwarderText = document.select("div.feed-bd > p.txt").firstOrNull()?.ownText()?.trim() ?: ""

        // 原作者名字
        val originalAuthor = blockquote?.select("a.username")?.firstOrNull()?.text()?.trim() ?: ""

        // 原说说内容：blockquote内p.txt的文本（排除用户名链接）
        val originalTextElement = blockquote?.select("p.txt")?.firstOrNull()
        val originalText = originalTextElement?.let { element ->
            // 移除用户名链接后获取剩余文本
            val clone = element.clone()
            clone.select("a.username").remove()
            clone.text().trim().removePrefix("\u00A0") // 移除&nbsp;
        } ?: ""

        // 原说说图片
        val originalImageUrls = mutableListOf<String>()
        blockquote?.select("img.img")?.forEach { img ->
            val src = img.attr("src")
            if (src.isNotBlank() && src.contains("qpic")) {
                val fullUrl = if (src.startsWith("//")) "https:$src" else src
                originalImageUrls.add(fullUrl.replace("&amp;", "&"))
            }
        }

        return ForwardResult_OLD(forwarderText, originalAuthor, originalText, originalImageUrls)
    }

    /**
     * 旧版提取图片URL方法
     */
    private fun extractImageUrls_OLD(document: Document, excludeBlockquote: Boolean = false): List<String> {
        val imageUrls = mutableListOf<String>()

        val targetDoc = if (excludeBlockquote) {
            val clone = document.clone()
            clone.select("blockquote.source").remove()
            clone
        } else {
            document
        }

        // 策略1: QQ空间移动版使用span.img作为图片容器（最常见）
        targetDoc.select("span.img, span[class*='img']").forEach { span ->
            val style = span.attr("style")
            if (style.isNotEmpty()) {
                val urlPattern = """background-image:\s*url\(["']?([^"')]+)["']?\)""".toRegex()
                urlPattern.find(style)?.groupValues?.get(1)?.let { rawUrl ->
                    val decodedUrl = rawUrl
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")

                    val fullUrl = when {
                        decodedUrl.startsWith("//") -> "https:$decodedUrl"
                        decodedUrl.startsWith("http") -> decodedUrl
                        else -> "https://$decodedUrl"
                    }

                    if (fullUrl.startsWith("http") &&
                        !fullUrl.contains("qqemoji") &&
                        !fullUrl.contains("/em/")) {
                        imageUrls.add(fullUrl)
                    }
                }
            }
        }

        // 策略2: 查找所有img标签（备用方案）
        val imgSelectors = listOf(
            "div.images img",
            "div.img-box img",
            "img.pic",
            "img[src*='qpic.cn']",
            "img[src*='m.qpic.cn']",
            "div.feed-img img",
            "div.photo-list img"
        )

        for (selector in imgSelectors) {
            targetDoc.select(selector).forEach { img ->
                val src = img.attr("src").takeIf { it.isNotBlank() }
                    ?: img.attr("data-src").takeIf { it.isNotBlank() }
                    ?: img.attr("data-original").takeIf { it.isNotBlank() }
                    ?: img.attr("data-url")

                if (!src.isNullOrBlank() && (src.startsWith("http") || src.startsWith("//"))) {
                    val fullUrl = if (src.startsWith("//")) "https:$src" else src
                    if (!fullUrl.contains("qqemoji") &&
                        !fullUrl.contains("qlogo") &&
                        !fullUrl.contains("qzonestyle") &&
                        fullUrl.contains("qpic")) {
                        imageUrls.add(fullUrl)
                    }
                }
            }
        }

        // 策略3: 查找data-image-url属性
        targetDoc.select("[data-image-url]").forEach { element ->
            val url = element.attr("data-image-url")
            if (url.isNotBlank() && url.startsWith("http") && url.contains("qpic")) {
                imageUrls.add(url)
            }
        }

        return imageUrls.distinct()
    }
}
