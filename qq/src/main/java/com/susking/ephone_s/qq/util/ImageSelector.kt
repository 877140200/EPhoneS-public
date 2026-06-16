package com.susking.ephone_s.qq.util

import android.R
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.album.ui.AllPhotosFragment
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.ImagePickerHelper
import com.susking.ephone_s.core.util.ImageSelectedData
import java.io.File

/**
 * 一个更全面的图片选择和处理类。
 * 使用 EventBus 进行跨模块通信。
 * @param fragment 需要使用此功能的Fragment实例。
 * @param requestKey 请求标识，用于区分不同的图片选择请求。
 * @param onResult 回调函数，返回一个可为空的Uri。如果用户选择清除，则返回null。
 */
class ImageSelector(
    private val fragment: Fragment,
    private val requestKey: String = REQUEST_KEY_IMAGE_SELECT,
    private val onResult: (Uri?) -> Unit
) {

    private val imagePickerHelper = ImagePickerHelper(fragment)

    // 用于从小手机相册返回后进行裁剪的专用Launcher
    private val cropperLauncher = fragment.registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                try {
                    val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    fragment.requireContext().contentResolver.takePersistableUriPermission(uri, flag)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                onResult(uri)
            }
        } else {
            Toast.makeText(fragment.requireContext(), "图片裁剪失败: ${result.error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // EventBus 观察者
    private val imageSelectedObserver = Observer<Event<ImageSelectedData>> { event ->
        Log.d("ImageSelector", "观察者被触发,event=$event, requestKey=$requestKey")
        // 先使用peekContent检查requestKey是否匹配,避免误标记为已处理
        val data = event.peekContent()
        Log.d("ImageSelector", "查看事件内容: requestKey=${data.requestKey}, 期望key=${this.requestKey}, hasBeenHandled=${event.hasBeenHandled}")

        if (data.requestKey == this.requestKey) {
            // requestKey匹配,现在才真正消费事件
            event.getContentIfNotHandled()?.let { matchedData ->
                Log.d("ImageSelector", "requestKey匹配且成功消费事件!开始处理图片: ${matchedData.imagePath}")
                handleImageSelected(matchedData.imagePath, matchedData.cropOptions)
            } ?: Log.d("ImageSelector", "requestKey匹配但事件已被其他观察者处理")
        } else {
            Log.d("ImageSelector", "requestKey不匹配(期望:$requestKey, 实际:${data.requestKey}),不消费此事件")
        }
    }

    init {
        Log.d("ImageSelector", "Instance created, listening for key: $requestKey via EventBus")
        // 使用 EventBus 监听图片选择事件
        // 使用lifecycleOwner而不是viewLifecycleOwner,确保即使Fragment的view被覆盖也能接收事件
        EventBus.imageSelectedEvent.observe(fragment, imageSelectedObserver)
    }

    private fun handleImageSelected(imagePath: String, cropOptions: CropImageOptions?) {
        Log.d("ImageSelector", "handleImageSelected被调用: imagePath=$imagePath")
        try {
            val imageFile = File(imagePath)
            Log.d("ImageSelector", "图片文件路径: ${imageFile.absolutePath}, 存在: ${imageFile.exists()}")
            if (imageFile.exists()) {
                val imageUri = FileProvider.getUriForFile(
                    fragment.requireContext(),
                    "${fragment.requireContext().packageName}.provider",
                    imageFile
                )
                Log.d("ImageSelector", "生成FileProvider URI: $imageUri")
                // 从小手机相册选择图片后，直接开始裁剪
                val options = cropOptions ?: CropImageOptions()
                Log.d("ImageSelector", "准备启动裁剪器")
                cropperLauncher.launch(CropImageContractOptions(imageUri, options))
                Log.d("ImageSelector", "裁剪器已启动")
            } else {
                Log.e("ImageSelector", "图片文件不存在: ${imageFile.absolutePath}")
                Toast.makeText(fragment.requireContext(), "图片文件不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IllegalArgumentException) {
            Log.e("ImageSelector", "图片路径无效", e)
            Toast.makeText(fragment.requireContext(), "图片路径无效", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ImageSelector", "处理图片时发生异常", e)
            Toast.makeText(fragment.requireContext(), "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示图片选择对话框。
     * @param title 对话框标题。
     * @param cropOptions 裁剪选项。
     * @param showClearOption 是否显示清除选项。
     */
    fun showSelectionDialog(title: String, cropOptions: CropImageOptions, showClearOption: Boolean) {
        val items = mutableListOf("从小手机相册选择", "从系统相册选择")
        if (showClearOption) {
            items.add("清除")
        }

        AlertDialog.Builder(fragment.requireContext())
            .setTitle(title)
            .setItems(items.toTypedArray()) { dialog, which ->
                when (which) {
                    0 -> { // 从小手机相册选择
                        val ephonesAlbumFragment = AllPhotosFragment.newInstance(
                            isSelectionMode = true,
                            requestKey = requestKey,
                            cropOptions = cropOptions
                        )
                        fragment.requireActivity().supportFragmentManager.beginTransaction()
                            .add(R.id.content, ephonesAlbumFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    1 -> { // 从系统相册选择
                        imagePickerHelper.pickAndCrop(cropOptions) { uri ->
                            onResult(uri)
                        }
                    }
                    2 -> { // 清除
                        if (showClearOption) {
                            onResult(null)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        const val REQUEST_KEY_IMAGE_SELECT = "ImageSelector.REQUEST_KEY_IMAGE_SELECT"
    }
}