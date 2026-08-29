package com.tower.app.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.tower.app.Prefs
import com.tower.app.R
import com.tower.app.overlay.FloatingService
import kotlin.math.roundToInt

/** Настройки панели. */
class SettingsActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        setContentView(build())
    }

    private fun build(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0E1016")) }
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(24))
        }
        scroll.addView(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        host.addView(title("TOWER · настройки"))

        host.addView(section("ОКНО"))
        host.addView(switchRow("Показывать системным слоем", Prefs.useA11yLayer,
            "Использовать слой Accessibility, если доступ включён") { Prefs.useA11yLayer = it })
        host.addView(switchRow("Воскрешать панель", Prefs.resumeOnUnlock,
            "Автоматически перезапускать, если система выгрузила") { Prefs.resumeOnUnlock = it })
        host.addView(sliderRow("Прозрачность окна", Prefs.windowAlpha, 40, 100) {
            Prefs.windowAlpha = it
        })
        host.addView(actionRow("Сбросить размер и позицию окна") {
            Prefs.winX = Int.MIN_VALUE
            Prefs.winY = 80
            Prefs.winW = 0
            Prefs.winH = 0
            toast("Перезапустите панель")
        })

        host.addView(section("САЙТЫ"))
        host.addView(switchRow("Режим «как на компьютере»", Prefs.desktopMode,
            "Desktop User-Agent: корректнее работает пропуск рекламы и фокус") {
            Prefs.desktopMode = it
        })
        host.addView(TextView(this).apply {
            text = "Домашняя страница"
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(14), dp(12), dp(14), dp(4))
        })
        host.addView(EditText(this).apply {
            setText(Prefs.homeUrl)
            setSingleLine(true)
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundResource(R.drawable.bg_round)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnFocusChangeListener { _, has ->
                if (!has) {
                    val u = text.toString().trim()
                    if (u.isNotEmpty()) Prefs.homeUrl = u
                }
            }
        }, lp())

        host.addView(section("ЭКРАН"))
        host.addView(sliderRow("Сила ночного фильтра", Prefs.nightAlpha, 10, 80) {
            Prefs.nightAlpha = it
        })

        host.addView(section("АВТОЗАПУСК"))
        host.addView(switchRow("Запускать после перезагрузки", Prefs.autoStartBoot,
            "Панель появится сама сразу после включения телефона") { Prefs.autoStartBoot = it })

        host.addView(section("О ПРИЛОЖЕНИИ"))
        host.addView(TextView(this).apply {
            text = "Tower v1.0 · 30 функций в 6 разделах\n" +
                "Плавающая панель управления видео.\n" +
                "Не требует root. Не собирает данные."
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        })

        return scroll
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#EAECF4"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#7C5CFF"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        letterSpacing = 0.08f
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun card(pad: Int = 14) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_button)
        setPadding(dp(pad), dp(12), dp(pad), dp(12))
    }

    private fun switchRow(name: String, initial: Boolean, desc: String, onChange: (Boolean) -> Unit): LinearLayout {
        val c = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        top.addView(TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(8)
        })
        top.addView(sw)
        c.addView(top)
        c.addView(TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(0, dp(3), 0, 0)
        })
        return c
    }

    private fun sliderRow(name: String, initial: Int, min: Int, max: Int, onChange: (Int) -> Unit): LinearLayout {
        val c = card()
        val label = TextView(this).apply {
            text = "$name: $initial"
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        c.addView(label)
        c.addView(SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                    val value = v + min
                    label.text = "$name: $value"
                    if (fromUser) onChange(value)
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        return c
    }

    private fun actionRow(name: String, onClick: () -> Unit): LinearLayout =
        card().apply {
            setOnClickListener { onClick() }
            addView(TextView(this@SettingsActivity).apply {
                text = name
                setTextColor(Color.parseColor("#EAECF4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(6) }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
