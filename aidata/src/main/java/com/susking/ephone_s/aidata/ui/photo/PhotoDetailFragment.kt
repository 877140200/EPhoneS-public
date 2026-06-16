package com.susking.ephone_s.aidata.ui.photo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.susking.ephone_s.aidata.databinding.FragmentPhotoDetailBinding
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.use_case.GenerateImageFromPromptUseCase
import com.susking.ephone_s.aidata.domain.use_case.RewriteImagePromptUseCase
import com.susking.ephone_s.core.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通用照片详情页
 * 支持左右滑动查看照片、重新生成图片、编辑提示词等功能
 * 可在cphone、qq等多个模块中使用
 */
@AndroidEntryPoint
class PhotoDetailFragment : Fragment() {

    private var _binding: FragmentPhotoDetailBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var generateImageUseCase: GenerateImageFromPromptUseCase
    @Inject lateinit var rewriteImagePromptUseCase: RewriteImagePromptUseCase
    @Inject lateinit var personProfileRepository: PersonProfileRepository

    private var photos: MutableList<AlbumPhoto> = mutableListOf()
    private var initialPosition: Int = 0
    private var contactId: String = ""
    
    // 回调接口,用于通知父Fragment更新照片数据
    private var photoUpdateListener: PhotoUpdateListener? = null

    /**
     * 照片更新监听接口
     */
    interface PhotoUpdateListener {
        fun onPhotoUpdated(contactId: String, updatedPhoto: AlbumPhoto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialPosition = arguments?.getInt(ARG_INITIAL_POSITION, 0) ?: 0
        contactId = arguments?.getString(ARG_CONTACT_ID) ?: ""
    }

    /**
     * 设置照片列表(由父Fragment调用)
     */
    fun setPhotos(photoList: List<AlbumPhoto>) {
        this.photos.clear()
        this.photos.addAll(photoList)
    }
    
    /**
     * 设置照片更新监听器(由父Fragment调用)
     */
    fun setPhotoUpdateListener(listener: PhotoUpdateListener) {
        this.photoUpdateListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupCloseButton()
        setupButtons()
        setupDialogListener()
        updatePageInfo(initialPosition)
    }

