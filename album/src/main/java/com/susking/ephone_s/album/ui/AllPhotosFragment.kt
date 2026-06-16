package com.susking.ephone_s.album.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageOptions
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.album.api.ImageSelectionCallback
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.databinding.FragmentAllPhotosBinding
import com.susking.ephone_s.album.ui.photogrid.PhotoAdapter
import kotlinx.coroutines.launch
import java.io.File

class AllPhotosFragment : Fragment() {

    private var isSelectionMode: Boolean = false
    private var requestKey: String? = null
    private var cropOptions: CropImageOptions? = null
 
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.addPhotoToDefaultAlbum(it.toString())
            }
        }
    
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 用户授予权限，执行下载
                downloadSelectedPhotos()
            } else {
                // 用户拒绝权限，显示提示
                Toast.makeText(requireContext(), "需要存储权限才能下载图片", Toast.LENGTH_SHORT).show()
            }
        }

    private var _binding: FragmentAllPhotosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumViewModel by activityViewModels {
        val provider = requireContext().applicationContext as AlbumDatabaseProvider
        val albumRepository = AlbumRepositoryImpl(provider.getAlbumDao(), provider.getPhotoDao())
        AlbumViewModelFactory(albumRepository)
    }
    private lateinit var photoAdapter: PhotoAdapter
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置背景和可点击属性，防止穿透
        view.setBackgroundResource(android.R.color.white)
        view.isClickable = true

        arguments?.let {
            isSelectionMode = it.getBoolean(ARG_IS_SELECTION_MODE, false)
            requestKey = it.getString(ARG_REQUEST_KEY)
            cropOptions = it.getParcelable(ARG_CROP_OPTIONS)
        }

        setupRecyclerView()
        observeAllPhotos()
        setupClickListeners()
        setupOnBackPressed()
    }
 
    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (photoAdapter.isSelectionMode) {
                        photoAdapter.toggleSelectionMode(false)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
    }


    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo, position ->
                if (isSelectionMode) {
                    requestKey?.let { key ->
                        // 通过接口回调发送图片选择事件
                        Log.d("AllPhotosFragment", "Posting image selected event for key: '$key'")
                        val callback = requireContext().applicationContext as? ImageSelectionCallback
                        callback?.onImageSelected(key, photo.uri, cropOptions)
                        // 延迟到下一帧再关闭Fragment,确保事件被处理
                        view?.post {
                            requireActivity().supportFragmentManager.popBackStack()
                        }
                    }
                } else {
                    val photos = photoAdapter.currentList
                    (parentFragment as? AlbumFragment)?.navigateToPhotoViewer(photos, position)
                }
            },
            onSelectionChange = { count ->
                updateUiForSelectionMode(photoAdapter.isSelectionMode, count)
            }
        )
        binding.allPhotosRecyclerView.adapter = photoAdapter
    }

    private fun updateUiForSelectionMode(inSelectionMode: Boolean, selectionCount: Int) {
        this.isSelectionMode = inSelectionMode
        binding.fabAddPhoto.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        binding.bottomActionBarContainer.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        (parentFragment as? AlbumFragment)?.updateToolbarForSelection(inSelectionMode, selectionCount)
    }

    private fun setupClickListeners() {
        binding.fabAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.deleteButton.setOnClickListener {
            val selectedPhotos = photoAdapter.getSelectedItems().toList()
            viewModel.deletePhotos(selectedPhotos)
            photoAdapter.toggleSelectionMode(false)
        }

        binding.favoriteButton.setOnClickListener {
            val selectedPhotos = photoAdapter.getSelectedItems().toList()
            viewModel.favoritePhotos(selectedPhotos)
            photoAdapter.toggleSelectionMode(false)
        }

        binding.shareButton.setOnClickListener {
            val selectedPhotos = photoAdapter.getSelectedItems().toList()
            if (selectedPhotos.isNotEmpty()) {
                val imageUris = ArrayList<Uri>()
                selectedPhotos.forEach { photo ->
                    val file = File(photo.uri.substringAfter("file://"))
                    val uri = FileProvider.getUriForFile(requireContext(), "com.susking.ephone_s.provider", file)
                    imageUris.add(uri)
                }
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "分享图片"))
                photoAdapter.toggleSelectionMode(false)
            } else {
                Toast.makeText(requireContext(), "没有选择图片", Toast.LENGTH_SHORT).show()
            }
        }

        binding.downloadButton.setOnClickListener {
            // 检查权限
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                when {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        // 已有权限，执行下载
                        downloadSelectedPhotos()
                    }
                    else -> {
                        // 请求权限
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            } else {
                // Android 10 (Q) 及以上版本，无需特定权限
                downloadSelectedPhotos()
            }
        }
    }

    fun selectAllPhotos() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.selectAll()
        }
    }

    fun clearPhotoSelection() {
        if (::photoAdapter.isInitialized) {
            photoAdapter.clearSelection()
        }
    }

    private fun observeAllPhotos() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPhotos.collect { photos ->
                photoAdapter.submitList(photos)
            }
        }
    }

    private fun downloadSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedItems().toList()
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(requireContext(), "没有选择图片", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "已开始下载...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            var successCount = 0
            var failureCount = 0

            selectedPhotos.forEach { photo ->
                try {
                    val file = File(photo.uri.substringAfter("file://"))
                    val uri = FileProvider.getUriForFile(requireContext(), "com.susking.ephone_s.provider", file)
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION") // 对于低于API 28的版本，这是必需的
                        MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    }
                    saveBitmapToGallery(bitmap, "ephone_s_${System.currentTimeMillis()}.jpg", photo.uri)
                    successCount++
                } catch (e: Exception) {
                    Log.e("AllPhotosFragment", "下载失败 for URI: ${photo.uri}", e)
                    failureCount++
                }
            }

            activity?.runOnUiThread {
                if (failureCount > 0) {
                    Toast.makeText(requireContext(), "$failureCount 张图片下载失败", Toast.LENGTH_LONG).show()
                }
                if (successCount > 0) {
                    Toast.makeText(requireContext(), "$successCount 张图片下载成功", Toast.LENGTH_LONG).show()
                }
                if (successCount == 0 && failureCount == 0) {
                    // 如果因为某些原因（例如，权限问题但未抛出异常）没有任何操作
                    Toast.makeText(requireContext(), "没有图片被下载", Toast.LENGTH_SHORT).show()
                }
            }
            photoAdapter.toggleSelectionMode(false)
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, displayName: String, photoUri: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                // 在UI线程上显示Toast
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "$displayName 已保存", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "保存图片 '${displayName}' 失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "创建媒体文件失败 for: $photoUri", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_SELECTION_MODE = "is_selection_mode"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_CROP_OPTIONS = "crop_options"
        const val BUNDLE_KEY_SELECTED_PHOTO_URI = "bundle_key_selected_photo_uri"

        fun newInstance(
            isSelectionMode: Boolean = false,
            requestKey: String? = null,
            cropOptions: CropImageOptions? = null
        ) = AllPhotosFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_IS_SELECTION_MODE, isSelectionMode)
                putString(ARG_REQUEST_KEY, requestKey)
                putParcelable(ARG_CROP_OPTIONS, cropOptions)
            }
        }
    }
}
