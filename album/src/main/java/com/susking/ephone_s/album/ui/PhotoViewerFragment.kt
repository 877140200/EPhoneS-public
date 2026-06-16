package com.susking.ephone_s.album.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.album.R
import com.susking.ephone_s.album.api.AlbumDatabaseProvider
import com.susking.ephone_s.album.data.repository.AlbumRepositoryImpl
import com.susking.ephone_s.album.databinding.FragmentAlbumPhotoViewerBinding
import com.susking.ephone_s.album.domain.model.Photo
import com.susking.ephone_s.core.R as CoreR

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentAlbumPhotoViewerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlbumViewModel by activityViewModels {
        val provider = requireContext().applicationContext as AlbumDatabaseProvider
        val albumRepository = AlbumRepositoryImpl(provider.getAlbumDao(), provider.getPhotoDao())
        AlbumViewModelFactory(albumRepository)
    }

    private lateinit var photos: List<Photo>
    private var currentPosition: Int = 0
    private var isSystemUiVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            photos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableArrayList(ARG_PHOTOS, Photo::class.java) ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableArrayList(ARG_PHOTOS) ?: emptyList()
            }
            currentPosition = it.getInt(ARG_POSITION)
        }

        // 在视图创建前就设置好窗口模式，这是最可靠的方式
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 调试:直接膨胀布局看看里面有什么
        android.util.Log.d("PhotoViewerFragment", "=== Starting onCreateView ===")
        android.util.Log.d("PhotoViewerFragment", "R.layout.fragment_album_photo_viewer: ${R.layout.fragment_album_photo_viewer}")
        
        try {
            // 先直接膨胀布局看看内容
            val contextInflater = LayoutInflater.from(requireContext())
            val rootView = contextInflater.inflate(R.layout.fragment_album_photo_viewer, container, false)
            android.util.Log.d("PhotoViewerFragment", "Root view inflated: ${rootView.javaClass.simpleName}")
            
            // 打印所有子View的ID
            if (rootView is ViewGroup) {
                android.util.Log.d("PhotoViewerFragment", "Root has ${rootView.childCount} children")
                for (i in 0 until rootView.childCount) {
                    val child = rootView.getChildAt(i)
                    val idName = try {
                        resources.getResourceEntryName(child.id)
                    } catch (e: Exception) {
                        "no-id"
                    }
                    android.util.Log.d("PhotoViewerFragment", "Child $i: ${child.javaClass.simpleName}, id=$idName")
                }
            }
            
            // 尝试直接查找bottom_sheet_container
            val bottomSheet = rootView.findViewById<View>(R.id.bottom_sheet_container)
            android.util.Log.d("PhotoViewerFragment", "Direct findViewById result: $bottomSheet")
            
            // 现在尝试bind
            _binding = FragmentAlbumPhotoViewerBinding.bind(rootView)
            android.util.Log.d("PhotoViewerFragment", "Binding created successfully!")
            return binding.root
        } catch (e: Exception) {
            android.util.Log.e("PhotoViewerFragment", "Error in onCreateView", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 隐藏父Fragment的AppBarLayout以实现全屏（通过资源名查找）
        val appBarId = resources.getIdentifier("app_bar_layout", "id", requireContext().packageName)
        if (appBarId != 0) {
            activity?.findViewById<AppBarLayout>(appBarId)?.visibility = View.GONE
        }

        // 这是最终的强制修正方案
        // 为工具栏和底部栏设置内边距，让它们浮动在系统栏之上
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomSheetContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // 为ViewPager设置监听器，用translationY来抵消父布局的位移
        ViewCompat.setOnApplyWindowInsetsListener(binding.viewPager) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 当底部系统栏出现时，给ViewPager一个反向的Y轴位移，以抵消整个Fragment被上推的效果
            v.translationY = -systemBars.bottom.toFloat()
            // 不要在ViewPager上应用任何内边距
            v.setPadding(0, 0, 0, 0)
            insets
        }

        setupViewPager()
        setupSystemUiControls()
        setupClickListeners()
        setupSystemBackButton()

        // 延迟到视图布局完成后再执行，确保获取到正确的视图尺寸
        view.post {
            // 初始进入时隐藏UI，避免闪烁
            toggleSystemUiVisibility()
        }
    }

    private fun setupViewPager() {
        val photoPagerAdapter = PhotoPagerAdapter(
            photos = photos.map { it.uri },
            onPhotoTap = { toggleSystemUiVisibility() },
            onSwipeDown = { parentFragmentManager.popBackStack() }
        )
        binding.viewPager.adapter = photoPagerAdapter
        binding.viewPager.setCurrentItem(currentPosition, false)
        updateFavoriteIcon()
    }

    private fun setupSystemUiControls() {
        val window = requireActivity().window
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 行为设置完成后，具体的显隐操作由调用方（如onViewCreated）决定
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonFavorite.setOnClickListener {
            val photo = photos[binding.viewPager.currentItem]
            val updatedPhoto = photo.copy(isFavorited = !photo.isFavorited)
            viewModel.updatePhoto(updatedPhoto)
            // 你可能需要观察ViewModel中的照片列表来自动更新UI
            // 这里为了简单，我们直接更新当前照片对象
            photos = photos.toMutableList().apply { set(binding.viewPager.currentItem, updatedPhoto) }
            updateFavoriteIcon()
        }

        binding.buttonDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认删除")
                .setMessage("您确定要删除这张照片吗？此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ ->
                    val photo = photos[binding.viewPager.currentItem]
                    viewModel.deletePhoto(photo)
                    // 删除后返回上一个界面
                    parentFragmentManager.popBackStack()
                }
                .show()
        }
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })
    }

    private fun toggleSystemUiVisibility() {
        isSystemUiVisible = !isSystemUiVisible
        val window = requireActivity().window
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val duration = 250L // 动画时长

        if (isSystemUiVisible) {
            // --- 显示UI ---
            // 先让View可见，然后执行入场动画
            binding.toolbar.visibility = View.VISIBLE
            binding.bottomSheetContainer.visibility = View.VISIBLE

            binding.toolbar.animate()
                .translationY(0f)
                .alpha(1f) // 淡入
                .setDuration(duration)
                .start()
            binding.bottomSheetContainer.animate()
                .translationY(0f)
                .alpha(1f) // 淡入
                .setDuration(duration)
                .start()

            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            binding.root.setBackgroundResource(CoreR.color.photo_viewer_background)
        } else {
            // --- 隐藏UI ---
            // 执行出场动画，动画结束后再隐藏View
            binding.toolbar.animate()
                .translationY(-binding.toolbar.height.toFloat())
                .alpha(0f) // 淡出
                .setDuration(duration)
                .withEndAction {
                    binding.toolbar.visibility = View.GONE
                }
                .start()
            binding.bottomSheetContainer.animate()
                .translationY(binding.bottomSheetContainer.height.toFloat())
                .alpha(0f) // 淡出
                .setDuration(duration)
                .withEndAction {
                    binding.bottomSheetContainer.visibility = View.GONE
                }
                .start()

            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            if (!isNightMode()) {
                binding.root.setBackgroundColor(Color.BLACK)
            }
        }
    }

    private fun isNightMode(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateFavoriteIcon() {
        val photo = photos[binding.viewPager.currentItem]
        if (photo.isFavorited) {
            binding.iconFavorite.setImageResource(CoreR.drawable.ic_like_filled_24)
        } else {
            binding.iconFavorite.setImageResource(CoreR.drawable.ic_star_outline)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 恢复父Fragment的AppBarLayout
        val appBarId = resources.getIdentifier("app_bar_layout", "id", requireContext().packageName)
        if (appBarId != 0) {
            activity?.findViewById<AppBarLayout>(appBarId)?.visibility = View.VISIBLE
        }

        // 恢复默认的窗口装饰，并确保退出时恢复系统UI
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        _binding = null
    }

    companion object {
        private const val ARG_PHOTOS = "photos"
        private const val ARG_POSITION = "position"

        fun newInstance(photos: ArrayList<Photo>, position: Int): PhotoViewerFragment {
            val fragment = PhotoViewerFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_PHOTOS, photos)
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }
}
