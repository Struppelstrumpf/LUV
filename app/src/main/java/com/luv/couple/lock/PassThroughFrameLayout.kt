package com.luv.couple.lock

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Overlay, das Touches standardmäßig durchlässt (zur DrawingView darunter).
 * Nur [View.isClickable]-Kinder (Emoji-Entwürfe) fangen Eingaben ab.
 */
class PassThroughFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var touchTarget: View? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTarget = null
                for (i in childCount - 1 downTo 0) {
                    val child = getChildAt(i)
                    if (!child.isShown || !child.isClickable) continue
                    if (!pointInChild(child, ev.x, ev.y)) continue
                    if (dispatchToChild(child, ev)) {
                        touchTarget = child
                        return true
                    }
                }
                return false
            }
            else -> {
                val target = touchTarget ?: return false
                val handled = dispatchToChild(target, ev)
                if (
                    ev.actionMasked == MotionEvent.ACTION_UP ||
                    ev.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    touchTarget = null
                }
                return handled
            }
        }
    }

    private fun pointInChild(child: View, x: Float, y: Float): Boolean {
        val left = child.x
        val top = child.y
        return x >= left && x < left + child.width &&
            y >= top && y < top + child.height
    }

    private fun dispatchToChild(child: View, ev: MotionEvent): Boolean {
        val transformed = MotionEvent.obtain(ev)
        transformed.offsetLocation(-child.x, -child.y)
        return try {
            child.dispatchTouchEvent(transformed)
        } finally {
            transformed.recycle()
        }
    }
}
