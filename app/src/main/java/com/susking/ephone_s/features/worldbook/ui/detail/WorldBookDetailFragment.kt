package com.susking.ephone_s.features.worldbook.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.appbar.MaterialToolbar
import com.susking.ephone_s.R
import com.susking.ephone_s.EPhoneSApplication
import com.susking.ephone_s.aidata.data.repository.WorldBookEntryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookRepositoryImpl
import com.susking.ephone_s.databinding.FragmentWorldBookDetailBinding
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.features.worldbook.ui.dialog.CreateWorldBookEntryDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorldBookDetailFragment : Fragment(), CreateWorldBookEntryDialogFragment.EntryInteractionListener {

    private var _binding: FragmentWorldBookDetailBinding? = null
    private val binding get() = _binding!!

    private val worldBookId by lazy { requireArguments().getLong(ARG_WORLD_BOOK_ID) }
    private val worldBookTitle by lazy { requireArguments().getString(ARG_WORLD_BOOK_TITLE, "") }

    private val viewModel: WorldBookDetailViewModel by viewModels {
        val aiDataDb = EPhoneSApplication.db
        // 实例化 Repository
        val entryRepository: WorldBookEntryRepository = WorldBookEntryRepositoryImpl(aiDataDb.worldBookEntryDao())
        val worldBookRepository: WorldBookRepository = WorldBookRepositoryImpl(aiDataDb.worldBookDao())
        // 将 repository 传递给 Factory
        WorldBookDetailViewModelFactory(entryRepository, worldBookRepository, worldBookId)
    }

    private lateinit var entryAdapter: WorldBookEntryAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorldBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupToolbar() {
        // 查找并控制父Fragment的Toolbar
        val mainToolbar = activity?.findViewById<MaterialToolbar>(R.id.toolbar)
        mainToolbar?.apply {
            title = worldBookTitle
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun setupRecyclerView() {
        entryAdapter = WorldBookEntryAdapter(
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            onListReordered = { reorderedList -> viewModel.updateEntryOrder(reorderedList) },
            onEntryChanged = { updatedEntry -> viewModel.updateEntry(updatedEntry) },
            onEditClicked = { entryToEdit ->
                CreateWorldBookEntryDialogFragment.newInstanceForEdit(
                    entryToEdit.entryId,
                    entryToEdit.name,
                    entryToEdit.content,
                    entryToEdit.lampColor
                ).show(childFragmentManager, CreateWorldBookEntryDialogFragment.TAG)
            }
        )
        binding.entryRecyclerView.adapter = entryAdapter

        val callback = ItemMoveCallback(entryAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.entryRecyclerView)
    }

    private fun setupFab() {
        binding.fabAddEntry.setOnClickListener {
            CreateWorldBookEntryDialogFragment.newInstanceForCreate().show(childFragmentManager, CreateWorldBookEntryDialogFragment.TAG)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.entries.collectLatest { entries ->
                        entryAdapter.submitList(entries)
                    }
                }
                launch {
                    viewModel.isSystemBook.collectLatest { isSystem ->
                        binding.fabAddEntry.visibility = if (isSystem) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.eventFlow.collectLatest { event ->
                        when (event) {
                            is DetailEvent.ShowMessage -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onEntryCreated(name: String, content: String) {
        viewModel.addEntry(name, content)
    }

    override fun onEntryUpdated(entryId: Long, newName: String, newContent: String, newLampColor: String) {
        viewModel.updateEntry(entryId, newName, newContent, newLampColor)
    }

    override fun onEntryDeleted(entryId: Long) {
        viewModel.deleteEntryById(entryId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 当Fragment销毁时，恢复父Toolbar的原始状态
        val mainToolbar = activity?.findViewById<MaterialToolbar>(R.id.toolbar)
        mainToolbar?.apply {
            title = getString(R.string.world_book_feature_name)
            navigationIcon = null
            setNavigationOnClickListener(null)
        }
        _binding = null
    }

    companion object {
        private const val ARG_WORLD_BOOK_ID = "world_book_id"
        private const val ARG_WORLD_BOOK_TITLE = "world_book_title"

        fun newInstance(worldBookId: Long, worldBookTitle: String): WorldBookDetailFragment {
            return WorldBookDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_WORLD_BOOK_ID, worldBookId)
                    putString(ARG_WORLD_BOOK_TITLE, worldBookTitle)
                }
            }
        }
    }
}