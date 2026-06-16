package com.susking.ephone_s.brain

import android.graphics.Color
import com.susking.ephone_s.brain.api.FloatingWindowStyleProvider
import com.susking.ephone_s.features.theme.domain.model.Theme
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * 将 app 主题仓库适配为 brain 模块悬浮窗样式接口。
 *
 * 这里保持 brain 模块无状态、只读取当前主题快照；真正的主题持久化与迁移仍由 app 的主题仓库负责。
 *
 * 设计要点：getter 全部走内存缓存，禁止在调用线程上 runBlocking 拉 Flow。
 * brain 悬浮窗刷新会连续调用多个 getter，若每次都阻塞拉取，将造成调用线程（常为主线程）卡顿甚至 ANR。
 * 因此构造时用应用级 IO 协程作用域订阅当前主题 Flow，把最新主题写入 @Volatile 缓存，getter 直接读缓存。
 *
 * 该实例由 Application 单例持有并复用，作用域与进程同生命周期，无需手动取消。
 */
class FloatingWindowStyleProviderImpl @Inject constructor(
    private val themeRepository: ThemeRepository
) : FloatingWindowStyleProvider {

    private val cacheScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedTheme: Theme = createFallbackTheme()

    init {
        // 订阅当前主题，主题变更时实时刷新缓存；首帧若 Flow 尚未发射则沿用 fallback。
        themeRepository.getCurrentTheme()
            .onEach { theme -> cachedTheme = theme }
            .launchIn(cacheScope)
    }

    override fun getDefaultImageUri(): String {
        return cachedTheme.floatingWindowStyle.defaultImageUri
    }

    override fun getDraggingImageUri(): String {
        return cachedTheme.floatingWindowStyle.draggingImageUri
    }

    override fun getDockedImageUri(): String {
        return cachedTheme.floatingWindowStyle.dockedImageUri
    }

    override fun getBackgroundColor(): Int {
        return cachedTheme.floatingWindowStyle.backgroundColor
    }

    override fun getTextColor(): Int {
        return cachedTheme.floatingWindowStyle.textColor
    }

    override fun getAccentColor(): Int {
        return cachedTheme.floatingWindowStyle.accentColor
    }

    override fun getCardBackgroundColor(): Int {
        return cachedTheme.floatingWindowStyle.cardBackgroundColor
    }

    private fun createFallbackTheme(): Theme {
        val fallbackStyle = com.susking.ephone_s.features.theme.domain.model.FloatingWindowStyle(
            id = "fallback_brain",
            name = "默认悬浮窗",
            defaultImageUri = "",
            draggingImageUri = "",
            dockedImageUri = "",
            backgroundColor = Color.WHITE,
            textColor = Color.BLACK,
            accentColor = Color.rgb(103, 80, 164),
            cardBackgroundColor = Color.WHITE
        )
        return Theme(
            id = "fallback_theme",
            name = "默认主题",
            description = "悬浮窗兜底主题。",
            author = "白枢",
            sourceType = com.susking.ephone_s.features.theme.domain.model.ThemeSourceType.BUILT_IN,
            previewUri = "",
            wallpaper = com.susking.ephone_s.features.theme.domain.model.WallpaperConfig(
                uri = "",
                blurRadius = 0,
                dimAmount = 0f,
                scaleType = com.susking.ephone_s.features.theme.domain.model.WallpaperScaleType.CENTER_CROP
            ),
            iconPack = com.susking.ephone_s.features.theme.domain.model.IconPack(
                id = "fallback_icons",
                name = "默认图标",
                icons = emptyMap(),
                fallbackIconUri = ""
            ),
            desktopStyle = com.susking.ephone_s.features.theme.domain.model.DesktopStyle(
                dockBackgroundColor = Color.WHITE,
                dockBackgroundAlpha = 220,
                dockCornerRadiusDp = 28f,
                pageIndicatorSelectedColor = Color.WHITE,
                pageIndicatorUnselectedColor = Color.GRAY,
                appLabelColor = Color.WHITE,
                appLabelShadowColor = Color.BLACK,
                isAppLabelShadowEnabled = true
            ),
            floatingWindowStyle = fallbackStyle,
            colorScheme = com.susking.ephone_s.features.theme.domain.model.ThemeColorScheme(
                primaryColor = Color.rgb(103, 80, 164),
                onPrimaryColor = Color.WHITE,
                primaryContainerColor = Color.rgb(234, 221, 255),
                onPrimaryContainerColor = Color.rgb(33, 0, 93),
                secondaryColor = Color.rgb(98, 91, 113),
                backgroundColor = Color.WHITE,
                surfaceColor = Color.WHITE,
                onSurfaceColor = Color.BLACK,
                errorColor = Color.rgb(186, 26, 26)
            ),
            nightMode = com.susking.ephone_s.features.theme.domain.model.ThemeNightMode.FOLLOW_SYSTEM,
            version = 1,
            updatedAt = 0L
        )
    }
}
