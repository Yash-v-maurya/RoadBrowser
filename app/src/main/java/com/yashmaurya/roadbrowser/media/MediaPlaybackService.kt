package com.yashmaurya.roadbrowser.media

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.R

/**
 * Foreground media-playback service that backs the browser's playback with a real
 * [MediaSession].
 *
 * Chromium takes audio focus for the page on its own, but without a session of our own the
 * head unit's steering-wheel buttons and the phone's media notification have nothing to talk
 * to, and — more importantly on a OnePlus — the process is just a background app the OS is free
 * to freeze once Maps has covered the browser for a while. Holding a `mediaPlayback` foreground
 * service while something is playing fixes both.
 *
 * The activity drives it with [update]; session callbacks are forwarded to whatever
 * [MediaActionHandler] is registered, which routes them into the active page via
 * [MediaSessionBridge.dispatch]. The service stops itself after [PAUSED_TIMEOUT_MS] of being
 * paused, when the user hits Stop, or when the activity is destroyed.
 */
class MediaPlaybackService : Service() {

    fun interface MediaActionHandler {
        fun onMediaAction(action: String)
    }

    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val stopWhenIdle = Runnable { stopSelf() }
    private var isPlaying = false
    private var title = ""
    private var artist = ""

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        session = MediaSession(this, "RoadBrowser").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = forward("play")
                override fun onPause() = forward("pause")
                override fun onSkipToNext() = forward("nexttrack")
                override fun onSkipToPrevious() = forward("previoustrack")
                override fun onFastForward() = forward("seekforward")
                override fun onRewind() = forward("seekbackward")
                override fun onStop() {
                    forward("pause")
                    stopSelf()
                }
            })
            setSessionActivity(contentIntent())
        }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
            }
        }
        publishState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(stopWhenIdle)
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun forward(action: String) {
        actionHandler?.onMediaAction(action)
    }

    private fun publishState() {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { getString(R.string.app_name) })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .build()
        )
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (isPlaying) 1f else 0f)
                .build()
        )
        session.isActive = true

        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Playback started while the activity was already stopped and the OS refused the
            // promotion. A service started with startForegroundService() that never reaches the
            // foreground is killed with an ANR, so bow out cleanly instead.
            Log.w(TAG, "Foreground promotion refused", e)
            stopSelf()
            return
        }

        handler.removeCallbacks(stopWhenIdle)
        if (!isPlaying) {
            handler.postDelayed(stopWhenIdle, PAUSED_TIMEOUT_MS)
        }
    }

    private fun buildNotification(): Notification {
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
        private const val PAUSED_TIMEOUT_MS = 10 * 60 * 1000L

        /** Receives session button presses; set by the activity while it is alive. */
        @Volatile
        var actionHandler: MediaActionHandler? = null

        @Volatile
        private var isRunning = false

        /**
         * Pushes the page's playback state into the session. The service is created on demand;
         * the promotion to foreground happens inside the service so the caller is never blocked.
         */
        fun update(context: Context, playing: Boolean, title: String, artist: String) {
            val intent = Intent(context, MediaPlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
            try {
                // A running service can be poked from the background; only a cold start needs
                // (and is allowed) the foreground variant.
                if (isRunning) context.startService(intent) else context.startForegroundService(intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Cannot start media service from background", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start media service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }
    }
}
