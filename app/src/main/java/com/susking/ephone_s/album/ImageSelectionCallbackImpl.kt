package com.susking.ephone_s.album

import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.album.api.ImageSelectionCallback
import com.susking.ephone_s.core.util.EventBus

/**
 * ImageSelectionCallback接口的实现类
 * 在app模块中实现，提供图片选择回调能力
 */
class ImageSelectionCallbackImpl : ImageSelectionCallback {
    
    override fun onImageSelected(requestKey: String, imageUri: String, cropOptions: CropImageOptions?) {
        EventBus.postImageSelectedEvent(requestKey, imageUri, cropOptions)
    }
}