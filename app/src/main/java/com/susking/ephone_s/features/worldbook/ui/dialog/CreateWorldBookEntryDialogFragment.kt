package com.susking.ephone_s.features.worldbook.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.R
import com.susking.ephone_s.databinding.DialogCreateWorldBookEntryBinding

class CreateWorldBookEntryDialogFragment : DialogFragment() {

    private var _binding: DialogCreateWorldBookEntryBinding? = null
    private val binding get() = _binding!!

    interface EntryInteractionListener {
        fun onEntryCreated(name: String, content: String)
        fun onEntryUpdated(entryId: Long, newName: String, newContent: String, newLampColor: String)
        fun onEntryDeleted(entryId: Long)
    }

    private var listener: EntryInteractionListener? = null
    private var editingEntryId: Long? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCreateWorldBookEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listener = parentFragment as? EntryInteractionListener

        arguments?.let {
            editingEntryId = it.getLong(ARG_ENTRY_ID, -1L).takeIf { id -> id != -1L }
            if (editingEntryId != null) {
                // 编辑模式
                binding.dialogTitle.text = "编辑条目"
                binding.nameEditText.setText(it.getString(ARG_ENTRY_NAME))
                binding.contentEditText.setText(it.getString(ARG_ENTRY_CONTENT))

                // 设置灯色
                val currentLampColor = it.getString(ARG_LAMP_COLOR)
                if (currentLampColor == "blue") {
                    binding.lampColorChipGroup.check(R.id.chipBlue)
                } else {
                    binding.lampColorChipGroup.check(R.id.chipGreen)
                }

                // 显示并设置删除按钮
                binding.deleteButton.visibility = View.VISIBLE
                binding.deleteButton.setOnClickListener {
                    listener?.onEntryDeleted(editingEntryId!!)
                    dismiss()
                }
            } else {
                // 创建模式
                binding.dialogTitle.text = "添加新条目"
                binding.lampColorChipGroup.check(R.id.chipGreen) // 默认绿灯
                binding.deleteButton.visibility = View.GONE
            }
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val content = binding.contentEditText.text.toString().trim()
            // 移除了 name.isNotBlank() 检查
            val selectedColor = if (binding.lampColorChipGroup.checkedChipId == R.id.chipBlue) "blue" else "green"
            if (editingEntryId != null) {
                listener?.onEntryUpdated(editingEntryId!!, name, content, selectedColor)
            } else {
                listener?.onEntryCreated(name, content)
            }
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateWorldBookEntryDialogFragment"
        private const val ARG_ENTRY_ID = "entry_id"
        private const val ARG_ENTRY_NAME = "entry_name"
        private const val ARG_ENTRY_CONTENT = "entry_content"
        private const val ARG_LAMP_COLOR = "lamp_color"

        fun newInstanceForCreate() = CreateWorldBookEntryDialogFragment()

        fun newInstanceForEdit(entryId: Long, name: String, content: String, lampColor: String): CreateWorldBookEntryDialogFragment {
            return CreateWorldBookEntryDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ENTRY_ID, entryId)
                    putString(ARG_ENTRY_NAME, name)
                    putString(ARG_ENTRY_CONTENT, content)
                    putString(ARG_LAMP_COLOR, lampColor)
                }
            }
        }
    }
}