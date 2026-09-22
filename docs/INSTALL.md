# Installing RoadBrowser

This guide walks through getting RoadBrowser onto your phone and showing it on the Android Auto head unit. It takes about five minutes.

**Requirements**

- A phone running **Android 15 or later**.
- A car (or aftermarket head unit) that supports Android Auto.
- The RoadBrowser APK from the [latest GitHub release](https://github.com/Yash-v-maurya/RoadBrowser/releases/latest). The file is named `RoadBrowser-<version>.apk`, for example `RoadBrowser-2.2.apk`.

Only download the APK from `https://github.com/Yash-v-maurya/RoadBrowser`. See the Security section of the README.

---

## 1. Install the APK on the phone

### Option A: from the phone

1. Open the [latest release](https://github.com/Yash-v-maurya/RoadBrowser/releases/latest) in the phone's browser and download the `.apk` file.
2. Open the downloaded file from the browser's download list or from your Files app.
3. If Android asks, allow that app (your browser or Files) to **install unknown apps**. This is a one-time prompt per app.
4. Tap **Install**. If Play Protect shows a warning, choose **Install anyway** (RoadBrowser is not distributed through the Play Store, so Play Protect doesn't recognise it).
5. Tap **Done**. You should now see RoadBrowser in the phone's app drawer. Open it once on the phone so it can set itself up.

### Option B: with adb from a computer

If you have [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) installed and USB debugging enabled on the phone:

```
adb install -r RoadBrowser-2.2.apk
```

`-r` reinstalls over an existing copy while keeping its data. Adjust the file name for the version you downloaded.

### Option C: Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) can install RoadBrowser and keep it updated automatically. Tap the "Get it on Obtainium" button in the README, or add the app manually with the source URL `https://github.com/Yash-v-maurya/RoadBrowser`.

---

## 2. Let Android Auto show non-Play-Store apps

Android Auto only lists apps that came from the Play Store unless you turn on its hidden **Unknown sources** switch.

1. On the phone, open **Settings** and search for **Android Auto** (or find it under Connected devices > Connection preferences > Android Auto).
2. Scroll all the way down to the **Version** entry.
3. Tap **Version** ten times in a row. A dialog asks whether to enable developer settings; tap **OK**.
4. Tap the **three-dot menu (⋮)** in the top-right corner and choose **Developer settings**.
5. Scroll down and turn on **Unknown sources**.
6. Go back, and **force-stop Android Auto**: phone Settings > Apps > Android Auto > Force stop. (Unplugging and reconnecting the phone also works.)

Connect the phone to the car. RoadBrowser now appears in the head unit's app launcher.

### If RoadBrowser doesn't open the first time

Some head units refuse to start a freshly installed app until another third-party app has run. Open a non-Google navigation app on the head unit (for example Waze), then go back to the launcher and open RoadBrowser. This is usually only needed once.

---

## 3. OnePlus / ColorOS / OxygenOS: stop the OS from freezing the browser

OnePlus, Oppo and Realme phones aggressively freeze background apps. That kills background audio a few minutes after you switch to Maps. To prevent it:

1. Phone Settings > **Apps** > **RoadBrowser** (or App management > RoadBrowser).
2. Tap **Battery** (or Battery usage / Power consumption).
3. Choose **Don't optimize** or **Unrestricted** / **Allow background activity**.
4. On some ColorOS versions there is an additional **Allow auto-launch** toggle under the same screen; turn it on as well.

Other manufacturers (Samsung, Xiaomi) have similar settings under Battery > Background usage limits; RoadBrowser should not be in any "sleeping apps" list.

RoadBrowser also holds a media-playback foreground service while something is playing, which is what shows the media notification and the steering-wheel controls. That service is what the battery allowlist protects.

---

## 4. Recommended settings once it's running

Open RoadBrowser on the head unit, tap the **Menu** button on the toolbar, then **Settings**.

- **Media & playback > YouTube compatibility:** on (default).
- **Media & playback > Keep audio playing in background:** on (default). Required for playback to continue while Maps is on screen and for steering-wheel buttons to work.
- **Shields > Block ads and trackers:** on (default).
- **Appearance > Theme:** Dark or Auto; Dark is a true-black theme that suits car displays at night.
- **Display scale:** raise it if the toolbar feels small on a large screen.

---

## 5. Updating

Every RoadBrowser release is signed with the same key, so a new version installs straight over the old one and keeps your bookmarks, tabs and settings.

- **From the phone:** download the new APK from the releases page and install it the same way as before. Android shows "Update" instead of "Install".
- **With adb:** `adb install -r RoadBrowser-<new-version>.apk`.
- **With Obtainium:** updates are found and installed automatically.

You don't need to repeat the Android Auto unknown-sources steps after an update.

---

## Troubleshooting

| Symptom | Try |
| --- | --- |
| Not listed in the head unit launcher | Confirm Unknown sources is on in Android Auto developer settings, then force-stop Android Auto and reconnect. |
| Opens on the phone but not in the car | Open Waze (or another non-Google navigation app) on the head unit first, then RoadBrowser. |
| Audio stops a few minutes after switching to Maps | Set the battery mode to Don't optimize / Unrestricted (section 3) and check that Keep audio playing in background is on. |
| Steering-wheel buttons do nothing | Keep audio playing in background must be on; start playback once from the page so the media session becomes active. |
| "App not installed" when updating | The APK is from a different signing key (for example a self-built copy over a release build). Uninstall the old copy first; bookmarks will be lost. |
| YouTube shows the desktop site or won't play | Turn YouTube compatibility on and turn desktop mode off from the Menu. |
