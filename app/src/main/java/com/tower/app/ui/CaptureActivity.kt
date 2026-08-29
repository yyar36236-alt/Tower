package com.tower.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.tower.app.overlay.FloatingService

/**
 * Прозрачная активность: показывает системный диалог «Начать запись экрана?»
 * и передаёт результат в FloatingService.
 */
class CaptureActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
        } catch (t: Throwable) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            val i = Intent(this, FloatingService::class.java)
                .setAction(FloatingService.ACTION_MP)
                .putExtra(FloatingService.EXTRA_CODE, resultCode)
            if (data != null) i.putExtra(FloatingService.EXTRA_DATA, data)
            try {
                startService(i)
            } catch (t: Throwable) {
                try { androidx.core.content.ContextCompat.startForegroundService(this, i) }
                catch (t2: Throwable) { }
            }
        }
        finish()
    }

    companion object {
        private const val REQ = 7001
    }
}
