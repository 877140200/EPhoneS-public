package com.susking.ephone_s.features.theme.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.databinding.ItemThemeBinding
import com.susking.ephone_s.features.theme.domain.model.Theme

class ThemeAdapter(
    private val onThemeClick: (Theme) -> Unit,
    private val onThemeLongClick: (Theme) -> Unit
) : ListAdapter<Theme, ThemeAdapter.ThemeViewHolder>(ThemeDiffCallback()) {

    private var selectedThemeId: String? = null
    private var runtimeTheme: Theme? = null

    fun updateSelectedThemeId(themeId: String): Unit {
        if (selectedThemeId == themeId) return
        selectedThemeId = themeId
        // 仅局部刷新选中态，避免 notifyDataSetChanged 全量重绑导致列表闪烁、图片重载。
        notifyItemRangeChanged(0, itemCount, PAYLOAD_REFRESH_RUNTIME)
    }

    fun updateRuntimeTheme(theme: Theme): Unit {
        if (runtimeTheme == theme) return
        runtimeTheme = theme
        // 运行时主题色变化同样走局部刷新，复用 onBindViewHolder 的 payload 分支。
        notifyItemRangeChanged(0, itemCount, PAYLOAD_REFRESH_RUNTIME)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThemeViewHolder(binding, onThemeClick, onThemeLongClick)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(getItem(position), selectedThemeId, runtimeTheme)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int, payloads: MutableList<Any>) {
        // 带 payload 的局部刷新只更新选中态与运行时主题色，不重载预览图，避免图片闪烁。
        if (payloads.contains(PAYLOAD_REFRESH_RUNTIME)) {
            holder.bindRuntimeState(getItem(position), selectedThemeId, runtimeTheme)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class ThemeViewHolder(
        private val binding: ItemThemeBinding,
        private val onThemeClick: (Theme) -> Unit,
        private val onThemeLongClick: (Theme) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(theme: Theme, selectedThemeId: String?, runtimeTheme: Theme?) {
            binding.themeNameText.text = theme.name
            binding.themeDescriptionText.text = theme.description
            binding.themeMetaText.text = "${theme.author} · ${theme.sourceType.name}"
            // 预览图只在全量绑定时加载，局部刷新（payload）不重载，避免切主题时图片闪烁。
            binding.themePreviewImage.load(theme.previewUri.ifBlank { theme.wallpaperUri })
            binding.root.setOnClickListener {
                onThemeClick(theme)
            }
            binding.root.setOnLongClickListener {
                onThemeLongClick(theme)
                true
            }
            bindRuntimeState(theme, selectedThemeId, runtimeTheme)
        }

        /**
         * 只更新与选中态、运行时主题色相关的视图，不触碰预览图与点击监听。
         * 供带 payload 的局部刷新复用。
         */
        fun bindRuntimeState(theme: Theme, selectedThemeId: String?, runtimeTheme: Theme?) {
            val isSelectedTheme: Boolean = theme.id == selectedThemeId
            binding.themeSelectedText.visibility = if (isSelectedTheme) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            applyRuntimeColors(runtimeTheme)
            binding.root.strokeWidth = if (isSelectedTheme) SELECTED_STROKE_WIDTH else DEFAULT_STROKE_WIDTH
            binding.root.isChecked = isSelectedTheme
        }

        private fun applyRuntimeColors(runtimeTheme: Theme?): Unit {
            runtimeTheme ?: return
            binding.root.setCardBackgroundColor(runtimeTheme.colorScheme.surfaceColor)
            binding.root.strokeColor = runtimeTheme.colorScheme.primaryColor
            binding.themeInfoContainer.setBackgroundColor(runtimeTheme.colorScheme.surfaceColor)
            binding.themeNameText.setTextColor(runtimeTheme.colorScheme.onSurfaceColor)
            binding.themeDescriptionText.setTextColor(runtimeTheme.colorScheme.onSurfaceColor)
            binding.themeMetaText.setTextColor(runtimeTheme.colorScheme.secondaryColor)
            binding.themeSelectedText.backgroundTintList = ColorStateList.valueOf(runtimeTheme.colorScheme.primaryColor)
            binding.themeSelectedText.setTextColor(runtimeTheme.colorScheme.onPrimaryColor)
        }
    }

    private class ThemeDiffCallback : DiffUtil.ItemCallback<Theme>() {
        override fun areItemsTheSame(oldItem: Theme, newItem: Theme): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Theme, newItem: Theme): Boolean {
            return oldItem == newItem
        }
    }
    private companion object {
        const val SELECTED_STROKE_WIDTH = 4
        const val DEFAULT_STROKE_WIDTH = 0
        // 局部刷新标记：仅更新选中态与运行时主题色，不重载预览图。
        const val PAYLOAD_REFRESH_RUNTIME = "payload_refresh_runtime"
    }
}