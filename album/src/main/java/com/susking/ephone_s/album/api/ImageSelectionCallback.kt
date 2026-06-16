package com.susking.ephone_s.album.api

import com.canhub.cropper.CropImageOptions

/**
 * 图片选择回调接口
 * 用于从相册选择图片后的回调通知
 */
interface ImageSelectionCallback {
    /**
     * 当图片被选中时调用
     * @param requestKey 请求标识
     * @param imageUri 图片URI
     * @param cropOptions 裁剪选项（可选）
     */
    fun onImageSelected(requestKey: String, imageUri: String, cropOptions: CropImageOptions?)
}