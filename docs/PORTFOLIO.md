# RoadBrowser — Project Case Study

**Yash V Maurya** · Android / Kotlin · 2026
Source: https://github.com/Yash-v-maurya/RoadBrowser · Download: https://github.com/Yash-v-maurya/RoadBrowser/releases/latest

---

## One-line summary

A car-first web browser for Android Auto head units, with a media-session-backed playback engine that keeps YouTube playing while Maps owns the screen, steering-wheel controls, and a premium warm-white design system.

## The problem

Android Auto only projects a handful of app categories, and none of them is a general web browser. Sideloaded browsers that do run on a head unit are built for phones: tiny targets, top-anchored URL bars, and a WebView that Android pauses the moment navigation covers it — so a YouTube video dies every time the driver glances at the map.

## What I built

I designed and shipped:

- **Car-first UI.** A bottom toolbar (Back, Home, address pill, Reload, Bookmarks, Tabs with count, Menu) sized for 800×480 head units; a start page with four-across quick-link tiles and a "continue where you left off" card; landscape and sw600dp dimension sets.
- **Playback lifecycle engine.** A `BackgroundPlaybackWebView` that spoofs window visibility, plus a page-injected shim that pins `document.hidden = false` and swallows `visibilitychange`/`pagehide` — so sites never see themselves go to the background.
- **MediaSession foreground service.** A `mediaPlayback` foreground service with a platform `MediaSession`. A document-start JS bridge wraps `navigator.mediaSession.setActionHandler`, so steering-wheel *next/previous/play/pause* invoke the page's own handlers (YouTube's playlist navigation works), with a `<video>` fallback. The service also keeps the process out of the OEM app freezer.
- **YouTube compatibility mode.** Pins YouTube to the mobile identity even in desktop mode, auto-dismisses consent/unsupported-browser banners, and exempts `googlevideo.com` from ad blocking.
- **Brave Search + Shields.** Brave as the default engine and a bundled ad/tracker blocklist with a live blocked counter.
- **Design system.** Warm-white / charcoal / champagne-gold palette (no blue), Manrope variable font wired into all 15 Material 3 text roles, soft 12–32 dp shape scale, flat hairline-outlined cards, matching adaptive launcher icon (a road running into a browser window) and a monochrome themed-icon layer.
- **Privacy.** Removed all third-party analytics; the only network traffic is the pages you open.
- **Release engineering.** Signed release builds with a local keystore, versioned APK naming, GitHub Actions CI (debug build + unit tests on every push) and a tag-triggered signed-release workflow that publishes the APK to GitHub Releases. Obtainium deep link for auto-updates.

## Technical highlights

| Area | Detail |
|---|---|
| Language / min SDK | Kotlin, minSdk 35 (Android 15), targetSdk 37 |
| UI | ViewBinding, Material 3, ConstraintLayout, RecyclerView with drag-to-reorder tiles |
| Web | `android.webkit.WebView` + AndroidX WebKit (document-start scripts, `WebMessageListener`, UA client hints) |
| Media | `android.media.session.MediaSession`, `Notification.MediaStyle`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| Car | `androidx.car.app`, `CAR_LAUNCHER` / `APP_MAPS` intent categories, `distractionOptimized` metadata |
| Build | Gradle 9 / AGP 9, R8 minification, custom APK rename task |
| CI/CD | GitHub Actions (JDK 21, Android SDK, cached Gradle), base64-encoded keystore secret |

## Hard problems and how I solved them

**1. Video stopped when Android Auto showed Maps.**
Two layers of the stack were pausing playback independently: `WebView.onPause()` from the Activity lifecycle, and Blink's Page Visibility API flipping to `hidden`. Fixing only the first still left YouTube's own JS pausing on `visibilitychange`. The fix was a `WebView` subclass that reports `VISIBLE` on `onWindowVisibilityChanged` plus a capture-phase JS shim that pins visibility and stops the events before page listeners run — while still letting element-level `blur` through so inputs work.

**2. Steering-wheel buttons did nothing.**
Chromium takes audio focus for the page, but exposes no `MediaSession` to the OS from a WebView. I added a foreground service owning a session and forward its callbacks into the page. Instead of guessing at DOM selectors, the shim captures the handlers the page registers via `navigator.mediaSession.setActionHandler`, so "next track" is exactly what YouTube would do on its own.

**3. The process got frozen by the OEM battery manager.**
On ColorOS a plain background app is frozen after a few minutes covered. A `mediaPlayback` foreground service is the sanctioned way to say "I'm playing audio"; the service is started from the page's `play` event, promotes itself with `startForeground(…, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`, and handles `ForegroundServiceStartNotAllowedException` cleanly when the OS refuses.

**4. Light theme rendered with dark widgets.**
`uiMode` is in `configChanges` so the Activity is never recreated on a night-mode change (needed on head units). Calling `setDefaultNightMode()` in `onCreate` was therefore too late — the views were already inflated with the system's dark colours. Moving the call into `Application.onCreate()` fixed it.

## Outcome

- Runs on an Android 16 OnePlus 12R and projects to Android Auto; YouTube keeps playing under Maps and responds to media-button presses.
- Zero third-party trackers; ~6.7 MB signed APK.
- Reproducible icon pipeline (`scripts/render_icon.py` → vector drawables + all mipmaps) and one-command release via git tag.

## Links

- Repository: https://github.com/Yash-v-maurya/RoadBrowser
- Latest APK: https://github.com/Yash-v-maurya/RoadBrowser/releases/latest
- Install guide: `docs/INSTALL.md`
