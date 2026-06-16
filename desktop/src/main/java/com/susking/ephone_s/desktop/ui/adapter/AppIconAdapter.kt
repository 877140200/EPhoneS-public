package com.susking.ephone_s.desktop.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.core.R
import com.susking.ephone_s.desktop.ui.drag.ItemMoveCallback
import com.susking.ephone_s.desktop.databinding.ItemAppIconBinding
import com.susking.ephone_s.desktop.model.AppIcon
import java.util.Collections

class AppIconAdapter(
    var icons: MutableList<AppIcon?>,
    private val onIconClick: (AppIcon) -> Unit,
    private val onIconLongClick: (View, AppIcon) -> Boolean,
    private val isDock: Boolean = false // 标识是否为Dock栏
) : RecyclerView.Adapter<AppIconAdapter.AppIconViewHolder>(), ItemMoveCallback.ItemTouchHelperContract {

    private var labelColor: Int = Color.WHITE
    private var labelShadowColor: Int = Color.BLACK
    private var isLabelShadowEnabled: Boolean = true

    fun updateLabelStyle(color: Int, shadowColor: Int, shadowEnabled: Boolean): Unit {
        labelColor = color
        labelShadowColor = shadowColor
        isLabelShadowEnabled = shadowEnabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppIconViewHolder {
        val binding = ItemAppIconBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // 根据isDock标志设置item高度
        val itemHeight = if (isDock) {
            // Dock栏：使用wrap_content，让内容决定高度
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            // 桌面网格：占满RecyclerView的1/6（6行）
            parent.measuredHeight / 6
        }
        binding.root.layoutParams.height = itemHeight
        
        return AppIconViewHolder(binding, onIconClick, onIconLongClick)
    }

    override fun onBindViewHolder(holder: AppIconViewHolder, position: Int) {
        val icon = icons[position]
        holder.bind(icon, labelColor, labelShadowColor, isLabelShadowEnabled)
    }

    override fun getItemCount(): Int = icons.size

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(icons, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(icons, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowClear() {
        // 保存新的顺序
    }

    class AppIconViewHolder(
        private val binding: ItemAppIconBinding,
        private val onIconClick: (AppIcon) -> Unit,
        private val onIconLongClick: (View, AppIcon) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            icon: AppIcon?,
            labelColor: Int,
            labelShadowColor: Int,
            isLabelShadowEnabled: Boolean
        ): Unit {
            if (icon == null) {
                // 空白占位符：隐藏图标、浮雕明暗边和文字，避免空格子显示效果层
                binding.appIconShadowContainer.visibility = View.GONE
                binding.appIconEmbossDarkImage.visibility = View.GONE
                binding.appIconImage.visibility = View.GONE
                binding.appIconName.visibility = View.GONE
                binding.root.setOnClickListener(null)
                binding.root.setOnLongClickListener(null)
            } else {
                // 正常图标：通过左上高光和右下暗边制造立体浮雕凸起感
                binding.appIconShadowContainer.visibility = View.VISIBLE
                binding.appIconEmbossDarkImage.visibility = View.VISIBLE
                binding.appIconImage.visibility = View.VISIBLE
                binding.appIconName.visibility = View.VISIBLE
                loadIconEffectLayers(binding, icon.iconUri)
                binding.appIconName.text = icon.name
                binding.appIconName.setTextColor(labelColor)
                // 桌面字体不再使用外模糊阴影，只保留主题配置的文字颜色。
                binding.appIconName.setShadowLayer(
                    DISABLED_LABEL_SHADOW_RADIUS,
                    DISABLED_LABEL_SHADOW_OFFSET,
                    DISABLED_LABEL_SHADOW_OFFSET,
                    Color.TRANSPARENT
                )
                binding.root.setOnClickListener {
                    onIconClick(icon)
                }
                binding.root.setOnLongClickListener { view ->
                    onIconLongClick(view, icon)
                }
            }
        }
        private fun loadIconEffectLayers(binding: ItemAppIconBinding, iconUri: String): Unit {
            binding.appIconEmbossDarkImage.load(iconUri) {
                crossfade(false)
                placeholder(R.drawable.ic_launcher_background)
                error(R.drawable.ic_error_24)
            }
            binding.appIconImage.load(iconUri) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_background)
                error(R.drawable.ic_error_24)
            }
        }
    }

    private companion object {
        const val LABEL_SHADOW_RADIUS = 3f
        const val LABEL_SHADOW_DX = 0f
        const val LABEL_SHADOW_DY = 1f
        const val DISABLED_LABEL_SHADOW_RADIUS = 0f
        const val DISABLED_LABEL_SHADOW_OFFSET = 0f
    }
}