package com.mono.launcher

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mono.launcher.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var repo: AppRepository
    private lateinit var prefs: PrefsStore

    private val favoritesAdapter = AppListAdapter(::onAppClick, ::onAppMore)
    private val allAppsAdapter = AppListAdapter(::onAppClick, ::onAppMore)

    private var allApps: List<AppInfo> = emptyList()
    private var sheetOpen = false
    private var accentColor = 0

    private val packageReceiver = PackageChangeReceiver { refreshApps() }
    private val notifListener: () -> Unit = { refreshFavoritesOnly(); refreshMediaCard() }

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000)
        }
    }

    private val requestCalendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshNextEvent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        accentColor = ContextCompat.getColor(this, R.color.accent)

        repo = AppRepository(this)
        prefs = PrefsStore(this)

        binding.favoritesList.layoutManager = LinearLayoutManager(this)
        binding.favoritesList.adapter = favoritesAdapter
        binding.allAppsList.layoutManager = LinearLayoutManager(this)
        binding.allAppsList.adapter = allAppsAdapter

        binding.allAppsButton.setOnClickListener { openSheet() }
        binding.closeSheetButton.setOnClickListener { closeSheet() }
        binding.hiddenButton.setOnClickListener { showUnhideDialog() }

        binding.notifHint.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.notifHint.setOnLongClickListener {
            prefs.setDismissedNotifHint()
            binding.notifHint.visibility = View.GONE
            true
        }

        binding.mediaPlayPause.setOnClickListener {
            val media = MediaHelper.activeMedia(this) ?: return@setOnClickListener
            if (media.isPlaying) media.controller.transportControls.pause()
            else media.controller.transportControls.play()
            clockHandler.postDelayed({ refreshMediaCard() }, 250)
        }

        binding.homeContainer.setOnTouchListener(VerticalSwipeDetector(
            onSwipeUp = { openSheet() },
            onSwipeDown = { StatusBarHelper.expandNotifications(this) }
        ))

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderAllApps(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }

        updateClock()
        refreshWallpaperAccent()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        NotificationTracker.addListener(notifListener)
        clockHandler.post(clockTick)

        binding.notifHint.visibility =
            if (!notificationAccessGranted() && !prefs.dismissedNotifHint()) View.VISIBLE else View.GONE

        refreshApps()
        refreshNextEvent()
        refreshMediaCard()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(packageReceiver)
        NotificationTracker.removeListener(notifListener)
        clockHandler.removeCallbacks(clockTick)
    }

    @Suppress("MissingSuperCall", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (sheetOpen) closeSheet()
        // else: this is the launcher's home screen — back has nowhere to go, so no-op.
    }

    private fun notificationAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    // ---- App list ----

    private fun refreshApps() {
        lifecycleScope.launch {
            val fresh = repo.getAllAppsAsync()
            allApps = fresh.filterNot { prefs.getHidden().contains(it.id) }
            binding.hiddenButton.visibility = if (prefs.getHidden().isEmpty()) View.GONE else View.VISIBLE
            renderFavorites()
            renderAllApps(binding.searchBox.text?.toString().orEmpty())
        }
    }

    /** Cheaper refresh for notification-only changes: no PackageManager rescan. */
    private fun refreshFavoritesOnly() {
        renderFavorites()
    }

    private fun renderFavorites() {
        val favIds = prefs.getFavorites()
        val favApps = favIds.mapNotNull { id -> allApps.find { it.id == id } }
        binding.emptyHome.visibility = if (favApps.isEmpty()) View.VISIBLE else View.GONE
        binding.favoritesList.visibility = if (favApps.isEmpty()) View.GONE else View.VISIBLE
        favoritesAdapter.submit(favApps.map { Row.App(it) })
    }

    private fun renderAllApps(query: String) {
        val filtered = allApps
            .filter { it.label.contains(query, ignoreCase = true) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        val rows = mutableListOf<Row>()
        var lastLetter: String? = null
        for (app in filtered) {
            val letter = app.label.firstOrNull()?.uppercaseChar()?.let {
                if (it in 'A'..'Z') it.toString() else "#"
            } ?: "#"
            if (letter != lastLetter) {
                rows.add(Row.Header(letter))
                lastLetter = letter
            }
            rows.add(Row.App(app))
        }
        allAppsAdapter.submit(rows)
        renderLetterIndex(rows.filterIsInstance<Row.Header>().map { it.letter }.toSet())
    }

    private fun renderLetterIndex(present: Set<String>) {
        binding.letterIndex.removeAllViews()
        val letters = ('A'..'Z').map { it.toString() } + "#"
        for (letter in letters) {
            val tv = TextView(this)
            tv.text = letter
            tv.textSize = 9f
            tv.gravity = Gravity.CENTER
            val active = present.contains(letter)
            tv.setTextColor(if (active) accentColor else ContextCompat.getColor(this, R.color.text_muted))
            tv.alpha = if (active) 1f else 0.45f
            if (active) {
                tv.setOnClickListener {
                    val pos = allAppsAdapter.positionOf(letter)
                    if (pos >= 0) (binding.allAppsList.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(pos, 0)
                }
            }
            binding.letterIndex.addView(tv)
        }
    }

    // ---- Interactions ----

    private fun onAppClick(app: AppInfo) {
        repo.launch(app)
        if (sheetOpen) closeSheet()
    }

    private fun onAppMore(app: AppInfo, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val isFav = prefs.isFavorite(app.id)
        popup.menu.add(0, 1, 0, if (isFav) R.string.remove_from_home else R.string.add_to_home)
        popup.menu.add(0, 2, 1, R.string.hide_app)
        popup.menu.add(0, 3, 2, R.string.app_info)
        popup.menu.add(0, 4, 3, R.string.uninstall)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { prefs.toggleFavorite(app.id); renderFavorites() }
                2 -> { prefs.hide(app.id); refreshApps() }
                3 -> repo.openAppInfo(app)
                4 -> repo.uninstall(app)
            }
            true
        }
        popup.show()
    }

    private fun showUnhideDialog() {
        val hidden = prefs.getHidden()
        if (hidden.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val all = repo.getAllAppsAsync()
            val hiddenApps = all.filter { hidden.contains(it.id) }.sortedBy { it.label }
            val labels = hiddenApps.map { it.label }.toTypedArray()
            AlertDialog.Builder(this@HomeActivity)
                .setTitle(R.string.unhide_title)
                .setItems(labels) { _, which ->
                    prefs.unhide(hiddenApps[which].id)
                    refreshApps()
                }
                .show()
        }
    }

    // ---- Widgets: clock, calendar, media, wallpaper accent ----

    private fun updateClock() {
        val now = Date()
        binding.timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        binding.dateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    }

    private fun refreshNextEvent() {
        lifecycleScope.launch {
            val event = withContext(Dispatchers.IO) { CalendarHelper.nextEvent(this@HomeActivity) }
            if (event != null) {
                binding.nextEventText.text = "${event.whenText} · ${event.title}"
                binding.nextEventText.visibility = View.VISIBLE
            } else {
                binding.nextEventText.visibility = View.GONE
            }
        }
    }

    private fun refreshMediaCard() {
        lifecycleScope.launch {
            val media = withContext(Dispatchers.Default) { MediaHelper.activeMedia(this@HomeActivity) }
            if (media != null && (media.title.isNotBlank() || media.artist.isNotBlank())) {
                binding.mediaCard.visibility = View.VISIBLE
                binding.mediaTitle.text = media.title.ifBlank { getString(R.string.app_name) }
                binding.mediaArtist.text = media.artist
                binding.mediaPlayPause.setImageResource(if (media.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            } else {
                binding.mediaCard.visibility = View.GONE
            }
        }
    }

    private fun refreshWallpaperAccent() {
        lifecycleScope.launch {
            val fallback = ContextCompat.getColor(this@HomeActivity, R.color.accent)
            val color = withContext(Dispatchers.Default) { WallpaperTheme.extractAccent(this@HomeActivity, fallback) }
            accentColor = color
            binding.closeSheetButton.setTextColor(color)
            binding.nextEventText.setTextColor(color)
            renderLetterIndex(currentPresentLetters())
        }
    }

    private fun currentPresentLetters(): Set<String> =
        (0 until binding.letterIndex.childCount)
            .map { binding.letterIndex.getChildAt(it) as TextView }
            .filter { it.alpha == 1f }
            .map { it.text.toString() }
            .toSet()

    // ---- Sheet open/close ----

    private fun openSheet() {
        sheetOpen = true
        binding.sheet.visibility = View.VISIBLE
        binding.sheet.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        binding.searchBox.text?.clear()
    }

    private fun closeSheet() {
        sheetOpen = false
        binding.sheet.animate().translationY(3000f).setDuration(200).withEndAction {
            binding.sheet.visibility = View.INVISIBLE
        }.start()
    }

    /** One listener, both directions: swipe up opens All apps, swipe down opens the notification shade. */
    private class VerticalSwipeDetector(
        private val onSwipeUp: () -> Unit,
        private val onSwipeDown: () -> Unit
    ) : View.OnTouchListener {
        private var startY = 0f
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startY = event.y
                MotionEvent.ACTION_UP -> {
                    val delta = event.y - startY
                    if (delta < -100) onSwipeUp()
                    else if (delta > 100 && startY < 300) onSwipeDown()
                }
            }
            return false
        }
    }
}
