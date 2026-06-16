package com.susking.ephone_s.core.ui.dialog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.core.R

class PromptSectionAdapter(
    private val sections: List<PromptSection>
) : RecyclerView.Adapter<PromptSectionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val section = sections[position]
        holder.bind(section)
    }

    override fun getItemCount(): Int = sections.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.section_title)
        private val contentView: TextView = itemView.findViewById(R.id.section_content)
        private val toggleIcon: ImageView = itemView.findViewById(R.id.section_toggle_icon)
        private val headerView: View = itemView.findViewById(R.id.section_header)

        fun bind(section: PromptSection) {
            titleView.text = section.title
            contentView.text = section.content

            updateVisibility(section)

            headerView.setOnClickListener {
                section.isExpanded = !section.isExpanded
                updateVisibility(section)
            }
        }

        private fun updateVisibility(section: PromptSection) {
            contentView.isVisible = section.isExpanded
            val iconRes = if (section.isExpanded) {
                R.drawable.ic_expand_less_24
            } else {
                R.drawable.ic_expand_more_24
            }
            toggleIcon.setImageResource(iconRes)
        }
    }
}