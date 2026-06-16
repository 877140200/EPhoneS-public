package com.susking.ephone_s.features.worldbook.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.R
import com.susking.ephone_s.databinding.DialogCreateWorldBookBinding // 假设您会创建 dialog_create_world_book.xml

class CreateWorldBookDialogFragment : DialogFragment() {

    private var _binding: DialogCreateWorldBookBinding? = null
    private val binding get() = _binding!!

    // 定义一个监听器接口，用于将创建或更新的结果传递给调用者
    interface CreateWorldBookListener {
        fun onWorldBookCreated(title: String, category: String)
        fun onWorldBookUpdated(worldBookId: Long, newTitle: String, newCategory: String)
    }

    private var listener: CreateWorldBookListener? = null

    // 用于编辑模式的参数
    private var editingWorldBookId: Long? = null
    private var initialTitle: String? = null
    private var initialCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateWorldBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listener = parentFragment as? CreateWorldBookListener // 尝试将父 Fragment 作为监听器

        // 获取编辑模式的参数
        arguments?.let {
            editingWorldBookId = it.getLong(ARG_WORLD_BOOK_ID, -1).takeIf { id -> id != -1L }
            initialTitle = it.getString(ARG_TITLE)
            initialCategory = it.getString(ARG_CATEGORY)
        }

        if (editingWorldBookId != null) {
            binding.titleEditText.setText(initialTitle)
            binding.categoryEditText.setText(initialCategory)
            binding.dialogTitle.text = getString(R.string.dialog_edit_world_book_title)
        } else {
            binding.dialogTitle.text = getString(R.string.dialog_create_world_book_title)
        }

        binding.saveButton.setOnClickListener {
            val title = binding.titleEditText.text.toString().trim()
            val category = binding.categoryEditText.text.toString().trim()

            if (title.isNotBlank()) {
                if (editingWorldBookId != null) {
                    listener?.onWorldBookUpdated(editingWorldBookId!!, title, category)
                } else {
                    listener?.onWorldBookCreated(title, category)
                }
                dismiss() // 关闭对话框
            } else {
                binding.titleInputLayout.error = "标题不能为空"
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss() // 关闭对话框
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateWorldBookDialogFragment"
        private const val ARG_WORLD_BOOK_ID = "world_book_id"
        private const val ARG_TITLE = "title"
        private const val ARG_CATEGORY = "category"

        // 用于创建世界书的newInstance
        fun newInstance(): CreateWorldBookDialogFragment {
            return CreateWorldBookDialogFragment()
        }

        // 用于编辑世界书的newInstance
        fun newInstance(worldBookId: Long, title: String, category: String): CreateWorldBookDialogFragment {
            val fragment = CreateWorldBookDialogFragment()
            val args = Bundle().apply {
                putLong(ARG_WORLD_BOOK_ID, worldBookId)
                putString(ARG_TITLE, title)
                putString(ARG_CATEGORY, category)
            }
            fragment.arguments = args
            return fragment
        }
    }
}