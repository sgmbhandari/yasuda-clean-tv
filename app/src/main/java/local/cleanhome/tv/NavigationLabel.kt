package local.cleanhome.tv

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.widget.TextView

class NavigationLabel(context: Context) : TextView(context) {
    override fun onFocusChanged(focused: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(focused, direction, previous)
        paintFlags = if (focused) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        setTextColor(if (focused) TvDesign.foreground else TvDesign.secondary)
    }
}
