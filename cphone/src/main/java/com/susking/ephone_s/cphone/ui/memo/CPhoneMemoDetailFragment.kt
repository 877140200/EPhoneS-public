package com.susking.ephone_s.cphone.ui.memo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneMemoDetailBinding
import com.susking.ephone_s.aidata.domain.model.Memo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone备忘录详情页
 * 显示备忘录完整内容
 */
class CPhoneMemoDetailFragment : Fragment() {

    private var _binding: FragmentCphoneMemoDetailBinding? = null
    private val binding get() = _binding!!

    private var memo: Memo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从父Fragment获取备忘录
    }

    /**
     * 设置备忘录(由父Fragment调用)
     */
    fun setMemo(memoData: Memo) {
        this.memo = memoData
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneMemoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        displayMemo()
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        // 获取Toolbar
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.toolbar
        )
        
        toolbar?.apply {
            title = memo?.title ?: "备忘录详情"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 隐藏刷新按钮
            menu.findItem(R.id.action_refresh)?.isVisible = false
        }
    }

    /**
     * 显示备忘录内容
     */
    private fun displayMemo() {
        memo?.let { memoData ->
            binding.apply {
                // 设置标题
                tvTitle.text = memoData.title
                
                // 设置时间
                tvTime.text = formatTime(memoData.timestamp)
                
                // 设置内容
                tvContent.text = memoData.content
                
                // 设置收藏按钮
                btnFavorite.apply {
                    icon = androidx.core.content.ContextCompat.getDrawable(
                        context,
                        if (memoData.isFavorite) {
                            R.drawable.ic_star_full
                        } else {
                            R.drawable.ic_star_outline
                        }
                    )
                    
                    text = if (memoData.isFavorite) "已收藏" else "收藏"
                    
                    setOnClickListener {
                        toggleFavorite()
                    }
                }
            }
        }
    }

    /**
     * 切换收藏状态
     */
    private fun toggleFavorite() {
        memo?.let { memoData ->
            val newMemo = memoData.copy(isFavorite = !memoData.isFavorite)
            memo = newMemo
            
            // 更新UI
            binding.btnFavorite.apply {
                icon = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    if (newMemo.isFavorite) {
                        R.drawable.ic_star_full
                    } else {
                        R.drawable.ic_star_outline
                    }
                )
                text = if (newMemo.isFavorite) "已收藏" else "收藏"
            }
            
            // TODO: 更新数据库中的收藏状态
        }
    }

    /**
     * 格式化时间戳
     */
    private fun formatTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(memo: Memo) = CPhoneMemoDetailFragment().apply {
            setMemo(memo)
        }
    }
}