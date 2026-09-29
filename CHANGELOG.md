# Changelog

## 2.3 - 2026-09-29

Version code 11.

### Added

- **Android Auto media screen.** RoadBrowser now also appears in Android Auto's media section, which stays usable while the car is moving (the browser itself stays behind Android Auto's "restricted while driving" screen). The list offers the page you last played, your quick links and your bookmarks; picking one plays it in a windowless background player, so nothing from the page is ever drawn on the car screen. Play, pause, next and previous work from Android Auto's player and the steering wheel. Only pages in that list can be played, and only Android Auto, the system media controls and RoadBrowser itself can read it.
- **Voice requests.** "Play *name* on RoadBrowser" plays the best-matching quick link or bookmark; "Play RoadBrowser" resumes the last page.
- **Legal pages.** Privacy policy, terms of use, driving safety and open-source notices, readable offline under **Settings > Legal** and in `docs/legal/`. `scripts/build_legal.py` regenerates the in-app copies from the Markdown.
- Unit tests for the Android Auto list, the media service's hand-over between the browser tabs and the car player, the legal pages, and layout fit across screen sizes (Robolectric).

### Changed

- New look: cream paper, warm ink and an instrument-cluster amber accent, set in Overpass, a typeface drawn from highway signage (replaces Manrope).
- The toolbar and start page resize live when the window changes (split screen, different head units) instead of keeping the size they launched with, and the quick-link grid always divides evenly.
- Closing the browser no longer stops audio that was started from Android Auto's media screen.

## 2.2 (RoadBrowser) - 2026-09-21

First release under the RoadBrowser name. Version code 10.

### Changed

- Renamed and rebranded the app to **RoadBrowser** under the new package id `com.yashmaurya.roadbrowser`. New package id, so it installs as a fresh app.
- New app icon.
- Car-first bottom toolbar: Back, Home, address pill, Reload, Bookmarks, Tabs (with count) and Menu sit along the bottom edge of the head-unit display with large touch targets.
- Brave Search is now the default search engine, alongside Google and DuckDuckGo.
- Removed third-party analytics and the sponsor / donation features. The app no longer makes any network requests of its own.

### Added

- Shields: Brave-Shields-style ad and tracker blocking using a bundled domain blocklist, with a Settings toggle and a live blocked-count indicator.
- MediaSession foreground service. While a page is playing audio or video, RoadBrowser holds a real `MediaSession` backed by a `mediaPlayback` foreground service, so steering-wheel next / previous / play / pause buttons and the phone's media notification control the page, and the browser survives being covered by Maps or the launcher instead of being frozen by the OS.
- Background playback visibility shim: when "Keep audio playing in background" is on, the WebView reports itself visible and a page shim pins `document.hidden` / `visibilityState`, so YouTube and similar sites no longer pause themselves when another Android Auto app takes the screen.

### Fixed

- YouTube playback and in-car lifecycle fixes: fullscreen video no longer collapses when the app briefly loses focus, exiting fullscreen always clears the keep-screen-on flag, Widevine permission grants are no longer dropped on a detached view, and blocked requests return a real `204` so players don't hang.
- Links from other apps now open in the browser (the `http`/`https` intent-filter was missing `BROWSABLE`).
- `target="_blank"` links and `window.open()` calls open as new tabs instead of being silently dropped.
- Inactive tabs are suspended as soon as they stop being the active tab, so they no longer keep playing audio indefinitely.

## Earlier

### Added

- Shields: Brave-Shields-style ad and tracker blocking using a bundled domain blocklist, with a Settings toggle and a live blocked-count indicator.
- Brave Search as a selectable search engine, alongside Google and DuckDuckGo.
- YouTube compatibility mode: forces the mobile YouTube interface and keeps playback-critical requests unblocked so videos load reliably.
- Background audio playback: audio can keep playing when the browser is not in the foreground, with a Settings toggle.
- Background playback now also survives the page being told it is hidden: when background audio is on, the WebView reports itself visible and a page shim pins `document.hidden`/`visibilityState`, so YouTube no longer pauses itself when Maps, the launcher or the assistant takes over the head unit.
- Pop-ups open as new tabs: links that try to open a new window become a new tab instead of being ignored, with a Settings toggle.
- Unit tests covering ad-blocker and YouTube host matching, including lookalike hosts such as `youtube.com.evil.example`.

### Fixed

- Fullscreen video no longer collapses back to inline playback when the app loses focus (e.g. switching to another Android Auto app briefly).
- Links from other apps now actually open the browser: the `http`/`https` `VIEW` intent-filter was missing the `BROWSABLE` category, so the existing link-handling code could never be reached.
- `target="_blank"` links and `window.open()` calls are no longer silently dropped; `onCreateWindow` returned `false` while multiple-window support was enabled.
- Blocked requests now return a real `204 No Content` instead of an empty `200`, which could hang players waiting on a parseable response.
- Widevine (protected media) permission grants are no longer posted to a possibly-detached view, where they could be dropped.
- Exiting fullscreen is now idempotent and always clears `FLAG_KEEP_SCREEN_ON`, so a teardown mid-video cannot leave the head-unit screen pinned on.
- Inactive tabs no longer keep playing audio indefinitely; a tab is suspended as soon as it stops being the active one. This is independent of the background-audio setting, which governs the app as a whole rather than individual tabs.

### Changed

- Settings: added a "Media & playback" section grouping the new YouTube compatibility, background audio, and pop-up tab toggles.
