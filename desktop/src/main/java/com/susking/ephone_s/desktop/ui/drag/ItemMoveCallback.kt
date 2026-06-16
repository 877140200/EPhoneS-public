package com.susking.ephone_s.desktop.ui.drag

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ItemMoveCallback(private val adapter: ItemTouchHelperContract) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {}

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
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
            viewHolder?.itemView?.alpha = 0.5f
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
        adapter.onRowClear()
    }

    interface ItemTouchHelperContract {
        fun onRowMoved(fromPosition: Int, toPosition: Int)
        fun onRowClear()
    }
}