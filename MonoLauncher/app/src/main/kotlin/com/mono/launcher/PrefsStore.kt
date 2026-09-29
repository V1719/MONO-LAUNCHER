package com.mono.launcher

import android.content.Context

/**
 * Local, on-device store for favorites and hidden apps.
 * Deliberately dependency-free: a delimited string in SharedPreferences,
 * no JSON library, nothing leaves the phone.
 */
class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mono_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): List<String> =
        prefs.getString(KEY_FAVORITES, "")!!.split(SEP).filter { it.isNotBlank() }

    fun setFavorites(ids: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, ids.joinToString(SEP)).apply()
    }

    fun toggleFavorite(id: String) {
        val current = getFavorites().toMutableList()
        if (current.contains(id)) current.remove(id) else current.add(id)
        setFavorites(current)
    }

    fun isFavorite(id: String): Boolean = getFavorites().contains(id)

    fun getHidden(): Set<String> =
        prefs.getString(KEY_HIDDEN, "")!!.split(SEP).filter { it.isNotBlank() }.toSet()

    fun setHidden(ids: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, ids.joinToString(SEP)).apply()
    }

    fun hide(id: String) {
        setHidden(getHidden() + id)
        setFavorites(getFavorites().toMutableList().also { it.remove(id) })
    }

    fun unhide(id: String) {
        setHidden(getHidden() - id)
    }

    fun dismissedNotifHint(): Boolean = prefs.getBoolean(KEY_DISMISSED_HINT, false)
    fun setDismissedNotifHint() { prefs.edit().putBoolean(KEY_DISMISSED_HINT, true).apply() }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_DISMISSED_HINT = "dismissed_notif_hint"
        private const val SEP = "|~|"
    }
}
