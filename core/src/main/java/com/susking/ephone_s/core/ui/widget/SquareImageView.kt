package com.susking.ephone_s.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * 正方形ImageView，宽高相等
 */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}