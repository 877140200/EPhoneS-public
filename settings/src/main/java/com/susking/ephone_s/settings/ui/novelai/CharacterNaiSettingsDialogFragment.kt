package com.susking.ephone_s.settings.ui.novelai

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.settings.R
import com.susking.ephone_s.settings.databinding.DialogCharacterNaiSettingsBinding

class CharacterNaiSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogCharacterNaiSettingsBinding? = null
    private val binding get() = _binding!!

    // 定义一个监听器接口
    interface OnNaiSettingsSaveListener {
        fun onNaiSettingsSave(
            promptSource: String,
            positivePrompt: String,
            negativePrompt: String
        )
    }
    var listener: OnNaiSettingsSaveListener? = null

    private var contact: PersonProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
        arguments?.let {
            contact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_CONTACT, PersonProfile::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_CONTACT)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCharacterNaiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun loadSettings() {
        contact?.let {
            binding.positivePromptInput.setText(it.naiPositivePrompt)
            binding.negativePromptInput.setText(it.naiNegativePrompt)
            if (it.naiPromptSource == "character") {
                binding.radioGroup.check(R.id.radio_character)
            } else {
                binding.radioGroup.check(R.id.radio_system)
            }
        }
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        binding.buttonSave.setOnClickListener {
            val selectedSource = if (binding.radioGroup.checkedRadioButtonId == R.id.radio_character) "character" else "system"
            val positive = binding.positivePromptInput.text.toString()
            val negative = binding.negativePromptInput.text.toString()

            listener?.onNaiSettingsSave(selectedSource, positive, negative)
            Toast.makeText(context, "角色专属NAI提示词已保存", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.buttonClear.setOnClickListener {
            binding.positivePromptInput.setText("")
            binding.negativePromptInput.setText("")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CharacterNaiSettingsDialog"
        private const val ARG_CONTACT = "contact"

        fun newInstance(contact: PersonProfile): CharacterNaiSettingsDialogFragment {
            val fragment = CharacterNaiSettingsDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_CONTACT, contact)
            fragment.arguments = args
            return fragment
        }
    }
}