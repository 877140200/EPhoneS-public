package com.susking.ephone_s.features.worldbook.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.R
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.databinding.FragmentWorldBookListBinding
import com.susking.ephone_s.features.worldbook.ui.WorldBookAdapter
import com.susking.ephone_s.features.worldbook.ui.detail.ItemMoveCallback
import com.susking.ephone_s.features.worldbook.ui.detail.WorldBookDetailFragment
import com.susking.ephone_s.features.worldbook.ui.dialog.CreateWorldBookDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorldBookListFragment : Fragment() {

    private var _binding: FragmentWorldBookListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorldBookViewModel by activityViewModels() // 使用activityViewModels获取父Fragment的ViewModel
    private lateinit var adapter: WorldBookAdapter

    override fun onResume() {
        super.onResume()
        // 当列表页可见时，确保父Toolbar处于正确状态
        val mainToolbar = activity?.findViewById<MaterialToolbar>(R.id.toolbar)
        mainToolbar?.apply {
            title = getString(R.string.world_book_feature_name)
            navigationIcon = null
            setNavigationOnClickListener(null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorldBookListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCategoryBar()
        observeViewModel()
    }

    private fun setupCategoryBar() {
        binding.buttonAddCategory.setOnClickListener {
            // 点击“+”号可视为创建世界书的快捷方式，用户可在弹窗中指定新分类
            CreateWorldBookDialogFragment.Companion.newInstance()
                .show(parentFragmentManager, CreateWorldBookDialogFragment.Companion.TAG)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察分类列表的变化
                launch {
                    viewModel.categories.collectLatest { categories ->
                        updateCategoryChips(categories, viewModel.selectedCategory.value)
                    }
                }

                // 观察选中分类的变化，以实时更新UI
                launch {
                    viewModel.selectedCategory.collectLatest { selectedCategory ->
                        updateCategoryChips(viewModel.categories.value, selectedCategory)
                    }
                }

                // 观察筛选后的世界书列表
                launch {
                    viewModel.filteredWorldBooks.collectLatest { worldBooks ->
                        adapter.submitList(worldBooks)
                        binding.emptyListMessage.visibility = if (worldBooks.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateCategoryChips(categories: List<String>, selectedCategory: String?) {
        binding.categoryChipGroup.removeAllViews()

        // 添加“全部” Chip
        val allChip = createChip("全部", isChecked = selectedCategory == null)
        allChip.setOnClickListener { viewModel.selectCategory(null) }
        binding.categoryChipGroup.addView(allChip)

        // 为每个分类添加 Chip
        categories.forEach { category ->
            // 如果分类是我们的特殊标签，则显示为“无分类”
            val chipText = if (category == WorldBookViewModel.UNCATEGORIZED_TAG) "无分类" else category
            val categoryChip = createChip(chipText, isChecked = category == selectedCategory)
            categoryChip.setOnClickListener { viewModel.selectCategory(category) }
            binding.categoryChipGroup.addView(categoryChip)
        }
    }

    private fun createChip(text: String, isChecked: Boolean): Chip {
        return Chip(context).apply {
            this.text = text
            this.isCheckable = true
            this.isChecked = isChecked
            this.setEnsureMinTouchTargetSize(false) // 使Chip更紧凑
        }
    }

    private fun setupRecyclerView() {
        adapter = WorldBookAdapter(
            onItemClicked = { worldBook ->
                // 点击世界书卡片，导航到内容条目详情页
                navigateToDetailFragment(worldBook)
            },
            onItemLongClicked = { worldBook ->
                // 长按世界书卡片，弹出删除确认或其他选项
                showDeleteConfirmationDialog(worldBook)
                true // 返回true表示事件已被消费
            },
            onMoveItem = { fromPosition, toPosition ->
                viewModel.onWorldBookMoved(fromPosition, toPosition)
            }
        )
        binding.worldBookRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.worldBookRecyclerView.adapter = adapter

        // 将ItemTouchHelper附加到RecyclerView
        val callback = ItemMoveCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.worldBookRecyclerView)
    }

    private fun navigateToDetailFragment(worldBook: WorldBookEntity) {
        val detailFragment = WorldBookDetailFragment.newInstance(worldBook.worldBookId, worldBook.title)
        parentFragmentManager.beginTransaction()
            .add(R.id.world_book_fragment_container, detailFragment)
            .hide(this) // 隐藏当前列表，而不是替换它
            .addToBackStack(null) // 将此事务添加到返回栈
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    private fun showDeleteConfirmationDialog(worldBook: WorldBookEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除世界书")
            .setMessage("确定要删除 '${worldBook.title}' 吗？此操作不可撤销。")
            .setNegativeButton(R.string.button_cancel, null)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                viewModel.deleteWorldBook(worldBook)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): WorldBookListFragment {
            return WorldBookListFragment()
        }
    }
}