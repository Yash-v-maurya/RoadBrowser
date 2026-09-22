package com.yashmaurya.roadbrowser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.yashmaurya.roadbrowser.data.BrowserPreferences

class RoadBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MainActivity handles uiMode changes itself (configChanges) so it is never recreated when
        // the night mode flips; the mode therefore has to be decided before the first activity
        // inflates, otherwise a system-dark phone shows the light theme with dark widgets.
        AppCompatDelegate.setDefaultNightMode(BrowserPreferences.getThemeMode(this).nightMode)
    }
}
