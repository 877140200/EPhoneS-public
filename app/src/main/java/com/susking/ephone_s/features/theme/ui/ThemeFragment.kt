package com.susking.ephone_s.features.theme.ui

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.core.desktop.DesktopAppNames
import com.susking.ephone_s.core.util.ImagePickerHelper
import com.susking.ephone_s.features.theme.domain.model.Theme
import com.susking.ephone_s.features.theme.domain.model.ThemeSourceType
import com.susking.ephone_s.databinding.FragmentThemeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ThemeFragment : Fragment() {

    private var _binding: FragmentThemeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ThemeViewModel by viewModels()
    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var imagePickerHelper: ImagePickerHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWallpaperPicker()
        setupWindowInsets()
        setupRecyclerView()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupWindowInsets(): Unit {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding: Int = DEFAULT_SCREEN_PADDING_DP.toPx()
            view.setPadding(
                basePadding + systemBars.left,
                basePadding + systemBars.top,
                basePadding + systemBars.right,
                basePadding + systemBars.bottom
            )
            insets
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerView() {
        themeAdapter = ThemeAdapter(
            onThemeClick = { theme: Theme ->
                viewModel.setCurrentTheme(theme)
            },
            onThemeLongClick = { theme: Theme ->
                showThemeActionDialog(theme)
            }
        )
        binding.themeRecyclerView.apply {
            adapter = themeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupWallpaperPicker(): Unit {
        imagePickerHelper = ImagePickerHelper(this)
    }

    private fun observeViewModel() {
        viewModel.themes.observe(viewLifecycleOwner) { themes ->
            themeAdapter.submitList(themes)
        }

        viewModel.currentTheme.observe(viewLifecycleOwner) { currentTheme ->
            // LiveData 首帧发射前理论上可能为 null，显式守卫避免后续非空调用崩溃。
            currentTheme ?: return@observe
            binding.currentThemeNameText.text = currentTheme.name
            binding.currentThemeDescriptionText.text = currentTheme.description
            themeAdapter.updateSelectedThemeId(currentTheme.id)
            themeAdapter.updateRuntimeTheme(currentTheme)
            applyThemeColors(currentTheme)
        }

        binding.addThemeButton.setOnClickListener {
            showThemeEditorDialog(theme = null)
        }

        binding.resetThemeButton.setOnClickListener {
            viewModel.resetToDefaultTheme()
        }
    }

    private fun showThemeEditorDialog(theme: Theme?): Unit {
        val context = requireContext()
        val baseTheme: Theme = theme ?: viewModel.currentTheme.value ?: return
        val iconOverrides: MutableMap<String, Uri> = mutableMapOf()
        var wallpaperUri: Uri = Uri.parse(theme?.wallpaperUri ?: getDefaultWhiteWallpaperUri())
        var dockedBrainImageUri: Uri? = null
        var draggingBrainImageUri: Uri? = null
        val previewImageViews: MutableMap<String, ImageView> = mutableMapOf()
        val wallpaperPreview: ImageView = createWallpaperPreview(wallpaperUri)
        val dockedBrainPreview = ImageView(context)
        val draggingBrainPreview = ImageView(context)
        val nameInput = EditText(context).apply {
            hint = "主题名称"
            setText(theme?.name ?: "我的壁纸主题")
        }
        val descriptionInput = EditText(context).apply {
            hint = "主题说明"
            minLines = DESCRIPTION_MIN_LINES
            setText(theme?.description ?: "使用桌面预览创建的用户自定义主题。")
        }
        val container = ScrollView(context).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(DIALOG_HORIZONTAL_PADDING_DP.toPx(), 0, DIALOG_HORIZONTAL_PADDING_DP.toPx(), 0)
                    addView(nameInput)
                    addView(descriptionInput)
                    addView(createPreviewTitle("桌面预览：点击壁纸区域选择图片并拖动裁剪显示范围"))
                    wallpaperPreview.setOnClickListener {
                        pickWallpaper { selectedUri: Uri ->
                            wallpaperUri = selectedUri
                            wallpaperPreview.load(selectedUri)
                        }
                    }
                    addView(wallpaperPreview)
                    addView(createPreviewTitle("点击应用图标更换图片，更换后会实时预览"))
                    createEditableIconEntries(baseTheme).forEach { entry: Map.Entry<String, String> ->
                        val iconRow: LinearLayout = createIconPreviewRow(
                            appName = entry.key,
                            iconUri = entry.value,
                            onClick = { appName: String ->
                                pickThemeIcon { selectedUri: Uri ->
                                    iconOverrides[appName] = selectedUri
                                    previewImageViews[appName]?.load(selectedUri)
                                }
                            }
                        )
                        previewImageViews[entry.key] = iconRow.findViewWithTag(entry.key)
                        addView(iconRow)
                    }
                    addView(createPreviewTitle("点击大脑图标，分别更换停靠状态和拖动状态"))
                    addView(createBrainPreviewRow(
                        title = "大脑停靠状态图标",
                        initialUri = baseTheme.floatingWindowStyle.dockedImageUri,
                        previewImageView = dockedBrainPreview,
                        onClick = {
                            pickThemeIcon { selectedUri: Uri ->
                                dockedBrainImageUri = selectedUri
                                dockedBrainPreview.load(selectedUri)
                            }
                        }
                    ))
                    addView(createBrainPreviewRow(
                        title = "大脑拖动状态图标",
                        initialUri = baseTheme.floatingWindowStyle.draggingImageUri,
                        previewImageView = draggingBrainPreview,
                        onClick = {
                            pickThemeIcon { selectedUri: Uri ->
                                draggingBrainImageUri = selectedUri
                                draggingBrainPreview.load(selectedUri)
                            }
                        }
                    ))
                }
            )
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(if (theme == null) "新建主题" else "编辑自定义主题")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (theme == null) {
                    viewModel.createCustomThemeWithWallpaperCustomization(
                        name = nameInput.text.toString().trim(),
                        description = descriptionInput.text.toString().trim(),
                        wallpaperUri = wallpaperUri,
                        iconOverrides = iconOverrides,
                        dockedBrainImageUri = dockedBrainImageUri,
                        draggingBrainImageUri = draggingBrainImageUri
                    )
                } else {
                    viewModel.updateCustomThemeAppearance(
                        theme = theme,
                        name = nameInput.text.toString().trim(),
                        description = descriptionInput.text.toString().trim(),
                        wallpaperUri = wallpaperUri,
                        iconOverrides = iconOverrides,
                        dockedBrainImageUri = dockedBrainImageUri,
                        draggingBrainImageUri = draggingBrainImageUri
                    )
                }
            }
            .show()
    }

    private fun pickWallpaper(onSelected: (Uri) -> Unit): Unit {
        val displayMetrics = resources.displayMetrics
        val cropOptions = CropImageOptions().apply {
            guidelines = CropImageView.Guidelines.ON
            fixAspectRatio = true
            aspectRatioX = displayMetrics.widthPixels
            aspectRatioY = displayMetrics.heightPixels
        }
        imagePickerHelper.pickAndCrop(cropOptions) { wallpaperUri: Uri ->
            onSelected(wallpaperUri)
        }
    }

    private fun createEditableIconEntries(theme: Theme): Map<String, String> {
        val fallbackIconUri: String = theme.iconPack.fallbackIconUri.ifBlank {
            getResourceUri(com.susking.ephone_s.R.drawable.app_ic_unknown_logo)
        }
        val requiredIcons: Map<String, String> = DesktopAppNames.ALL.associateWith { appName: String ->
            when (appName) {
                DesktopAppNames.SCHEDULE -> getResourceUri(com.susking.ephone_s.R.drawable.ic_schedule_24)
                else -> fallbackIconUri
            }
        }
        return requiredIcons + theme.iconPack.icons
    }

    private fun pickThemeIcon(onSelected: (Uri) -> Unit): Unit {
        val cropOptions = CropImageOptions().apply {
            guidelines = CropImageView.Guidelines.ON
            fixAspectRatio = true
            aspectRatioX = ICON_ASPECT_RATIO
            aspectRatioY = ICON_ASPECT_RATIO
        }
        imagePickerHelper.pickAndCrop(cropOptions) { selectedUri: Uri ->
            onSelected(selectedUri)
        }
    }

    private fun createPreviewTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = PREVIEW_TITLE_TEXT_SIZE_SP
            setPadding(0, PREVIEW_SECTION_TOP_PADDING_DP.toPx(), 0, PREVIEW_TITLE_BOTTOM_PADDING_DP.toPx())
        }
    }

    private fun createWallpaperPreview(wallpaperUri: Uri): ImageView {
        return ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                WALLPAPER_PREVIEW_HEIGHT_DP.toPx()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(wallpaperUri)
        }
    }

    private fun createIconPreviewRow(
        appName: String,
        iconUri: String,
        onClick: (String) -> Unit
    ): LinearLayout {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, ICON_ROW_VERTICAL_PADDING_DP.toPx(), 0, ICON_ROW_VERTICAL_PADDING_DP.toPx())
            val imageView = ImageView(context).apply {
                tag = appName
                layoutParams = LinearLayout.LayoutParams(ICON_PREVIEW_SIZE_DP.toPx(), ICON_PREVIEW_SIZE_DP.toPx())
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(iconUri)
            }
            val labelView = TextView(context).apply {
                text = appName
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = ICON_LABEL_MARGIN_START_DP.toPx()
                }
            }
            val button = MaterialButton(context).apply {
                text = "更换图片"
                setOnClickListener { onClick(appName) }
            }
            addView(imageView)
            addView(labelView)
            addView(button)
            setOnClickListener { onClick(appName) }
        }
    }

    private fun createBrainPreviewRow(
        title: String,
        initialUri: String,
        previewImageView: ImageView,
        onClick: () -> Unit
    ): LinearLayout {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, ICON_ROW_VERTICAL_PADDING_DP.toPx(), 0, ICON_ROW_VERTICAL_PADDING_DP.toPx())
            previewImageView.layoutParams = LinearLayout.LayoutParams(ICON_PREVIEW_SIZE_DP.toPx(), ICON_PREVIEW_SIZE_DP.toPx())
            previewImageView.scaleType = ImageView.ScaleType.CENTER_CROP
            previewImageView.load(initialUri)
            val labelView = TextView(context).apply {
                text = title
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = ICON_LABEL_MARGIN_START_DP.toPx()
                }
            }
            val button = MaterialButton(context).apply {
                text = "更换图片"
                setOnClickListener { onClick() }
            }
            addView(previewImageView)
            addView(labelView)
            addView(button)
            setOnClickListener { onClick() }
        }
    }

    private fun showThemeActionDialog(theme: Theme): Unit {
        val actions: Array<String> = if (theme.sourceType == ThemeSourceType.CUSTOM) {
            arrayOf("编辑", "置顶", "删除")
        } else {
            arrayOf("置顶")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(theme.name)
            .setItems(actions) { _, which: Int ->
                when (actions[which]) {
                    "编辑" -> showThemeEditorDialog(theme)
                    "置顶" -> viewModel.pinTheme(theme)
                    "删除" -> showDeleteThemeConfirmDialog(theme)
                }
            }
            .show()
    }

    private fun showDeleteThemeConfirmDialog(theme: Theme): Unit {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除主题")
            .setMessage("确定要删除「${theme.name}」吗？该主题引用的壁纸和图标文件会一并清理，此操作不可撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteCustomTheme(theme)
            }
            .show()
    }

    private fun applyThemeColors(theme: Theme): Unit {
        val primaryColor = theme.colorScheme.primaryColor
        val onPrimaryColor = theme.colorScheme.onPrimaryColor
        val surfaceColor = theme.colorScheme.surfaceColor
        val onSurfaceColor = theme.colorScheme.onSurfaceColor
        val backgroundColor = theme.colorScheme.backgroundColor
        val secondaryColor = theme.colorScheme.secondaryColor
        val primaryTintList = ColorStateList.valueOf(primaryColor)
        val onSurfaceTintList = ColorStateList.valueOf(onSurfaceColor)

        binding.root.setBackgroundColor(backgroundColor)
        binding.currentThemeCard.setCardBackgroundColor(surfaceColor)
        binding.themeTitleText.setTextColor(onSurfaceColor)
        binding.currentThemeLabelText.setTextColor(secondaryColor)
        binding.currentThemeNameText.setTextColor(onSurfaceColor)
        binding.currentThemeDescriptionText.setTextColor(onSurfaceColor)
        binding.addThemeButton.iconTint = primaryTintList
        binding.resetThemeButton.strokeColor = onSurfaceTintList
        binding.resetThemeButton.setTextColor(onSurfaceColor)
    }

    private fun getDefaultWhiteWallpaperUri(): String {
        return getResourceUri(com.susking.ephone_s.R.drawable.white_background)
    }

    private fun getResourceUri(resourceId: Int): String {
        return "android.resource://${requireContext().packageName}/$resourceId"
    }

    companion object {
        private const val DEFAULT_SCREEN_PADDING_DP = 16
        private const val DIALOG_HORIZONTAL_PADDING_DP = 20
        private const val DESCRIPTION_MIN_LINES = 2
        private const val ICON_ASPECT_RATIO = 1
        private const val WALLPAPER_PREVIEW_HEIGHT_DP = 420
        private const val ICON_PREVIEW_SIZE_DP = 48
        private const val ICON_ROW_VERTICAL_PADDING_DP = 6
        private const val ICON_LABEL_MARGIN_START_DP = 12
        private const val PREVIEW_SECTION_TOP_PADDING_DP = 14
        private const val PREVIEW_TITLE_BOTTOM_PADDING_DP = 8
        private const val PREVIEW_TITLE_TEXT_SIZE_SP = 15f

        fun newInstance(): ThemeFragment = ThemeFragment()
    }
}