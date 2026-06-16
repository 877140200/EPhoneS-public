package com.susking.ephone_s.brain.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.brain.databinding.BrainItemAutoPlanBinding

/**
 * 自动计划列表适配器。
 *
 * 列表复用大脑日志窗口的 RecyclerView，只展示当前已开启或需要提示的自动任务。
 */
class AutoPlanAdapter : ListAdapter<AutoPlanItem, AutoPlanAdapter.AutoPlanViewHolder>(AutoPlanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutoPlanViewHolder {
        val binding: BrainItemAutoPlanBinding = BrainItemAutoPlanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AutoPlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AutoPlanViewHolder, position: Int): Unit {
        holder.bind(getItem(position))
    }

    class AutoPlanViewHolder(
        private val binding: BrainItemAutoPlanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AutoPlanItem): Unit {
            binding.textViewPlanTitle.text = item.title
            binding.textViewPlanDescription.text = item.description
            binding.textViewPlanType.text = item.type.displayName
            binding.textViewPlanType.alpha = if (item.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        }
    }

    class AutoPlanDiffCallback : DiffUtil.ItemCallback<AutoPlanItem>() {
        override fun areItemsTheSame(oldItem: AutoPlanItem, newItem: AutoPlanItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AutoPlanItem, newItem: AutoPlanItem): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        private const val ENABLED_ALPHA: Float = 1.0f
        private const val DISABLED_ALPHA: Float = 0.6f
    }
}
