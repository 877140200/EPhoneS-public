package com.susking.ephone_s.brain

import android.content.Context
import com.susking.ephone_s.R
import com.susking.ephone_s.brain.ui.AiActivityAdapter

/**
 * AiActivityAdapter.ColorProvider的实现。
 */
class BrainColorProvider(private val context: Context) : AiActivityAdapter.ColorProvider {
    
    override fun getUnreadColor(): Int = context.getColor(R.color.red_500)
    
    override fun getFailedColor(): Int = context.getColor(R.color.red_500)
    
    override fun getSuccessColor(): Int = context.getColor(R.color.material_green_500)
    
    override fun getDefaultColor(): Int = context.getColor(android.R.color.darker_gray)
}