package com.tower.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notif {

    const val CHANNEL_MAIN = "tower_main"
    const val CHANNEL_CAPTURE = "tower_capture"
    const val ID_MAIN = 4242
    const val ID_REC = 4243
    const val ID_CAPTURE = 4244

    private val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    fun createChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MAIN, "Tower — панель", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Плавающая панель Tower активна"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURE, "Tower — захват экрана", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Запись и снимки экрана"
                setShowBadge(false)
            }
        )
    }

    private fun mainPi(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 7,
            Intent(c, com.tower.app.ui.MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PI_FLAGS
        )

    private fun svcPi(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            c, code,
            Intent(c, com.tower.app.overlay.FloatingService::class.java).setAction(action),
            PI_FLAGS
        )

    fun main(c: Context, text: String = c.getString(R.string.notif_text)): Notification =
        NotificationCompat.Builder(c, CHANNEL_MAIN)
            .setSmallIcon(R.drawable.ic_stat_tower)
            .setContentTitle(c.getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPi(c))
            .addAction(
                android.R.drawable.ic_menu_view,
                c.getString(R.string.notif_action_open),
                mainPi(c)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                c.getString(R.string.notif_action_stop),
                svcPi(c, com.tower.app.overlay.FloatingService.ACTION_STOP, 11)
            )
            .build()

    fun recording(c: Context, seconds: Long): Notification =
        NotificationCompat.Builder(c, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_tower)
            .setContentTitle(c.getString(R.string.notif_recording))
            .setContentText("Идёт запись: ${fmt(seconds)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPi(c))
            .build()

    fun captureConsent(c: Context): Notification {
        val i = Intent(c, com.tower.app.ui.CaptureActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return NotificationCompat.Builder(c, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_tower)
            .setContentTitle(c.getString(R.string.notif_capture_title))
            .setContentText(c.getString(R.string.notif_capture_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(c, 9, i, PI_FLAGS))
            .build()
    }

    private fun fmt(s: Long): String = "%02d:%02d".format(s / 60, s % 60)
}
