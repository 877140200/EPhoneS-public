package com.susking.ephone_s.qq.ui.contactList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.susking.ephone_s.qq.databinding.FragmentQqContactListBinding
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.SideBarView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QqContactListFragment : Fragment() {

     private var _binding: FragmentQqContactListBinding? = null
     private val binding get() = _binding!!

     private lateinit var itemTouchHelper: QqContactGroupItemTouchHelper

     // 使用 Hilt 注入 ViewModel
     private val viewModel: QqViewModel by activityViewModels()
     
     // 注入 Manager
     @Inject lateinit var qqContactManager: QqContactManager

     override fun onResume() {
         super.onResume()
     }

     override fun onCreateView(
         inflater: LayoutInflater, container: ViewGroup?,
         savedInstanceState: Bundle?
     ): View {
         _binding = FragmentQqContactListBinding.inflate(inflater, container, false)
         return binding.root
     }

     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
         super.onViewCreated(view, savedInstanceState)

         setupTabs()
         setupRecyclerView()
         setupSideBar()

         // 先设置观察者
         qqContactManager.contactListItems.observe(viewLifecycleOwner) { items ->
             (binding.contactRecyclerView.adapter as QqContactGroupAdapter).submitList(items.mapNotNull { it as? ContactListItem }) {
                 // 列表更新完成后，滚动到顶部，以避免因项目重排产生的动画效果
                 binding.contactRecyclerView.scrollToPosition(0)
             }
         }

         // 设置adapter的footer显示
         (binding.contactRecyclerView.adapter as? QqContactGroupAdapter)?.setShowFooter(true)
         
         // 延迟加载，确保观察者已经设置完成，并且数据已经准备好
         binding.root.post {
             qqContactManager.loadGroupedContacts()
         }
     }

     private fun setupTabs() {
         binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
             override fun onTabSelected(tab: TabLayout.Tab?) {
                 when (tab?.position) {
                     0 -> {
                         qqContactManager.loadGroupedContacts()
                         binding.sideBarView.visibility = View.GONE
                         (binding.contactRecyclerView.adapter as? QqContactGroupAdapter)?.setShowFooter(true)
                     }
                     1 -> {
                         qqContactManager.loadAllFriends()
                         binding.sideBarView.visibility = View.VISIBLE
                         (binding.contactRecyclerView.adapter as? QqContactGroupAdapter)?.setShowFooter(false)
                     }
                     2 -> {
                         qqContactManager.loadGroupedChats()
                         binding.sideBarView.visibility = View.GONE
                         (binding.contactRecyclerView.adapter as? QqContactGroupAdapter)?.setShowFooter(false)
                     }
                 }
             }
             override fun onTabUnselected(tab: TabLayout.Tab?) {}
             override fun onTabReselected(tab: TabLayout.Tab?) {}
         })
     }

     private fun setupRecyclerView() {
        val contactAdapter = QqContactGroupAdapter(
            onContactClicked = { contact ->
                // 如果有任何项是打开的，先关闭它
                if (itemTouchHelper.isAnyItemOpen()) {
                    itemTouchHelper.closeOpenItem()
                } else {
                    viewModel.navigateToChat(contact)
                }
            },
            onHeaderClicked = { header ->
                itemTouchHelper.closeOpenItem()
                val currentTabPosition = binding.tabLayout.selectedTabPosition
                qqContactManager.toggleGroupExpansion(header.title, currentTabPosition)
            },
            onFooterClicked = {
                ManageGroupsDialogFragment().show(parentFragmentManager, "ManageGroupsDialog")
            }
        )

        binding.contactRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contactAdapter
            itemAnimator = null // 禁用动画
        }

        itemTouchHelper = QqContactGroupItemTouchHelper(
            requireContext(),
            contactAdapter,
            onPinClicked = { qqContactManager.pinContact(it.id) },
            onDeleteClicked = { qqContactManager.deleteContact(it.id) }
        ).also {
            ItemTouchHelper(it).attachToRecyclerView(binding.contactRecyclerView)
        }

        // 恢复简单的触摸监听器逻辑
        binding.contactRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (itemTouchHelper.isAnyItemOpen() && itemTouchHelper.onTouch(e)) {
                    return true // 按钮被点击，拦截事件
                }

                // 点击外部关闭项目
                if (itemTouchHelper.isAnyItemOpen() && e.action == MotionEvent.ACTION_UP) {
                    val childView = rv.findChildViewUnder(e.x, e.y)
                    if (childView == null) {
                        itemTouchHelper.closeOpenItem()
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }


     private fun setupSideBar() {
         binding.sideBarView.setTextView(binding.dialog)
        binding.sideBarView.setOnTouchingLetterChangedListener(object : SideBarView.OnTouchingLetterChangedListener {
            override fun onTouchingLetterChanged(s: String) {
                val adapter = binding.contactRecyclerView.adapter as QqContactGroupAdapter
                val position = adapter.getPositionForSection(s[0])
                if (position != -1) {
                    (binding.contactRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}