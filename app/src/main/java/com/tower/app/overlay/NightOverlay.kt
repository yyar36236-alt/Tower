package com.tower.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.tower.app.service.TowerAccessibilityService

/** Тёплый фильтр поверх всего экрана (системный слой, касания проходят сквозь него). */
object NightOverlay {

    private var view: View? = null
    private var wm: WindowManager? = null

    fun show(c: Context, alphaPercent: Int) {
        val w = wm ?: (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { wm = it }
        val a = (255 * alphaPercent / 100f).toInt().coerceIn(10, 220)
        val v = view ?: View(c).also { view = it }
        v.setBackgroundColor(Color.argb(a, 255, 122, 20))

        val type = if (TowerAccessibilityService.connected)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            if (v.parent == null) w.addView(v, p) else w.updateViewLayout(v, p)
        } catch (t: Throwable) {
            try {
                p.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                if (v.parent == null) w.addView(v, p) else w.updateViewLayout(v, p)
            } catch (t2: Throwable) { }
        }
    }

    fun hide() {
        val v = view ?: return
        try { wm?.removeView(v) } catch (t: Throwable) { }
        view = null
    }

    fun isShown(): Boolean = view != null && Build.VERSION.SDK_INT > 0
}
