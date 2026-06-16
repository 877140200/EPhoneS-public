package com.susking.ephone_s.qq.ui.chat

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.qq.databinding.DialogEditStickerBinding

class EditStickerDialogFragment : DialogFragment() {

    private var _binding: DialogEditStickerBinding? = null
    private val binding get() = _binding!!

    private var message: ChatMessage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                message = it.getParcelable(ARG_MESSAGE, ChatMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                message = it.getParcelable(ARG_MESSAGE)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditStickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        message?.let {
            binding.stickerUrlEditText.setText(it.stickerUrl)
            loadStickerPreview(it.stickerUrl)
        }
    }

    private fun setupListeners() {
        binding.stickerUrlEditText.doAfterTextChanged { text ->
            loadStickerPreview(text.toString())
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            val newUrl = binding.stickerUrlEditText.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_MESSAGE_ID to message?.id,
                    RESULT_NEW_URL to newUrl
                ))
            }
            dismiss()
        }
    }

    private fun loadStickerPreview(url: String?) {
        if (url.isNullOrBlank()) {
            binding.stickerPreviewImageView.setImageResource(R.drawable.ic_image_load_failed)
            return
        }

        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.ic_image_load_failed) // 当URL无效时显示裂开的图片
            .into(binding.stickerPreviewImageView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditStickerDialogFragment"
        const val REQUEST_KEY = "EditStickerDialogRequest"
        const val RESULT_MESSAGE_ID = "messageId"
        const val RESULT_NEW_URL = "newUrl"
        private const val ARG_MESSAGE = "message"

        fun newInstance(message: ChatMessage): EditStickerDialogFragment {
            return EditStickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MESSAGE, message)
                }
            }
        }
    }
}