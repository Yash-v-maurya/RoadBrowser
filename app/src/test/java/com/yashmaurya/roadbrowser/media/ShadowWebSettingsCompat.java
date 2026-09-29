package com.yashmaurya.roadbrowser.media;

import android.webkit.WebSettings;
import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Robolectric has no WebView provider behind androidx.webkit, so these setters report the
 * feature as available and then throw. The browser setup only needs them to be callable.
 */
@Implements(WebSettingsCompat.class)
public class ShadowWebSettingsCompat {

    @Implementation
    public static void setAlgorithmicDarkeningAllowed(WebSettings settings, boolean allow) {
    }

    @Implementation
    public static void setUserAgentMetadata(WebSettings settings, UserAgentMetadata metadata) {
    }
}
