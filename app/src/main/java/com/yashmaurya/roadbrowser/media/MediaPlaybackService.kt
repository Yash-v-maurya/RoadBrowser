package com.yashmaurya.roadbrowser.media

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences

/**
 * Foreground media-playback service that backs the browser's playback with a real
 * [MediaSession], and RoadBrowser's entry in Android Auto's media screen.
 *
 * Chromium takes audio focus for the page on its own, but without a session of our own the
 * head unit's steering-wheel buttons and the phone's media notification have nothing to talk
 * to, and — more importantly on a OnePlus — the process is just a background app the OS is free
 * to freeze once Maps has covered the browser for a while. Holding a `mediaPlayback` foreground
 * service while something is playing fixes both.
 *
 * As a [MediaBrowserService] it also lists [CarMediaCatalog] to Android Auto. The browser itself
 * is blocked while the car is moving, but Android Auto's own player stays usable, so picking a
 * page there plays it in [BackgroundWebPlayer] with nothing shown on screen.
 *
 * Two sources can feed the session: the browser tabs ([Source.BROWSER], driven by the activity
 * through [update]) and the car player ([Source.CAR_PLAYER]). Whichever started playing last owns
 * the session and receives the buttons; the other one is paused so two pages never play over
 * each other. The service shuts down after [PAUSED_TIMEOUT_MS] of being paused, when the user
 * hits Stop, or when the browser closes while it owns the session.
 */
class MediaPlaybackService : MediaBrowserService() {

    fun interface MediaActionHandler {
        fun onMediaAction(action: String)
    }

