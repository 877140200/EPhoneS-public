package com.susking.ephone_s.features.theme.domain.repository

import com.susking.ephone_s.features.theme.domain.model.Theme
import kotlinx.coroutines.flow.Flow

/**
 * 主题仓库接口，用于获取和设置主题。
 */
interface ThemeRepository {
    /**
     * 获取所有可用主题的列表。
     *
     * @return 一个包含所有主题的 Flow。
     */
    fun getThemes(): Flow<List<Theme>>

    /**
     * 获取当前应用的主题。
     *
     * @return 一个包含当前主题的 Flow。
     */
    fun getCurrentTheme(): Flow<Theme>

    /**
     * 设置当前应用的主题。
     *
     * @param theme 要设置的主题。
     */
    suspend fun setCurrentTheme(theme: Theme)

    /**
     * 恢复为默认内置主题。
     */
    suspend fun resetToDefaultTheme()

    /**
     * 基于当前主题、外部壁纸资源和图标覆盖配置创建用户自定义主题。
     *
     * @param name 用户输入的主题名称。
     * @param description 用户输入的主题说明。
     * @param wallpaperUri 用户确认显示范围后的壁纸Uri字符串。
     * @param iconOverrides 应用名称到图标Uri字符串的覆盖映射。
     * @param dockedBrainImageUri Brain停靠状态图标Uri字符串。
     * @param draggingBrainImageUri Brain拖动状态图标Uri字符串。
     * @return 保存后的自定义主题。
     */
    suspend fun createCustomThemeWithWallpaperCustomization(
        name: String,
        description: String,
        wallpaperUri: String,
        iconOverrides: Map<String, String>,
        dockedBrainImageUri: String?,
        draggingBrainImageUri: String?
    ): Theme

    /**
     * 将主题置顶到主题列表前方。
     *
     * @param theme 需要置顶的主题。
     */
    suspend fun pinTheme(theme: Theme)

    /**
     * 更新已有用户自定义主题的完整外观。
     *
     * @param theme 需要更新的自定义主题。
     * @param name 新名称。
     * @param description 新说明。
     * @param wallpaperUri 新壁纸Uri字符串。
     * @param iconOverrides 应用名称到图标Uri字符串的覆盖映射。
     * @param dockedBrainImageUri Brain停靠状态图标Uri字符串。
     * @param draggingBrainImageUri Brain拖动状态图标Uri字符串。
     * @return 更新后的主题，内置主题会返回null。
     */
    suspend fun updateCustomThemeAppearance(
        theme: Theme,
        name: String,
        description: String,
        wallpaperUri: String,
        iconOverrides: Map<String, String>,
        dockedBrainImageUri: String?,
        draggingBrainImageUri: String?
    ): Theme?

    /**
     * 删除指定的用户自定义主题，并清理其独占的本地资源文件。
     *
     * 仅允许删除 CUSTOM 主题；若删除的是当前主题，会自动回退到默认主题。
     *
     * @param theme 需要删除的自定义主题。
     * @return 删除成功返回true，内置主题或不存在则返回false。
     */
    suspend fun deleteCustomTheme(theme: Theme): Boolean

    /**
     * 从持久化存储重新加载全部主题状态。
     *
     * 用于导入数据后刷新单例仓库的内存状态，避免必须重启应用才能看到导入的主题。
     */
    suspend fun reloadFromStorage()
}