package app.map.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.content.ContextCompat
import kotlin.random.Random

internal fun startMapMusicService(context: Context, intent: Intent): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
    else context.startService(intent)
}.isSuccess

@SuppressLint("UnsafeOptInUsageError")
class MusicService : Service() {
    private lateinit var database: MusicDatabase
    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentUri: String? = null
    private var pendingSeek = 0
    private var effectsSessionId = C.AUDIO_SESSION_ID_UNSET
    private var sleepMode = SLEEP_OFF
    private var sleepEndsAt = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var tickCount = 0
    private var allowExternalResume = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.25f)
            AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f)
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = pause()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val currentPlayer = player
            if (currentPlayer != null && currentPlayer.isPlaying && ++tickCount % 5 == 0) {
                database.setPosition(runCatching { currentPlayer.currentPosition.toInt() }.getOrDefault(0))
            }
            if (sleepMode == SLEEP_TIMER && sleepEndsAt <= SystemClock.elapsedRealtime()) stopPlayback()
            else {
                broadcastState()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        database = MusicDatabase(this)
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
        session = MediaSession(this, "MAP Music").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (allowExternalResume) resume()
                }
                override fun onPause() = pause()
                override fun onStop() = stopPlayback()
                override fun onSkipToNext() = advance(1)
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seek(pos.toInt())
            })
            isActive = true
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            RECEIVER_NOT_EXPORTED
        ) else @Suppress("DEPRECATION") registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            startForeground(NOTIFICATION_ID, notification())
            when (intent?.action) {
                ACTION_PLAY -> intent.getStringExtra(EXTRA_URI)?.let {
                    allowExternalResume = true
                    play(it, intent.getBooleanExtra(EXTRA_RESTORE_POSITION, false))
                }
                ACTION_RESUME -> {
                    allowExternalResume = true
                    resume()
                }
                ACTION_TOGGLE -> if (player?.isPlaying == true) pause() else {
                    allowExternalResume = true
                    resume()
                }
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> advance(1)
                ACTION_PREVIOUS -> previous()
                ACTION_SEEK -> seek(intent.getIntExtra(EXTRA_POSITION, 0))
                ACTION_SHUFFLE -> {
                    database.setShuffle(!database.shuffle())
                    updateState()
                }
                ACTION_REPEAT -> {
                    database.setRepeat(when (database.repeat()) { REPEAT_OFF -> REPEAT_ALL; REPEAT_ALL -> REPEAT_ONE; else -> REPEAT_OFF })
                    updateState()
                }
                ACTION_SLEEP -> setSleep(intent.getStringExtra(EXTRA_SLEEP_MODE) ?: SLEEP_OFF, intent.getIntExtra(EXTRA_MINUTES, 0))
                ACTION_EQ_ENABLED -> setEqualizerEnabled(intent.getBooleanExtra(EXTRA_ENABLED, false))
                ACTION_EQ_BAND -> setEqualizerBand(intent.getIntExtra(EXTRA_BAND, 0), intent.getShortExtra(EXTRA_LEVEL, 0))
                ACTION_EQ_PRESET -> usePreset(intent.getShortExtra(EXTRA_PRESET, 0))
                ACTION_BASS -> setBass(intent.getShortExtra(EXTRA_LEVEL, 0))
                ACTION_VIRTUALIZER -> setVirtualizer(intent.getShortExtra(EXTRA_LEVEL, 0))
                ACTION_STOP -> {
                    stopPlayback()
                    return START_NOT_STICKY
                }
            }
            updateState()
            START_STICKY
        } catch (_: Exception) {
            broadcastError("MAP could not start music safely. Try Play again.")
            runCatching { stopPlayback() }
            runCatching { stopSelf() }
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        isRunning = false
        savePosition()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(noisyReceiver) }
        releaseEffects()
        player?.release()
        abandonAudioFocus()
        session.release()
        database.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun play(uri: String, restorePosition: Boolean = false) {
        val item = database.track(uri)
        if (item == null || !item.available) {
            broadcastError("This audio file is unavailable. Refresh its folder or choose another track.")
            advance(1)
            return
        }
        if (!requestAudioFocus()) return
        currentUri = uri
        database.setCurrent(uri)
        pendingSeek = if (restorePosition) database.position() else 0
        releaseEffects()
        player?.release()
        player = try {
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    Media3AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false
                )
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> {
                                    if (pendingSeek > 0) seekTo(pendingSeek.toLong())
                                    pendingSeek = 0
                                    database.markPlayed(uri)
                                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != effectsSessionId) {
                                        initialiseEffects(audioSessionId)
                                        effectsSessionId = audioSessionId
                                    }
                                    updateMetadata(item)
                                    updateState()
                                }
                                Player.STATE_ENDED -> onTrackComplete()
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updateState()
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            broadcastError("MAP could not play ${item.title}.")
                            advance(1)
                        }
                    })
                    setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                    prepare()
                    play()
                }
        } catch (_: Exception) {
            broadcastError("MAP could not open ${item.title}.")
            null
        }
        updateMetadata(item)
        updateState()
    }

    private fun resume() {
        val currentPlayer = player
        if (currentPlayer != null) {
            if (requestAudioFocus()) runCatching { currentPlayer.play() }
            updateState()
            return
        }
        database.current()?.let { play(it, true) }
    }

    private fun pause() {
        allowExternalResume = false
        runCatching { player?.pause() }
        savePosition()
        updateState()
    }

    private fun previous() {
        val currentPlayer = player
        if (currentPlayer != null && runCatching { currentPlayer.currentPosition }.getOrDefault(0) > 5_000) seek(0)
        else advance(-1)
    }

    private fun advance(direction: Int) {
        val queue = database.queue().filter { it.available }
        if (queue.isEmpty()) {
            stopPlayback()
            return
        }
        val currentIndex = queue.indexOfFirst { it.uri == (currentUri ?: database.current()) }
        val next = when {
            database.shuffle() && queue.size > 1 -> queue.filterIndexed { index, _ -> index != currentIndex }.random(Random.Default)
            currentIndex < 0 -> queue.first()
            currentIndex + direction in queue.indices -> queue[currentIndex + direction]
            database.repeat() == REPEAT_ALL -> if (direction > 0) queue.first() else queue.last()
            else -> null
        }
        if (next == null) stopPlayback() else play(next.uri)
    }

    private fun onTrackComplete() {
        database.setPosition(0)
        when {
            sleepMode == SLEEP_TRACK -> stopPlayback()
            sleepMode == SLEEP_QUEUE && isLastQueueTrack() -> stopPlayback()
            database.repeat() == REPEAT_ONE -> currentUri?.let(::play)
            else -> {
                advance(1)
            }
        }
    }

    private fun isLastQueueTrack(): Boolean {
        val queue = database.queue().filter { it.available }
        return queue.isNotEmpty() && queue.lastOrNull()?.uri == currentUri
    }

    private fun seek(position: Int) {
        val safePosition = position.coerceAtLeast(0)
        val currentPlayer = player
        val shouldResume = currentPlayer?.playWhenReady == true
        runCatching {
            currentPlayer?.seekTo(safePosition.toLong())
            if (shouldResume) currentPlayer?.play()
        }
        database.setPosition(safePosition)
        updateState()
    }

    private fun stopPlayback() {
        allowExternalResume = false
        savePosition()
        player?.release()
        player = null
        releaseEffects()
        abandonAudioFocus()
        sleepMode = SLEEP_OFF
        sleepEndsAt = 0
        updatePlaybackState(false)
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun savePosition() {
        val position = runCatching { player?.currentPosition?.toInt() ?: 0 }.getOrDefault(0)
        if (position > 0) database.setPosition(position)
    }

    private fun setSleep(mode: String, minutes: Int) {
        sleepMode = mode
        sleepEndsAt = if (mode == SLEEP_TIMER && minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        broadcastState()
    }

    private fun initialiseEffects(audioSessionId: Int) {
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply {
                for (band in 0 until numberOfBands.toInt()) database.equalizerBand(band)?.let { setBandLevel(band.toShort(), it.coerceIn(bandLevelRange[0], bandLevelRange[1])) }
                enabled = database.equalizerEnabled()
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) setStrength(database.bassStrength().coerceIn(0, 1_000))
                enabled = database.equalizerEnabled()
            }
        }.onFailure {
            releaseEffects()
        }
        @Suppress("DEPRECATION")
        virtualizer = runCatching {
            Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) setStrength(database.virtualizerStrength().coerceIn(0, 1_000))
                enabled = database.equalizerEnabled()
            }
        }.getOrNull()
        broadcastState()
    }

    private fun setEqualizerEnabled(enabled: Boolean) {
        database.setEqualizerEnabled(enabled)
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        broadcastState()
    }

    private fun setEqualizerBand(band: Int, level: Short) {
        val effect = equalizer ?: return
        if (band !in 0 until effect.numberOfBands.toInt()) return
        val safeLevel = level.coerceIn(effect.bandLevelRange[0], effect.bandLevelRange[1])
        effect.setBandLevel(band.toShort(), safeLevel)
        database.setEqualizerBand(band, safeLevel)
        broadcastState()
    }

    private fun usePreset(preset: Short) {
        val effect = equalizer ?: return
        if (preset !in 0 until effect.numberOfPresets) return
        effect.usePreset(preset)
        for (band in 0 until effect.numberOfBands.toInt()) database.setEqualizerBand(band, effect.getBandLevel(band.toShort()))
        broadcastState()
    }

    private fun setBass(level: Short) {
        bassBoost?.takeIf { it.strengthSupported }?.setStrength(level.coerceIn(0, 1_000))
        database.setBassStrength(level.coerceIn(0, 1_000))
        broadcastState()
    }

    private fun setVirtualizer(level: Short) {
        virtualizer?.takeIf { it.strengthSupported }?.setStrength(level.coerceIn(0, 1_000))
        database.setVirtualizerStrength(level.coerceIn(0, 1_000))
        broadcastState()
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        effectsSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun requestAudioFocus(): Boolean {
        if (focusRequest == null) focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun updateMetadata(item: AudioItem) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, item.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, item.durationMs)
                .build()
        )
    }

    private fun updateState() {
        updatePlaybackState(player?.isPlaying == true)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        broadcastState()
    }

    private fun updatePlaybackState(playing: Boolean) {
        database.setPlaying(playing)
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
        session.setPlaybackState(
            PlaybackState.Builder().setActions(actions).setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                runCatching { player?.currentPosition?.toLong() ?: database.position().toLong() }.getOrDefault(0),
                if (playing) 1f else 0f
            ).build()
        )
    }

    private fun notification(): Notification {
        val item = database.track(currentUri ?: database.current())
        val playing = player?.isPlaying == true
        val content = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val previous = serviceIntent(11, ACTION_PREVIOUS)
        val toggle = serviceIntent(12, ACTION_TOGGLE)
        val next = serviceIntent(13, ACTION_NEXT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (playing) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(item?.title ?: "MAP Music")
            .setContentText(item?.artist ?: "Ready to play")
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", previous).build())
            .addAction(Notification.Action.Builder(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", toggle).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", next).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun serviceIntent(requestCode: Int, action: String) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, MusicService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun broadcastState() {
        val currentPlayer = player
        val effect = equalizer
        val remaining = if (sleepMode == SLEEP_TIMER) ((sleepEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1_000).toInt() else 0
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_URI, currentUri ?: database.current())
            putExtra(EXTRA_PLAYING, currentPlayer?.isPlaying == true)
            putExtra(EXTRA_POSITION, runCatching { currentPlayer?.currentPosition?.toInt() ?: database.position() }.getOrDefault(0))
            putExtra(EXTRA_DURATION, runCatching { currentPlayer?.duration?.toInt() ?: database.track(currentUri ?: database.current())?.durationMs?.toInt() ?: 0 }.getOrDefault(0))
            putExtra(EXTRA_SHUFFLE, database.shuffle())
            putExtra(EXTRA_REPEAT, database.repeat())
            putExtra(EXTRA_SLEEP_MODE, sleepMode)
            putExtra(EXTRA_SLEEP_REMAINING, remaining)
            putExtra(EXTRA_ENABLED, database.equalizerEnabled())
            if (effect != null) {
                putExtra(EXTRA_EQ_RANGE, effect.bandLevelRange)
                putExtra(EXTRA_EQ_LEVELS, ShortArray(effect.numberOfBands.toInt()) { effect.getBandLevel(it.toShort()) })
                putExtra(EXTRA_EQ_FREQUENCIES, IntArray(effect.numberOfBands.toInt()) { effect.getCenterFreq(it.toShort()) })
                putExtra(EXTRA_EQ_PRESETS, Array(effect.numberOfPresets.toInt()) { effect.getPresetName(it.toShort()) })
            }
            putExtra(EXTRA_BASS_SUPPORTED, bassBoost?.strengthSupported == true)
            putExtra(EXTRA_VIRTUALIZER_SUPPORTED, virtualizer?.strengthSupported == true)
            putExtra(EXTRA_BASS_LEVEL, bassBoost?.roundedStrength ?: database.bassStrength())
            putExtra(EXTRA_VIRTUALIZER_LEVEL, virtualizer?.roundedStrength ?: database.virtualizerStrength())
        })
    }

    private fun broadcastError(message: String) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_ERROR, message))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Playback controls for your local music"
                }
            )
        }
    }

    companion object {
        @Volatile
        var isRunning = false

        const val ACTION_PLAY = "app.map.android.music.PLAY"
        const val ACTION_RESUME = "app.map.android.music.RESUME"
        const val ACTION_TOGGLE = "app.map.android.music.TOGGLE"
        const val ACTION_PAUSE = "app.map.android.music.PAUSE"
        const val ACTION_STOP = "app.map.android.music.STOP"
        const val ACTION_NEXT = "app.map.android.music.NEXT"
        const val ACTION_PREVIOUS = "app.map.android.music.PREVIOUS"
        const val ACTION_SEEK = "app.map.android.music.SEEK"
        const val ACTION_SHUFFLE = "app.map.android.music.SHUFFLE"
        const val ACTION_REPEAT = "app.map.android.music.REPEAT"
        const val ACTION_SLEEP = "app.map.android.music.SLEEP"
        const val ACTION_EQ_ENABLED = "app.map.android.music.EQ_ENABLED"
        const val ACTION_EQ_BAND = "app.map.android.music.EQ_BAND"
        const val ACTION_EQ_PRESET = "app.map.android.music.EQ_PRESET"
        const val ACTION_BASS = "app.map.android.music.BASS"
        const val ACTION_VIRTUALIZER = "app.map.android.music.VIRTUALIZER"
        const val ACTION_STATE = "app.map.android.music.STATE"
        const val EXTRA_URI = "uri"
        const val EXTRA_RESTORE_POSITION = "restore_position"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_POSITION = "position"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_SHUFFLE = "shuffle"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_SLEEP_MODE = "sleep_mode"
        const val EXTRA_SLEEP_REMAINING = "sleep_remaining"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_BAND = "band"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_PRESET = "preset"
        const val EXTRA_EQ_RANGE = "eq_range"
        const val EXTRA_EQ_LEVELS = "eq_levels"
        const val EXTRA_EQ_FREQUENCIES = "eq_frequencies"
        const val EXTRA_EQ_PRESETS = "eq_presets"
        const val EXTRA_BASS_SUPPORTED = "bass_supported"
        const val EXTRA_VIRTUALIZER_SUPPORTED = "virtualizer_supported"
        const val EXTRA_BASS_LEVEL = "bass_level"
        const val EXTRA_VIRTUALIZER_LEVEL = "virtualizer_level"
        const val EXTRA_ERROR = "error"
        const val REPEAT_OFF = "off"
        const val REPEAT_ALL = "all"
        const val REPEAT_ONE = "one"
        const val SLEEP_OFF = "off"
        const val SLEEP_TIMER = "timer"
        const val SLEEP_TRACK = "track"
        const val SLEEP_QUEUE = "queue"
        private const val CHANNEL_ID = "map_music"
        private const val NOTIFICATION_ID = 20
    }
}
