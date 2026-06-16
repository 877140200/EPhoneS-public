package com.susking.ephone_s.cphone.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneDiaryDetailBinding
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone日记详情页
 * 显示完整的日记内容(支持Markdown)
 */
class CPhoneDiaryDetailFragment : Fragment() {

    private var _binding: FragmentCphoneDiaryDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var diary: DiaryEntry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            diary = DiaryEntry(
                id = it.getString(ARG_DIARY_ID) ?: "",
                title = it.getString(ARG_DIARY_TITLE) ?: "",
                content = it.getString(ARG_DIARY_CONTENT) ?: "",
                isFavorite = it.getBoolean(ARG_DIARY_IS_FAVORITE, false),
                timestamp = it.getLong(ARG_DIARY_CREATED_AT, 0L)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneDiaryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        displayDiary()
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
            title = diary.title
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
        binding.root.findViewById<View>(R.id.btn_refresh)?.visibility = View.GONE
    }

    /**
     * 显示日记内容
     */
    private fun displayDiary() {
        binding.apply {
            // 设置标题
            tvTitle.text = diary.title
            
            // 设置日期
            tvDate.text = formatDate(diary.timestamp)
            
            // 设置内容 - 使用日记专属渲染器，正确显示 !h{}/!u{}/||涂黑|| 等专属语法
            DiaryMarkdownRenderer.render(tvContent, diary.content)
        }
    }

    /**
     * 格式化日期
     */
    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DIARY_ID = "diary_id"
        private const val ARG_DIARY_TITLE = "diary_title"
        private const val ARG_DIARY_CONTENT = "diary_content"
        private const val ARG_DIARY_IS_FAVORITE = "diary_is_favorite"
        private const val ARG_DIARY_CREATED_AT = "diary_created_at"

        fun newInstance(diary: DiaryEntry) = CPhoneDiaryDetailFragment().apply {
            arguments = bundleOf(
                ARG_DIARY_ID to diary.id,
                ARG_DIARY_TITLE to diary.title,
                ARG_DIARY_CONTENT to diary.content,
                ARG_DIARY_IS_FAVORITE to diary.isFavorite,
                ARG_DIARY_CREATED_AT to diary.timestamp
            )
        }
    }
}