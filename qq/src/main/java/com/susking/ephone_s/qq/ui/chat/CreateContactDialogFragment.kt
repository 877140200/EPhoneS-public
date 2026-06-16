package com.susking.ephone_s.qq.ui.chat

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.databinding.DialogCreateContactBinding


class CreateContactDialogFragment : DialogFragment() {

    // 用于将创建的角色传递回Fragment的回调接口
    interface CreateContactListener {
        fun onContactCreated(contact: PersonProfile)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val inflater = activity.layoutInflater
        // 使用ViewBinding来安全地访问视图
        val binding = DialogCreateContactBinding.inflate(inflater, null, false)

        return AlertDialog.Builder(activity, com.susking.ephone_s.core.R.style.Theme_EPhoneS_CustomDialog)
            .setView(binding.root)
            .setTitle("创建新角色")
            .setPositiveButton("创建") { _, _ ->
                val remarkName = binding.remarkNameEditText.text.toString()
                val realName = binding.realNameEditText.text.toString()

                // 确保输入不为空
                if (remarkName.isNotBlank() && realName.isNotBlank()) {
                    // 优先尝试从 parentFragment 获取监听器，如果不存在则从 activity 获取
                    val listener = (parentFragment as? CreateContactListener)
                        ?: (activity as? CreateContactListener)
                    val defaultPersona = "你正在扮演一个角色，你的名字是 $realName。请以 $realName 的身份和口吻进行回复。"
                    val contact = PersonProfile(remarkName = remarkName, realName = realName, persona = defaultPersona)
                    listener?.onContactCreated(contact)
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.cancel()
            }
            .create()
    }

    companion object {
        const val TAG = "CreateContactDialogFragment"
    }
}