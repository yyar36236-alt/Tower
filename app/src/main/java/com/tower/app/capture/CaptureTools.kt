package com.tower.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Захват экрана (снимок / запись) через MediaProjection.
 * Работает поверх любого приложения, не требует root.
 */
object CaptureTools {

    @Volatile
    var projection: MediaProjection? = null

    @Volatile
    var recording: Boolean = false

    @Volatile
    var recStartedAt: Long = 0L

    private var recorder: MediaRecorder? = null
    private var recDisplay: VirtualDisplay? = null
    private var recUri: android.net.Uri? = null
    private var callback: MediaProjection.Callback? = null

    fun metrics(c: Context): DisplayMetrics {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    fun attach(mp: MediaProjection, onStop: () -> Unit) {
        projection = mp
        callback = object : MediaProjection.Callback() {
            override fun onStop() {
                projection = null
                onStop()
            }
        }
        mp.registerCallback(callback, Handler(android.os.Looper.getMainLooper()))
    }

    // ── снимок экрана ────────────────────────────────────────────────────

    fun screenshot(c: Context, onDone: (Boolean, String) -> Unit) {
        val mp = projection
        if (mp == null) {
            onDone(false, "Нет доступа к захвату экрана")
            return
        }
        val m = metrics(c)
        val w = m.widthPixels
        val h = m.heightPixels
        val dpi = m.densityDpi
        val reader: ImageReader
        val vd: VirtualDisplay
        try {
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            vd = mp.createVirtualDisplay(
                "tower-shot", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
        } catch (t: Throwable) {
            onDone(false, "Ошибка захвата: ${t.message}")
            return
        }

        val thread = HandlerThread("tower-shot").apply { start() }
        val handler = Handler(thread.looper)
        var done = false

        fun finish(ok: Boolean, msg: String) {
            if (done) return
            done = true
            try { vd.release() } catch (t: Throwable) { }
            try { reader.close() } catch (t: Throwable) { }
            thread.quitSafely()
            onDone(ok, msg)
        }

        reader.setOnImageAvailableListener({ r ->
            if (done) return@setOnImageAvailableListener
            try {
                val image = r.acquireLatestImage()
                if (image == null) {
                    finish(false, "Пустой кадр")
                    return@setOnImageAvailableListener
                }
                val iw = image.width
                val ih = image.height
                val plane = image.planes[0]
                val buffer = plane.buffer
                val ps = plane.pixelStride
                val rs = plane.rowStride
                val pad = rs - ps * iw
                val bmp = Bitmap.createBitmap(iw + pad / ps, ih, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                image.close()
                val out = Bitmap.createBitmap(bmp, 0, 0, iw, ih)
                val uri = saveImage(c, out)
                finish(uri != null, if (uri != null) "Снимок сохранён в Галерею / Pictures/Tower" else "Не удалось сохранить")
            } catch (t: Throwable) {
                finish(false, "Ошибка: ${t.message}")
            }
        }, handler)

        handler.postDelayed({ finish(false, "Таймаут захвата") }, 4000)
    }

    private fun saveImage(c: Context, bmp: Bitmap): android.net.Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Tower_${stamp()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tower")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return try {
            val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            c.contentResolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                c.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (t: Throwable) {
            null
        }
    }

    // ── запись экрана ────────────────────────────────────────────────────

    fun startRecording(c: Context, onEvent: (Boolean, String) -> Unit) {
        val mp = projection
        if (mp == null) {
            onEvent(false, "Нет доступа к захвату экрана")
            return
        }
        if (recording) return
        val m = metrics(c)
        val w = m.widthPixels / 2 * 2
        val h = m.heightPixels / 2 * 2

        val rec = try {
            buildRecorder(c, mp, w, h, true)
        } catch (t: Throwable) {
            try { buildRecorder(c, mp, w, h, false) } catch (t2: Throwable) {
                onEvent(false, "Ошибка рекордера: ${t2.message}")
                return
            }
        }

        try {
            val surface = rec.surface
            recDisplay = mp.createVirtualDisplay(
                "tower-rec", w, h, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
            rec.start()
        } catch (t: Throwable) {
            try { rec.release() } catch (t2: Throwable) { }
            try { recDisplay?.release() } catch (t2: Throwable) { }
            recDisplay = null
            onEvent(false, "Не удалось начать запись: ${t.message}")
            return
        }
        recorder = rec
        recording = true
        recStartedAt = System.currentTimeMillis()
        onEvent(true, "Запись экрана начата")
    }

    fun stopRecording(c: Context): android.net.Uri? {
        val rec = recorder ?: return null
        recording = false
        try { rec.stop() } catch (t: Throwable) { }
        try { rec.reset() } catch (t: Throwable) { }
        try { rec.release() } catch (t: Throwable) { }
        recorder = null
        try { recDisplay?.release() } catch (t: Throwable) { }
        recDisplay = null
        val uri = recUri
        recUri = null
        if (uri != null && Build.VERSION.SDK_INT >= 29) {
            try {
                val v = ContentValues()
                v.put(MediaStore.Video.Media.IS_PENDING, 0)
                c.contentResolver.update(uri, v, null, null)
            } catch (t: Throwable) { }
        }
        return uri
    }

    private fun buildRecorder(
        c: Context,
        mp: MediaProjection,
        w: Int,
        h: Int,
        withAudio: Boolean
    ): MediaRecorder {
        val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else MediaRecorder()
        try {
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (withAudio && Build.VERSION.SDK_INT >= 29) {
                try {
                    mr.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                    val cfg = android.media.AudioPlaybackCaptureConfiguration.Builder(mp)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                        .build()
                    mr.setAudioPlaybackCaptureConfig(cfg)
                } catch (t: Throwable) {
                    // Внутренний звук не поддерживается — пишем только видео
                    mr.reset()
                    mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                }
            }
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setVideoSize(w, h)
            mr.setVideoFrameRate(30)
            mr.setVideoEncodingBitRate(8_000_000)
            if (withAudio && Build.VERSION.SDK_INT >= 29) {
                try {
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mr.setAudioSamplingRate(48_000)
                    mr.setAudioEncodingBitRate(128_000)
                } catch (t: Throwable) { }
            }
            val uri = newVideoUri(c)
            recUri = uri
            val pfd = c.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("no fd")
            mr.setOutputFile(pfd.fileDescriptor)
            mr.prepare()
            pfd.close()
            return mr
        } catch (t: Throwable) {
            try { mr.release() } catch (t2: Throwable) { }
            throw t
        }
    }

    private fun newVideoUri(c: Context): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Tower_${stamp()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Tower")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        return c.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("cannot create video uri")
    }

    fun release() {
        try { recorder?.stop() } catch (t: Throwable) { }
        try { recorder?.release() } catch (t: Throwable) { }
        recorder = null
        try { recDisplay?.release() } catch (t: Throwable) { }
        recDisplay = null
        try { projection?.stop() } catch (t: Throwable) { }
        projection = null
        recording = false
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
