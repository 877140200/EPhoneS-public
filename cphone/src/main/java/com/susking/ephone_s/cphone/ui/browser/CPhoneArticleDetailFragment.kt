package com.susking.ephone_s.cphone.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneArticleDetailBinding
import com.susking.ephone_s.aidata.domain.model.BrowserRecord

/**
 * CPhone浏览器文章详情页
 * 显示文章完整内容
 */
class CPhoneArticleDetailFragment : Fragment() {

    private var _binding: FragmentCphoneArticleDetailBinding? = null
    private val binding get() = _binding!!

    private var history: BrowserRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从父Fragment获取历史记录
    }

    /**
     * 设置浏览历史(由父Fragment调用)
     */
    fun setHistory(browserHistory: BrowserRecord) {
        this.history = browserHistory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneArticleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        displayArticle()
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
            title = history?.title ?: "文章详情"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 隐藏刷新按钮
            menu.findItem(R.id.action_refresh)?.isVisible = false
        }
    }

    /**
     * 显示文章内容
     */
    private fun displayArticle() {
        history?.let { article ->
            binding.apply {
                // 设置标题
                tvTitle.text = article.title
                
                // 设置URL
                tvUrl.text = article.url
                
                // 设置内容
                tvContent.text = article.content
                
                // 设置收藏按钮
                btnFavorite.apply {
                    icon = androidx.core.content.ContextCompat.getDrawable(
                        context,
                        if (article.isFavorite) {
                            R.drawable.ic_star_full
                        } else {
                            R.drawable.ic_star_outline
                        }
                    )
                    
                    text = if (article.isFavorite) "已收藏" else "收藏"
                    
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
        history?.let { article ->
            val newHistory = article.copy(isFavorite = !article.isFavorite)
            history = newHistory
            
            // 更新UI
            binding.btnFavorite.apply {
                icon = androidx.core.content.ContextCompat.getDrawable(
                    context,
                    if (newHistory.isFavorite) {
                        R.drawable.ic_star_full
                    } else {
                        R.drawable.ic_star_outline
                    }
                )
                text = if (newHistory.isFavorite) "已收藏" else "收藏"
            }
            
            // TODO: 更新数据库中的收藏状态
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(history: BrowserRecord) = CPhoneArticleDetailFragment().apply {
            setHistory(history)
        }
    }
}