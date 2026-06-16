package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.susking.ephone_s.qq.databinding.DialogFragmentLocationBinding

class LocationDialogFragment(
    private val onConfirm: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogFragmentLocationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.confirmButton.setOnClickListener {
            val location = binding.locationEditText.text.toString().trim()
            if (location.isNotEmpty()) {
                onConfirm(location)
                dismiss()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}