package com.susking.ephone_s.qq.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.susking.ephone_s.qq.databinding.ItemMoreOptionBinding

class MoreOptionsAdapter(
    private val options: List<MoreOption>,
    private val onItemClick: (MoreOption) -> Unit
) : BaseAdapter() {

    private var itemHeight: Int = 0
    
    // 设置item高度
    fun setItemHeight(height: Int) {
        itemHeight = height
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return options.size
    }

    override fun getItem(position: Int): Any {
        return options[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding: ItemMoreOptionBinding
        val view: View

        if (convertView == null) {
            binding = ItemMoreOptionBinding.inflate(LayoutInflater.from(parent?.context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as ItemMoreOptionBinding
            view = convertView
        }

        // 设置item高度
        if (itemHeight > 0) {
            val layoutParams = view.layoutParams
            layoutParams.height = itemHeight
            view.layoutParams = layoutParams
        }

        val option = options[position]
        binding.optionIcon.setImageResource(option.iconResId)
        binding.optionText.text = option.text
        view.setOnClickListener {
            onItemClick(option)
        }

        return view
    }
}