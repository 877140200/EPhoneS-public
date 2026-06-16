package com.susking.ephone_s.shopping.ui.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.susking.ephone_s.shopping.databinding.FragmentMessageTabBinding

/**
 * 消息Tab Fragment
 * 
 * 显示"待开发"提示
 */
class MessageTabFragment : Fragment() {
    
    private var _binding: FragmentMessageTabBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageTabBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
    }
    
    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "消息"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = MessageTabFragment()
    }
}