    /**
     * 设置ViewPager2
     */
    private fun setupViewPager() {
        val adapter = PhotoDetailAdapter(photos)

        binding.viewPager.apply {
            this.adapter = adapter
            setCurrentItem(initialPosition, false)

            // 注册页面切换回调
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePageInfo(position)
                }
            })
        }
    }

    /**
     * 设置关闭按钮
     */
    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 设置按钮点击事件
     */
    private fun setupButtons() {
        binding.btnRerollImage.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition in photos.indices) {
                val photo = photos[currentPosition]
                rerollImage(photo)
            }
        }

        binding.btnEditPrompt.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition in photos.indices) {
                val photo = photos[currentPosition]
                showEditPromptDialog(photo)
            }
        }
    }

    /**
     * 设置对话框结果监听
     */
    private fun setupDialogListener() {
        childFragmentManager.setFragmentResultListener(
            EditImagePromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val photoId = bundle.getString(EditImagePromptDialogFragment.RESULT_PHOTO_ID) ?: return@setFragmentResultListener
            val action = bundle.getString(EditImagePromptDialogFragment.RESULT_ACTION)

            val photo = photos.find { it.id == photoId } ?: return@setFragmentResultListener

            if (action == EditImagePromptDialogFragment.ACTION_AI_REWRITE) {
                val specialRequirements = bundle.getString(EditImagePromptDialogFragment.RESULT_SPECIAL_REQUIREMENTS)
                val includeOriginalPrompt = bundle.getBoolean(EditImagePromptDialogFragment.RESULT_INCLUDE_ORIGINAL_PROMPT, true)
                rewriteAndRegenerateImage(photo, specialRequirements, includeOriginalPrompt)
            } else {
                val newPrompt = bundle.getString(EditImagePromptDialogFragment.RESULT_NEW_PROMPT) ?: return@setFragmentResultListener
                regenerateImage(photo, newPrompt)
            }
        }
    }

    /**
     * 显示编辑提示词对话框
     */
    private fun showEditPromptDialog(photo: AlbumPhoto) {
        EditImagePromptDialogFragment.newInstance(photo.id, photo.imagePrompt)
            .show(childFragmentManager, EditImagePromptDialogFragment.TAG)
    }

    /**
     * 重roll图片(使用原提示词)
     */
    private fun rerollImage(photo: AlbumPhoto) {
        regenerateImage(photo, photo.imagePrompt)
    }

    /**
     * 重新生成图片
     */
    private fun regenerateImage(photo: AlbumPhoto, newPrompt: String) {
        lifecycleScope.launch {
            // 禁用按钮,防止重复点击
            binding.btnRerollImage.isEnabled = false
            binding.btnEditPrompt.isEnabled = false
            binding.btnRerollImage.alpha = 0.5f
            binding.btnEditPrompt.alpha = 0.5f
            
            Toast.makeText(requireContext(), "正在重新生成图片...", Toast.LENGTH_SHORT).show()

            try {
                // 获取联系人
                val contact = personProfileRepository.getPersonProfileById(contactId)
                if (contact == null) {
                    Toast.makeText(requireContext(), "联系人不存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 生成图片
                val result = generateImageUseCase(newPrompt, contact)

                result.onSuccess { filePath ->
                    // 更新照片数据
                    val updatedPhoto = photo.copy(
                        imagePrompt = newPrompt,
                        imageUrl = filePath
                    )

                    // 更新本地列表
                    val index = photos.indexOfFirst { it.id == photo.id }
                    if (index != -1) {
                        photos[index] = updatedPhoto
                        binding.viewPager.adapter?.notifyItemChanged(index)
                    }

                    // 通知父Fragment更新数据库
                    photoUpdateListener?.onPhotoUpdated(contactId, updatedPhoto)

                    Toast.makeText(requireContext(), "图片重新生成成功", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "重新生成失败: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // 恢复按钮状态
                binding.btnRerollImage.isEnabled = true
                binding.btnEditPrompt.isEnabled = true
                binding.btnRerollImage.alpha = 1.0f
                binding.btnEditPrompt.alpha = 1.0f
            }
        }
    }

    /**
     * AI重写提示词并重新生成
     */
    private fun rewriteAndRegenerateImage(
        photo: AlbumPhoto,
        specialRequirements: String?,
        includeOriginalPrompt: Boolean
    ) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "AI正在重写提示词...", Toast.LENGTH_SHORT).show()

            try {
                // TODO: 实现AI重写提示词功能
                // 目前暂时使用原提示词
                Toast.makeText(requireContext(), "AI重写功能待实现,使用原提示词", Toast.LENGTH_SHORT).show()
                regenerateImage(photo, photo.imagePrompt)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "AI重写失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 更新页码和描述信息
     */
    private fun updatePageInfo(position: Int) {
        if (position in photos.indices) {
            val photo = photos[position]
            binding.tvDescription.text = photo.description
            binding.tvPageIndicator.text = "${position + 1} / ${photos.size}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 照片详情页ViewPager2的Adapter
     */
    private inner class PhotoDetailAdapter(
        private val photos: List<AlbumPhoto>
    ) : RecyclerView.Adapter<PhotoDetailAdapter.PhotoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val imageView = ShapeableImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            return PhotoViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.bind(photos[position])
        }

        override fun getItemCount(): Int = photos.size

        inner class PhotoViewHolder(
            private val imageView: ShapeableImageView
        ) : RecyclerView.ViewHolder(imageView) {

            fun bind(photo: AlbumPhoto) {
                // 直接使用imageUrl,如果为空则显示占位符
                // 图片应该通过NovelAI服务在其他地方提前生成
                imageView.load(photo.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
        }
    }

    companion object {
        private const val ARG_INITIAL_POSITION = "initial_position"
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String, photos: List<AlbumPhoto>, initialPosition: Int) =
            PhotoDetailFragment().apply {
                setPhotos(photos)
                arguments = bundleOf(
                    ARG_CONTACT_ID to contactId,
                    ARG_INITIAL_POSITION to initialPosition
                )
            }
    }
}