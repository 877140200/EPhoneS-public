package com.susking.ephone_s.core.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions

/**
 * 一个帮助类，用于封装从系统相册选择图片、裁剪图片以及处理结果的逻辑。
 * @param fragment 需要使用此功能的Fragment实例。
 */
class ImagePickerHelper(private val fragment: Fragment) {

    private var onResult: ((Uri) -> Unit)? = null
    private var cropOptions: CropImageOptions = CropImageOptions()

    // 注册图片裁剪器的Activity Result Launcher
    private val cropperLauncher = fragment.registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                // 持久化URI权限，防止重启后无法访问
                handleImagePersistence(uri)
                // 调用外部传入的回调函数，处理最终的URI
                onResult?.invoke(uri)
            }
        } else {
            Toast.makeText(fragment.requireContext(), "图片裁剪失败: ${result.error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 注册图片选择器的Activity Result Launcher
    private val pickerLauncher = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // 获取到URI后，启动裁剪器
            cropperLauncher.launch(CropImageContractOptions(it, cropOptions))
        }
    }

    /**
     * 启动图片选择和裁剪流程。
     * @param options 图片裁剪的配置选项。
     * @param resultCallback 裁剪成功后的回调，返回一个可用的Uri。
     */
    fun pickAndCrop(options: CropImageOptions, resultCallback: (Uri) -> Unit) {
        this.cropOptions = options
        this.onResult = resultCallback
        pickerLauncher.launch("image/*") // 启动系统图片选择器
    }

    /**
     * 获取对图片URI的持久化读取权限。
     */
    private fun handleImagePersistence(uri: Uri) {
        try {
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            fragment.requireContext().contentResolver.takePersistableUriPermission(uri, flag)
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(fragment.requireContext(), "无法获取图片权限", Toast.LENGTH_SHORT).show()
        }
    }
}