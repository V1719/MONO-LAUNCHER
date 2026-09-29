package com.mono.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The closest safe equivalent to Niagara's wallpaper-adaptive theming:
 * pulls a vibrant/dominant swatch off the current wallpaper via the Palette
 * library. Falls back to the fixed accent color if anything goes wrong —
 * this is best-effort styling, never load-bearing.
 */
object WallpaperTheme {
    fun extractAccent(context: Context, fallback: Int): Int = try {
        val drawable = WallpaperManager.getInstance(context).drawable as? BitmapDrawable
        val bitmap = drawable?.bitmap
        if (bitmap != null) {
            val palette = Palette.from(bitmap).generate()
            palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: fallback
        } else fallback
    } catch (e: Exception) {
        fallback
    }
}

/** Reads the next upcoming event off the device calendar, if permission was granted. */
object CalendarHelper {
    data class NextEvent(val title: String, val whenText: String)

    fun nextEvent(context: Context): NextEvent? {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, now + 7L * 24 * 60 * 60 * 1000)
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)

        return try {
            context.contentResolver.query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val title = c.getString(0) ?: return null
                        val begin = c.getLong(1)
                        NextEvent(title, SimpleDateFormat("EEE, HH:mm", Locale.getDefault()).format(Date(begin)))
                    } else null
                }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Surfaces whatever's currently playing, the same way Niagara pins the active
 * player to the top. Piggybacks on the notification-listener permission,
 * since that's what MediaSessionManager requires to see active sessions.
 */
object MediaHelper {
    data class ActiveMedia(val title: String, val artist: String, val isPlaying: Boolean, val controller: MediaController)

    fun activeMedia(context: Context): ActiveMedia? = try {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, NotificationService::class.java)
        val sessions = msm.getActiveSessions(component)
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        controller?.metadata?.let { md ->
            ActiveMedia(
                title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                controller = controller
            )
        }
    } catch (e: SecurityException) {
        null // Notification access not granted yet.
    } catch (e: Exception) {
        null
    }
}

/**
 * Expands the system notification shade for the swipe-down gesture. This
 * calls a documented-but-hidden StatusBarManager method (the same trick most
 * third-party launchers use) gated behind the EXPAND_STATUS_BAR permission.
 * It's not a stable public API, so it's wrapped to fail silently rather than
 * crash if a future Android release blocks it.
 */
object StatusBarHelper {
    fun expandNotifications(context: Context): Boolean = try {
        val service = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandNotificationsPanel").invoke(service)
        true
    } catch (e: Exception) {
        false
    }
}
