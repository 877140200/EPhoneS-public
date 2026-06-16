package com.susking.ephone_s.qq.ui.contactList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.databinding.DialogManageGroupsBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ManageGroupsDialogFragment : DialogFragment() {

    private var _binding: DialogManageGroupsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 GroupManager
    @Inject lateinit var groupManager: QqContactManager
    private lateinit var adapter: GroupManagementAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogManageGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        // 设置对话框宽度
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupRecyclerView() {
        adapter = GroupManagementAdapter(
            onDeleteClicked = { groupName ->
                showDeleteConfirmationDialog(groupName)
            },
            onOrderChanged = { newOrder ->
                groupManager.updateGroupOrder(newOrder)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        )
        binding.groupsRecyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.groupsRecyclerView)
    }

    private fun setupClickListeners() {
        binding.addGroupButton.setOnClickListener {
            val newGroupName = binding.newGroupNameEditText.text.toString().trim()
            if (newGroupName.isNotEmpty()) {
                groupManager.addGroup(newGroupName)
                binding.newGroupNameEditText.text.clear()
            } else {
                Toast.makeText(requireContext(), "分组名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }

        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    private fun observeViewModel() {
        groupManager.allGroupNames.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
        }
    }

    private fun showDeleteConfirmationDialog(groupName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除分组")
            .setMessage("确定要删除分组 “$groupName” 吗？\n该分组下的所有联系人都将移至“我的好友”。")
            .setPositiveButton("确定") { _, _ ->
                groupManager.deleteGroup(groupName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}