package com.tower.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.tower.app.Prefs
import com.tower.app.overlay.FloatingService

/**
 * Дополнительный «доступ» (Accessibility Service).
 *
 * Зачем он нужен:
 *  1. Позволяет рисовать панель системным слоем TYPE_ACCESSIBILITY_OVERLAY —
 *     она остаётся поверх любых приложений, игр и системных окон.
 *  2. Система сама перезапускает связанный сервис, поэтому панель «воскресает»
 *     даже после выгрузки процесса.
 *  3. Даёт работу функции «Блокировка касаний» и удержание экрана.
 *
 * Сервис НЕ читает текст с экрана, НЕ перехватывает ввод и ничего не отправляет.
 */
class TowerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        Prefs.init(this)
        if (Prefs.resumeOnUnlock) {
            runCatching { FloatingService.start(this) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Не используется — сервис нужен только для системного слоя и живучести.
    }

    override fun onInterrupt() {
        // Не используется.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var connected: Boolean = false
    }
}
