package com.tower.app.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.EditorInfo
import android.text.InputType
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.tower.app.Fn
import com.tower.app.Notif
import com.tower.app.Prefs
import com.tower.app.R
import com.tower.app.TowerJs
import com.tower.app.audio.AudioTools
import com.tower.app.capture.CaptureTools
import com.tower.app.service.TowerAccessibilityService
import com.tower.app.ui.CaptureActivity
import com.tower.app.ui.SettingsActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Плавающая панель Tower.
 *
 * Держит окно поверх всех приложений (тип окна выбирается автоматически:
 * TYPE_ACCESSIBILITY_OVERLAY, если включён Accessibility-доступ, иначе
 * TYPE_APPLICATION_OVERLAY), внутри — WebView с YouTube/любым видео
 * и панель из 30 функций, разбитая на 6 секций.
 */
class FloatingService : Service() {

    companion object {
        const val ACTION_START = "com.tower.app.action.START"
        const val ACTION_STOP = "com.tower.app.action.STOP"
        const val ACTION_URL = "com.tower.app.action.URL"
        const val ACTION_MP = "com.tower.app.action.MP"
        const val ACTION_REFRESH = "com.tower.app.action.REFRESH"
        const val EXTRA_URL = "url"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        @Volatile
        var instance: FloatingService? = null

        /** Действие, которое нужно выполнить после получения доступа к захвату экрана. */
        @Volatile
        var pendingCapture: (() -> Unit)? = null

        fun start(c: Context, url: String? = null) {
            val i = Intent(c, FloatingService::class.java).apply {
                action = if (url.isNullOrBlank()) ACTION_START else ACTION_URL
                if (!url.isNullOrBlank()) putExtra(EXTRA_URL, url)
            }
            try {
                ContextCompat.startForegroundService(c, i)
            } catch (t: Throwable) {
                try { c.startService(i) } catch (t2: Throwable) { }
            }
        }

        fun stop(c: Context) {
            try { c.startService(Intent(c, FloatingService::class.java).setAction(ACTION_STOP)) }
            catch (t: Throwable) { }
        }
    }

    // ── состояние ────────────────────────────────────────────────────────

    private lateinit var wm: WindowManager
    private lateinit var ui: Context
    private var params: WindowManager.LayoutParams? = null

    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var headerTitle: LinearLayout? = null
    private var statusTv: TextView? = null
    private var urlRow: LinearLayout? = null
    private var urlEdit: EditText? = null
    private var webFrame: FrameLayout? = null
    private var web: WebView? = null
    private var fullContainer: FrameLayout? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var panelHost: FrameLayout? = null
    private var panel: PanelUi? = null
    private var bubble: FrameLayout? = null
    private var lockShield: FrameLayout? = null
    private var audioBadge: TextView? = null
    private var resizeHandle: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private var sleepRunnable: Runnable? = null
    private var unlockRunnable: Runnable? = null

    private var dragSx = 0f
    private var dragSy = 0f
    private var dragPx = 0
    private var dragPy = 0

    private var abStage = 0
    private var showingVolume = false
    private var showingSleep = false
    private var showingMarks = false
    private var currentTitle = ""

    // ── жизненный цикл ───────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ui = ContextThemeWrapper(this, R.style.Theme_Tower)
        Notif.createChannel(this)
        startFg()
        buildUi()
        addWindow()
        load(Prefs.lastUrl)
        applySystemToggles()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_URL -> {
                val u = intent.getStringExtra(EXTRA_URL)
                if (!u.isNullOrBlank()) load(u)
                setCollapsed(false)
            }

            ACTION_START -> setCollapsed(false)

            ACTION_REFRESH -> refresh()

