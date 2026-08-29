package com.tower.app.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tower.app.Functions
import com.tower.app.Prefs
import com.tower.app.R
import com.tower.app.overlay.FloatingService
import com.tower.app.service.TowerAccessibilityService
import kotlin.math.roundToInt

/** Главный экран: доступы, запуск панели, список функций. */
class MainActivity : Activity() {

    private lateinit var startBtn: TextView
    private lateinit var urlEdit: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        setContentView(buildUi())
        refreshStartButton()
        handleIncoming(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStartButton()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    // ── интерфейс ────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0E1016")) }
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(24))
        }
        scroll.addView(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        host.addView(TextView(this).apply {
            text = "\uD83D\uDDFC"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            gravity = Gravity.CENTER
        })
        host.addView(TextView(this).apply {
            text = "TOWER"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.2f
        })
        host.addView(TextView(this).apply {
            text = "Плавающая панель управления видео"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        host.addView(TextView(this).apply {
            text = "30 функций · 6 разделов · поверх любого приложения"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#7C5CFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(4), 0, dp(18))
        })

        host.addView(sectionTitle("1. Доступы"))
        host.addView(permCard(
            title = "Показ поверх других приложений",
            desc = "Обязательно. Без этого меню не сможет летать поверх YouTube, браузера и игр.",
            button = "Выдать",
            check = { canOverlay() },
            action = { askOverlay() }
        ))
        host.addView(permCard(
            title = "Специальные возможности (необязательно)",
            desc = "Рекомендуется: панель рисуется системным слоем, не выгружается и сама воскресает. Tower не читает текст с экрана.",
            button = "Включить",
            check = { a11yEnabled() },
            action = { askA11y() }
        ))
        host.addView(permCard(
            title = "Уведомления",
            desc = "Нужны для постоянной работы панели и кнопки «Остановить».",
            button = "Выдать",
            check = { notifAllowed() },
            action = { askNotif() }
        ))
        host.addView(permCard(
            title = "Не выгружать из памяти",
            desc = "Отключите оптимизацию батареи для Tower, чтобы панель не пропадала.",
            button = "Открыть",
            check = { batteryIgnored() },
            action = { askBattery() }
        ))

        host.addView(sectionTitle("2. Запуск"))
        startBtn = TextView(this).apply {
            text = "ЗАПУСТИТЬ ПАНЕЛЬ"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.parseColor("#0B0D13"))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#22D3EE"))
            ).apply { cornerRadius = dp(14).toFloat() }
            setPadding(0, dp(15), 0, dp(15))
            setOnClickListener { togglePanel() }
        }
        host.addView(startBtn, lp(mt = 4))

        host.addView(TextView(this).apply {
            text = "или откройте ссылку сразу в плавающем окне:"
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(16), 0, dp(6))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlEdit = EditText(this).apply {
            setSingleLine(true)
            hint = "https://www.youtube.com/watch?v=…"
            setHintTextColor(Color.parseColor("#5A6178"))
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundResource(R.drawable.bg_round)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        row.addView(urlEdit, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(TextView(this).apply {
            text = "Открыть"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0B0D13"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#22D3EE"))
            ).apply { cornerRadius = dp(12).toFloat() }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                val u = urlEdit.text.toString().trim()
                if (u.isEmpty()) toast("Введите ссылку") else openInTower(u)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { leftMargin = dp(8) })
        host.addView(row)

        host.addView(sectionTitle("3. Информация"))
        host.addView(menuCard("Все 30 функций", "Список по разделам") { showFunctions() })
        host.addView(menuCard("Настройки", "Размер окна, UA, прозрачность, автозапуск") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        host.addView(menuCard("Как это работает", "Коротко о возможностях и ограничениях") { showHowTo() })

        return scroll
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#7C5CFF"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        letterSpacing = 0.08f
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_button)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun permCard(
        title: String,
        desc: String,
        button: String,
        check: () -> Boolean,
        action: () -> Unit
    ): LinearLayout {
        val c = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        box.addView(TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(0, dp(3), 0, 0)
        })
        top.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btn = TextView(this).apply {
            text = button
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener { action(); refreshStartButton() }
        }
        top.addView(btn)
        c.addView(top)

        fun paint() {
            val ok = check()
            btn.text = if (ok) "✓ Выдано" else button
            btn.setTextColor(if (ok) Color.parseColor("#3DDC84") else Color.parseColor("#0B0D13"))
            btn.setBackgroundResource(if (ok) R.drawable.bg_button else R.drawable.bg_button_on)
        }
        paint()
        c.post { paint() }
        c.tag = { paint() }
        return c
    }

    private fun menuCard(title: String, desc: String, onClick: () -> Unit): LinearLayout {
        val c = card().apply {
            setOnClickListener { onClick() }
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        c.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#EAECF4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        c.addView(TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        })
        return c
    }

    private fun lp(mt: Int = 0, mb: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(mt); bottomMargin = dp(mb) }

    // ── доступы ──────────────────────────────────────────────────────────

    private fun canOverlay(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun askOverlay() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (t: Throwable) {
            toast("Откройте настройки и разрешите показ поверх других окон")
        }
    }

    private fun a11yEnabled(): Boolean {
        val want = "$packageName/.service.TowerAccessibilityService"
        return try {
            val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            s?.split(':')?.any { it.equals(want, true) } == true
        } catch (t: Throwable) {
            false
        }
    }

    private fun askA11y() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast("Специальные возможности → Tower → включить")
        } catch (t: Throwable) { }
    }

    private fun notifAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
            )
        }
    }

    private fun batteryIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun askBattery() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (t: Throwable) { }
        }
    }

    // ── запуск панели ────────────────────────────────────────────────────

    private fun refreshStartButton() {
        val running = FloatingService.instance != null
        startBtn.text = if (running) "ОСТАНОВИТЬ ПАНЕЛЬ" else "ЗАПУСТИТЬ ПАНЕЛЬ"
        startBtn.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (running) intArrayOf(Color.parseColor("#FF5C6C"), Color.parseColor("#FF9A2E"))
            else intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#22D3EE"))
        ).apply { cornerRadius = dp(14).toFloat() }
    }

    private fun togglePanel() {
        if (FloatingService.instance != null) {
            FloatingService.stop(this)
            refreshStartButton()
            return
        }
        if (!canOverlay()) {
            askOverlay()
            toast("Сначала выдайте разрешение «поверх других окон»")
            return
        }
        FloatingService.start(this)
        toast("Панель запущена — меню всегда сверху")
        finish()
    }

    private fun openInTower(u: String) {
        if (!canOverlay()) {
            askOverlay()
            toast("Сначала выдайте разрешение «поверх других окон»")
            return
        }
        FloatingService.start(this, u)
        Toast.makeText(this, "Открываю в плавающем окне", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleIncoming(intent: Intent?) {
        val a = intent?.action ?: return
        when (a) {
            Intent.ACTION_VIEW -> {
                val u = intent.dataString
                if (!u.isNullOrBlank() && canOverlay()) {
                    FloatingService.start(this, u)
                    finish()
                }
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = Regex("https?://\\S+").find(text.orEmpty())?.value
                if (!url.isNullOrBlank() && canOverlay()) {
                    FloatingService.start(this, url)
                    finish()
                } else if (!text.isNullOrBlank()) {
                    urlEdit.setText(text)
                }
            }
        }
    }

    // ── диалоги ──────────────────────────────────────────────────────────

    private fun showFunctions() {
        val scroll = ScrollView(this)
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        scroll.addView(host)
        var n = 0
        for ((section, fns) in Functions.grouped) {
            host.addView(TextView(this).apply {
                text = section.title
                setTextColor(Color.parseColor("#7C5CFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                letterSpacing = 0.08f
                setPadding(0, dp(14), 0, dp(4))
            })
            host.addView(TextView(this).apply {
                text = section.hint
                setTextColor(Color.parseColor("#9AA2B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            })
            for (f in fns) {
                n++
                host.addView(TextView(this).apply {
                    text = "$n. ${f.icon}  ${f.title} — ${f.desc}"
                    setTextColor(Color.parseColor("#EAECF4"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                })
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Все ${Functions.COUNT} функций")
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showHowTo() {
        val tv = TextView(this).apply {
            text = """
Панель Tower — это плавающее окно с браузером и 30 функциями.

ЧТО УМЕЕТ СКОРОСТЬ
2X / 3X / 4X / 5X / 10X и любой шаг от 0.25X до 16X — применяется
к любому <video> на странице: YouTube, VK, Rutube, Twitch, плееры
на сайтах и прямые ссылки .mp4 / .m3u8.

ПОЧЕМУ ИМЕННО ТАК
Android не даёт стороннему приложению менять скорость видео
внутри чужого приложения (YouTube, браузер) — это может только
само приложение. Поэтому Tower показывает видео у себя в плавающем
окне и управляет им полностью. Зато функции раздела «ЗВУК»,
«ЭКРАН» и «ВИДЕО» (снимок, запись) работают системно — поверх
любого приложения, включая обычное приложение YouTube.

ЧТО РАБОТАЕТ ВЕЗДЕ
Усиление звука, режим «Голос», громкость, ночной фильтр, яркость,
«не гаснет», таймер сна, блокировка касаний, снимок и запись экрана.

КАК ОТКРЫТЬ СВОЙ ВИДЕОФАЙЛ
Вставьте в строку панели прямую ссылку на .mp4 / .webm / .m3u8 —
Tower откроет его своим плеером и даст все 30 функций.
            """.trimIndent()
            setTextColor(Color.parseColor("#D7DBE8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(22), dp(16), dp(22), dp(8))
            setLineSpacing(0f, 1.25f)
        }
        AlertDialog.Builder(this)
            .setTitle("Как это работает")
            .setView(tv)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
