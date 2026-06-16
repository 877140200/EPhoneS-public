package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.susking.ephone_s.qq.databinding.FragmentImageViewerBinding

class ImageViewerFragment : DialogFragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!
    private var isSystemUiVisible = true

    override fun getTheme(): Int {
        // 使用一个完全透明的主题
        return com.susking.ephone_s.core.R.style.TransparentDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupImmersiveMode()

        val imageUrl = arguments?.getString(ARG_IMAGE_URL)
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .into(binding.photoView)
        }

        // 点击图片切换UI显隐
        binding.photoView.setOnPhotoTapListener { _, _, _ ->
            toggleSystemUiVisibility()
        }
    }

    private fun setupImmersiveMode() {
        dialog?.window?.let { window ->
            // 关键：让布局能够延伸到系统栏后面
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowInsetsControllerCompat(window, binding.root)
            // 初始状态下隐藏系统栏
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            // 当用户从屏幕边缘滑动时，系统栏会短暂显示然后再次隐藏
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isSystemUiVisible = false
        }
    }

    private fun toggleSystemUiVisibility() {
        dialog?.window?.let { window ->
            val insetsController = WindowInsetsControllerCompat(window, binding.root)
            if (isSystemUiVisible) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            isSystemUiVisible = !isSystemUiVisible
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IMAGE_URL = "image_url"
        const val TAG = "ImageViewerFragment"

        fun newInstance(imageUrl: String): ImageViewerFragment {
            return ImageViewerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URL, imageUrl)
                }
            }
        }
    }
}