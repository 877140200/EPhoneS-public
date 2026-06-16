package com.susking.ephone_s.qq.ui.persona

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.qq.databinding.FragmentPersonaEditorBinding
import com.susking.ephone_s.qq.ui.QqViewModel

class PersonaEditorFragment : Fragment() {

    private var _binding: FragmentPersonaEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonaEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 加载当前人设
        viewModel.qqContactManager.userProfile.observe(viewLifecycleOwner) { profile ->
            // 只有在文本不同时才更新，防止光标跳动
            if (binding.etPersona.text.toString() != profile.persona) {
                binding.etPersona.setText(profile.persona)
            }
        }

        // 设置返回按钮，点击时保存并返回
        binding.toolbar.setNavigationOnClickListener {
            handleExitConfirmation()
        }

        // 处理系统返回键
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitConfirmation()
            }
        })
    }

    private fun handleExitConfirmation() {
        val currentPersona = viewModel.qqContactManager.userProfile.value?.persona ?: ""
        val newPersona = binding.etPersona.text.toString()

        if (currentPersona != newPersona) {
            AlertDialog.Builder(requireContext())
                .setTitle("确认保存")
                .setMessage("人设内容已修改，是否保存？")
                .setPositiveButton("保存") { _, _ ->
                    savePersona()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("不保存") { _, _ ->
                    parentFragmentManager.popBackStack()
                }
                .setNeutralButton("取消") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            parentFragmentManager.popBackStack()
        }
    }

    private fun savePersona() {
        val newPersona = binding.etPersona.text.toString()
        viewModel.qqContactManager.userProfile.value?.let { profile ->
            viewModel.qqContactManager.updateUserProfile(profile.copy(persona = newPersona))
            Toast.makeText(context, "人设已保存", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PersonaEditorFragment()
    }
}