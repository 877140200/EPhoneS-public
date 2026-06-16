package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.databinding.FragmentQqAiProfileEditDetailsBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QqAiProfileEditDetailsFragment : Fragment() {

    private var _binding: FragmentQqAiProfileEditDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 QqContactManager
    @Inject lateinit var contactManager: QqContactManager

    private var contactId: String? = null
    private var currentContact: PersonProfile? = null // 保存当前联系人的完整数据
    private var isDataBound = false // 新增标志位，确保数据只绑定一次

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
        if (contactId == null) {
            Toast.makeText(context, "错误：未找到联系人ID", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqAiProfileEditDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeContact()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeContact() {
        viewModel.contactManager.contacts.observe(viewLifecycleOwner) { contacts ->
            if (!isDataBound) { // 仅在数据未绑定时执行
                contacts.find { it.id == contactId }?.let { contact ->
                    Log.d("QqProfileEdit", "绑定联系人数据: ${contact.id}, 性别: ${contact.gender}, 年龄: ${contact.age}")
                    this.currentContact = contact // 保存初始数据
                    bindContactData(contact)
                    isDataBound = true // 标记为已绑定
                }
            }
        }
    }

    private fun bindContactData(contact: PersonProfile) {
        binding.genderEditText.setText(contact.gender)
        binding.ageEditText.setText(contact.age?.toString())
        binding.birthdayEditText.setText(contact.birthday)
        binding.zodiacSignEditText.setText(contact.zodiacSign)
        binding.locationEditText.setText(contact.location)
        binding.companySchoolEditText.setText(contact.companyOrSchool)
        binding.professionEditText.setText(contact.profession)
    }

    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener {
            saveDetails()
        }
    }

    private fun saveDetails() {
        Log.d("QqProfileEdit", "尝试保存个人资料...")
        // 从ViewModel中获取最新的联系人数据作为更新基础
        // 使用保存在Fragment成员变量中的、进入页面时的完整数据作为基础
        val baseContact = currentContact
        if (baseContact == null) {
            Log.e("QqProfileEdit", "保存失败：无法获取当前联系人的基础数据。")
            Toast.makeText(context, "保存失败：联系人数据异常", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedContact = baseContact.copy(
            gender = binding.genderEditText.text.toString().takeIf { it.isNotBlank() },
            age = binding.ageEditText.text.toString().toIntOrNull(),
            birthday = binding.birthdayEditText.text.toString().takeIf { it.isNotBlank() },
            zodiacSign = binding.zodiacSignEditText.text.toString().takeIf { it.isNotBlank() },
            location = binding.locationEditText.text.toString().takeIf { it.isNotBlank() },
            companyOrSchool = binding.companySchoolEditText.text.toString().takeIf { it.isNotBlank() },
            profession = binding.professionEditText.text.toString().takeIf { it.isNotBlank() }
        )
        Log.d("QqProfileEdit", "更新后的联系人数据: 性别: ${updatedContact.gender}, 年龄: ${updatedContact.age}")
        contactManager.updateContact(updatedContact)
        Toast.makeText(context, "个人资料已保存", Toast.LENGTH_SHORT).show()
        Log.d("QqProfileEdit", "个人资料已保存，返回上一页。")
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): QqAiProfileEditDetailsFragment {
            return QqAiProfileEditDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}