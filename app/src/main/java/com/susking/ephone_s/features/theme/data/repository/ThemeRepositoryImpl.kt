package com.susking.ephone_s.features.theme.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.R
import com.susking.ephone_s.core.desktop.DesktopAppNames
import com.susking.ephone_s.features.theme.domain.model.DesktopStyle
import com.susking.ephone_s.features.theme.domain.model.FloatingWindowStyle
import com.susking.ephone_s.features.theme.domain.model.IconPack
import com.susking.ephone_s.features.theme.domain.model.Theme
import com.susking.ephone_s.features.theme.domain.model.ThemeColorScheme
import com.susking.ephone_s.features.theme.domain.model.ThemeNightMode
import com.susking.ephone_s.features.theme.domain.model.ThemeSourceType
import com.susking.ephone_s.features.theme.domain.model.WallpaperConfig
import com.susking.ephone_s.features.theme.domain.model.WallpaperScaleType
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ThemeRepositoryImpl(
    private val context: Context,
    private val gson: Gson
) : ThemeRepository {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val builtInThemes: List<Theme> by lazy { createBuiltInThemes() }
    private val defaultTheme: Theme by lazy { builtInThemes.first { theme -> theme.id == DEFAULT_THEME_ID } }
    private val pinnedThemeIdsState: MutableStateFlow<List<String>> by lazy { MutableStateFlow(loadPinnedThemeIds()) }
    private val customThemesState: MutableStateFlow<List<Theme>> by lazy { MutableStateFlow(loadCustomThemes()) }
    private val themesState: MutableStateFlow<List<Theme>> by lazy { MutableStateFlow(buildAvailableThemes()) }
    private val currentThemeState: MutableStateFlow<Theme> by lazy { MutableStateFlow(loadCurrentTheme()) }
    private val currentThemeFlow: Flow<Theme> by lazy { currentThemeState.asStateFlow() }

    // 仓库级协程作用域，仅用于响应导入后的异步刷新；进程级单例，无需手动取消。
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 监听数据导入完成广播：导入流程只写 SharedPreferences、绕过本单例，
    // 收到广播后重新读盘刷新内存状态，避免必须重启进程才能让导入的主题生效。
    private val themeReloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != ACTION_THEME_RELOAD) return
            repositoryScope.launch { reloadFromStorage() }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            themeReloadReceiver,
            IntentFilter(ACTION_THEME_RELOAD),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun getThemes(): Flow<List<Theme>> {
        return themesState.asStateFlow()
    }

    override fun getCurrentTheme(): Flow<Theme> {
        return currentThemeFlow
    }

    override suspend fun setCurrentTheme(theme: Theme) {
        val safeTheme: Theme = findThemeById(theme.id) ?: theme
        currentThemeState.value = safeTheme
        saveCurrentTheme(safeTheme)
        applyNightMode(safeTheme.nightMode)
        notifyThemeChanged()
    }

    override suspend fun resetToDefaultTheme() {
        setCurrentTheme(defaultTheme)
    }

    override suspend fun createCustomThemeWithWallpaperCustomization(
        name: String,
        description: String,
        wallpaperUri: String,
        iconOverrides: Map<String, String>,
        dockedBrainImageUri: String?,
        draggingBrainImageUri: String?
    ): Theme {
        // 壁纸、图标等资源复制属于阻塞型文件 IO，统一切到 IO 线程，避免卡主线程导致 ANR。
        val customTheme: Theme = withContext(Dispatchers.IO) {
            val stableWallpaperUri: String = copyThemeResource(wallpaperUri, THEME_WALLPAPER_PREFIX)
            val baseTheme: Theme = currentThemeState.value
            val stableIconOverrides: Map<String, String> = iconOverrides.mapValues { entry ->
                copyThemeResource(entry.value, THEME_ICON_PREFIX)
            }
            val stableDockedBrainImageUri: String? = dockedBrainImageUri?.let { uri ->
                copyThemeResource(uri, THEME_BRAIN_PREFIX)
            }
            val stableDraggingBrainImageUri: String? = draggingBrainImageUri?.let { uri ->
                copyThemeResource(uri, THEME_BRAIN_PREFIX)
            }
            buildCustomTheme(
                baseTheme = baseTheme,
                name = name,
                description = description,
                wallpaperUri = stableWallpaperUri
            ).let { theme ->
                applyThemeResourceOverrides(
                    theme = theme,
                    iconOverrides = stableIconOverrides,
                    dockedBrainImageUri = stableDockedBrainImageUri,
                    draggingBrainImageUri = stableDraggingBrainImageUri
                )
            }
        }
        saveCustomTheme(customTheme)
        setCurrentTheme(customTheme)
        return customTheme
    }

    override suspend fun pinTheme(theme: Theme) {
        val pinnedThemeIds: MutableList<String> = pinnedThemeIdsState.value.toMutableList()
        pinnedThemeIds.remove(theme.id)
        pinnedThemeIds.add(0, theme.id)
        pinnedThemeIdsState.value = pinnedThemeIds
        savePinnedThemeIds(pinnedThemeIds)
        themesState.value = buildAvailableThemes()
    }

    override suspend fun updateCustomThemeAppearance(
        theme: Theme,
        name: String,
        description: String,
        wallpaperUri: String,
        iconOverrides: Map<String, String>,
        dockedBrainImageUri: String?,
        draggingBrainImageUri: String?
    ): Theme? {
        if (theme.sourceType != ThemeSourceType.CUSTOM) return null
        // 记录编辑前主题引用的所有本地资源，编辑成功后清理不再被引用的旧文件，避免磁盘只增不减。
        val previousResourceUris: Set<String> = collectThemeResourceUris(theme)
        // 资源复制属于阻塞型文件 IO，统一切到 IO 线程，避免卡主线程导致 ANR。
        val updatedTheme: Theme = withContext(Dispatchers.IO) {
            val stableWallpaperUri: String = copyThemeResource(wallpaperUri, THEME_WALLPAPER_PREFIX)
            val stableIconOverrides: Map<String, String> = iconOverrides.mapValues { entry ->
                copyThemeResource(entry.value, THEME_ICON_PREFIX)
            }
            val stableDockedBrainImageUri: String? = dockedBrainImageUri?.let { uri ->
                copyThemeResource(uri, THEME_BRAIN_PREFIX)
            }
            val stableDraggingBrainImageUri: String? = draggingBrainImageUri?.let { uri ->
                copyThemeResource(uri, THEME_BRAIN_PREFIX)
            }
            applyThemeResourceOverrides(
                theme = theme.copy(
                    name = name.ifBlank { theme.name },
                    description = description.ifBlank { theme.description },
                    previewUri = stableWallpaperUri,
                    wallpaper = theme.wallpaper.copy(uri = stableWallpaperUri),
                    updatedAt = System.currentTimeMillis()
                ),
                iconOverrides = stableIconOverrides,
                dockedBrainImageUri = stableDockedBrainImageUri,
                draggingBrainImageUri = stableDraggingBrainImageUri
            )
        }
        saveCustomTheme(updatedTheme)
        if (currentThemeState.value.id == theme.id) {
            setCurrentTheme(updatedTheme)
        }
        deleteUnreferencedResources(previousResourceUris)
        return updatedTheme
    }

    override suspend fun deleteCustomTheme(theme: Theme): Boolean {
        if (theme.sourceType != ThemeSourceType.CUSTOM) return false
        val customThemes: MutableList<Theme> = customThemesState.value.toMutableList()
        val removed: Boolean = customThemes.removeAll { customTheme -> customTheme.id == theme.id }
        if (!removed) return false
        // 收集被删主题引用的资源，删除后清理不再被任何主题引用的本地文件。
        val deletedResourceUris: Set<String> = collectThemeResourceUris(theme)
        customThemesState.value = customThemes
        saveCustomThemes(customThemes)
        // 取消置顶，避免残留无效置顶 id。
        if (pinnedThemeIdsState.value.contains(theme.id)) {
            val pinnedThemeIds: MutableList<String> = pinnedThemeIdsState.value.toMutableList()
            pinnedThemeIds.remove(theme.id)
            pinnedThemeIdsState.value = pinnedThemeIds
            savePinnedThemeIds(pinnedThemeIds)
        }
        themesState.value = buildAvailableThemes()
        // 若删除的是当前主题，回退到默认主题。
        if (currentThemeState.value.id == theme.id) {
            setCurrentTheme(defaultTheme)
        }
        deleteUnreferencedResources(deletedResourceUris)
        return true
    }

    override suspend fun reloadFromStorage() {
        // 导入完成后重新从 SharedPreferences 读取所有主题状态，刷新单例内存缓存，
        // 避免依赖用户手动重启进程才能让 lazy 初始化重新读盘。
        withContext(Dispatchers.IO) {
            val reloadedCustomThemes: List<Theme> = loadCustomThemes()
            val reloadedPinnedIds: List<String> = loadPinnedThemeIds()
            customThemesState.value = reloadedCustomThemes
            pinnedThemeIdsState.value = reloadedPinnedIds
            themesState.value = buildAvailableThemes()
            currentThemeState.value = loadCurrentTheme()
        }
        applyNightMode(currentThemeState.value.nightMode)
        notifyThemeChanged()
    }

    private fun createBuiltInThemes(): List<Theme> {
        val packageName: String = context.packageName
        val defaultIcons: Map<String, String> = createDefaultIconMap(packageName)
        val fallbackIconUri: String = resourceUri(packageName, R.drawable.app_ic_unknown_logo)
        val brainImageUri: String = resourceUri(packageName, R.drawable.ic_brain_docked)

        return listOf(
            createTheme(
                id = "default_light",
                name = "日间模式",
                description = "清爽明亮的默认小手机主题。",
                previewUri = resourceUri(packageName, R.drawable.white_background),
                wallpaperUri = resourceUri(packageName, R.drawable.white_background),
                iconPack = createIconPack("default", "默认图标", defaultIcons, fallbackIconUri),
                primaryColor = Color.rgb(103, 80, 164),
                backgroundColor = Color.rgb(255, 251, 254),
                surfaceColor = Color.WHITE,
                onSurfaceColor = Color.rgb(29, 27, 32),
                dockBackgroundColor = Color.WHITE,
                appLabelColor = Color.rgb(33, 33, 33),
                brainImageUri = brainImageUri,
                nightMode = ThemeNightMode.LIGHT
            ),
            createTheme(
                id = "dark_tavern",
                name = "酒馆暗夜",
                description = "适合夜晚使用的深色酒馆主题。",
                previewUri = resourceUri(packageName, R.drawable.gradient_purple),
                wallpaperUri = resourceUri(packageName, R.drawable.gradient_purple),
                iconPack = createIconPack("default_dark", "默认暗夜图标", defaultIcons, fallbackIconUri),
                primaryColor = Color.rgb(187, 134, 252),
                backgroundColor = Color.rgb(18, 18, 18),
                surfaceColor = Color.rgb(31, 31, 31),
                onSurfaceColor = Color.rgb(245, 239, 247),
                dockBackgroundColor = Color.rgb(36, 31, 43),
                appLabelColor = Color.WHITE,
                brainImageUri = brainImageUri,
                nightMode = ThemeNightMode.DARK
            ),
            createTheme(
                id = "pink_lovers",
                name = "粉色恋人",
                description = "柔软甜蜜的粉色主题，适合黏黏糊糊的小手机。",
                previewUri = resourceUri(packageName, R.drawable.white_background),
                wallpaperUri = resourceUri(packageName, R.drawable.white_background),
                iconPack = createIconPack("pink_default", "粉色默认图标", defaultIcons, fallbackIconUri),
                primaryColor = Color.rgb(214, 71, 125),
                backgroundColor = Color.rgb(255, 247, 250),
                surfaceColor = Color.rgb(255, 238, 245),
                onSurfaceColor = Color.rgb(51, 24, 36),
                dockBackgroundColor = Color.rgb(255, 226, 236),
                appLabelColor = Color.rgb(90, 35, 55),
                brainImageUri = brainImageUri,
                nightMode = ThemeNightMode.LIGHT
            ),
            createTheme(
                id = "system_follow",
                name = "跟随系统",
                description = "跟随真实手机系统深浅色设置的小手机主题。",
                previewUri = resourceUri(packageName, R.drawable.white_background),
                wallpaperUri = resourceUri(packageName, R.drawable.white_background),
                iconPack = createIconPack("system_default", "系统默认图标", defaultIcons, fallbackIconUri),
                primaryColor = Color.rgb(0, 105, 92),
                backgroundColor = Color.rgb(245, 250, 248),
                surfaceColor = Color.rgb(235, 246, 242),
                onSurfaceColor = Color.rgb(20, 33, 30),
                dockBackgroundColor = Color.rgb(225, 242, 237),
                appLabelColor = Color.rgb(19, 50, 44),
                brainImageUri = brainImageUri,
                nightMode = ThemeNightMode.FOLLOW_SYSTEM
            )
        )
    }

    private fun createTheme(
        id: String,
        name: String,
        description: String,
        previewUri: String,
        wallpaperUri: String,
        iconPack: IconPack,
        primaryColor: Int,
        backgroundColor: Int,
        surfaceColor: Int,
        onSurfaceColor: Int,
        dockBackgroundColor: Int,
        appLabelColor: Int,
        brainImageUri: String,
        nightMode: ThemeNightMode
    ): Theme {
        return Theme(
            id = id,
            name = name,
            description = description,
            author = "小手机",
            sourceType = ThemeSourceType.BUILT_IN,
            previewUri = previewUri,
            wallpaper = WallpaperConfig(
                uri = wallpaperUri,
                blurRadius = DEFAULT_WALLPAPER_BLUR_RADIUS,
                dimAmount = DEFAULT_WALLPAPER_DIM_AMOUNT,
                scaleType = WallpaperScaleType.CENTER_CROP
            ),
            iconPack = iconPack,
            desktopStyle = DesktopStyle(
                dockBackgroundColor = dockBackgroundColor,
                dockBackgroundAlpha = DEFAULT_DOCK_BACKGROUND_ALPHA,
                dockCornerRadiusDp = DEFAULT_DOCK_CORNER_RADIUS_DP,
                pageIndicatorSelectedColor = primaryColor,
                pageIndicatorUnselectedColor = Color.argb(120, Color.red(onSurfaceColor), Color.green(onSurfaceColor), Color.blue(onSurfaceColor)),
                appLabelColor = appLabelColor,
                appLabelShadowColor = Color.argb(160, 0, 0, 0),
                isAppLabelShadowEnabled = true
            ),
            floatingWindowStyle = FloatingWindowStyle(
                id = "${id}_brain",
                name = "$name 悬浮窗",
                defaultImageUri = brainImageUri,
                draggingImageUri = brainImageUri,
                dockedImageUri = brainImageUri,
                backgroundColor = backgroundColor,
                textColor = onSurfaceColor,
                accentColor = primaryColor,
                cardBackgroundColor = surfaceColor
            ),
            colorScheme = ThemeColorScheme(
                primaryColor = primaryColor,
                onPrimaryColor = Color.WHITE,
                primaryContainerColor = dockBackgroundColor,
                onPrimaryContainerColor = onSurfaceColor,
                secondaryColor = primaryColor,
                backgroundColor = backgroundColor,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                errorColor = Color.rgb(186, 26, 26)
            ),
            nightMode = nightMode,
            version = CURRENT_THEME_SCHEMA_VERSION,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createDefaultIconMap(packageName: String): Map<String, String> {
        return mapOf(
            DesktopAppNames.QQ to resourceUri(packageName, R.drawable.app_ic_qq_logo),
            DesktopAppNames.WORLD_BOOK to resourceUri(packageName, R.drawable.app_ic_book_logo),
            DesktopAppNames.THEME to resourceUri(packageName, R.drawable.app_ic_theme_logo),
            DesktopAppNames.ALBUM to resourceUri(packageName, R.drawable.app_ic_album_logo),
            DesktopAppNames.SHOPPING to resourceUri(packageName, R.drawable.app_ic_shopping_logo),
            DesktopAppNames.ALIPAY to resourceUri(packageName, R.drawable.app_ic_alipay_logo),
            DesktopAppNames.PRESET to resourceUri(packageName, R.drawable.app_ic_preset_logo),
            DesktopAppNames.X to resourceUri(packageName, R.drawable.app_ic_x_logo),
            DesktopAppNames.SETTINGS to resourceUri(packageName, R.drawable.app_ic_settings_logo),
            DesktopAppNames.UNKNOWN to resourceUri(packageName, R.drawable.app_ic_unknown_logo),
            DesktopAppNames.CPHONE to resourceUri(packageName, R.drawable.app_ic_cphone_logo),
            DesktopAppNames.TAVERN_RECORD to resourceUri(packageName, R.drawable.app_ic_tavern_logo),
            DesktopAppNames.EVENT_GRAPH to resourceUri(packageName, R.drawable.app_ic_event_graph_logo),
            DesktopAppNames.SCHEDULE to resourceUri(packageName, R.drawable.ic_schedule_24),
            DesktopAppNames.HEALTH to resourceUri(packageName, R.drawable.app_ic_health_logo)
        )
    }

    private fun createIconPack(
        id: String,
        name: String,
        icons: Map<String, String>,
        fallbackIconUri: String
    ): IconPack {
        return IconPack(
            id = id,
            name = name,
            icons = icons,
            fallbackIconUri = fallbackIconUri
        )
    }

    private fun resourceUri(packageName: String, resourceId: Int): String {
        return "android.resource://$packageName/$resourceId"
    }

    private fun buildAvailableThemes(): List<Theme> {
        val themes: List<Theme> = builtInThemes + customThemesState.value
        val pinnedThemeIds: List<String> = pinnedThemeIdsState.value
        if (pinnedThemeIds.isEmpty()) return themes
        return themes.sortedWith(
            compareBy<Theme> { theme ->
                val pinnedIndex: Int = pinnedThemeIds.indexOf(theme.id)
                if (pinnedIndex >= 0) pinnedIndex else Int.MAX_VALUE
            }.thenBy { theme -> themes.indexOf(theme) }
        )
    }

    private fun applyThemeResourceOverrides(
        theme: Theme,
        iconOverrides: Map<String, String>,
        dockedBrainImageUri: String?,
        draggingBrainImageUri: String?
    ): Theme {
        val updatedIcons: Map<String, String> = getCompleteThemeIcons(theme) + iconOverrides
        return theme.copy(
            iconPack = theme.iconPack.copy(icons = updatedIcons),
            floatingWindowStyle = theme.floatingWindowStyle.copy(
                defaultImageUri = dockedBrainImageUri ?: theme.floatingWindowStyle.defaultImageUri,
                dockedImageUri = dockedBrainImageUri ?: theme.floatingWindowStyle.dockedImageUri,
                draggingImageUri = draggingBrainImageUri ?: theme.floatingWindowStyle.draggingImageUri
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun getCompleteThemeIcons(theme: Theme): Map<String, String> {
        val defaultIcons: Map<String, String> = builtInThemes.firstOrNull()?.iconPack?.icons.orEmpty()
        return defaultIcons + theme.iconPack.icons
    }

    private fun buildCustomTheme(
        baseTheme: Theme,
        name: String,
        description: String,
        wallpaperUri: String
    ): Theme {
        val timestamp: Long = System.currentTimeMillis()
        val safeName: String = name.ifBlank { "自定义主题" }
        val safeDescription: String = description.ifBlank { "由当前主题复制生成的用户自定义主题。" }

        // 确保新主题的图标包包含健康图标（向前兼容：即使 base 是旧主题也能补齐）。
        val ensuredIcons: Map<String, String> = baseTheme.iconPack.icons.toMutableMap().apply {
            if (!containsKey(DesktopAppNames.HEALTH)) {
                put(DesktopAppNames.HEALTH, resourceUri(context.packageName, R.drawable.app_ic_health_logo))
            }
        }

        return baseTheme.copy(
            id = "custom_$timestamp",
            name = safeName,
            description = safeDescription,
            author = "小北",
            sourceType = ThemeSourceType.CUSTOM,
            previewUri = wallpaperUri,
            wallpaper = baseTheme.wallpaper.copy(uri = wallpaperUri),
            iconPack = baseTheme.iconPack.copy(
                id = "custom_icon_pack_$timestamp",
                icons = ensuredIcons
            ),
            floatingWindowStyle = baseTheme.floatingWindowStyle.copy(id = "custom_brain_$timestamp"),
            version = CURRENT_THEME_SCHEMA_VERSION,
            updatedAt = timestamp
        )
    }

    private fun saveCustomTheme(theme: Theme) {
        val customThemes: MutableList<Theme> = customThemesState.value.toMutableList()
        val existingIndex: Int = customThemes.indexOfFirst { customTheme -> customTheme.id == theme.id }
        if (existingIndex >= 0) {
            customThemes[existingIndex] = theme
        } else {
            customThemes.add(theme)
        }
        customThemesState.value = customThemes
        themesState.value = buildAvailableThemes()
        saveCustomThemes(customThemes)
    }

    private fun saveCustomThemes(customThemes: List<Theme>) {
        sharedPreferences.edit {
            putString(KEY_CUSTOM_THEMES_JSON, gson.toJson(customThemes))
            putInt(KEY_THEME_SCHEMA_VERSION, CURRENT_THEME_SCHEMA_VERSION)
        }
    }

    private fun loadCustomThemes(): List<Theme> {
        val customThemesJson: String? = sharedPreferences.getString(KEY_CUSTOM_THEMES_JSON, null)
        if (customThemesJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val listType = object : TypeToken<List<Theme>>() {}.type
            gson.fromJson<List<Theme>>(customThemesJson, listType)
                .filter { theme -> theme.sourceType == ThemeSourceType.CUSTOM }
        }.getOrDefault(emptyList())
    }

    private fun copyThemeResource(sourceUri: String, filePrefix: String): String {
        if (sourceUri.startsWith("android.resource://")) return sourceUri
        if (sourceUri.startsWith("file://") && sourceUri.contains(THEME_RESOURCES_DIR)) return sourceUri
        val resourcesDir = File(context.filesDir, THEME_RESOURCES_DIR).apply { mkdirs() }
        // 文件名加 UUID，避免同一毫秒内连续复制多个资源时时间戳碰撞导致互相覆盖。
        val targetFile = File(resourcesDir, "${filePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return sourceUri
        return Uri.fromFile(targetFile).toString()
    }

    /**
     * 收集主题引用的全部本地资源 file URI（壁纸、预览图、图标、Brain 三态图标）。
     *
     * 只收集落在主题资源目录下的 file URI，android.resource 内置资源不在清理范围内。
     */
    private fun collectThemeResourceUris(theme: Theme): Set<String> {
        val candidateUris: List<String> = buildList {
            add(theme.wallpaper.uri)
            add(theme.previewUri)
            addAll(theme.iconPack.icons.values)
            add(theme.floatingWindowStyle.defaultImageUri)
            add(theme.floatingWindowStyle.dockedImageUri)
            add(theme.floatingWindowStyle.draggingImageUri)
        }
        return candidateUris.filter { uri -> isManagedThemeResource(uri) }.toSet()
    }

    /**
     * 删除候选资源中不再被任何当前主题引用的本地文件。
     *
     * 编辑或删除自定义主题后调用：避免旧壁纸、图标文件在资源目录中无限堆积。
     */
    private fun deleteUnreferencedResources(candidateResourceUris: Set<String>) {
        if (candidateResourceUris.isEmpty()) return
        val referencedUris: Set<String> = buildReferencedResourceUris()
        candidateResourceUris.forEach { uri ->
            if (uri in referencedUris) return@forEach
            runCatching {
                val resourceFile: File = Uri.parse(uri).path?.let { path -> File(path) } ?: return@runCatching
                if (resourceFile.exists()) resourceFile.delete()
            }
        }
    }

    /**
     * 汇总当前所有内置主题与自定义主题仍在引用的本地资源 URI。
     */
    private fun buildReferencedResourceUris(): Set<String> {
        val allThemes: List<Theme> = builtInThemes + customThemesState.value
        return allThemes.flatMap { theme -> collectThemeResourceUris(theme) }.toSet()
    }

    private fun isManagedThemeResource(uri: String): Boolean {
        return uri.startsWith("file://") && uri.contains(THEME_RESOURCES_DIR)
    }

    private fun savePinnedThemeIds(pinnedThemeIds: List<String>) {
        sharedPreferences.edit {
            putString(KEY_PINNED_THEME_IDS_JSON, gson.toJson(pinnedThemeIds))
        }
    }

    private fun loadPinnedThemeIds(): List<String> {
        val pinnedThemeIdsJson: String? = sharedPreferences.getString(KEY_PINNED_THEME_IDS_JSON, null)
        if (pinnedThemeIdsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(pinnedThemeIdsJson, listType)
        }.getOrDefault(emptyList())
    }

    private fun saveCurrentTheme(theme: Theme) {
        sharedPreferences.edit {
            putString(KEY_CURRENT_THEME_ID, theme.id)
            putString(KEY_CURRENT_THEME_SNAPSHOT, gson.toJson(theme))
            putInt(KEY_THEME_SCHEMA_VERSION, CURRENT_THEME_SCHEMA_VERSION)
        }
    }

    private fun loadCurrentTheme(): Theme {
        val currentThemeId: String? = sharedPreferences.getString(KEY_CURRENT_THEME_ID, null)
        val currentThemeSnapshot: String? = sharedPreferences.getString(KEY_CURRENT_THEME_SNAPSHOT, null)
        val legacyThemeJson: String? = sharedPreferences.getString(LEGACY_KEY_CURRENT_THEME, null)

        val loadedTheme: Theme = findThemeById(currentThemeId)
            ?: parseThemeSnapshot(currentThemeSnapshot)
            ?: migrateLegacyTheme(legacyThemeJson)
            ?: defaultTheme

        // 如果加载的当前主题是旧版（version < 3），刷新其图标包到最新的完整映射。
        // 这确保用户切换到旧主题后，桌面图标能正确显示（旧主题可能缺少新增的应用图标）。
        return if (loadedTheme.version < CURRENT_THEME_SCHEMA_VERSION) {
            val refreshedIcons: Map<String, String> = createDefaultIconMap(context.packageName)
            loadedTheme.copy(
                iconPack = loadedTheme.iconPack.copy(icons = refreshedIcons),
                version = CURRENT_THEME_SCHEMA_VERSION,
                updatedAt = System.currentTimeMillis()
            ).also { refreshedTheme ->
                // 回写刷新后的主题到快照，避免每次启动都重复刷新。
                sharedPreferences.edit {
                    putString(KEY_CURRENT_THEME_SNAPSHOT, gson.toJson(refreshedTheme))
                }
            }
        } else {
            loadedTheme
        }
    }

    private fun parseThemeSnapshot(themeJson: String?): Theme? {
        if (themeJson.isNullOrBlank()) return null
        return runCatching { gson.fromJson(themeJson, Theme::class.java) }.getOrNull()
    }

    private fun migrateLegacyTheme(themeJson: String?): Theme? {
        if (themeJson.isNullOrBlank()) return null
        return runCatching {
            val jsonObject: JsonObject = JsonParser.parseString(themeJson).asJsonObject
            val legacyName: String = jsonObject.get("name")?.asString ?: defaultTheme.name
            val legacyWallpaperUri: String = jsonObject.get("wallpaperUri")?.asString ?: defaultTheme.wallpaper.uri
            defaultTheme.copy(
                id = "legacy_migrated",
                name = legacyName,
                description = "由旧主题数据自动迁移。",
                sourceType = ThemeSourceType.CUSTOM,
                previewUri = legacyWallpaperUri,
                wallpaper = defaultTheme.wallpaper.copy(uri = legacyWallpaperUri),
                updatedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun findThemeById(themeId: String?): Theme? {
        if (themeId.isNullOrBlank()) return null
        return buildAvailableThemes().firstOrNull { theme -> theme.id == themeId }
    }

    private fun notifyThemeChanged(): Unit {
        context.sendBroadcast(Intent(ACTION_THEME_CHANGED).setPackage(context.packageName))
    }

    private fun applyNightMode(nightMode: ThemeNightMode) {
        val delegateMode: Int = when (nightMode) {
            ThemeNightMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeNightMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeNightMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }

    private companion object {
        const val PREFERENCES_NAME = "theme_prefs"
        const val KEY_CURRENT_THEME_ID = "current_theme_id"
        const val KEY_CURRENT_THEME_SNAPSHOT = "current_theme_snapshot"
        const val KEY_CUSTOM_THEMES_JSON = "custom_themes_json"
        const val KEY_PINNED_THEME_IDS_JSON = "pinned_theme_ids_json"
        const val KEY_THEME_SCHEMA_VERSION = "theme_schema_version"
        const val LEGACY_KEY_CURRENT_THEME = "current_theme"
        const val THEME_RESOURCES_DIR = "themes/resources"
        const val CURRENT_THEME_SCHEMA_VERSION = 3  // v3: 新增健康图标映射
        const val DEFAULT_THEME_ID = "system_follow"
        const val THEME_WALLPAPER_PREFIX = "theme_wallpaper"
        const val THEME_ICON_PREFIX = "theme_icon"
        const val THEME_BRAIN_PREFIX = "theme_brain"
        const val ACTION_THEME_CHANGED = "com.susking.ephone_s.ACTION_THEME_CHANGED"
        // 数据导入完成后由 aidata 模块发送，通知本单例重新读盘刷新内存状态。
        const val ACTION_THEME_RELOAD = "com.susking.ephone_s.ACTION_THEME_RELOAD"
        const val DEFAULT_WALLPAPER_BLUR_RADIUS = 0
        const val DEFAULT_WALLPAPER_DIM_AMOUNT = 0.0f
        const val DEFAULT_DOCK_BACKGROUND_ALPHA = 220
        const val DEFAULT_DOCK_CORNER_RADIUS_DP = 28f
    }
}