            ACTION_MP -> {
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                if (code == Activity.RESULT_OK && data != null) {
                    try {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val mp = mpm.getMediaProjection(code, data)
                        CaptureTools.attach(mp) {
                            stopFgProjection()
                            refresh()
                        }
                        val act = pendingCapture
                        pendingCapture = null
                        act?.invoke()
                    } catch (t: Throwable) {
                        toast("Не удалось получить доступ к захвату")
                    }
                } else {
                    pendingCapture = null
                    toast("Доступ к захвату не получен")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        sleepRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        try { root?.let { wm.removeView(it) } } catch (t: Throwable) { }
        NightOverlay.hide()
        AudioTools.release()
        if (CaptureTools.recording) CaptureTools.stopRecording(this)
        CaptureTools.release()
        try { web?.stopLoading() } catch (t: Throwable) { }
        try { web?.destroy() } catch (t: Throwable) { }
        web = null
        root = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startFg() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, Notif.ID_MAIN, Notif.main(this), type)
        } catch (t: Throwable) {
            try { startForeground(Notif.ID_MAIN, Notif.main(this)) } catch (t2: Throwable) { }
        }
    }

    private fun startFgProjection() {
        if (Build.VERSION.SDK_INT < 29) return
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            ServiceCompat.startForeground(this, Notif.ID_MAIN, Notif.main(this), type)
        } catch (t: Throwable) { }
    }

    private fun stopFgProjection() {
        startFg()
    }

    // ── построение интерфейса ────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun buildUi() {
        val c = ui

        val rootView = FrameLayout(c).apply {
            setBackgroundResource(R.drawable.bg_window)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(20).toFloat()
        }
        root = rootView

        bubble = FrameLayout(c).apply {
            setBackgroundResource(R.drawable.bg_bubble)
            addView(TextView(c).apply {
                text = "\uD83D\uDDFC" // 🗼
                textSize = 24f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
            visibility = View.GONE
            setOnClickListener { setCollapsed(false) }
            setOnLongClickListener { stopSelf(); true }
        }
        rootView.addView(
            bubble,
            FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER)
        )

        val contentView = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        content = contentView
        rootView.addView(
            contentView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // ── шапка (перетаскивание) ───────────────────────────────────────
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_header)
            setPadding(dp(6), 0, dp(6), 0)
        }
        contentView.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        header.addView(TextView(c).apply {
            text = "\uD83D\uDDFC"
            textSize = 16f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(26), dp(44)))

        headerTitle = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }.also { box ->
            box.addView(TextView(c).apply {
                text = "TOWER"
                setTextColor(Color.parseColor("#EAECF4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                letterSpacing = 0.12f
            })
            statusTv = TextView(c).apply {
                text = "панель активна"
                setTextColor(Color.parseColor("#9AA2B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setSingleLine(true)
            }
            box.addView(statusTv)
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, dp(44), 1f))

        header.addView(hBtn("⚙") { openSettings() })
        header.addView(hBtn("★") {
            showingMarks = !showingMarks
            refreshMarks()
        })
        header.addView(hBtn("—") { setCollapsed(true) })
        header.addView(hBtn("✕") { stopSelf() })

        headerTitle?.setOnTouchListener(dragTouch())

        // ── строка ссылки ────────────────────────────────────────────────
        urlRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), dp(4))
        }
        contentView.addView(urlRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        urlEdit = EditText(c).apply {
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#EAECF4"))
            setHintTextColor(Color.parseColor("#6E7688"))
            hint = "Ссылка, поиск или .mp4…"
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundResource(R.drawable.bg_round)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    load(text.toString())
                    hideKb()
                    true
                } else false
            }
        }
        urlRow?.addView(urlEdit, LinearLayout.LayoutParams(0, dp(32), 1f))
        urlRow?.addView(hBtn("↵") { load(urlEdit?.text?.toString().orEmpty()); hideKb() })
        urlRow?.addView(hBtn("\uD83D\uDCCB") { pasteFromClipboard() })

        // ── окно видео ───────────────────────────────────────────────────
        webFrame = FrameLayout(c).apply { setBackgroundColor(Color.BLACK) }
        contentView.addView(
            webFrame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
        )

        val wv = WebView(c)
        web = wv
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowContentAccess = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (Prefs.desktopMode) TowerJs.UA_DESKTOP else TowerJs.UA_MOBILE
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                injectCore()
                applyPageState()
                Prefs.lastUrl = url
                urlEdit?.setText(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url.toString()
                if (u.startsWith("http://") || u.startsWith("https://")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (t: Throwable) {
                    false
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                customView = v
                customCb = cb
                fullContainer?.visibility = View.VISIBLE
                fullContainer?.addView(
                    v,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                )
                wv.visibility = View.GONE
            }

            override fun onHideCustomView() {
                fullContainer?.removeAllViews()
                fullContainer?.visibility = View.GONE
                wv.visibility = View.VISIBLE
                customCb?.onCustomViewHidden()
                customView = null
                customCb = null
            }
        }
        webFrame?.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        fullContainer = FrameLayout(c).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }
        webFrame?.addView(
            fullContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        audioBadge = TextView(c).apply {
            text = "\uD83C\uDFA7 Только звук"
            setTextColor(Color.parseColor("#9AA2B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        webFrame?.addView(
            audioBadge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // ── панель функций ───────────────────────────────────────────────
        panelHost = FrameLayout(c)
        contentView.addView(
            panelHost,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        panel = PanelUi(
            c,
            onFn = ::onFunction,
            onSpeed = ::onSpeedChanged,
            onVolume = ::onVolumeChanged,
            onSleep = ::onSleepChosen,
            onMark = { u -> load(u); showingMarks = false; refreshMarks() },
            onMarkDelete = { u ->
                saveMarks(marks().filter { it.second != u })
                refreshMarks()
                toast("Закладка удалена")
            },
            onMarkSave = { addMark(currentTitle, Prefs.lastUrl) }
        )
        panelHost?.addView(
            panel?.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        panel?.setSpeedLabel(Prefs.speed)

        // ── ручка изменения размера ──────────────────────────────────────
        resizeHandle = TextView(c).apply {
            text = "⇲"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5A6178"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setOnTouchListener(resizeTouch())
        }
        contentView.addView(
            resizeHandle,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
        )

        // ── экран блокировки касаний ─────────────────────────────────────
        lockShield = FrameLayout(c).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC0B0D13"))
            addView(TextView(c).apply {
                text = "\uD83D\uDD12\nУдерживайте 1 сек,\nчтобы снять блокировку"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#EAECF4"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        unlockRunnable = Runnable { setLock(false) }
                        handler.postDelayed(unlockRunnable!!, 900)
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        unlockRunnable?.let { handler.removeCallbacks(it) }
                        unlockRunnable = null
                        true
                    }

                    else -> true
                }
            }
        }
        rootView.addView(
            lockShield,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun hBtn(text: String, onClick: () -> Unit): TextView = TextView(ui).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Color.parseColor("#D7DBE8"))
        setBackgroundResource(R.drawable.bg_round)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { leftMargin = dp(4) }
    }

    private fun dragTouch() = View.OnTouchListener { v, e ->
        val p = params ?: return@OnTouchListener false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragSx = e.rawX
                dragSy = e.rawY
                dragPx = p.x
                dragPy = p.y
                true
            }

            MotionEvent.ACTION_MOVE -> {
                p.x = (dragPx + e.rawX - dragSx).toInt()
                p.y = (dragPy + e.rawY - dragSy).toInt()
                updateLayout()
                true
            }

            MotionEvent.ACTION_UP -> {
                saveWin()
                v.performClick()
                true
            }

            else -> false
        }
    }

    private fun resizeTouch() = View.OnTouchListener { _, e ->
        val p = params ?: return@OnTouchListener false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragSx = e.rawX
                dragSy = e.rawY
                dragPx = p.width
                dragPy = p.height
                true
            }

            MotionEvent.ACTION_MOVE -> {
                p.width = (dragPx + e.rawX - dragSx).toInt().coerceIn(dp(240), screenW())
                p.height = (dragPy + e.rawY - dragSy).toInt().coerceIn(dp(220), screenH())
                updateLayout()
                applyChildSizes()
                true
            }

            MotionEvent.ACTION_UP -> {
                saveWin()
                true
            }

            else -> false
        }
    }

    // ── окно ─────────────────────────────────────────────────────────────

    @SuppressLint("RtlHardcoded")
    private fun addWindow() {
        val useA11y = Prefs.useA11yLayer && TowerAccessibilityService.connected
        val type = if (useA11y)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (Prefs.winX == Int.MIN_VALUE) maxOf(dp(8), screenW() - dp(440)) else Prefs.winX
            y = Prefs.winY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = p
        applyFlags()

        val r = root ?: return
        try {
            wm.addView(r, p)
        } catch (t: Throwable) {
            p.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            try {
                wm.addView(r, p)
            } catch (t2: Throwable) {
                toast("Нет разрешения «Поверх других окон»")
                stopSelf()
                return
            }
        }
        applyMode()
    }

    private fun updateLayout() {
        val p = params ?: return
        val r = root ?: return
        try { wm.updateViewLayout(r, p) } catch (t: Throwable) { }
    }

    private fun applyFlags() {
        val p = params ?: return
        var f = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (Prefs.keepAwake) f = f or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (Prefs.touchLock) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        p.flags = f
        p.screenBrightness = if (Prefs.bright) 1f else -1f
        p.alpha = (Prefs.windowAlpha / 100f).coerceIn(0.3f, 1f)
    }

    private fun applyMode() {
        val p = params ?: return
        if (Prefs.collapsed) {
            p.width = dp(58)
            p.height = dp(58)
            bubble?.visibility = View.VISIBLE
            content?.visibility = View.GONE
        } else {
            bubble?.visibility = View.GONE
            content?.visibility = View.VISIBLE
            when {
                Prefs.fullscreen -> {
                    p.width = ViewGroup.LayoutParams.MATCH_PARENT
                    p.height = ViewGroup.LayoutParams.MATCH_PARENT
                    p.x = 0
                    p.y = 0
                }

                Prefs.mini -> {
                    val w = minOf(screenW(), dp(320))
                    p.width = w
                    p.height = w * 9 / 16 + dp(44)
                }

                else -> {
                    val w = if (Prefs.winW > 0) Prefs.winW else minOf(screenW() - dp(8), dp(430))
                    val h = if (Prefs.winH > 0) Prefs.winH else (screenH() * 0.72).roundToInt()
                    p.width = w.coerceIn(dp(240), screenW())
                    p.height = h.coerceIn(dp(240), screenH())
                }
            }
        }
        applyFlags()
        applyChildSizes()
        updateLayout()
    }

    private fun applyChildSizes() {
        val p = params ?: return
        val compact = Prefs.mini || Prefs.fullscreen
        urlRow?.visibility = if (compact) View.GONE else View.VISIBLE
        panelHost?.visibility = if (compact) View.GONE else View.VISIBLE
        resizeHandle?.visibility = if (compact) View.GONE else View.VISIBLE

        val lp = webFrame?.layoutParams as? LinearLayout.LayoutParams
        if (lp != null) {
            if (compact) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = 0
                lp.weight = 1f
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = (p.width * 9 / 16)
                lp.weight = 0f
            }
            webFrame?.layoutParams = lp
        }
    }

    private fun saveWin() {
        val p = params ?: return
        if (Prefs.collapsed || Prefs.mini || Prefs.fullscreen) return
        Prefs.winX = p.x
        Prefs.winY = p.y
        Prefs.winW = p.width
        Prefs.winH = p.height
    }

    private fun setCollapsed(v: Boolean) {
        Prefs.collapsed = v
        applyMode()
    }

    // ── страница ─────────────────────────────────────────────────────────

    private fun load(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://") &&
            !u.startsWith("file://") && !u.startsWith("content://")
        ) {
            u = if (looksLikeDomain(u)) "https://$u"
            else "https://www.youtube.com/results?search_query=" + Uri.encode(u)
        }
        Prefs.lastUrl = u
        urlEdit?.setText(u)
        if (TowerJs.isDirectMedia(u)) {
            val html = TowerJs.playerHtml(u, u.substringAfterLast('/').take(60))
            web?.loadDataWithBaseURL("https://tower.local/", html, "text/html", "utf-8", null)
        } else {
            web?.loadUrl(u)
        }
    }

    private fun looksLikeDomain(s: String): Boolean =
        Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(s)

    private fun injectCore() {
        jsRaw(TowerJs.CORE)
    }

    private fun applyPageState() {
        val s = Prefs.speed
        js(
            "if(window.Tower){" +
                "window.Tower.setRate($s);" +
                "window.Tower.setAutoSkip(${Prefs.autoSkip});" +
                "window.Tower.setFocus(${Prefs.focusMode});" +
                "window.Tower.setHideShorts(${Prefs.hideShorts});" +
                "window.Tower.setLoop(${Prefs.loop});" +
                "window.Tower.setAudioOnly(${Prefs.audioOnly});" +
                "window.Tower.setMuted(${Prefs.muted});" +
                "}"
        )
    }

    private fun js(script: String) {
        handler.post {
            try {
                web?.evaluateJavascript(
                    "(function(){try{\n$script\n}catch(e){}})()",
                    ValueCallback<String> { }
                )
            } catch (t: Throwable) { }
        }
    }

    private fun jsRaw(script: String) {
        handler.post {
            try {
                web?.evaluateJavascript(script, ValueCallback<String> { })
            } catch (t: Throwable) { }
        }
    }

    private fun jsVal(expr: String, cb: (String) -> Unit) {
        handler.post {
            try {
                web?.evaluateJavascript("(function(){try{return ($expr)}catch(e){return null}})()") { v ->
                    cb(v ?: "null")
                }
            } catch (t: Throwable) {
                cb("null")
            }
        }
    }

    private fun applySystemToggles() {
        if (Prefs.nightFilter) NightOverlay.show(this, Prefs.nightAlpha)
        if (Prefs.boostDb > 0) AudioTools.boost(Prefs.boostDb)
        if (Prefs.voiceMode) AudioTools.voice(true)
        setLock(Prefs.touchLock, silent = true)
        audioBadge?.visibility = if (Prefs.audioOnly) View.VISIBLE else View.GONE
        refresh()
    }

    // ── 30 функций ───────────────────────────────────────────────────────

    private fun onFunction(f: Fn) {
        when (f.id) {
            // 1. СКОРОСТЬ
            "spd_2" -> setSpeed(2f)
            "spd_3" -> setSpeed(3f)
            "spd_4" -> setSpeed(4f)
            "spd_5" -> setSpeed(5f)
            "spd_10" -> setSpeed(10f)

            // 2. ВОСПРОИЗВЕДЕНИЕ
            "pb_play" -> js("if(window.Tower)window.Tower.playPause()")
            "pb_back" -> js("if(window.Tower)window.Tower.seek(-10)")
            "pb_fwd" -> js("if(window.Tower)window.Tower.seek(10)")
            "pb_ab" -> toggleAB()
            "pb_loop" -> {
                Prefs.loop = !Prefs.loop
                js("if(window.Tower)window.Tower.setLoop(${Prefs.loop})")
                toast(if (Prefs.loop) "Повтор включён" else "Повтор выключен")
                refresh()
            }

            // 3. ВИДЕО
            "vd_full" -> {
                Prefs.fullscreen = !Prefs.fullscreen
                if (Prefs.fullscreen) {
                    Prefs.mini = false
                    js("if(window.Tower)window.Tower.fullscreen()")
                } else {
                    web?.webChromeClient?.onHideCustomView()
                }
                applyMode()
                refresh()
            }
            "vd_mini" -> {
                Prefs.mini = !Prefs.mini
                if (Prefs.mini) Prefs.fullscreen = false
                applyMode()
                refresh()
                toast(if (Prefs.mini) "Мини-окно" else "Обычный размер")
            }
            "vd_shot" -> doScreenshot()
            "vd_rec" -> toggleRecord()
            "vd_audio" -> {
                Prefs.audioOnly = !Prefs.audioOnly
                js("if(window.Tower)window.Tower.setAudioOnly(${Prefs.audioOnly})")
                audioBadge?.visibility = if (Prefs.audioOnly) View.VISIBLE else View.GONE
                toast(if (Prefs.audioOnly) "Только звук" else "Видео включено")
                refresh()
            }

            // 4. YOUTUBE
            "yt_skip" -> {
                js("if(window.Tower)window.Tower.skipAd()")
                toast("Пропускаю рекламу")
            }
            "yt_autoskip" -> {
                Prefs.autoSkip = !Prefs.autoSkip
                js("if(window.Tower)window.Tower.setAutoSkip(${Prefs.autoSkip})")
                toast(if (Prefs.autoSkip) "Авто-пропуск включён" else "Авто-пропуск выключен")
                refresh()
            }
            "yt_focus" -> {
                Prefs.focusMode = !Prefs.focusMode
                js("if(window.Tower)window.Tower.setFocus(${Prefs.focusMode})")
                toast(if (Prefs.focusMode) "Режим фокуса включён" else "Режим фокуса выключен")
                refresh()
            }
            "yt_shorts" -> {
                Prefs.hideShorts = !Prefs.hideShorts
                js("if(window.Tower)window.Tower.setHideShorts(${Prefs.hideShorts})")
                toast(if (Prefs.hideShorts) "Shorts скрыты" else "Shorts показаны")
                refresh()
            }
            "yt_open" -> openLinkBar()

            // 5. ЗВУК
            "au_mute" -> {
                Prefs.muted = !Prefs.muted
                AudioTools.mute(this, Prefs.muted)
                js("if(window.Tower)window.Tower.setMuted(${Prefs.muted})")
                toast(if (Prefs.muted) "Звук выключен" else "Звук включён")
                refresh()
            }
            "au_boost" -> {
                val db = if (Prefs.boostDb >= 15) 0 else Prefs.boostDb + 3
                Prefs.boostDb = db
                val ok = AudioTools.boost(db)
                toast(if (ok) "Усиление звука: +$db дБ" else "Устройство не поддерживает усиление")
                refresh()
            }
            "au_reset" -> {
                Prefs.boostDb = 0
                AudioTools.boost(0)
                Prefs.voiceMode = false
                AudioTools.voice(false)
                Prefs.muted = false
                AudioTools.mute(this, false)
                AudioTools.setVolumePercent(this, 60)
                js("if(window.Tower){window.Tower.setMuted(false);window.Tower.setVolume(1)}")
                toast("Звук сброшен")
                refresh()
            }
            "au_voice" -> {
                Prefs.voiceMode = !Prefs.voiceMode
                val ok = AudioTools.voice(Prefs.voiceMode)
                toast(
                    if (ok) (if (Prefs.voiceMode) "Режим «Голос» включён" else "Режим «Голос» выключен")
                    else "Эквалайзер не поддерживается"
                )
                refresh()
            }
            "au_vol" -> {
                showingVolume = !showingVolume
                panel?.toggleVolumeRow(showingVolume, AudioTools.maxVolume(this), AudioTools.volume(this))
                refresh()
            }

            // 6. ЭКРАН
            "sc_awake" -> {
                Prefs.keepAwake = !Prefs.keepAwake
                applyFlags()
                updateLayout()
                toast(if (Prefs.keepAwake) "Экран не будет гаснуть" else "Обычный режим экрана")
                refresh()
            }
            "sc_night" -> {
                Prefs.nightFilter = !Prefs.nightFilter
                if (Prefs.nightFilter) NightOverlay.show(this, Prefs.nightAlpha) else NightOverlay.hide()
                refresh()
                toast(if (Prefs.nightFilter) "Ночной фильтр включён" else "Ночной фильтр выключен")
            }
            "sc_bright" -> {
                Prefs.bright = !Prefs.bright
                applyFlags()
                updateLayout()
                toast(if (Prefs.bright) "Яркость на максимум" else "Обычная яркость")
                refresh()
            }
            "sc_sleep" -> {
                showingSleep = !showingSleep
                panel?.toggleSleepRow(showingSleep)
                refresh()
            }
            "sc_lock" -> {
                setLock(!Prefs.touchLock)
                refresh()
            }
        }
    }

    private fun setSpeed(v: Float) {
        Prefs.speed = v
        js("if(window.Tower)window.Tower.setRate($v)")
        panel?.setSpeedLabel(v)
        toast("Скорость ${trim(v)}X")
        refresh()
    }

    private fun onSpeedChanged(v: Float) = setSpeed(v)

    private fun onVolumeChanged(percent: Int) {
        AudioTools.setVolumePercent(this, percent)
    }

    private fun onSleepChosen(minutes: Int) {
        showingSleep = false
        panel?.toggleSleepRow(false)
        sleepRunnable?.let { handler.removeCallbacks(it) }
        sleepRunnable = null
        Prefs.sleepMinutes = minutes
        if (minutes > 0) {
            val r = Runnable {
                js("if(window.Tower)window.Tower.pause()")
                Prefs.sleepMinutes = 0
                toast("Таймер сна: воспроизведение остановлено")
                refresh()
            }
            sleepRunnable = r
            handler.postDelayed(r, minutes * 60_000L)
            toast("Видео остановится через $minutes мин")
        } else {
            toast("Таймер сна выключен")
        }
        refresh()
    }

    private fun toggleAB() {
        when (abStage) {
            0 -> {
                jsVal("window.Tower?window.Tower.info():'null'") { res ->
                    val o = parseInfo(res)
                    val pos = o?.optDouble("position", 0.0) ?: 0.0
                    Prefs.abA = pos.toFloat()
                    abStage = 1
                    toast("Точка A: ${fmt(pos)}")
                    refresh()
                }
            }

            1 -> {
                jsVal("window.Tower?window.Tower.info():'null'") { res ->
                    val o = parseInfo(res)
                    val pos = o?.optDouble("position", 0.0) ?: 0.0
                    if (pos > Prefs.abA + 0.5) {
                        Prefs.abB = pos.toFloat()
                        js("if(window.Tower)window.Tower.setAB(${Prefs.abA},${Prefs.abB})")
                        abStage = 2
                        toast("A-B повтор: ${fmt(Prefs.abA.toDouble())} — ${fmt(pos)}")
                    } else {
                        toast("Точка B должна быть позже A")
                    }
                    refresh()
                }
            }

            else -> {
                abStage = 0
                Prefs.abA = -1f
                Prefs.abB = -1f
                js("if(window.Tower)window.Tower.clearAB()")
                toast("A-B повтор выключен")
                refresh()
            }
        }
    }

    private fun setLock(on: Boolean, silent: Boolean = false) {
        Prefs.touchLock = on
        lockShield?.visibility = if (on) View.VISIBLE else View.GONE
        applyFlags()
        updateLayout()
        if (!silent) toast(if (on) "Касания заблокированы" else "Касания разблокированы")
    }

    private fun openLinkBar() {
        val clip = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (t: Throwable) {
            null
        }
        val u = clip?.trim().orEmpty()
        if (u.isNotEmpty() && (u.startsWith("http") || looksLikeDomain(u))) {
            urlEdit?.setText(u)
            toast("Ссылка из буфера — нажмите ↵")
        } else {
            toast("Введите ссылку или запрос")
        }
        focusUrl()
    }

    private fun pasteFromClipboard() {
        val clip = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (t: Throwable) {
            null
        }
        val u = clip?.trim().orEmpty()
        if (u.isEmpty()) {
            toast("Буфер обмена пуст")
            return
        }
        urlEdit?.setText(u)
        load(u)
    }

    private fun focusUrl() {
        urlEdit?.requestFocus()
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(urlEdit, InputMethodManager.SHOW_IMPLICIT)
        } catch (t: Throwable) { }
    }

    private fun hideKb() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(urlEdit?.windowToken, 0)
        } catch (t: Throwable) { }
    }

    private fun openSettings() {
        try {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            toast("Откройте настройки через иконку Tower")
        }
    }

    // ── захват экрана ────────────────────────────────────────────────────

    private fun ensureProjection(then: () -> Unit) {
        if (CaptureTools.projection != null) {
            then()
            return
        }
        pendingCapture = then
        try {
            startActivity(
                Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(Notif.ID_CAPTURE, Notif.captureConsent(this))
            } catch (t2: Throwable) { }
            toast("Разрешите захват через уведомление")
        }
    }

    private fun doScreenshot() {
        ensureProjection {
            startFgProjection()
            CaptureTools.screenshot(this) { ok, msg ->
                handler.post {
                    toast(msg)
                    if (!ok) stopFgProjection()
                }
            }
        }
    }

    private fun toggleRecord() {
        if (CaptureTools.recording) {
            CaptureTools.stopRecording(this)
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notif.ID_REC)
            } catch (t: Throwable) { }
            stopFgProjection()
            toast("Запись сохранена: Movies/Tower")
            refresh()
        } else {
            ensureProjection {
                startFgProjection()
                CaptureTools.startRecording(this) { ok, msg ->
                    handler.post {
                        toast(msg)
                        if (!ok) stopFgProjection()
                        refresh()
                    }
                }
            }
        }
    }

    // ── закладки ─────────────────────────────────────────────────────────

    private fun marks(): MutableList<Pair<String, String>> {
        return try {
            val arr = JSONArray(Prefs.bookmarks)
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o.optString("t", "") to o.optString("u", ""))
            }
            out
        } catch (t: Throwable) {
            mutableListOf()
        }
    }

    private fun saveMarks(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        for ((t, u) in list) {
            arr.put(JSONObject().put("t", t).put("u", u))
        }
        Prefs.bookmarks = arr.toString()
    }

    private fun addMark(title: String, url: String) {
        if (url.isBlank()) return
        val l = marks()
        if (l.any { it.second == url }) {
            toast("Уже в закладках")
            return
        }
        l.add(0, title to url)
        saveMarks(l.take(30))
        refreshMarks()
        toast("Сохранено в закладки")
    }

    private fun refreshMarks() {
        panel?.renderMarks(showingMarks, marks())
    }

    // ── состояние кнопок ─────────────────────────────────────────────────

    private fun isOn(id: String): Boolean = when (id) {
        "spd_2" -> Prefs.speed == 2f
        "spd_3" -> Prefs.speed == 3f
        "spd_4" -> Prefs.speed == 4f
        "spd_5" -> Prefs.speed == 5f
        "spd_10" -> Prefs.speed == 10f
        "pb_ab" -> abStage > 0
        "pb_loop" -> Prefs.loop
        "vd_full" -> Prefs.fullscreen
        "vd_mini" -> Prefs.mini
        "vd_rec" -> CaptureTools.recording
        "vd_audio" -> Prefs.audioOnly
        "yt_autoskip" -> Prefs.autoSkip
        "yt_focus" -> Prefs.focusMode
        "yt_shorts" -> Prefs.hideShorts
        "au_mute" -> Prefs.muted
        "au_boost" -> Prefs.boostDb > 0
        "au_voice" -> Prefs.voiceMode
        "au_vol" -> showingVolume
        "sc_awake" -> Prefs.keepAwake
        "sc_night" -> Prefs.nightFilter
        "sc_bright" -> Prefs.bright
        "sc_sleep" -> Prefs.sleepMinutes > 0
        "sc_lock" -> Prefs.touchLock
        else -> false
    }

    private fun refresh() {
        panel?.refresh(::isOn)
    }

    // ── тикер ────────────────────────────────────────────────────────────

    private fun startTicker() {
        ticker = object : Runnable {
            override fun run() {
                jsVal("window.Tower?window.Tower.info():'null'") { res ->
                    val o = parseInfo(res)
                    if (o != null) {
                        val pos = o.optDouble("position", 0.0)
                        val dur = o.optDouble("duration", 0.0)
                        val rate = o.optDouble("rate", 1.0)
                        val ad = o.optBoolean("ad", false)
                        currentTitle = o.optString("title", "")
                        statusTv?.text = buildString {
                            append(fmt(pos)).append(" / ").append(fmt(dur))
                            append("   ").append("%.2f".format(rate)).append("X")
                            if (ad) append("   реклама")
                        }
                    }
                }
                if (CaptureTools.recording) {
                    val secs = (System.currentTimeMillis() - CaptureTools.recStartedAt) / 1000
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(Notif.ID_REC, Notif.recording(this@FloatingService, secs))
                    } catch (t: Throwable) { }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(ticker!!)
    }

    private fun parseInfo(res: String): JSONObject? {
        if (res.isBlank() || res == "null") return null
        return try {
            val raw = if (res.startsWith("\"")) {
                JSONObject("{\"v\":$res}").optString("v", "")
            } else res
            if (raw.isBlank() || raw == "null") null else JSONObject(raw)
        } catch (t: Throwable) {
            null
        }
    }

    // ── мелочи ───────────────────────────────────────────────────────────

    private fun toast(msg: String) {
        try {
            handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        } catch (t: Throwable) { }
    }

    private fun screenW(): Int = CaptureTools.metrics(this).widthPixels

    private fun screenH(): Int = CaptureTools.metrics(this).heightPixels

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun trim(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else "%.2f".format(v)

    private fun fmt(sec: Double): String {
        if (sec.isNaN() || sec < 0) return "0:00"
        val s = sec.toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
    }
}
