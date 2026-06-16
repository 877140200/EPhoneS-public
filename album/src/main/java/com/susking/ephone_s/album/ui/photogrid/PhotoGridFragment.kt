package com.susking.ephone_s.album.ui.photogrid

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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.album.R
import com.susking.ephone_s.core.R as CoreR
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.album.api.AlbumNavigator
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.databinding.FragmentPhotoGridBinding
import com.susking.ephone_s.album.ui.PhotoViewerFragment
import kotlinx.coroutines.launch
import java.io.File

class PhotoGridFragment : Fragment() {

    private var _binding: FragmentPhotoGridBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PhotoGridViewModel
    private lateinit var photoAdapter: PhotoAdapter
    private var isSelectionMode = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.addPhoto(it.toString())
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoGridBinding.inflate(inflater, container, false)
        val albumId = arguments?.getLong(ARG_ALBUM_ID)
        val isFavorites = arguments?.getBoolean(ARG_IS_FAVORITES) ?: false
        val provider = requireContext().applicationContext as AlbumDatabaseProvider
        val albumRepository = AlbumRepositoryImpl(provider.getAlbumDao(), provider.getPhotoDao())
        val factory = if (isFavorites) {
            PhotoGridViewModelFactory(albumRepository, null, true)
        } else {
            PhotoGridViewModelFactory(albumRepository, albumId!!, false)
        }
        viewModel = ViewModelProvider(this, factory)[PhotoGridViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observePhotos()
        setupClickListeners()
        setupOnBackPressed()
    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isSelectionMode) {
                        photoAdapter.toggleSelectionMode(false)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
    }


    private fun setupToolbar() {
        val isFavorites = arguments?.getBoolean(ARG_IS_FAVORITES) ?: false
        binding.photoGridToolbar.setNavigationOnClickListener {
            if (isSelectionMode) {
                photoAdapter.toggleSelectionMode(false)
            } else {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
        if (isFavorites) {
            binding.photoGridToolbar.title = "收藏夹"
            binding.fabAddPhoto.visibility = View.GONE
        } else {
            val albumName = arguments?.getString(ARG_ALBUM_NAME) ?: "相册"
            binding.photoGridToolbar.title = albumName
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo, position ->
                val photos = ArrayList(photoAdapter.currentList)
                val navigator = requireContext().applicationContext as? AlbumNavigator
                navigator?.navigateToPhotoViewer(parentFragmentManager, photos, position)
            },
            onSelectionChange = { count ->
                updateUiForSelectionMode(photoAdapter.isSelectionMode, count)
            }
        )
        binding.photoRecyclerView.adapter = photoAdapter
    }

    private fun updateUiForSelectionMode(inSelectionMode: Boolean, selectionCount: Int) {
        this.isSelectionMode = inSelectionMode
        binding.fabAddPhoto.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        binding.bottomActionBarContainer.visibility = if (inSelectionMode) View.VISIBLE else View.GONE

        if (inSelectionMode) {
            val title = if (selectionCount > 0) "已选择 $selectionCount 项" else "选择项目"
            binding.photoGridToolbar.title = title
            binding.photoGridToolbar.setNavigationIcon(CoreR.drawable.ic_close_24)
            binding.photoGridToolbar.menu.clear()
            binding.photoGridToolbar.inflateMenu(CoreR.menu.menu_photo_selection)
        } else {
            setupToolbar() // 恢复原始工具栏状态
            binding.photoGridToolbar.menu.clear()
        }
    }

    private fun setupClickListeners() {
        binding.fabAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.photoGridToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_select_all -> {
                    photoAdapter.selectAll()
                    true
                }
                R.id.action_clear_selection -> {
                    photoAdapter.clearSelection()
                    true
                }
                else -> false
            }
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

    private fun observePhotos() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.photos.collect { photos ->
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
                    Log.e("PhotoGridFragment", "下载失败 for URI: ${photo.uri}", e)
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
        private const val ARG_ALBUM_ID = "albumId"
        private const val ARG_ALBUM_NAME = "albumName"
        private const val ARG_IS_FAVORITES = "isFavorites"

        fun newInstance(albumId: Long, albumName: String): PhotoGridFragment {
            return PhotoGridFragment().apply {
                arguments = bundleOf(
                    ARG_ALBUM_ID to albumId,
                    ARG_ALBUM_NAME to albumName,
                    ARG_IS_FAVORITES to false
                )
            }
        }

        fun newInstanceForFavorites(): PhotoGridFragment {
            return PhotoGridFragment().apply {
                arguments = bundleOf(
                    ARG_IS_FAVORITES to true
                )
            }
        }
    }
}
