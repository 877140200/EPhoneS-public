package com.susking.ephone_s.qq

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.susking.ephone_s.core.api.QqFragmentProvider
import com.susking.ephone_s.qq.ui.chat.QqChatFragment
import com.susking.ephone_s.qq.ui.QqMainFragment
import com.susking.ephone_s.qq.ui.chat.profile.QqAiProfileFragment
import com.susking.ephone_s.qq.ui.chat.profile.QqSpaceFragment
import com.susking.ephone_s.qq.ui.forward.ForwardCallback
import com.susking.ephone_s.qq.ui.forward.ForwardSelectorFragment

/**
 * QQ Fragment 提供者实现
 *
 * 提供QQ模块的所有Fragment实例
 * 实现依赖倒置,由app模块提供具体实现
 */
class QqFragmentProviderImpl : QqFragmentProvider {
    
    override fun getQqMainFragment(): Fragment {
        return QqMainFragment()
    }
    
    override fun getQqChatFragment(contactId: String): Fragment {
        return QqChatFragment().apply {
            arguments = bundleOf("contactId" to contactId)
        }
    }
    
    override fun getContactProfileFragment(contactId: String): Fragment {
        return QqAiProfileFragment().apply {
            arguments = bundleOf("contactId" to contactId)
        }
    }
    
    override fun getQqSpaceFragment(): Fragment {
        return QqSpaceFragment()
    }
    
    override fun getForwardSelectorFragment(
        contentType: String?,
        contentId: String?,
        onContactsSelected: (List<String>, String?, String?) -> Unit,
        onCancelled: (() -> Unit)?
    ): Fragment {
        return ForwardSelectorFragment.newInstance(
            contentType = contentType,
            contentId = contentId,
            callback = object : ForwardCallback {
                override fun onContactsSelected(
                    contactIds: List<String>,
                    contentType: String?,
                    contentId: String?
                ) {
                    onContactsSelected(contactIds, contentType, contentId)
                }
                
                override fun onCancelled() {
                    onCancelled?.invoke()
                }
            }
        )
    }
}