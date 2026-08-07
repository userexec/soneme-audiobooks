package com.userexec.soneme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlin.math.max

class PlaybackService : Service() {
    inner class PlaybackBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    private val binder = PlaybackBinder()
    private lateinit var db: SonemeDatabase
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var mediaSession: MediaSession
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var prepared = false
    private var currentUri: String? = null
    private var pendingAutoplay = false
    private var pendingStartMs = 0L
    private var resumeAfterFocusGain = false
    private var sleepDeadlineElapsed: Long? = null
    private var foregroundActive = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying()) {
                persistProgress(markRecent = true)
                handler.postDelayed(this, 60_000L)
            }
        }
    }

    private val sleepRunnable: Runnable = Runnable {
        val deadline = sleepDeadlineElapsed ?: return@Runnable
        if (SystemClock.elapsedRealtime() >= deadline) {
            sleepDeadlineElapsed = null
            pauseInternal(abandonFocus = true)
            persistProgress(markRecent = true)
        } else {
            handler.postDelayed(sleepRunnable, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = SonemeDatabase(this)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        audioManager = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes())
            .setOnAudioFocusChangeListener(::onAudioFocusChanged)
            .build()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_KEEP_ALIVE -> ensureForeground()
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> previousQueueTitle()
            ACTION_NEXT -> nextQueueTitle()
            ACTION_PAUSE -> pause()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        persistProgress(markRecent = isPlaying())
        handler.removeCallbacksAndMessages(null)
        player?.release()
        mediaSession.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        db.close()
        super.onDestroy()
    }

    fun load(uri: String, resumeSaved: Boolean = true, autoplay: Boolean = false) {
        if (currentUri == uri && prepared) {
            if (!resumeSaved) seekTo(0)
            if (autoplay) play()
            return
        }

        persistProgress(markRecent = isPlaying())
        player?.release()
        prepared = false
        currentUri = uri
        pendingAutoplay = autoplay

        val record = db.getAudio(uri)
        pendingStartMs = if (resumeSaved && record != null && record.durationMs > 0 && record.positionMs < record.durationMs - 1000) {
            record.positionMs
        } else 0L

        val newPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes())
            setDataSource(this@PlaybackService, Uri.parse(uri))
            setOnPreparedListener {
                prepared = true
                applyPlaybackSpeed()
                if (pendingStartMs > 0) it.seekTo(pendingStartMs.toInt())
                updateSessionMetadata()
                updatePlaybackState()
                if (pendingAutoplay) play()
            }
            setOnCompletionListener { handleCompletion() }
            setOnErrorListener { _, _, _ ->
                prepared = false
                updatePlaybackState(PlaybackState.STATE_ERROR)
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundActive = false
                true
            }
            prepareAsync()
        }
        player = newPlayer
    }

    fun play() {
        if (!prepared) {
            pendingAutoplay = true
            return
        }
        if (requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        ensureStarted()
        ensureForeground()
        player?.start()
        updatePlaybackState()
        schedulePeriodicProgress()
        persistProgress(markRecent = true)
    }

    fun pause() {
        pauseInternal(abandonFocus = true)
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        if (prepared && player?.isPlaying == true) player?.pause()
        handler.removeCallbacks(progressRunnable)
        persistProgress(markRecent = true)
        if (abandonFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        updatePlaybackState()
        // Once playback has started, remain a foreground media service while paused.
        // This keeps hardware/headset controls alive with the flip closed.
        refreshNotificationIfPresent()
    }

    fun togglePlayPause() {
        if (isPlaying()) pause() else play()
    }

    fun seekRelative(deltaMs: Long) {
        if (!prepared) return
        seekTo(positionMs() + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        if (!prepared) return
        val duration = durationMs()
        val target = positionMs.coerceIn(0L, max(0L, duration))
        player?.seekTo(target.toInt())
        persistProgress(markRecent = true, positionOverride = target)
        updatePlaybackState()
    }

    fun previousQueueTitle() = stepQueue(-1)
    fun nextQueueTitle() = stepQueue(1)

    private fun stepQueue(direction: Int) {
        val queue = db.queueUris()
        if (queue.size <= 1) return
        val index = queue.indexOf(currentUri).takeIf { it >= 0 } ?: 0
        val targetIndex = (index + direction + queue.size) % queue.size
        load(queue[targetIndex], resumeSaved = false, autoplay = true)
    }

    private fun handleCompletion() {
        val uri = currentUri ?: return
        db.saveProgress(uri, durationMs(), durationMs(), markRecent = true)
        val queue = db.queueUris()
        val index = queue.indexOf(uri)
        when (repeatMode()) {
            RepeatMode.ONE -> load(uri, resumeSaved = false, autoplay = true)
            RepeatMode.ALL -> {
                if (queue.isNotEmpty()) {
                    val next = if (index >= 0) (index + 1) % queue.size else 0
                    load(queue[next], resumeSaved = false, autoplay = true)
                }
            }
            RepeatMode.OFF -> {
                if (index >= 0 && index < queue.lastIndex) {
                    load(queue[index + 1], resumeSaved = false, autoplay = true)
                } else {
                    handler.removeCallbacks(progressRunnable)
                    updatePlaybackState(PlaybackState.STATE_STOPPED)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundActive = false
                }
            }
        }
    }

    fun isPlaying(): Boolean = prepared && runCatching { player?.isPlaying == true }.getOrDefault(false)
    fun currentUri(): String? = currentUri
    fun positionMs(): Long = if (prepared) runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L) else pendingStartMs
    fun durationMs(): Long = if (prepared) runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L) else db.getAudio(currentUri ?: "")?.durationMs ?: 0L

    fun currentRecord(): AudioRecord? = currentUri?.let(db::getAudio)

    fun queueSize(): Int = db.queueUris().size

    fun repeatMode(): RepeatMode = runCatching {
        RepeatMode.valueOf(prefs.getString(KEY_REPEAT, RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
    }.getOrDefault(RepeatMode.OFF)

    fun setRepeatMode(mode: RepeatMode) {
        prefs.edit().putString(KEY_REPEAT, mode.name).apply()
        updatePlaybackState()
    }

    fun cycleRepeat() {
        setRepeatMode(
            when (repeatMode()) {
                RepeatMode.OFF -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.OFF
            }
        )
    }

    fun playbackSpeed(): Float = prefs.getFloat(KEY_SPEED, 1f)

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        applyPlaybackSpeed()
        updatePlaybackState()
    }

    private fun applyPlaybackSpeed() {
        if (!prepared) return
        runCatching {
            player?.playbackParams = PlaybackParams().setSpeed(playbackSpeed())
        }
    }

    fun rewindIntervalMs(): Long = prefs.getLong(KEY_REWIND_INTERVAL, 10_000L)
    fun forwardIntervalMs(): Long = prefs.getLong(KEY_FORWARD_INTERVAL, 10_000L)
    fun setRewindIntervalMs(ms: Long) { prefs.edit().putLong(KEY_REWIND_INTERVAL, ms).apply() }
    fun setForwardIntervalMs(ms: Long) { prefs.edit().putLong(KEY_FORWARD_INTERVAL, ms).apply() }

    fun setSleepMinutes(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        sleepDeadlineElapsed = if (minutes <= 0) null else SystemClock.elapsedRealtime() + minutes * 60_000L
        if (sleepDeadlineElapsed != null) handler.post(sleepRunnable)
    }

    fun addSleepMinutes(minutes: Int) {
        val now = SystemClock.elapsedRealtime()
        val base = sleepDeadlineElapsed?.takeIf { it > now } ?: now
        sleepDeadlineElapsed = base + minutes * 60_000L
        handler.removeCallbacks(sleepRunnable)
        handler.post(sleepRunnable)
    }

    fun sleepRemainingMs(): Long {
        val deadline = sleepDeadlineElapsed ?: return 0L
        return (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun persistProgress(
        markRecent: Boolean,
        positionOverride: Long? = null
    ) {
        val uri = currentUri ?: return
        val duration = durationMs()
        if (duration <= 0) return
        val position = positionOverride ?: positionMs()
        db.saveProgress(uri, position, duration, markRecent)
    }

    private fun schedulePeriodicProgress() {
        handler.removeCallbacks(progressRunnable)
        handler.postDelayed(progressRunnable, 60_000L)
    }

    private fun requestAudioFocus(): Int = audioManager.requestAudioFocus(focusRequest)

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                pauseInternal(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeAfterFocusGain = isPlaying()
                pauseInternal(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    play()
                }
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SonemePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToPrevious() = previousQueueTitle()
                override fun onSkipToNext() = nextQueueTitle()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() = pause()
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        ?: return super.onMediaButtonEvent(mediaButtonIntent)
                    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayPause()
                        KeyEvent.KEYCODE_MEDIA_PLAY -> play()
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
                        KeyEvent.KEYCODE_MEDIA_NEXT -> nextQueueTitle()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> previousQueueTitle()
                        else -> return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                    return true
                }
            })
            isActive = true
        }
        updatePlaybackState()
    }

    private fun updateSessionMetadata() {
        val record = currentRecord() ?: return
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, record.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, record.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs().takeIf { it > 0 } ?: record.durationMs)
                .build()
        )
    }

    private fun updatePlaybackState(forcedState: Int? = null) {
        val state = forcedState ?: when {
            isPlaying() -> PlaybackState.STATE_PLAYING
            prepared -> PlaybackState.STATE_PAUSED
            currentUri != null -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_NONE
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs(), playbackSpeed())
                .build()
        )
        if (isPlaying()) refreshNotificationIfPresent()
    }

    private fun audioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun ensureStarted() {
        startService(Intent(this, PlaybackService::class.java).setAction(ACTION_KEEP_ALIVE))
    }

    private fun ensureForeground() {
        if (foregroundActive) return
        startForeground(NOTIFICATION_ID, buildNotification())
        foregroundActive = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audiobook playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val record = currentRecord()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
            this, requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previous = Notification.Action.Builder(
            R.drawable.ic_notification, "Previous", serviceIntent(ACTION_PREVIOUS, 1)
        ).build()
        val playPause = Notification.Action.Builder(
            R.drawable.ic_notification,
            if (isPlaying()) "Pause" else "Play",
            serviceIntent(ACTION_PLAY_PAUSE, 2)
        ).build()
        val next = Notification.Action.Builder(
            R.drawable.ic_notification, "Next", serviceIntent(ACTION_NEXT, 3)
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(record?.title ?: "Soneme Audiobooks")
            .setContentText(record?.artist ?: "")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(previous)
            .addAction(playPause)
            .addAction(next)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun refreshNotificationIfPresent() {
        if (currentUri == null || !foregroundActive) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val PREFS = "player_settings"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_SPEED = "speed"
        private const val KEY_REWIND_INTERVAL = "rewind_interval"
        private const val KEY_FORWARD_INTERVAL = "forward_interval"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_KEEP_ALIVE = "com.userexec.soneme.KEEP_ALIVE"
        const val ACTION_PLAY_PAUSE = "com.userexec.soneme.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.userexec.soneme.PREVIOUS"
        const val ACTION_NEXT = "com.userexec.soneme.NEXT"
        const val ACTION_PAUSE = "com.userexec.soneme.PAUSE"
    }
}
