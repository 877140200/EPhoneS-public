package com.susking.ephone_s.qq.ui.memories

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.susking.ephone_s.qq.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QqMemoriesAdapter(
    private val onItemLongClick: (Memory) -> Unit,
    private val onDetailClick: (Memory.GeneralMemory) -> Unit
) : ListAdapter<Memory, RecyclerView.ViewHolder>(MemoryDiffCallback()) {

    private val handler = Handler(Looper.getMainLooper())
    private val countdownRunnables = mutableMapOf<TextView, Runnable>()

    companion object {
        private const val TYPE_GENERAL = 0
        private const val TYPE_APPOINTMENT = 1
        private const val VECTORIZED_COLOR: String = "#2E7D32"
        private const val UNVECTORIZED_COLOR: String = "#C62828"
        private const val LEGACY_MEMORY_COLOR: String = "#616161"
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Memory.GeneralMemory -> TYPE_GENERAL
            is Memory.Appointment -> TYPE_APPOINTMENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GENERAL -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
                GeneralMemoryViewHolder(view)
            }
            TYPE_APPOINTMENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
                AppointmentViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GeneralMemoryViewHolder -> holder.bind(getItem(position) as Memory.GeneralMemory)
            is AppointmentViewHolder -> holder.bind(getItem(position) as Memory.Appointment)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AppointmentViewHolder) {
            countdownRunnables[holder.countdownTextView]?.let { handler.removeCallbacks(it) }
            countdownRunnables.remove(holder.countdownTextView)
        }
    }

    inner class GeneralMemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTextView: TextView = itemView.findViewById(R.id.tv_memory_date)
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_memory_title)
        private val contentTextView: TextView = itemView.findViewById(R.id.tv_memory_content)
        private val detailButton: MaterialButton = itemView.findViewById(R.id.btn_memory_detail)

        fun bind(memory: Memory.GeneralMemory) {
            dateTextView.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(memory.date)
            titleTextView.text = memory.title
            contentTextView.text = memory.content
            detailButton.backgroundTintList = ColorStateList.valueOf(getVectorStateColor(memory))
            detailButton.text = "详情"
            detailButton.setOnClickListener { onDetailClick(memory) }
            itemView.setOnLongClickListener {
                onItemLongClick(memory)
                true
            }
        }

        private fun getVectorStateColor(memory: Memory.GeneralMemory): Int {
            if (!memory.isLongTermMemory) return Color.parseColor(LEGACY_MEMORY_COLOR)
            return if (memory.isVectorized) {
                Color.parseColor(VECTORIZED_COLOR)
            } else {
                Color.parseColor(UNVECTORIZED_COLOR)
            }
        }
    }

    inner class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_appointment_title)
        val countdownTextView: TextView = itemView.findViewById(R.id.tv_countdown)
        private val targetDateTextView: TextView = itemView.findViewById(R.id.tv_target_date)

        fun bind(appointment: Memory.Appointment) {
            titleTextView.text = appointment.title
            val date = Date(appointment.appointmentDate)
            targetDateTextView.text = "目标时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)}"
            updateCountdown(date)
            
            itemView.setOnLongClickListener {
                onItemLongClick(appointment)
                true
            }
        }

        private fun updateCountdown(targetDate: Date) {
            countdownRunnables[countdownTextView]?.let { handler.removeCallbacks(it) }

            val runnable = object : Runnable {
                override fun run() {
                    val diff = targetDate.time - System.currentTimeMillis()
                    if (diff > 0) {
                        val days = diff / (1000 * 60 * 60 * 24)
                        val hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                        val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
                        val seconds = (diff % (1000 * 60)) / 1000
                        countdownTextView.text = String.format("%d天 %02d:%02d:%02d", days, hours, minutes, seconds)
                        handler.postDelayed(this, 1000)
                    } else {
                        countdownTextView.text = "约定已到期"
                    }
                }
            }
            countdownRunnables[countdownTextView] = runnable
            handler.post(runnable)
        }
    }
}

class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
    override fun areItemsTheSame(oldItem: Memory, newItem: Memory): Boolean {
        return when {
            oldItem is Memory.GeneralMemory && newItem is Memory.GeneralMemory -> oldItem.id == newItem.id
            oldItem is Memory.Appointment && newItem is Memory.Appointment -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: Memory, newItem: Memory): Boolean {
        return oldItem == newItem
    }
}