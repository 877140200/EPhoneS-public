package com.susking.ephone_s.features.worldbook.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.susking.ephone_s.R
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.EPhoneSApplication
import com.susking.ephone_s.aidata.data.repository.WorldBookRepositoryImpl
import com.susking.ephone_s.databinding.FragmentWorldBookBinding
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.features.worldbook.ui.dialog.CreateWorldBookDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorldBookFragment : Fragment(), CreateWorldBookDialogFragment.CreateWorldBookListener {

    private var _binding: FragmentWorldBookBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorldBookViewModel by activityViewModels {
        val aiDataDb = EPhoneSApplication.db
        // 修改点：在这里实例化具体的实现类 WorldBookRepositoryImpl
        val repository: WorldBookRepository = WorldBookRepositoryImpl(aiDataDb.worldBookDao())
        // 将 repository (作为接口类型) 传递给 Factory
        WorldBookViewModelFactory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorldBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.paddingBottom)
            insets
        }

        // 拦截系统返回事件
        val callback = object : OnBackPressedCallback(childFragmentManager.backStackEntryCount > 0) {
            override fun handleOnBackPressed() {
                // 当此回调被启用时（即详情页存在于返回栈中），处理返回事件
                childFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        // 监听子返回栈的变化，并动态更新回调的启用状态
        childFragmentManager.addOnBackStackChangedListener {
            callback.isEnabled = childFragmentManager.backStackEntryCount > 0
        }

        setupToolbar()
        observeViewModelEvents()

        // 默认显示世界书列表 Fragment
        if (savedInstanceState == null) {
            switchFragment(WorldBookListFragment.newInstance())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = "世界书集"

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_create_world_book -> {
                    // 显示创建世界书的对话框
                    CreateWorldBookDialogFragment().show(childFragmentManager, CreateWorldBookDialogFragment.Companion.TAG)
                    true
                }
                R.id.action_export_world_books -> {
                    viewModel.exportWorldBook(WorldBookEntity(title = "Example", category = "Content", createdAt = 0, updatedAt = 0)) // 示例调用
                    true
                }
                R.id.action_import_world_book -> {
                    viewModel.importWorldBook("some data") // 示例调用
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModelEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collectLatest { event ->
                    when (event) {
                        is WorldBookEvent.ShowMessage -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.world_book_fragment_container, fragment) // 假设 fragment_world_book.xml 中有这个容器
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    override fun onWorldBookCreated(title: String, category: String) {
        viewModel.createWorldBook(title, category)
    }

    override fun onWorldBookUpdated(worldBookId: Long, newTitle: String, newCategory: String) {
        // 当编辑对话框关闭并保存时，会调用此方法
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getWorldBookById(worldBookId)?.let { existingWorldBook ->
                viewModel.updateWorldBook(existingWorldBook, newTitle, newCategory)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): WorldBookFragment {
            return WorldBookFragment()
        }
    }
}