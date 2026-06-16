
package com.susking.ephone_s.qq.ui.qzone

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.aidata.data.local.entity.FeedComment
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.data.local.entity.NotificationEntity
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.NotificationRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.qq.util.QqEmojiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject

data class FeedItemUiModel(
    val feed: FeedEntity,
    val authorAvatarUrl: String?,
    val isLikedByUser: Boolean,
    val likerNames: String,
    val originalFeed: FeedEntity? = null
)

// 定义一个私有辅助类，用于统一处理"人"的信息，避免直接操作复杂的 PersonProfile
private data class PersonInfo(val name: String, val avatarUrl: String?)

// 解析出的单条说说数据
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
    val comments: List<QzoneComment>,
    val likes: String,
    val views: String
)

data class QzoneComment(
    val username: String,
    val text: String,
    val time: String
)

@HiltViewModel
class QqFeedsViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    // --- 私有数据源 ---

    private val allFeedsSource: LiveData<List<FeedEntity>> = feedRepository.getAllFeeds().asLiveData()
    private val userProfileSource: LiveData<UserProfile> = liveData(Dispatchers.IO) { emit(personProfileRepository.getUserProfile()) }

    // 创建一个统一的"人物地图"，将您自己和所有AI联系人包含在内
    private val peopleMapSource: LiveData<Map<String, PersonInfo>> = liveData(Dispatchers.IO) {
        val contacts = personProfileRepository.getPersonProfiles()
        val user = personProfileRepository.getUserProfile()

        val people = contacts.associate { contact ->
            contact.id to PersonInfo(contact.realName, contact.avatarUri)
        }.toMutableMap()
        people[user.id] = PersonInfo(user.nickname, user.avatarUri)
        emit(people)
    }

    val userProfile: LiveData<UserProfile> = userProfileSource

    private val _feedsBackgroundUri = MutableLiveData<String?>()
    val feedsBackgroundUri: LiveData<String?> = _feedsBackgroundUri

    val feedItems = MediatorLiveData<List<FeedItemUiModel>>()

    init {
        loadFeedsBackground()

        // 监听所有数据源，当任何一个发生变化时，重新计算UI模型
        feedItems.addSource(allFeedsSource) { recomputeUiModels() }
        feedItems.addSource(userProfileSource) { recomputeUiModels() }
        feedItems.addSource(peopleMapSource) { recomputeUiModels() }
    }

    private fun recomputeUiModels() {
        val feeds = allFeedsSource.value ?: return
        val user = userProfileSource.value ?: return
        val people = peopleMapSource.value ?: return

        val feedsById = feeds.associateBy { it.id }
        val uiModels = feeds.map { feed ->
            val originalFeed = feed.originalFeedId?.let { feedsById[it] }
            FeedItemUiModel(
                feed = feed,
                authorAvatarUrl = people[feed.contactId]?.avatarUrl,
                isLikedByUser = feed.likes.contains(user.id),
                likerNames = feed.likes.mapNotNull { likerId -> people[likerId]?.name }.joinToString(", "),
                originalFeed = originalFeed
            )
        }
        feedItems.value = uiModels
    }

    /**
     * 【新增】从后台刷新动态列表。
     */
    fun refreshFeeds() {
        recomputeUiModels()
    }
 
    private fun loadFeedsBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            // getFeedsBackground已迁移，暂时设置为null
            _feedsBackgroundUri.postValue(null)
        }
    }

    // --- 公开给UI的事件处理方法 ---

    fun deleteFeed(feed: FeedEntity) {
        viewModelScope.launch {
            feedRepository.deleteFeed(feed)
        }
    }

    fun favoriteFeed(feed: FeedEntity) {
        viewModelScope.launch {
            val people = peopleMapSource.value
            val authorAvatar = people?.get(feed.contactId)?.avatarUrl
            val favorite = FavoriteMessageEntity(
                messageId = "feed_${feed.id}",
                contactId = feed.contactId,
                text = feed.content,
                senderName = feed.authorName,
                senderAvatar = authorAvatar,
                source = "动态",
                timestamp = feed.timestamp,
                type = "text"
            )
        }
    }

    fun toggleLike(feedId: Int) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        val user = userProfileSource.value ?: return@launch
        val currentLikes = feed.likes.toMutableList()
        if (currentLikes.contains(user.id)) {
            currentLikes.remove(user.id)
        } else {
            currentLikes.add(user.id)
        }
        feedRepository.updateFeed(feed.copy(likes = currentLikes))
    }

    fun addComment(feedId: Int, commentText: String) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        val user = userProfileSource.value ?: return@launch
        val newComment = FeedComment(
            commenterId = user.id,
            commenterName = user.nickname,
            commentText = commentText,
            timestamp = System.currentTimeMillis()
        )
        val currentComments = feed.comments.toMutableList()
        currentComments.add(newComment)
        feedRepository.updateFeed(feed.copy(comments = currentComments))

        if (feed.contactId != user.id) {
            val notification = NotificationEntity(
                type = "new_comment",
                feedId = feedId,
                commentId = newComment.id,
                actorId = user.id,
                actorName = user.nickname,
                timestamp = System.currentTimeMillis()
            )
            notificationRepository.addNotification(notification)
        }
    }

    fun updateFeedsBackground(backgroundUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val uriString = backgroundUri?.toString()
            _feedsBackgroundUri.postValue(uriString)
        }
    }

    fun shareFeed(feed: FeedEntity) = viewModelScope.launch(Dispatchers.IO) {
        val user = userProfile.value ?: return@launch
        feedRepository.shareFeed(feed, user.id, user.nickname)
    }

    fun deleteComment(feedId: Int, commentId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        val updatedComments = feed.comments.filter { it.id != commentId }
        feedRepository.updateFeed(feed.copy(comments = updatedComments))
    }

    fun editComment(feedId: Int, commentId: Long, newCommentText: String) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        val updatedComments = feed.comments.map {
            if (it.id == commentId) {
                it.copy(commentText = newCommentText)
            } else {
                it
            }
        }
        feedRepository.updateFeed(feed.copy(comments = updatedComments))
    }

    fun editFeed(feedId: Int, newContent: String) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        feedRepository.updateFeed(feed.copy(content = newContent))
    }

    fun updateFeedImages(feedId: Int, newImageUrls: List<String>, newImagePrompts: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val feed = feedRepository.getFeedById(feedId) ?: return@launch
        feedRepository.updateFeed(feed.copy(
            imageUrls = newImageUrls,
            imagePrompts = newImagePrompts
        ))
    }

    /**
     * 从HTML文件解析QQ空间说说内容
     * @param htmlContent HTML文件内容
     * @return ParseResult 解析结果，包含所有说说的列表
     */
    suspend fun parseQzoneHtml(htmlContent: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.parse(htmlContent)
            
            // 验证HTML文件类型
            val htmlTag = document.select("html").first()
            val htmlClass = htmlTag?.attr("class") ?: ""

            // 检查是否为QQ空间个人主页：html标签需要包含 pg-profile 类
            if (!htmlClass.contains("pg-profile")) {
                val errorMsg = "网页类型错误，请上传自己的空间主页\n" +
                        "【调试信息】HTML标签的class=\"$htmlClass\""
                android.util.Log.e("QzoneParse", errorMsg)
                return@withContext ParseResult.Error(errorMsg)
            }
            
            // 验证URL格式：从base标签或link[rel=canonical]标签获取
            // 必须是 user.qzone.qq.com/数字QQ号/main
            val baseElement = document.select("base[href]").first()
            val baseHref = baseElement?.attr("href") ?: ""
            
            // 如果没有base标签，尝试从link[rel=canonical]获取
            val canonicalElement = document.select("link[rel=canonical]").first()
            val canonicalHref = canonicalElement?.attr("href") ?: ""

            // 优先使用canonical，其次使用base
            val qzoneUrl = if (canonicalHref.isNotBlank()) canonicalHref else baseHref
            android.util.Log.d("QzoneParse", "最终使用的URL: [$qzoneUrl]")
            
            // 正则表达式：匹配 user.qzone.qq.com/ 后面跟数字QQ号，再跟 /main
            val qzoneUrlPattern = """user\.qzone\.qq\.com/\d+/main""".toRegex()
            val patternMatches = qzoneUrlPattern.containsMatchIn(qzoneUrl)
            android.util.Log.d("QzoneParse", "URL格式是否匹配: $patternMatches")
            
            if (!patternMatches) {
                val errorMsg = "网页类型错误，请上传自己的空间主页\n" +
                        "【调试信息】\n" +
                        "Base标签的href=\"$baseHref\"\n" +
                        "Canonical标签的href=\"$canonicalHref\"\n" +
                        "期望格式：user.qzone.qq.com/数字QQ号/main"
                android.util.Log.e("QzoneParse", errorMsg)
                return@withContext ParseResult.Error(errorMsg)
            }
            
            android.util.Log.d("QzoneParse", "HTML标签和URL验证通过，继续解析...")
            
            // 关键步骤：桌面版空间的内容在iframe的srcdoc属性里
            val iframeDocString = document.select("iframe#QM_Feeds_Iframe").attr("srcdoc")
            if (iframeDocString.isBlank()) {
                return@withContext ParseResult.Error("未找到QQ空间说说内容，请确保上传的是从 user.qzone.qq.com 保存的完整HTML文件")
            }
            
            // 将iframe的内容作为新的文档进行解析
            val feedsDocument = Jsoup.parse(iframeDocString)
            
            // 解析CSS变量
            val cssVariables = parseCssVariables(feedsDocument)
            
            // 选取所有说说的li元素
            val feedElements = feedsDocument.select("li.f-single")
            if (feedElements.isEmpty()) {
                return@withContext ParseResult.Error("未找到任何说说，请检查HTML文件是否完整")
            }
            
            // 解析所有说说
            val feeds = feedElements.mapNotNull { feedElement ->
                try {
                    parseFeed(feedElement, cssVariables)
                } catch (e: Exception) {
                    null // 忽略解析失败的单条说说
                }
            }
            
            if (feeds.isEmpty()) {
                ParseResult.Error("说说解析失败，请检查HTML文件格式")
            } else {
                ParseResult.Success(feeds = feeds)
            }
        } catch (e: Exception) {
            ParseResult.Error("解析失败：${e.message ?: "未知错误"}")
        }
    }
    
    /**
     * 解析HTML中的CSS变量定义
     */
    private fun parseCssVariables(document: Document): Map<String, String> {
        val cssVariables = mutableMapOf<String, String>()
        
        document.select("style").forEach { styleElement ->
            val cssContent = styleElement.html()
            val pattern = """--(sf-img-\d+):\s*url\("([^"]+)"\)""".toRegex()
            pattern.findAll(cssContent).forEach { matchResult ->
                val varName = matchResult.groupValues[1]
                val url = matchResult.groupValues[2]
                cssVariables[varName] = url
            }
        }
        
        return cssVariables
    }
    
    /**
     * 解析单条说说元素
     */
    private fun parseFeed(feedElement: Element, cssVariables: Map<String, String>): QzoneFeed {
        // 判断是否为转发说说
        val txtBox = feedElement.select(".f-ct-txtimg .txt-box").first()
        val isForward = txtBox != null && txtBox.text().trim().isNotEmpty()
        
        var content = ""
        var forwarderText: String? = null
        var originalAuthor: String? = null
        var originalImageUrls: List<String>? = null
        
        if (isForward && txtBox != null) {
            // 转发说说逻辑
            forwarderText = extractTextWithEmoji(feedElement.select(".f-info").first(), cssVariables)
            originalAuthor = txtBox.select("a.nickname").first()?.text()?.trim()
            content = extractOriginalContent(txtBox, cssVariables)
            
            val imgBox = feedElement.select(".f-ct-txtimg .img-box").first()
            if (imgBox != null) {
                originalImageUrls = extractImageUrlsFromBox(imgBox)
            }
        } else {
            // 原创说说逻辑
            content = extractTextWithEmoji(feedElement.select(".f-info").first(), cssVariables)
        }
        
        return QzoneFeed(
            isForward = isForward,
            content = content,
            forwarderText = forwarderText,
            originalAuthor = originalAuthor,
            imageUrls = if (!isForward) extractImageUrlsFromElement(feedElement, false) else emptyList(),
            originalImageUrls = originalImageUrls,
            videoUrls = extractVideoUrls(feedElement),
            timeText = extractTimeTextFromElement(feedElement),
            username = extractUsername(feedElement),
            device = extractDeviceWithEmoji(feedElement, cssVariables),
            comments = extractComments(feedElement, cssVariables),
            likes = extractLikes(feedElement),
            views = extractViews(feedElement)
        )
    }
    
    /**
     * 提取转发说说中的原内容
     */
    private fun extractOriginalContent(txtBox: Element, cssVariables: Map<String, String>): String {
        val clone = txtBox.clone()
        clone.select("a.nickname").first()?.remove()
        clone.select(".state").remove()
        val text = extractTextWithEmoji(clone, cssVariables)
        return text.removePrefix(":").trim()
    }
    
    /**
     * 从.img-box元素中提取图片URLs
     */
    private fun extractImageUrlsFromBox(imgBox: Element): List<String> {
        val imageUrls = mutableListOf<String>()
        
        imgBox.select("a.img-item img").forEach { img ->
            val src = img.attr("src")
            // 保留完整的base64数据，以便Glide可以正确加载
            if (src.isNotBlank()) {
                imageUrls.add(src)
            }
        }
        
        return imageUrls.distinct()
    }
    
    private fun extractUsername(feedElement: Element): String {
        return feedElement.select(".f-nick a.f-name").first()?.text()?.trim() ?: "（未知用户）"
    }
    
    private fun extractTimeTextFromElement(feedElement: Element): String {
        return feedElement.select(".info-detail .state").first()?.text()?.trim() ?: "（未知时间）"
    }
    
    private fun extractDeviceWithEmoji(feedElement: Element, cssVariables: Map<String, String>): String {
        val deviceElement = feedElement.select(".f-reprint .phone-style").first()
        return extractTextWithEmoji(deviceElement, cssVariables)
    }
    
    private fun extractImageUrlsFromElement(element: Element, isForward: Boolean): List<String> {
        val imageUrls = mutableListOf<String>()
        val targetElement = if (isForward) {
            element.select(".f-repost").first() ?: element
        } else {
            element.select(".f-ct-txtimg").first() ?: element
        }
        
        targetElement.select(".img-box a.img-item img").forEach { img ->
            val imgClass = img.attr("class")
            if (imgClass.contains("user-avatar")) {
                return@forEach
            }
            
            val src = img.attr("src")
            // 保留完整的base64数据，以便Glide可以正确加载
            if (src.isNotBlank()) {
                imageUrls.add(src)
            }
        }
        
        return imageUrls.distinct()
    }
    
    private fun extractVideoUrls(feedElement: Element): List<String> {
        val videoUrls = mutableListOf<String>()
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
        val likeList = feedElement.select(".f-like-list .user-list").first()?.text()?.trim()
        return if (likeList.isNullOrEmpty()) "0人点赞" else likeList
    }
    
    private fun extractViews(feedElement: Element): String {
        return feedElement.select(".f-op-detail .state[data-role=Visitor]").first()?.text()?.trim() ?: "（未知）"
    }
    
    private fun extractComments(feedElement: Element, cssVariables: Map<String, String>): List<QzoneComment> {
        val comments = mutableListOf<QzoneComment>()
        
        feedElement.select(".comments-list > ul > li.comments-item[data-type=commentroot]").forEach { commentItem ->
            val singleReply = commentItem.select(".single-reply").first()
            if (singleReply != null) {
                val username = singleReply.select(".comments-content a.nickname").first()?.text()?.trim() ?: "（匿名）"
                val contentElement = singleReply.select(".comments-content").first()
                val text = extractCommentTextWithEmoji(contentElement, cssVariables)
                val time = singleReply.select(".comments-op .state").first()?.text()?.trim() ?: ""
                
                if (text.isNotEmpty()) {
                    comments.add(QzoneComment(username, text, time))
                }
            }
            
            commentItem.select(".mod-comments-sub li.comments-item[data-type=replyroot]").forEach { subCommentItem ->
                val singleSubReply = subCommentItem.select(".single-reply").first()
                if (singleSubReply != null) {
                    val contentElement = singleSubReply.select(".comments-content").first()
                    if (contentElement != null) {
                        val nicknames = contentElement.select("a.nickname")
                        val replyFromUser = nicknames.getOrNull(0)?.text()?.trim() ?: "（匿名）"
                        val replyToUser = nicknames.getOrNull(1)?.text()?.trim() ?: ""
                        
                        val subText = extractCommentTextWithEmoji(contentElement, cssVariables)
                        val subTime = singleSubReply.select(".comments-op .state").first()?.text()?.trim() ?: ""
                        
                        if (subText.isNotEmpty()) {
                            val formattedText = if (replyToUser.isNotEmpty()) {
                                "$replyFromUser 回复 $replyToUser: $subText"
                            } else {
                                subText
                            }
                            comments.add(QzoneComment(replyFromUser, formattedText, subTime))
                        }
                    }
                }
            }
        }
        return comments
    }
    
    private fun extractCssVariableUrl(style: String, cssVariables: Map<String, String>): String? {
        val pattern = """background-image:\s*var\(--([^)]+)\)""".toRegex()
        val matchResult = pattern.find(style) ?: return null
        val varName = matchResult.groupValues[1]
        return cssVariables[varName]
    }
    
    private fun extractTextWithEmoji(element: Element?, cssVariables: Map<String, String>): String {
        if (element == null) return ""
        
        val result = StringBuilder()
        
        element.childNodes().forEach { node ->
            when {
                node is org.jsoup.nodes.TextNode -> {
                    result.append(node.text())
                }
                node is Element -> {
                    if (node.tagName() == "img") {
                        val style = node.attr("style")
                        if (style.contains("background-image") && style.contains("var(--sf-img-")) {
                            extractCssVariableUrl(style, cssVariables)?.let { emojiUrl ->
                                // 保留完整的emoji base64数据
                                result.append("[emoji:$emojiUrl]")
                            }
                        } else {
                            val alt = node.attr("alt")
                            if (alt.isNotBlank()) {
                                result.append(alt)
                            }
                        }
                    } else {
                        result.append(extractTextWithEmoji(node, cssVariables))
                    }
                }
            }
        }
        
        // 转换QQ表情为Unicode emoji
        return QqEmojiMapper.convertQqEmojiToUnicode(result.toString().trim())
    }
    
    private fun extractCommentTextWithEmoji(element: Element?, cssVariables: Map<String, String>): String {
        if (element == null) return ""
        
        val clone = element.clone()
        clone.select("a.nickname").remove()
        clone.select(".comments-op").remove()
        
        val result = StringBuilder()
        
        clone.childNodes().forEach { node ->
            when {
                node is org.jsoup.nodes.TextNode -> {
                    result.append(node.text())
                }
                node is Element -> {
                    if (node.tagName() == "img") {
                        val style = node.attr("style")
                        if (style.contains("background-image") && style.contains("var(--sf-img-")) {
                            extractCssVariableUrl(style, cssVariables)?.let { emojiUrl ->
                                // 保留完整的emoji base64数据
                                result.append("[emoji:$emojiUrl]")
                            }
                        } else {
                            val src = node.attr("src")
                            if (src.isNotBlank()) {
                                // 保留完整的图片URL(包括base64),用于PreviewCommentAdapter提取和显示
                                result.append("（带图片评论：$src）")
                            } else {
                                val alt = node.attr("alt")
                                if (alt.isNotBlank()) {
                                    result.append(alt)
                                }
                            }
                        }
                    } else if (node.className().contains("comments-thumbnails")) {
                        node.select("img").forEach { img ->
                            val src = img.attr("src")
                            if (src.isNotBlank()) {
                                // 保留完整的图片URL(包括base64)
                                result.append("（带图片评论：$src）")
                            }
                        }
                    } else {
                        result.append(extractCommentTextWithEmoji(node, cssVariables))
                    }
                }
            }
        }
        
        val rawText = result.toString()
            .removePrefix(":")
            .removePrefix(" 回复 ")
            .removePrefix("回复")
            .trim()
            .replace(Regex("^:\\s*"), "")
        
        // 转换QQ表情为Unicode emoji
        return QqEmojiMapper.convertQqEmojiToUnicode(rawText)
    }

    /**
     * 批量导入解析出的说说
     */
    suspend fun importFeedsFromHtml(feeds: List<QzoneFeed>) = withContext(Dispatchers.IO) {
        val user = userProfile.value ?: return@withContext
        
        feeds.forEach { qzoneFeed ->
            try {
                // 根据是否有图片选择合适的创建方法
                if (qzoneFeed.imageUrls.isNotEmpty()) {
                    feedRepository.createFeedWithImages(
                        contactId = user.id,
                        authorName = user.nickname,
                        content = qzoneFeed.content,
                        imageUrls = qzoneFeed.imageUrls,
                        imagePrompts = emptyList(),
                        imageDescriptions = List(qzoneFeed.imageUrls.size) { "从QQ空间导入" }
                    )
                } else {
                    feedRepository.createFeed(
                        contactId = user.id,
                        authorName = user.nickname,
                        content = qzoneFeed.content
                    )
                }
            } catch (e: Exception) {
                // 忽略单条导入失败
            }
        }
    }

    /**
     * 获取分页的说说列表
     * @param feeds 所有说说列表
     * @param page 页码（从0开始）
     * @param pageSize 每页数量，默认10条
     * @return 当前页的说说列表
     */
    fun getPagedFeeds(feeds: List<QzoneFeed>, page: Int, pageSize: Int = 10): List<QzoneFeed> {
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, feeds.size)
        return if (startIndex < feeds.size) {
            feeds.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    /**
     * 计算总页数
     * @param totalCount 总说说数
     * @param pageSize 每页数量，默认10条
     * @return 总页数
     */
    fun getTotalPages(totalCount: Int, pageSize: Int = 10): Int {
        return if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize
    }
}

/**
 * 解析结果密封类
 */
sealed class ParseResult {
    data class Success(val feeds: List<QzoneFeed>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}