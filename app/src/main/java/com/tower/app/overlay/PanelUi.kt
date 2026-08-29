package com.tower.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.tower.app.Fn
import com.tower.app.Functions
import com.tower.app.R
import com.tower.app.Section
import kotlin.math.roundToInt

/**
 * Панель из 30 функций, разбитая на 6 секций.
 * Строится программно: окно добавляется через WindowManager, а не через Activity.
 */
class PanelUi(
    private val ctx: Context,
    private val onFn: (Fn) -> Unit,
    private val onSpeed: (Float) -> Unit,
    private val onVolume: (Int) -> Unit,
    private val onSleep: (Int) -> Unit,
    private val onMark: (String) -> Unit,
    private val onMarkDelete: (String) -> Unit,
    private val onMarkSave: () -> Unit
) {

    val view: View

    private val buttons = LinkedHashMap<String, TextView>()
    private lateinit var speedText: TextView
    private lateinit var speedBar: SeekBar
    private lateinit var volumeRow: LinearLayout
    private lateinit var volumeBar: SeekBar
    private lateinit var sleepRow: LinearLayout
    private lateinit var marksRow: LinearLayout

    private val accent = Color.parseColor("#7C5CFF")
    private val dim = Color.parseColor("#9AA2B8")
    private val text = Color.parseColor("#EAECF4")

    init {
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val host = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        scroll.addView(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        host.addView(buildMarksRow())

        for ((section, fns) in Functions.grouped) {
            host.addView(sectionHeader(section.title, section.hint))
            for (row in fns.chunked(3)) host.addView(buildRow(row))
            when (section) {
                Section.SPEED -> host.addView(buildSpeedRow())
                Section.AUDIO -> host.addView(buildVolumeRow())
                Section.SCREEN -> host.addView(buildSleepRow())
                else -> Unit
            }
        }
        view = scroll
    }

    // ── секции и кнопки ──────────────────────────────────────────────────

    private fun sectionHeader(title: String, hint: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(10), dp(4), dp(4))
        addView(TextView(ctx).apply {
            text = title
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        })
        addView(TextView(ctx).apply {
            text = hint
            setTextColor(dim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
        })
    }

    private fun buildRow(fns: List<Fn>) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        for (f in fns) addView(functionButton(f), lp)
    }

    private fun functionButton(f: Fn): TextView {
        val tv = TextView(ctx).apply {
            text = if (f.big) f.icon else f.icon + "\n" + f.title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (f.big) 17f else 10.5f)
            if (f.big) {
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                minHeight = dp(46)
            } else {
                minHeight = dp(56)
                setLineSpacing(0f, 1.05f)
                setPadding(dp(2), dp(6), dp(2), dp(6))
                includeFontPadding = false
            }
            setBackgroundResource(R.drawable.bg_button)
            setTextColor(text)
            isClickable = true
            isFocusable = true
            contentDescription = f.title
            setOnClickListener { onFn(f) }
            setOnLongClickListener { toast(f.desc); true }
        }
        buttons[f.id] = tv
        return tv
    }

    private fun buildSpeedRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(2))
        speedText = TextView(ctx).apply {
            text = "Своя скорость: 1.00X"
            setTextColor(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        addView(speedText)
        speedBar = SeekBar(ctx).apply {
            max = 160
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                    val s = (v / 10f).coerceIn(0.25f, 16f)
                    speedText.text = "Своя скорость: %.2fX".format(s)
                    if (fromUser) onSpeed(s)
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        addView(speedBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))
    }

    private fun buildVolumeRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(2), dp(6), dp(6))
        visibility = View.GONE
        volumeBar = SeekBar(ctx).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                    if (fromUser) onVolume(v)
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        addView(volumeBar)
        volumeRow = this
    }

    private fun buildSleepRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        setPadding(dp(2), dp(2), dp(2), dp(6))
        for (m in listOf(15, 30, 45, 60, 90, 0)) {
            val lp = LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
            addView(TextView(ctx).apply {
                text = if (m == 0) "Выкл" else "$m мин"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(text)
                setBackgroundResource(R.drawable.bg_button)
                setOnClickListener { onSleep(m) }
            }, lp)
        }
        sleepRow = this
    }

    private fun buildMarksRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(2), dp(6), dp(2), dp(2))
        marksRow = this
    }

    // ── состояние ────────────────────────────────────────────────────────

    fun refresh(isOn: (String) -> Boolean) {
        for ((id, tv) in buttons) {
            val f = Functions.find(id) ?: continue
            val on = isOn(id)
            tv.setBackgroundResource(
                when {
                    f.id == "vd_rec" && on -> R.drawable.bg_danger
                    on -> R.drawable.bg_button_on
                    else -> R.drawable.bg_button
                }
            )
            tv.setTextColor(if (on) Color.parseColor("#0B0D13") else text)
        }
    }

    fun setSpeedLabel(v: Float) {
        speedText.text = "Своя скорость: %.2fX".format(v)
        speedBar.progress = (v * 10).roundToInt().coerceIn(25, 160)
    }

    fun toggleVolumeRow(show: Boolean, max: Int, current: Int) {
        volumeRow.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            volumeBar.max = max
            volumeBar.progress = current
        }
    }

    fun toggleSleepRow(show: Boolean) {
        sleepRow.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun renderMarks(show: Boolean, items: List<Pair<String, String>>) {
        marksRow.visibility = if (show) View.VISIBLE else View.GONE
        marksRow.removeAllViews()
        if (!show) return

        marksRow.addView(TextView(ctx).apply {
            text = "★ Сохранить текущую страницу"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#0B0D13"))
            setBackgroundResource(R.drawable.bg_button_on)
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { onMarkSave() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(2), dp(2), dp(2), dp(6))
        })

        if (items.isEmpty()) {
            marksRow.addView(TextView(ctx).apply {
                text = "Закладок пока нет"
                setTextColor(dim)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
            return
        }

        for ((title, url) in items) {
            marksRow.addView(TextView(ctx).apply {
                text = "• " + title.ifBlank { url }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(text)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundResource(R.drawable.bg_button)
                setOnClickListener { onMark(url) }
                setOnLongClickListener { onMarkDelete(url); true }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).roundToInt()
}
