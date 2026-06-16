package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class QqSpaceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val textView = TextView(requireContext()).apply {
            text = "这里是TA的QQ空间"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        }
        return textView
    }
}