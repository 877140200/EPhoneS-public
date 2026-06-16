package com.susking.ephone_s.features.worldbook.ui.detail

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ItemMoveCallback(private val adapter: ItemTouchHelperContract) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled() = true
    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // 如果是系统条目，则不允许拖动
        if (adapter.isSystemEntry(viewHolder.bindingAdapterPosition)) {
            return 0 // 不允许任何拖动或滑动
        }
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.onRowMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            viewHolder?.let { adapter.onRowSelected(it) }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.onRowClear(viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    interface ItemTouchHelperContract {
        fun onRowMoved(fromPosition: Int, toPosition: Int)
        fun onRowSelected(myViewHolder: RecyclerView.ViewHolder)
        fun onRowClear(myViewHolder: RecyclerView.ViewHolder)
        fun isSystemEntry(position: Int): Boolean // 用于判断是否为系统条目
    }
}