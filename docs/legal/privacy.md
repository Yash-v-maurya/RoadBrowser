# Privacy Policy

**Effective 29 September 2026**

RoadBrowser is a web browser for Android phones and Android Auto, made by Yash V Maurya. This policy explains what happens to your information when you use it.

**The short version:** RoadBrowser has no accounts, no analytics, no advertising and no servers. The developer never receives any of your data. What you do in the browser stays on your phone, apart from what websites receive when you visit them.

## 1. What the developer collects

Nothing. RoadBrowser contains no analytics, crash reporting, advertising or tracking code, and it never sends anything to the developer or to any server the developer runs.

## 2. What is stored on your phone

To work as a browser, RoadBrowser keeps the following in its private storage on your phone. Other apps can't read it, and it is never uploaded by the app.

- **Bookmarks and quick links** you save.
- **Open tabs** (their addresses and titles), so they can be restored the next time you open the app.
- **The last page you visited** and **the last page that played audio**, used for "resume last page" and for "continue" in Android Auto.
- **Your settings**, such as theme, display scale, search engine and playback options.
- **Site permission choices**: the websites you allowed to use the microphone or your location, plain-HTTP sites you allowed, and client certificates you chose for automatic sign-in.
- **Site icons** downloaded for your quick links and bookmarks.
- **Website data** that sites store through Android System WebView: cookies, cache and local storage.
- **A reference to your start-page background photo**, if you picked one. The app stores a link to the image you chose, not a copy.

## 3. Connections the app makes

RoadBrowser only goes online in these cases:

- **Websites you open.** Your phone connects directly to each site and to anything that page loads (images, scripts, video). Those sites receive your IP address, your browser details (the user agent), cookies they set and anything you type into them. Their own privacy policies apply. Shields blocks known ad and tracker domains, but no blocker catches everything.
- **Search.** Text you type in the address bar that isn't a web address goes to the search engine you picked: Brave Search by default, or Google or DuckDuckGo.
- **Site icons.** To show icons on quick links, bookmarks and the Android Auto list, the app downloads each icon directly from that site.
- **Update check.** Only when you open "Check for updates" in the menu, the app asks GitHub's public API for the latest release. GitHub receives your IP address, and GitHub's privacy statement applies.

The app makes no other connections of its own.

## 4. Android and Google services involved

Some parts of the browser are provided by Android and Google, and their own privacy terms apply:

- **Android System WebView** draws web pages. Safe Browsing is switched on, so WebView checks the addresses you visit against Google Safe Browsing and warns you about dangerous sites.
- **Voice input.** If a website offers voice input and you allow the microphone for that site, your speech goes to your phone's speech recognition service (often Google), which turns it into text for the page.
- **Location.** A website only gets your location if it asks and you allow it for that site. The location comes from Android.
- **Protected video (DRM).** To play protected video, Widevine licence requests go to the content provider's servers.
- **Android Auto.** When your phone is connected to a car, Android Auto shows the app on the car screen. The title and artist of what is playing, and the names and icons in RoadBrowser's Android Auto list (last played page, quick links and bookmarks), are passed to Android Auto and the car screen so they can be shown and controlled. Playback controls also appear in your phone's notifications and on the lock screen.
- **Backups.** If device backup is switched on, Android may include RoadBrowser's data (bookmarks, settings, tabs) in the backup saved to your Google account. You can control this in Android's backup settings.

## 5. Permissions and why they are needed

| Permission | Why |
| --- | --- |
| Internet | To load web pages. |
| Microphone | Only for websites that use voice input, after you allow it for that site. |
| Location (precise and approximate) | Only for websites that ask for your location, after you allow it for that site. |
| Notifications | To show playback controls. |
| Foreground service (media playback) | Keeps audio playing and steering-wheel buttons working when the browser isn't on screen. |
| Change audio settings | Used for web audio and voice input. |
| Car app permissions | Declared so the app can run on an Android Auto car screen. |

## 6. Selling and sharing

The developer does not sell, rent or share personal data. Since nothing is collected, there is nothing to sell or share.

## 7. Deleting your data

- **Settings > Privacy and site data**: delete cookies and site data, reset saved site permissions, or reset the plain-HTTP sites you allowed.
- **Bookmarks and quick links**: remove them in the bookmark manager. **Tabs**: close them.
- **Everything**: in Android Settings, open Apps > RoadBrowser > Storage and clear storage, or uninstall the app.

The developer holds no data about you, so there is nothing to request, correct or delete on the developer's side. This applies to the rights you have under laws such as the GDPR, the CCPA and India's Digital Personal Data Protection Act.

## 8. Children

RoadBrowser is a general-purpose web browser and is not aimed at children under 13. It doesn't knowingly collect information from anyone.

## 9. Security

App data is kept in RoadBrowser's private storage, protected by Android. Pages loaded over plain HTTP are not encrypted, so RoadBrowser asks before opening them and warns you about certificate problems on HTTPS sites.

## 10. Data safety summary

In the terms used by the Google Play data safety form:

- **Data collected:** none.
- **Data shared with third parties:** none.
- **Encryption in transit:** not applicable, because no data is collected. The app's own update check uses HTTPS.
- **Data deletion:** you can delete all data in the app, or by clearing the app's storage.

## 11. Changes to this policy

When this policy changes, the new version ships with the app and is published in the project's source repository with a new effective date.

## 12. Contact

Questions about this policy: open an issue at [github.com/Yash-v-maurya/RoadBrowser/issues](https://github.com/Yash-v-maurya/RoadBrowser/issues).

See also: [Terms of Use](terms.md) · [Driving Safety](driving-safety.md) · [Open-Source Notices](notices.md)
