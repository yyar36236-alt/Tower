package com.tower.app

import android.content.Context
import android.content.SharedPreferences

/** Простое хранилище настроек панели. */
object Prefs {

    private const val F = "tower"

    @Volatile
    private var _sp: SharedPreferences? = null

    private val sp: SharedPreferences
        get() = _sp ?: (TowerApp.ctx ?: throw IllegalStateException("no ctx"))
            .getSharedPreferences(F, Context.MODE_PRIVATE)
            .also { _sp = it }

    fun init(c: Context) {
        if (_sp == null) _sp = c.applicationContext.getSharedPreferences(F, Context.MODE_PRIVATE)
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        val e = sp.edit()
        e.block()
        e.apply()
    }

    // ── окно ─────────────────────────────────────────────────────────────
    var winX: Int
        get() = sp.getInt("winX", Int.MIN_VALUE)
        set(v) = put { putInt("winX", v) }

    var winY: Int
        get() = sp.getInt("winY", 80)
        set(v) = put { putInt("winY", v) }

    var winW: Int
        get() = sp.getInt("winW", 0)
        set(v) = put { putInt("winW", v) }

    var winH: Int
        get() = sp.getInt("winH", 0)
        set(v) = put { putInt("winH", v) }

    var collapsed: Boolean
        get() = sp.getBoolean("collapsed", false)
        set(v) = put { putBoolean("collapsed", v) }

    var mini: Boolean
        get() = sp.getBoolean("mini", false)
        set(v) = put { putBoolean("mini", v) }

    var fullscreen: Boolean
        get() = sp.getBoolean("fullscreen", false)
        set(v) = put { putBoolean("fullscreen", v) }

    // ── страница ─────────────────────────────────────────────────────────
    var lastUrl: String
        get() = sp.getString("lastUrl", null) ?: "https://www.youtube.com/"
        set(v) = put { putString("lastUrl", v) }

    var homeUrl: String
        get() = sp.getString("homeUrl", null) ?: "https://www.youtube.com/"
        set(v) = put { putString("homeUrl", v) }

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop", true)
        set(v) = put { putBoolean("desktop", v) }

    // ── состояние функций ────────────────────────────────────────────────
    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = put { putFloat("speed", v) }

    var autoSkip: Boolean
        get() = sp.getBoolean("autoSkip", true)
        set(v) = put { putBoolean("autoSkip", v) }

    var focusMode: Boolean
        get() = sp.getBoolean("focus", false)
        set(v) = put { putBoolean("focus", v) }

    var hideShorts: Boolean
        get() = sp.getBoolean("shorts", false)
        set(v) = put { putBoolean("shorts", v) }

    var loop: Boolean
        get() = sp.getBoolean("loop", false)
        set(v) = put { putBoolean("loop", v) }

    var audioOnly: Boolean
        get() = sp.getBoolean("audioOnly", false)
        set(v) = put { putBoolean("audioOnly", v) }

    var muted: Boolean
        get() = sp.getBoolean("muted", false)
        set(v) = put { putBoolean("muted", v) }

    var voiceMode: Boolean
        get() = sp.getBoolean("voice", false)
        set(v) = put { putBoolean("voice", v) }

    var boostDb: Int
        get() = sp.getInt("boost", 0)
        set(v) = put { putInt("boost", v) }

    var nightFilter: Boolean
        get() = sp.getBoolean("night", false)
        set(v) = put { putBoolean("night", v) }

    var nightAlpha: Int
        get() = sp.getInt("nightAlpha", 45)
        set(v) = put { putInt("nightAlpha", v) }

    var keepAwake: Boolean
        get() = sp.getBoolean("awake", false)
        set(v) = put { putBoolean("awake", v) }

    var bright: Boolean
        get() = sp.getBoolean("bright", false)
        set(v) = put { putBoolean("bright", v) }

    var touchLock: Boolean
        get() = sp.getBoolean("touchLock", false)
        set(v) = put { putBoolean("touchLock", v) }

    var mediaVolume: Int
        get() = sp.getInt("mediaVolume", -1)
        set(v) = put { putInt("mediaVolume", v) }

    var sleepMinutes: Int
        get() = sp.getInt("sleepMin", 0)
        set(v) = put { putInt("sleepMin", v) }

    var abA: Float
        get() = sp.getFloat("abA", -1f)
        set(v) = put { putFloat("abA", v) }

    var abB: Float
        get() = sp.getFloat("abB", -1f)
        set(v) = put { putFloat("abB", v) }

    // ── настройки приложения ─────────────────────────────────────────────
    var autoStartBoot: Boolean
        get() = sp.getBoolean("boot", false)
        set(v) = put { putBoolean("boot", v) }

    var useA11yLayer: Boolean
        get() = sp.getBoolean("a11yLayer", true)
        set(v) = put { putBoolean("a11yLayer", v) }

    var resumeOnUnlock: Boolean
        get() = sp.getBoolean("resume", true)
        set(v) = put { putBoolean("resume", v) }

    var windowAlpha: Int
        get() = sp.getInt("alpha", 100)
        set(v) = put { putInt("alpha", v) }

    var bookmarks: String
        get() = sp.getString("marks", null) ?: "[]"
        set(v) = put { putString("marks", v) }

    var seenOnboarding: Boolean
        get() = sp.getBoolean("onb", false)
        set(v) = put { putBoolean("onb", v) }
}
