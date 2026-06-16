package com.susking.ephone_s.tavern.ui.jinnang

/**
 * 锦囊宿主契约。
 *
 * 由 TavernFragment 实现，锦囊子页面（[JinnangHomeFragment] 等）通过
 * `parentFragment as? JinnangHost` 取得宿主，向其请求页面导航、保存对话与关闭锦囊，
 * 从而把「需要 WebView / Fragment 事务」的能力收敛在宿主，子页面保持纯 UI。
 */
interface JinnangHost {
    /** 进入提示词储存器页面。 */
    fun jinnangNavigateToPromptStorage()

    /** 请求保存当前酒馆对话到酒馆记录（由宿主读取 WebView 并写文件）。 */
    fun jinnangRequestSaveChat()

    /** 请求把 SillyTavern 全局正则一键拉取到酒馆记录（由宿主读取 WebView 并写入）。 */
    fun jinnangRequestPullRegex()

    /** 关闭整个锦囊覆盖层。 */
    fun jinnangClose()
}
