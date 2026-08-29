package com.tower.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tower.app.Prefs
import com.tower.app.overlay.FloatingService

/** Автозапуск панели после перезагрузки (если включено в настройках). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED &&
            a != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        Prefs.init(c)
        if (Prefs.autoStartBoot) {
            runCatching { FloatingService.start(c) }
        }
    }
}
