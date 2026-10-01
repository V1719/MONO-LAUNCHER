[README.md](https://github.com/user-attachments/files/32881591/README.md)
# Mono — a real Android launcher

A minimalist home-screen launcher in the spirit of Niagara Launcher: an
alphabetical app list instead of a grid, favorites on home, live notification
previews, a Now Playing card, a next-calendar-event line, and wallpaper-based
accent theming. It's a genuine native app — set it as your default launcher
and it replaces your home screen for real, not a mockup.

## Open it

1. Install **Android Studio** (free — Ladybug/2024.x or newer).
2. **File > Open**, pick this `MonoLauncher` folder.
3. Android Studio will notice there's no Gradle wrapper jar and offer to
   create one — accept that. (I can write text-only project files in this
   environment; a compiled wrapper `.jar` binary isn't something I can
   produce, so Android Studio generates it for you on first open — one
   click, completely normal.)
4. It'll probably also prompt to update AGP/Gradle/Kotlin a minor version or
   two past what's pinned here — that's fine, accept it.
5. Plug in a phone (USB debugging on) or start an emulator, hit **Run**.

## Make it your home screen

Install it, press the device Home button, and Android will ask which
launcher to use — pick **Mono**. (Or: Settings → Apps → Default apps →
Home app.) To go back to your old launcher later, the same setting switches
back — nothing is destructive.

## What's included

- **Alphabetical app list** with a tappable A–Z index on the right, live
  search, favorites pinned to home — the core Niagara mechanic.
- **Notification previews** — once you grant notification access, the most
  recent notification for a favorite shows as a subtitle right on the home
  row, no need to open the app.
- **Now Playing card** — shows the active media session (title/artist) with
  play/pause, the same way Niagara surfaces whatever's playing.
- **Next calendar event** — one line under the date, if you grant calendar
  access.
- **Wallpaper-adaptive accent color** — pulls a vibrant color off your
  current wallpaper (via the Palette library) and uses it for the accent
  throughout, instead of a fixed color.
- **Swipe up** opens All apps (or tap the "All apps" text); **swipe down**
  from the top expands the system notification shade.
- **Hide apps** / **unhide** from a small link that appears once you've
  hidden something; **app info** and **uninstall** from each app's ⋯ menu.
- App list refreshes live on install/uninstall. Dark mode follows the
  system. Everything — favorites, hidden apps — is stored locally in
  SharedPreferences; nothing leaves the phone.
- The PackageManager scan and the wallpaper color extraction both run off
  the main thread, so opening the list and the app itself starting up
  shouldn't stutter even on a big app catalog.

## What's simplified (be aware of these)

- **Swipe-down-to-expand-notifications** calls a real but *undocumented*
  System API (`StatusBarManager.expandNotificationsPanel`), gated behind the
  `EXPAND_STATUS_BAR` permission. It's the same trick most third-party
  launchers use, and it's wrapped to fail silently rather than crash — but
  Google could block it in a future Android release without warning.
- **Notification previews** show the single most recent notification per
  app, not a full swipe-to-reply inbox — replying inline would mean
  building a whole `RemoteInput` UI, which felt like scope for a v2 rather
  than v1.
- **No icon-pack support, no home-screen widgets, no "pop-up" shortcuts
  pinned to a favorite.** These are the more involved Niagara Pro features
  (parsing third-party icon-pack APKs, hosting `AppWidgetHost`, a whole
  secondary-shortcut system) — all buildable, just meaningfully more work
  than the rest of this combined, so they're left out rather than rushed.
- Dependency versions (AndroidX, Material, coroutines) are pinned to
  specific numbers that were current when this was written — if Android
  Studio's dependency-upgrade lightbulb suggests a newer one, that's normal
  drift, not a bug; safe to accept.

## Toolchain this was written against

AGP 9.2.0 · Kotlin (AGP 9's built-in Kotlin, no separate plugin needed) ·
Gradle 9.4.1 · compileSdk/targetSdk 36 · minSdk 28 (Android 9+).
