package com.susking.ephone_s.cphone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * CPhone第一层界面：AI联系人选择器
 * 显示所有AI联系人，用户选择要查看其手机的AI角色
 */
@AndroidEntryPoint
class CPhoneMainFragment : Fragment() {

    private var _binding: FragmentCphoneMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CPhoneViewModel by activityViewModels()
    private lateinit var adapter: CPhoneAiContactAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneAiContactAdapter { contact ->
            onAiContactClick(contact)
        }

        binding.rvAiContacts.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@CPhoneMainFragment.adapter
        }
    }

    /**
     * 观察ViewModel数据
     */
    private fun observeViewModel() {
        viewModel.aiContacts.observe(viewLifecycleOwner) { contacts ->
            // 过滤出AI联系人（非群组）
            val aiContacts = contacts.filter { !it.isGroupChat }
            
            if (aiContacts.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvAiContacts.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvAiContacts.visibility = View.VISIBLE
                adapter.submitList(aiContacts)
            }
        }
    }

    /**
     * 处理AI联系人点击事件
     */
    private fun onAiContactClick(contact: PersonProfile) {
        // 跳转到第二层：AI手机桌面
        val fragment = CPhoneDesktopFragment.newInstance(contact.id, contact.remarkName)
        // 获取当前Fragment所在的容器ID
        val containerId = (view?.parent as? ViewGroup)?.id ?: android.R.id.content
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CPhoneMainFragment()
    }
}