    enum class Source { BROWSER, CAR_PLAYER }

    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val stopWhenIdle = Runnable { shutDown() }
    private var isStarted = false
    private var state = PlaybackState.STATE_NONE
    private var title = ""
    private var artist = ""

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "RoadBrowser").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = forward("pause")
                override fun onSkipToNext() = forward("nexttrack")
                override fun onSkipToPrevious() = forward("previoustrack")
                override fun onFastForward() = forward("seekforward")
                override fun onRewind() = forward("seekbackward")
                override fun onStop() = shutDown()
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = playFromMediaId(mediaId)
                override fun onPlayFromSearch(query: String?, extras: Bundle?) = playFromSearch(query)
            })
            setSessionActivity(contentIntent())
        }
        sessionToken = session.sessionToken
        BackgroundWebPlayer.listener = BackgroundWebPlayer.Listener { playing, pageTitle, pageArtist, pageUrl ->
            update(this, Source.CAR_PLAYER, playing, pageTitle, pageArtist, pageUrl)
        }
        ensureChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStarted = true
        when (intent?.action) {
            ACTION_STOP -> {
                shutDown()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> applyUpdate(
                intent.getBooleanExtra(EXTRA_PLAYING, false),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_ARTIST).orEmpty()
            )
            ACTION_PLAY_FROM_SEARCH -> playFromSearch(intent.getStringExtra(EXTRA_QUERY))
            else -> if (state != PlaybackState.STATE_NONE) publishState()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        owner = Source.BROWSER
        handler.removeCallbacks(stopWhenIdle)
        BackgroundWebPlayer.listener = null
        BackgroundWebPlayer.release()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // The list holds the user's bookmarks, so only Android Auto, the system media controls
        // and RoadBrowser itself may browse it (the framework checks the package against the uid).
        val trusted = clientUid == Process.myUid() ||
            clientUid == Process.SYSTEM_UID ||
            clientPackageName in TRUSTED_BROWSER_PACKAGES
        return if (trusted) BrowserRoot(CarMediaCatalog.ROOT_ID, null) else null
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (!BrowserPreferences.hasAcceptedCurrentTerms(this)) {
            result.sendResult(mutableListOf(termsNotice()))
            return
        }
        result.detach()
        val appContext = applicationContext
        Thread {
            val items = runCatching { CarMediaCatalog.children(appContext, parentId) }
                .onFailure { Log.w(TAG, "Could not build media list for $parentId", it) }
                .getOrDefault(emptyList())
            result.sendResult(items.toMutableList())
        }.start()
    }

    private fun applyUpdate(playing: Boolean, newTitle: String, newArtist: String) {
        state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        title = newTitle
        artist = newArtist
        publishState()
    }

    /** Shown in Android Auto's list until the terms have been accepted on the phone. */
    private fun termsNotice(): MediaItem {
        val description = android.media.MediaDescription.Builder()
            .setMediaId("terms")
            .setTitle(getString(R.string.car_media_accept_terms_first))
            .build()
        return MediaItem(description, 0)
    }

    /** False (after telling Android Auto why) until the terms have been accepted on the phone. */
    private fun termsAccepted(): Boolean {
        if (BrowserPreferences.hasAcceptedCurrentTerms(this)) return true
        showError(getString(R.string.car_media_accept_terms_first))
        return false
    }

    /** Play button: resume whatever the session describes, or the last page when it's empty. */
    @VisibleForTesting
    internal fun resume() {
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
            if (!termsAccepted()) return
            CarMediaCatalog.lastPlayed(this)?.let { playInCar(it) }
            return
        }
        forward("play")
    }

    /** A page picked in Android Auto's list; anything not in [CarMediaCatalog] is ignored. */
    @VisibleForTesting
    internal fun playFromMediaId(mediaId: String?) {
        if (!termsAccepted()) return
        CarMediaCatalog.pageFor(this, mediaId)?.let { playInCar(it) }
    }

    /**
     * Voice request ("Hey Google, play jazz radio on RoadBrowser"). An empty query means "play
     * RoadBrowser" and resumes; otherwise the best-matching listed page plays.
     */
    @VisibleForTesting
    internal fun playFromSearch(query: String?) {
        if (!termsAccepted()) return
        val wanted = query?.trim().orEmpty()
        if (wanted.isEmpty()) {
            resume()
            return
        }
        val page = CarMediaCatalog.search(this, wanted)
        if (page != null) {
            playInCar(page)
            return
        }
        // Keep whatever is already playing; only an idle session reports the miss.
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) return
        showError(getString(R.string.car_media_no_match, wanted))
    }

    /** Shows [message] on Android Auto's player without touching what the session describes. */
    private fun showError(message: String) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(PlaybackState.STATE_ERROR, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setErrorMessage(message)
                .build()
        )
        session.isActive = true
        handler.removeCallbacks(stopWhenIdle)
        handler.postDelayed(stopWhenIdle, PAUSED_TIMEOUT_MS)
    }

    private fun playInCar(page: CarMediaCatalog.Page) {
        if (!BackgroundWebPlayer.play(this, page.url)) {
            showError(getString(R.string.webview_unavailable_title))
            return
        }
        takeOwnership(Source.CAR_PLAYER)
        state = PlaybackState.STATE_BUFFERING
        title = page.title
        artist = ""
        publishState()
    }

    @VisibleForTesting
    internal fun forward(action: String) {
        when (owner) {
            Source.CAR_PLAYER -> BackgroundWebPlayer.dispatch(action)
            Source.BROWSER -> actionHandler?.onMediaAction(action)
        }
    }

    private fun shutDown() {
        forward("pause")
        BackgroundWebPlayer.release()
        owner = Source.BROWSER
        handler.removeCallbacks(stopWhenIdle)
        state = PlaybackState.STATE_STOPPED
        session.setPlaybackState(PlaybackState.Builder().setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f).build())
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        isStarted = false
        // Android Auto may still be bound to the browser service; this only ends the started state.
        stopSelf()
    }

    private fun publishState() {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { getString(R.string.app_name) })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .build()
        )
        val isPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
                .build()
        )
        session.isActive = true

        // A service Android Auto created by binding isn't started; start it so playback outlives
        // the car disconnecting. Allowed here because a foreground app (Android Auto or the
        // browser) is what triggered the playback.
        if (!isStarted && isPlaying) {
            try {
                startService(Intent(this, MediaPlaybackService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not start media service", e)
            }
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(isPlaying), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Playback started while the app was already in the background and the OS refused
            // the promotion. A service started with startForegroundService() that never reaches
            // the foreground is killed with an ANR, so bow out cleanly instead.
            Log.w(TAG, "Foreground promotion refused", e)
            stopSelf()
            return
        }

        handler.removeCallbacks(stopWhenIdle)
        if (!isPlaying) {
            handler.postDelayed(stopWhenIdle, PAUSED_TIMEOUT_MS)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_roadbrowser)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(artist.ifBlank { getString(R.string.media_notification_subtitle) })
            .setContentIntent(contentIntent())
            .setDeleteIntent(stopIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()
    }

    private fun contentIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "MediaPlaybackService"
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 0x4D45
        private const val ACTION_UPDATE = "com.yashmaurya.roadbrowser.media.UPDATE"
        private const val ACTION_STOP = "com.yashmaurya.roadbrowser.media.STOP"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val ACTION_PLAY_FROM_SEARCH = "com.yashmaurya.roadbrowser.media.PLAY_FROM_SEARCH"
        private const val EXTRA_QUERY = "query"
        private const val PAUSED_TIMEOUT_MS = 10 * 60 * 1000L

        private const val SUPPORTED_ACTIONS =
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH

        private val TRUSTED_BROWSER_PACKAGES = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.android.systemui"                    // phone media controls / resumption
        )

        /** Receives session button presses for the tabs; set by the activity while it is alive. */
        @Volatile
        var actionHandler: MediaActionHandler? = null

        @Volatile
        private var instance: MediaPlaybackService? = null

        @Volatile
        private var owner = Source.BROWSER

        /**
         * Pushes a source's playback state into the session. A source that isn't the current
         * owner only matters once it starts playing, at which point it takes the session over.
         * The service is created on demand; the promotion to foreground happens inside the
         * service so the caller is never blocked. Main thread only.
         */
        fun update(context: Context, source: Source, playing: Boolean, title: String, artist: String, pageUrl: String?) {
            if (source != owner) {
                if (!playing) return
                takeOwnership(source)
            }
            if (playing && !pageUrl.isNullOrBlank()) {
                BrowserPreferences.setLastMediaPage(context, pageUrl, title)
            }
            val running = instance
            if (running != null) {
                running.applyUpdate(playing, title, artist)
                return
            }
            val intent = Intent(context, MediaPlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
            try {
                // Only a cold start needs (and is allowed) the foreground variant.
                context.startForegroundService(intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Cannot start media service from background", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start media service", e)
            }
        }

        /**
         * A voice search handed to the app outside Android Auto (the phone's assistant sends
         * MEDIA_PLAY_FROM_SEARCH to the browser activity). Called from a visible activity, which
         * is what allows the start.
         */
        fun playFromSearch(context: Context, query: String?) {
            val running = instance
            if (running != null) {
                running.playFromSearch(query)
                return
            }
            val intent = Intent(context, MediaPlaybackService::class.java)
                .setAction(ACTION_PLAY_FROM_SEARCH)
                .putExtra(EXTRA_QUERY, query)
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start media service for a voice search", e)
            }
        }

        /** Called when the browser activity goes away; ends the session if the tabs owned it. */
        fun onBrowserClosed() {
            actionHandler = null
            if (owner == Source.BROWSER) {
                instance?.shutDown()
            }
        }

        private fun takeOwnership(source: Source) {
            if (source == owner) return
            when (owner) {
                Source.CAR_PLAYER -> BackgroundWebPlayer.dispatch("pause")
                Source.BROWSER -> actionHandler?.onMediaAction("pause")
            }
            owner = source
        }
    }
}
