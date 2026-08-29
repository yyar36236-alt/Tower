package com.tower.app.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.tower.app.Prefs
import kotlin.math.roundToInt

/**
 * Системный звук: работает не только в окне Tower, но и в ЛЮБОМ приложении
 * (YouTube, браузер, плеер) — эффекты навешиваются на общий аудиовыход (session 0).
 */
object AudioTools {

    @Volatile
    private var loud: LoudnessEnhancer? = null

    @Volatile
    private var eq: Equalizer? = null

    private var savedBands: ShortArray? = null

    private fun am(c: Context): AudioManager =
        c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun maxVolume(c: Context): Int =
        try { am(c).getStreamMaxVolume(AudioManager.STREAM_MUSIC) } catch (t: Throwable) { 15 }

    fun volume(c: Context): Int =
        try { am(c).getStreamVolume(AudioManager.STREAM_MUSIC) } catch (t: Throwable) { 0 }

    fun setVolumePercent(c: Context, percent: Int) {
        val max = maxVolume(c)
        val v = (max * percent / 100f).roundToInt().coerceIn(0, max)
        try {
            am(c).setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        } catch (t: Throwable) {
            // Игнорируем: на некоторых прошивках нужно разрешение «Не беспокоить»
        }
    }

    fun mute(c: Context, on: Boolean) {
        if (on) {
            if (Prefs.mediaVolume < 0) Prefs.mediaVolume = volume(c)
            setVolumePercent(c, 0)
        } else {
            val saved = Prefs.mediaVolume
            if (saved >= 0) {
                val max = maxVolume(c)
                try {
                    am(c).setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceIn(0, max), 0)
                } catch (t: Throwable) { Unit }
            }
            Prefs.mediaVolume = -1
        }
    }

    /** Усиление звука сверх системного максимума. db: 0..20 */
    fun boost(db: Int): Boolean {
        return try {
            val l = loud ?: LoudnessEnhancer(0).also { loud = it }
            val gain = (db * 100).coerceIn(0, 2000)
            l.setTargetGain(gain)
            l.enabled = db > 0
            true
        } catch (t: Throwable) {
            loud = null
            false
        }
    }

    fun boostSupported(): Boolean = loud != null

    /** Режим «Голос»: режем бас/гул, поднимаем середину и разборчивость речи. */
    fun voice(on: Boolean): Boolean {
        return try {
            val e = eq ?: Equalizer(0, 0).also { eq = it }
            val bands = e.numberOfBands.toInt()
            if (on) {
                if (savedBands == null) {
                    savedBands = ShortArray(bands) { i -> e.getBandLevel(i.toShort()) }
                }
                val range = e.bandLevelRange
                val min = range[0].toInt()
                val max = range[1].toInt()
                for (i in 0 until bands) {
                    val fr = e.getBandFreqRange(i.toShort())
                    val centerHz = ((fr[0].toLong() + fr[1].toLong()) / 2L / 1000L).toInt()
                    val mb = when {
                        centerHz < 250 -> -450
                        centerHz < 800 -> -150
                        centerHz < 1600 -> 250
                        centerHz < 4000 -> 800
                        centerHz < 8000 -> 450
                        else -> 0
                    }.coerceIn(min, max).toShort()
                    e.setBandLevel(i.toShort(), mb)
                }
                e.enabled = true
            } else {
                savedBands?.let { saved ->
                    for (i in 0 until minOf(saved.size, bands)) {
                        e.setBandLevel(i.toShort(), saved[i])
                    }
                }
                e.enabled = false
            }
            true
        } catch (t: Throwable) {
            eq = null
            false
        }
    }

    fun release() {
        try { loud?.release() } catch (t: Throwable) { Unit }
        try { eq?.release() } catch (t: Throwable) { Unit }
        loud = null
        eq = null
        savedBands = null
    }
}
