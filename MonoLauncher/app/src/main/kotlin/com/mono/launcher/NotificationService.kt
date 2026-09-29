package com.mono.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * In-memory only: holds whatever notifications are currently active so the
 * home screen can show a one-line preview under a favorite. Nothing here is
 * written to disk. Requires the user to grant "Notification access" once,
 * from Settings > Apps > Special access (Mono links straight there).
 */
object NotificationTracker {
    private val byKey = LinkedHashMap<String, StatusBarNotification>()
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun put(sbn: StatusBarNotification) {
        byKey[sbn.key] = sbn
        fireListeners()
    }

    @Synchronized
    fun remove(key: String) {
        byKey.remove(key)
        fireListeners()
    }

    @Synchronized
    fun clearAll() {
        byKey.clear()
        fireListeners()
    }

    @Synchronized
    fun peek(packageName: String): StatusBarNotification? =
        byKey.values.lastOrNull { it.packageName == packageName }

    @Synchronized
    fun hasAny(packageName: String): Boolean =
        byKey.values.any { it.packageName == packageName }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun fireListeners() { listeners.toList().forEach { it() } }
}

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
            // Nothing active yet on some OEM skins right after connect; safe to ignore.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationTracker.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationTracker.clearAll()
    }

    private fun handle(sbn: StatusBarNotification) {
        // Ongoing media-transport notifications are read through MediaSessionManager
        // instead (see HomeWidgets.kt) — skip them here to avoid a duplicate row.
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationTracker.put(sbn)
    }
}
