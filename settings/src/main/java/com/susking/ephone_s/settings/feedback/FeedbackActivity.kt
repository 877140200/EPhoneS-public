package com.susking.ephone_s.settings.feedback

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.susking.ephone_s.core.license.DeviceFingerprint
import com.susking.ephone_s.core.license.LicenseReader
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.settings.R
import com.susking.ephone_s.settings.databinding.ActivityFeedbackBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 意见反馈界面。
 *
 * 提供原生输入框 + 图片附件（最多10张）+ 问题类别，用户填写后 POST 到 Worker /feedback，
 * 由 Worker 转发到 Server酱推送到作者微信，并在 KV 中存储完整反馈（含 base64 图片）。
 *
 * 体验要点：
 *  - 纯原生输入，不跳转网页，简洁直观。
 *  - 自动携带当前 app 版本名、激活码、设备指纹，便于作者定位问题。
 *  - 图片压缩后转 base64（单张限 500KB），避免传输过大。
 *  - 免费 workers.dev 国内访问不稳，失败给出友好提示并允许重试。
 */
class FeedbackActivity : BaseActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    private val remoteService: FeedbackRemoteService by lazy { FeedbackRemoteService() }

    /** 已选择的图片 URI 列表，最多 10 张。 */
    private val selectedImages: MutableList<Uri> = mutableListOf()

    /** 图片选择器。 */
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { addImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCategoryDropdown()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    /**
     * 设置问题类别下拉选择器。
     * 六大类别：功能异常、界面显示、数据丢失、性能卡顿、功能建议、其他。
     */
    private fun setupCategoryDropdown() {
        val categories = arrayOf("功能异常", "界面显示", "数据丢失", "性能卡顿", "功能建议", "其他")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)
        // 默认选中第一项
        binding.actvCategory.setText(categories[0], false)
    }

    private fun setupClickListeners() {
        // 提交按钮
        binding.btnSubmit.setOnClickListener {
            val content: String = binding.etContent.text?.toString()?.trim().orEmpty()
            if (content.isEmpty()) {
                binding.tilContent.error = "请填写反馈内容"
                return@setOnClickListener
            }
            binding.tilContent.error = null
            val category: String = binding.actvCategory.text?.toString()?.trim().orEmpty()
            val contact: String = binding.etContact.text?.toString()?.trim().orEmpty()
            executeSubmit(content, category, contact)
        }

        // 添加图片按钮
        binding.cvAddImage.setOnClickListener {
            if (selectedImages.size >= MAX_IMAGE_COUNT) {
                Toast.makeText(this, "最多添加 $MAX_IMAGE_COUNT 张图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            imagePickerLauncher.launch("image/*")
        }
    }

    /**
     * 添加一张图片到列表并刷新界面。
     * @param uri 图片的 URI
     */
    private fun addImage(uri: Uri) {
        if (selectedImages.size >= MAX_IMAGE_COUNT) {
            Toast.makeText(this, "最多添加 $MAX_IMAGE_COUNT 张图片", Toast.LENGTH_SHORT).show()
            return
        }
        selectedImages.add(uri)
        refreshImageGrid()
    }

    /**
     * 移除指定位置的图片并刷新界面。
     * @param position 图片在列表中的索引
     */
    private fun removeImage(position: Int) {
        if (position in selectedImages.indices) {
            selectedImages.removeAt(position)
            refreshImageGrid()
        }
    }

    /**
     * 刷新图片网格显示：已选图片 + 添加按钮（不满 10 张时显示）。
     */
    private fun refreshImageGrid() {
        val container = binding.llImageContainer
        container.removeAllViews()

        // 添加已选图片（每张可删除）
        selectedImages.forEachIndexed { index, uri ->
            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_feedback_image, container, false) as MaterialCardView
            val imageView = cardView.findViewById<ImageView>(R.id.ivImage)
            val btnRemove = cardView.findViewById<ImageView>(R.id.ivRemove)

            imageView.setImageURI(uri)
            btnRemove.setOnClickListener { removeImage(index) }

            container.addView(cardView)
        }

        // 添加按钮（不满 10 张时显示）
        if (selectedImages.size < MAX_IMAGE_COUNT) {
            val addButton = LayoutInflater.from(this)
                .inflate(R.layout.item_feedback_add_image, container, false)
            addButton.setOnClickListener {
                imagePickerLauncher.launch("image/*")
            }
            container.addView(addButton)
        }
    }

    /**
     * 提交反馈。
     * @param content 反馈正文
     * @param category 问题类别
     * @param contact 可选联系方式
     */
    private fun executeSubmit(content: String, category: String, contact: String) {
        setLoading(true)
        lifecycleScope.launch {
            val appVersion: String = getAppVersionName()
            val activationCode: String = LicenseReader.getActivationCode(this@FeedbackActivity)
            val fingerprint: String = DeviceFingerprint.getFingerprint(this@FeedbackActivity)
            val imageBase64List: List<String> = compressAndEncodeImages()

            val result: FeedbackResult = remoteService.submit(
                content = content,
                category = category,
                contact = contact,
                appVersion = appVersion,
                activationCode = activationCode,
                fingerprint = fingerprint,
                images = imageBase64List
            )
            setLoading(false)
            handleResult(result)
        }
    }

    /**
     * 压缩并编码所有已选图片为 base64。
     * 每张图片压缩到宽高不超过 1080，JPEG 质量 80，单张限制 500KB。
     * @return base64 字符串列表
     */
    private suspend fun compressAndEncodeImages(): List<String> = withContext(Dispatchers.IO) {
        selectedImages.mapNotNull { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val originalBitmap = BitmapFactory.decodeStream(input)
                    val compressed = compressBitmap(originalBitmap)
                    Base64.encodeToString(compressed, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 压缩单张图片。
     * 宽高缩放到不超过 MAX_IMAGE_DIMENSION，JPEG 质量 80，输出大小不超过 MAX_IMAGE_SIZE_BYTES。
     * @param bitmap 原始 Bitmap
     * @return 压缩后的字节数组
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        // 缩放尺寸
        val scaledBitmap = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // JPEG 压缩，质量从 80 开始，逐步降低直到满足大小限制
        var quality = 80
        var output: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            output = stream.toByteArray()
            quality -= 10
        } while (output.size > MAX_IMAGE_SIZE_BYTES && quality > 10)

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        return output
    }

    /**
     * 处理提交结果。
     * @param result 提交结果
     */
    private fun handleResult(result: FeedbackResult) {
        when (result) {
            is FeedbackResult.Success -> {
                Toast.makeText(this, "反馈已提交，感谢你的反馈", Toast.LENGTH_LONG).show()
                finish()
            }
            is FeedbackResult.Error -> {
                Toast.makeText(this, "提交失败：${result.message}\n请检查网络后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 切换加载状态，加载中禁用按钮避免重复提交。
     * @param isLoading 是否处于请求中
     */
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnSubmit.text = if (isLoading) "提交中…" else "提交反馈"
    }

    /**
     * 读取当前应用版本名，读取失败时返回占位符。
     * @return 版本名，如 "1.0"
     */
    private fun getAppVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    companion object {
        /** 最多可选图片数量。 */
        private const val MAX_IMAGE_COUNT: Int = 10

        /** 图片压缩后的最大宽高（像素）。 */
        private const val MAX_IMAGE_DIMENSION: Int = 1080

        /** 单张图片压缩后的最大字节数（500KB）。 */
        private const val MAX_IMAGE_SIZE_BYTES: Int = 500 * 1024
    }
}
