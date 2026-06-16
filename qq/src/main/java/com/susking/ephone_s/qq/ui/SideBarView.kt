package com.susking.ephone_s.qq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

class SideBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var onTouchingLetterChangedListener: OnTouchingLetterChangedListener? = null

    private val letters = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I",
        "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
        "W", "X", "Y", "Z", "#")
    private var choose = -1
    private val paint = Paint()

    private var mTextDialog: TextView? = null

    fun setTextView(mTextDialog: TextView?) {
        this.mTextDialog = mTextDialog
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val height = height
        val width = width
        val singleHeight = height / letters.size.toFloat()

        for (i in letters.indices) {
            paint.color = Color.GRAY // TODO: Use theme color later
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.isAntiAlias = true
            paint.textSize = 30f // Adjust size
            if (i == choose) {
                paint.color = Color.BLUE // TODO: Use theme color later
                paint.isFakeBoldText = true
            }
            val xPos = width / 2 - paint.measureText(letters[i]) / 2
            val yPos = singleHeight * i + singleHeight
            c.drawText(letters[i], xPos, yPos, paint)
            paint.reset()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        val y = event.y
        val oldChoose = choose
        val c = (y / height * letters.size).toInt()

        when (action) {
            MotionEvent.ACTION_UP -> {
                setBackgroundColor(Color.TRANSPARENT)
                choose = -1
                invalidate()
                mTextDialog?.visibility = GONE
            }
            else -> {
                if (oldChoose != c) {
                    if (c >= 0 && c < letters.size) {
                        onTouchingLetterChangedListener?.onTouchingLetterChanged(letters[c])
                        mTextDialog?.let {
                            it.text = letters[c]
                            it.visibility = VISIBLE
                        }
                        choose = c
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    fun setOnTouchingLetterChangedListener(
        listener: OnTouchingLetterChangedListener) {
        this.onTouchingLetterChangedListener = listener
    }

    interface OnTouchingLetterChangedListener {
        fun onTouchingLetterChanged(s: String)
    }
}