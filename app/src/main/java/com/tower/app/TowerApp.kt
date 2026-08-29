package com.tower.app

import android.app.Application
import android.content.Context

class TowerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ctx = this
        Prefs.init(this)
        Notif.createChannel(this)
    }

    companion object {
        @Volatile
        var ctx: Context? = null
            private set
    }
}

fun appCtx(): Context = TowerApp.ctx ?: throw IllegalStateException("TowerApp not ready")
