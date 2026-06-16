package com.susking.ephone_s.qq.ui.chat.profile

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.qq.databinding.DialogSelectWorldBookBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 世界书选择对话框
 * 允许用户从所有可用世界书中选择要绑定的世界书
 */
@AndroidEntryPoint
class SelectWorldBookDialogFragment : DialogFragment() {

    private var _binding: DialogSelectWorldBookBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var worldBookRepository: WorldBookRepository

    private lateinit var adapter: SelectableWorldBookAdapter
    private var allWorldBooks = listOf<WorldBookEntity>()
    private val selectedWorldBookIds = mutableSetOf<Long>()
    private var excludedIds = longArrayOf()
    
    private var onWorldBooksSelectedListener: ((List<Long>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        excludedIds = arguments?.getLongArray(ARG_EXCLUDED_IDS) ?: longArrayOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSelectWorldBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchBar()
        setupButtons()
        loadWorldBooks()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = SelectableWorldBookAdapter { worldBook, isSelected ->
            if (isSelected) {
                selectedWorldBookIds.add(worldBook.worldBookId)
            } else {
                selectedWorldBookIds.remove(worldBook.worldBookId)
            }
        }
        
        binding.worldBooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SelectWorldBookDialogFragment.adapter
        }
    }

    /**
     * 设置搜索栏
     */
    private fun setupSearchBar() {
        binding.searchEditText.doAfterTextChanged { text ->
            filterWorldBooks(text.toString())
        }
    }

    /**
     * 设置按钮
     */
    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        
        binding.confirmButton.setOnClickListener {
            onWorldBooksSelectedListener?.invoke(selectedWorldBookIds.toList())
            dismiss()
        }
    }

    /**
     * 加载世界书列表
     */
    private fun loadWorldBooks() {
        lifecycleScope.launch {
            allWorldBooks = worldBookRepository.getAllWorldBooks().first()
                .filter { it.worldBookId !in excludedIds }
            filterWorldBooks("")
        }
    }

    /**
     * 过滤世界书
     */
    private fun filterWorldBooks(query: String) {
        val filteredList = if (query.isBlank()) {
            allWorldBooks
        } else {
            allWorldBooks.filter { worldBook ->
                worldBook.title.contains(query, ignoreCase = true) ||
                worldBook.category.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filteredList)
    }

    /**
     * 设置世界书选择监听器
     */
    fun setOnWorldBooksSelectedListener(listener: (List<Long>) -> Unit) {
        onWorldBooksSelectedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EXCLUDED_IDS = "excluded_ids"

        fun newInstance(excludedIds: LongArray): SelectWorldBookDialogFragment {
            return SelectWorldBookDialogFragment().apply {
                arguments = Bundle().apply {
                    putLongArray(ARG_EXCLUDED_IDS, excludedIds)
                }
            }
        }
    }
}