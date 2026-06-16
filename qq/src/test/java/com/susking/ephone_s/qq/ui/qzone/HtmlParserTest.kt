package com.susking.ephone_s.qq.ui.qzone

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Test
import java.io.File

/**
 * HTML解析器测试类
 * 用于提取网页中所有HTML代码并显示
 */
class HtmlParserTest {
    /**
     * 缩略显示base64编码的字符串
     * @param html 原始HTML字符串
     * @return 处理后的HTML字符串，base64数据被缩略显示
     */
    private fun abbreviateBase64(html: String): String {
        // 匹配data:image开头的base64编码（常见于图片）
        val imageBase64Pattern = Regex("""data:image/[^;]+;base64,([A-Za-z0-9+/=]{100,})""")
        var result = html
        
        imageBase64Pattern.findAll(html).forEach { match ->
            val fullBase64 = match.groupValues[1]
            val abbreviated = "${fullBase64.take(50)}...[省略${fullBase64.length - 100}个字符]...${fullBase64.takeLast(50)}"
            result = result.replace(match.value, "data:image/...;base64,$abbreviated")
        }
        
        // 匹配其他可能的base64编码（通用模式）
        val generalBase64Pattern = Regex("""([A-Za-z0-9+/=]{200,})""")
        generalBase64Pattern.findAll(result).forEach { match ->
            val fullBase64 = match.value
            // 检查是否真的是base64（简单判断：长度够长且字符集匹配）
            if (fullBase64.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" }) {
                val abbreviated = "${fullBase64.take(50)}...[省略${fullBase64.length - 100}个字符]...${fullBase64.takeLast(50)}"
                result = result.replace(fullBase64, abbreviated)
            }
        }
        
        return result
    }
    
    /**
     * 从本地HTML文件获取HTML
     * 使用方法：将HTML文件路径替换为实际文件路径
     */
    @Test
    fun testFetchHtmlFromLocalFile() {
        // 修改为实际的本地HTML文件路径
        val filePath = ""+
                "C:/Users/Susking/Downloads/公开.html"
//                "C:/Users/Susking/Downloads/个人_files/feeds_html_module.html"
        try {
            val file = File(filePath)
            if (!file.exists()) {
                println("文件不存在：$filePath")
                return
            }
            
            // 使用Jsoup解析本地HTML文件
            val document = Jsoup.parse(file, "UTF-8")
            val htmlContent = document.html()
            
            // 缩略显示base64编码
            val abbreviatedHtml = abbreviateBase64(htmlContent)
            
            println("=== 完整HTML（从本地文件获取）===")
            println("文件路径：$filePath")
            println("文件大小：${file.length()} 字节")
            println("原始HTML长度：${htmlContent.length} 字符")
            println("处理后HTML长度：${abbreviatedHtml.length} 字符")
            println()
            println(abbreviatedHtml)
        } catch (e: Exception) {
            println("读取HTML文件失败：${e.message}")
            e.printStackTrace()
        }
    }
}