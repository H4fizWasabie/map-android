package app.map.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private enum class MusicViewMode(val label: String) {
    SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), GENRES("Genres"), FOLDERS("Folders"),
    PLAYLISTS("Playlists"), FAVORITES("Favorites"), RECENT("Recently played")
}

private data class MusicFilters(
    var format: String = "All formats",
    var artist: String = "All artists",
    var album: String = "All albums",
    var genre: String = "All genres",
    var folder: String = "All folders",
    var duration: String = "Any length",
    var availability: String = "All files"
)

class MusicActivity : Activity() {
    private lateinit var database: MusicDatabase
    private val executor = Executors.newSingleThreadExecutor()
    private var mode = MusicViewMode.SONGS
    private var activePlaylist: Long? = null
    private var filters = MusicFilters()
    private var query = ""
    private var sort = "Title"
    private var refreshing = false
    private var showingPlayer = false
    private var currentUri: String? = null
    private var playing = false
    private var position = 0
    private var duration = 0
    private var seekBar: SeekBar? = null
    private var playButton: ImageButton? = null
    private var nowPlayingTitle: TextView? = null
    private var nowPlayingArtist: TextView? = null
    private var sleepLabel: TextView? = null
    private var eqLevels = shortArrayOf()
    private var eqRange = shortArrayOf(-1500, 1500)
    private var eqFrequencies = intArrayOf()
    private var eqPresets = emptyArray<String>()
    private var eqEnabled = false
    private var bassSupported = false
    private var virtualizerSupported = false
    private var bassLevel: Short = 0
    private var virtualizerLevel: Short = 0

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent ?: return
            event.getStringExtra(MusicService.EXTRA_ERROR)?.let {
                Toast.makeText(this@MusicActivity, it, Toast.LENGTH_LONG).show()
            }
            val uri = event.getStringExtra(MusicService.EXTRA_URI)
            if (uri != null && uri != currentUri && showingPlayer) {
                currentUri = uri
                renderPlayer()
            }
            currentUri = uri ?: currentUri
            playing = event.getBooleanExtra(MusicService.EXTRA_PLAYING, playing)
            position = event.getIntExtra(MusicService.EXTRA_POSITION, position)
            duration = event.getIntExtra(MusicService.EXTRA_DURATION, duration)
            val sleepMode = event.getStringExtra(MusicService.EXTRA_SLEEP_MODE).orEmpty()
            val sleepRemaining = event.getIntExtra(MusicService.EXTRA_SLEEP_REMAINING, 0)
            sleepModeText = when (sleepMode) {
                MusicService.SLEEP_TRACK -> "Stops after this track"
                MusicService.SLEEP_QUEUE -> "Stops after this queue"
                MusicService.SLEEP_TIMER -> "Stops in ${sleepRemaining / 60}:${(sleepRemaining % 60).toString().padStart(2, '0')}"
                else -> ""
            }
            eqEnabled = event.getBooleanExtra(MusicService.EXTRA_ENABLED, eqEnabled)
            event.getShortArrayExtra(MusicService.EXTRA_EQ_LEVELS)?.let { eqLevels = it }
            event.getShortArrayExtra(MusicService.EXTRA_EQ_RANGE)?.let { eqRange = it }
            event.getIntArrayExtra(MusicService.EXTRA_EQ_FREQUENCIES)?.let { eqFrequencies = it }
            event.getStringArrayExtra(MusicService.EXTRA_EQ_PRESETS)?.let { eqPresets = it }
            bassSupported = event.getBooleanExtra(MusicService.EXTRA_BASS_SUPPORTED, bassSupported)
            virtualizerSupported = event.getBooleanExtra(MusicService.EXTRA_VIRTUALIZER_SUPPORTED, virtualizerSupported)
            bassLevel = event.getShortExtra(MusicService.EXTRA_BASS_LEVEL, bassLevel)
            virtualizerLevel = event.getShortExtra(MusicService.EXTRA_VIRTUALIZER_LEVEL, virtualizerLevel)
            updatePlaybackViews()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = MusicDatabase(this)
        currentUri = database.current()
        playing = database.playing()
        window.statusBarColor = getColor(R.color.map_background)
        window.navigationBarColor = getColor(R.color.map_background)
        renderLibrary()
        refreshFolders()
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) && currentUri != null) renderPlayer()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, stateReceiver, IntentFilter(MusicService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        database.close()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (showingPlayer) renderLibrary() else super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FOLDER_REQUEST || resultCode != RESULT_OK) return
        val tree = data?.data ?: return
        val takeFlags = (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            .takeIf { it != 0 } ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(tree, takeFlags) }
        val name = tree.pathSegments.lastOrNull()?.substringAfterLast(':') ?: "Music folder"
        val folder = MusicFolder(tree.toString(), Uri.decode(name).ifBlank { "Music folder" })
        database.addFolder(folder.uri, folder.name)
        renderLibrary()
        refreshFolder(folder)
    }

    private fun renderLibrary() {
        showingPlayer = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.map_background))
        }
        root.addView(header("Music", false))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(24))
        }
        body.addView(TextView(this).apply {
            text = "Your music"
            textSize = 30f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(8), 0, dp(4))
        })
        body.addView(TextView(this).apply {
            val count = database.tracks().size
            text = if (refreshing) "Refreshing your selected folders…" else "$count local ${if (count == 1) "track" else "tracks"}"
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(16))
        })
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Add folder") { requestFolder() })
            addView(actionButton("Refresh") { refreshFolders() })
            addView(actionButton("Folders") { showFoldersDialog() })
        }.also { body.addView(it) }
        body.addView(horizontalModes())
        val search = EditText(this).apply {
            hint = "Search your music"
            setSingleLine(true)
            setText(query)
            contentDescription = "Search music"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); renderResults(results) }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) })
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Filters") { showFiltersDialog() })
            addView(actionButton("Sort: $sort") { showSortDialog() })
            addView(TextView(this@MusicActivity).apply {
                text = activeFilterSummary()
                textSize = 13f
                setTextColor(getColor(R.color.map_muted))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }.also { body.addView(it) }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        currentTrack()?.let { root.addView(miniPlayer(it)) }
        setContentView(root)
        renderResults(results)
    }

    private lateinit var results: LinearLayout

    private fun renderResults(target: LinearLayout) {
        if (!::results.isInitialized || target !== results) return
        target.removeAllViews()
        val all = database.tracks()
        when (mode) {
            MusicViewMode.SONGS -> addTrackRows(target, filteredTracks(all))
            MusicViewMode.FAVORITES -> addTrackRows(target, filteredTracks(all.filter { it.favorite }))
            MusicViewMode.RECENT -> addTrackRows(target, filteredTracks(all.filter { it.lastPlayed > 0 }).sortedByDescending { it.lastPlayed })
            MusicViewMode.ALBUMS -> addGroups(target, filteredTracks(all), { it.album }, "album")
            MusicViewMode.ARTISTS -> addGroups(target, filteredTracks(all), { it.artist }, "artist")
            MusicViewMode.GENRES -> addGroups(target, filteredTracks(all), { it.genre }, "genre")
            MusicViewMode.FOLDERS -> addGroups(target, filteredTracks(all), { it.folderName }, "folder")
            MusicViewMode.PLAYLISTS -> addPlaylists(target)
        }
        if (target.childCount == 0) addEmpty(target, if (all.isEmpty()) "Add a local folder to start listening." else "Nothing matches these filters.")
    }

    private fun horizontalModes(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MusicActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            MusicViewMode.values().forEach { item ->
                addView(actionButton(item.label) { mode = item; activePlaylist = null; renderLibrary() }.apply { isEnabled = item != mode })
            }
        })
    }

    private fun filteredTracks(source: List<AudioItem>): List<AudioItem> {
        val result = source.filter { item ->
            val needle = query.trim().lowercase()
            (needle.isBlank() || listOf(item.title, item.artist, item.album, item.genre, item.folderName).any { it.lowercase().contains(needle) }) &&
                (filters.format == "All formats" || item.format == filters.format) &&
                (filters.artist == "All artists" || item.artist == filters.artist) &&
                (filters.album == "All albums" || item.album == filters.album) &&
                (filters.genre == "All genres" || item.genre == filters.genre) &&
                (filters.folder == "All folders" || item.folderName == filters.folder) &&
                (filters.availability == "All files" || (filters.availability == "Available" && item.available) || (filters.availability == "Unavailable" && !item.available)) &&
                when (filters.duration) {
                    "Under 3 minutes" -> item.durationMs in 1..179_999
                    "3–5 minutes" -> item.durationMs in 180_000..300_000
                    "Over 5 minutes" -> item.durationMs > 300_000
                    else -> true
                }
        }
        return when (sort) {
            "Artist" -> result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist + it.title })
            "Album" -> result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album + it.title })
            "Duration" -> result.sortedBy { it.durationMs }
            "Recently played" -> result.sortedByDescending { it.lastPlayed }
            else -> result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }

    private fun addTrackRows(parent: LinearLayout, tracks: List<AudioItem>) {
        tracks.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(8), dp(10))
                background = rounded(getColor(R.color.map_card), dp(14))
                contentDescription = "${item.title}, ${item.artist}"
                setOnClickListener { playTracks(tracks, item) }
            }
            val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            text.addView(TextView(this).apply {
                this.text = item.title
                textSize = 16f
                setTextColor(getColor(R.color.map_text))
            })
            text.addView(TextView(this).apply {
                this.text = listOf(item.artist, item.album, formatDuration(item.durationMs)).joinToString(" · ")
                textSize = 13f
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, dp(3), 0, 0)
            })
            if (!item.available) text.addView(TextView(this).apply {
                this.text = "Unavailable — refresh the folder or remove it"
                textSize = 12f
                setTextColor(getColor(R.color.map_accent))
            })
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("More") { showTrackMenu(item, tracks) })
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun addGroups(parent: LinearLayout, tracks: List<AudioItem>, name: (AudioItem) -> String, kind: String) {
        tracks.groupBy(name).toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (group, items) ->
            parent.addView(actionRow(group, "${items.size} ${if (items.size == 1) "track" else "tracks"}") {
                when (kind) {
                    "album" -> filters.album = group
                    "artist" -> filters.artist = group
                    "genre" -> filters.genre = group
                    "folder" -> filters.folder = group
                }
                mode = MusicViewMode.SONGS
                renderLibrary()
            })
        }
    }

    private fun addPlaylists(parent: LinearLayout) {
        parent.addView(actionButton("New playlist") { promptPlaylist() })
        database.playlists().forEach { playlist ->
            parent.addView(actionRow(playlist.name, "${playlist.trackCount} tracks") {
                activePlaylist = playlist.id
                showPlaylist(playlist.id)
            })
        }
    }

    private fun showPlaylist(id: Long) {
        val playlist = database.playlists().firstOrNull { it.id == id } ?: run { renderLibrary(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.map_background)) }
        root.addView(header(playlist.name, false))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(24)) }
        body.addView(TextView(this).apply {
            text = "${playlist.trackCount} tracks"
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(12))
        })
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Play all") { database.playlistTracks(id).firstOrNull()?.let { playTracks(database.playlistTracks(id), it) } })
            addView(actionButton("Rename") { promptRenamePlaylist(playlist) })
            addView(actionButton("Delete") { confirmDeletePlaylist(playlist) })
        }.also { body.addView(it) }
        val tracks = database.playlistTracks(id)
        addPlaylistRows(body, id, tracks)
        if (tracks.isEmpty()) addEmpty(body, "Add tracks from your library using More.")
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        currentTrack()?.let { root.addView(miniPlayer(it)) }
        setContentView(root)
    }

    private fun addPlaylistRows(parent: LinearLayout, playlistId: Long, tracks: List<AudioItem>) {
        tracks.forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(8), dp(8)); background = rounded(getColor(R.color.map_card), dp(14)) }
            row.addView(TextView(this).apply { text = "${item.title}\n${item.artist}"; textSize = 15f; setTextColor(getColor(R.color.map_text)) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("Up") { database.movePlaylistItem(playlistId, item.uri, -1); showPlaylist(playlistId) })
            row.addView(actionButton("Down") { database.movePlaylistItem(playlistId, item.uri, 1); showPlaylist(playlistId) })
            row.addView(actionButton("Remove") { database.removeFromPlaylist(playlistId, item.uri); showPlaylist(playlistId) })
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun showTrackMenu(item: AudioItem, visibleTracks: List<AudioItem>) {
        val actions = arrayOf("Play now", "Play next", "Add to queue", if (item.favorite) "Remove favorite" else "Add favorite", "Add to playlist")
        AlertDialog.Builder(this).setTitle(item.title).setItems(actions) { _, which ->
            when (which) {
                0 -> playTracks(visibleTracks, item)
                1 -> { database.playNext(item); Toast.makeText(this, "Added to play next", Toast.LENGTH_SHORT).show() }
                2 -> { database.addToQueue(item); Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show() }
                3 -> { database.setFavorite(item.uri, !item.favorite); renderLibrary() }
                4 -> showPlaylistPicker(item)
            }
        }.show()
    }

    private fun playTracks(tracks: List<AudioItem>, item: AudioItem) {
        if (!item.available) {
            Toast.makeText(this, "This file is unavailable. Refresh its folder first.", Toast.LENGTH_LONG).show()
            return
        }
        val available = tracks.filter { it.available }
        database.setQueue(available, item.uri)
        currentUri = item.uri
        requestNotificationsIfNeeded()
        startMusicAction(MusicService.ACTION_PLAY, item.uri)
        renderPlayer()
    }

    private fun currentTrack(): AudioItem? = database.track(currentUri ?: database.current())

    private fun miniPlayer(item: AudioItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundColor(getColor(R.color.map_card))
        contentDescription = "Now playing ${item.title}"
        setOnClickListener { renderPlayer() }
        addView(cover(item, dp(52)), LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(TextView(this@MusicActivity).apply {
            text = "${item.title}\n${item.artist}"
            textSize = 14f
            setTextColor(getColor(R.color.map_text))
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(iconButton(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play") { startMusicAction(MusicService.ACTION_TOGGLE) })
    }

    private fun renderPlayer() {
        showingPlayer = true
        val item = currentTrack() ?: run { renderLibrary(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.map_background)) }
        root.addView(header("Now Playing", true))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), 0, dp(24), dp(28)) }
        body.addView(cover(item, dp(292)), LinearLayout.LayoutParams(dp(292), dp(292)).apply { topMargin = dp(20); bottomMargin = dp(24) })
        nowPlayingTitle = TextView(this).apply { text = item.title; textSize = 24f; setTextColor(getColor(R.color.map_text)); gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        body.addView(nowPlayingTitle)
        nowPlayingArtist = TextView(this).apply { text = "${item.artist} · ${item.album}"; textSize = 15f; setTextColor(getColor(R.color.map_muted)); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(16)) }
        body.addView(nowPlayingArtist)
        seekBar = SeekBar(this).apply {
            max = maxOf(duration, item.durationMs.toInt(), 1)
            progress = position.coerceIn(0, max)
            contentDescription = "Playback position"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) position = progress }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { startMusicAction(MusicService.ACTION_SEEK, extras = mapOf(MusicService.EXTRA_POSITION to position)) }
            })
        }
        body.addView(seekBar, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MusicActivity).apply { text = formatDuration(position.toLong()); setTextColor(getColor(R.color.map_muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MusicActivity).apply { text = formatDuration((if (duration > 0) duration else item.durationMs.toInt()).toLong()); setTextColor(getColor(R.color.map_muted)); gravity = Gravity.END })
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(iconButton(android.R.drawable.ic_media_previous, "Previous") { startMusicAction(MusicService.ACTION_PREVIOUS) })
            playButton = iconButton(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play") { requestNotificationsIfNeeded(); startMusicAction(MusicService.ACTION_TOGGLE) }
            addView(playButton)
            addView(iconButton(android.R.drawable.ic_media_next, "Next") { startMusicAction(MusicService.ACTION_NEXT) })
        }, LinearLayout.LayoutParams(-1, dp(72)))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton(if (database.shuffle()) "Shuffle on" else "Shuffle") { startMusicAction(MusicService.ACTION_SHUFFLE) })
            addView(actionButton("Repeat: ${database.repeat()}") { startMusicAction(MusicService.ACTION_REPEAT) })
            addView(actionButton(if (item.favorite) "Favorite" else "Add favorite") { database.setFavorite(item.uri, !item.favorite); renderPlayer() })
        })
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Queue") { showQueueDialog() })
            addView(actionButton("Equalizer") { showEqualizerDialog() })
            addView(actionButton("Sleep") { showSleepDialog() })
        })
        sleepLabel = TextView(this).apply { text = ""; textSize = 13f; setTextColor(getColor(R.color.map_muted)); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        body.addView(sleepLabel)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updatePlaybackViews()
    }

    private fun updatePlaybackViews() {
        playButton?.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playButton?.contentDescription = if (playing) "Pause" else "Play"
        seekBar?.let { bar -> if (!bar.isPressed) { bar.max = maxOf(duration, 1); bar.progress = position.coerceIn(0, bar.max) } }
        sleepLabel?.text = if (sleepModeText.isBlank()) "" else sleepModeText
    }

    private var sleepModeText = ""

    private fun showQueueDialog() {
        val queue = database.queue()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        body.addView(actionButton("Save queue as playlist") { promptPlaylist(queue) })
        body.addView(actionButton("Clear queue") { database.clearQueue(); startMusicAction(MusicService.ACTION_STOP); renderLibrary() })
        queue.forEach { item ->
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MusicActivity).apply { text = "${item.title}\n${item.artist}"; setTextColor(getColor(R.color.map_text)) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(actionButton("Up") { database.moveQueue(item.uri, -1); showQueueDialog() })
                addView(actionButton("Down") { database.moveQueue(item.uri, 1); showQueueDialog() })
                addView(actionButton("Remove") { database.removeFromQueue(item.uri); showQueueDialog() })
            })
        }
        AlertDialog.Builder(this).setTitle("Queue").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Done", null).show()
    }

    private fun showEqualizerDialog() {
        if (eqLevels.isEmpty()) {
            Toast.makeText(this, "Start a track to see the equalizer controls supported by this device.", Toast.LENGTH_LONG).show()
            return
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        body.addView(Switch(this).apply {
            text = "Equalizer enabled"
            isChecked = eqEnabled
            setOnCheckedChangeListener { _, enabled -> startMusicAction(MusicService.ACTION_EQ_ENABLED, extras = mapOf(MusicService.EXTRA_ENABLED to enabled)) }
        })
        if (eqPresets.isNotEmpty()) {
            val preset = Spinner(this).apply { adapter = ArrayAdapter(this@MusicActivity, android.R.layout.simple_spinner_dropdown_item, eqPresets.toList()) }
            body.addView(preset)
            body.addView(actionButton("Apply preset") { startMusicAction(MusicService.ACTION_EQ_PRESET, extras = mapOf(MusicService.EXTRA_PRESET to preset.selectedItemPosition.toShort())) })
        }
        eqLevels.forEachIndexed { index, level ->
            val frequency = if (index < eqFrequencies.size) "${eqFrequencies[index] / 1000} kHz" else "Band ${index + 1}"
            body.addView(TextView(this).apply { text = frequency; setTextColor(getColor(R.color.map_muted)); setPadding(0, dp(8), 0, 0) })
            body.addView(SeekBar(this).apply {
                max = (eqRange[1] - eqRange[0]).toInt()
                progress = (level - eqRange[0]).toInt().coerceIn(0, max)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) startMusicAction(MusicService.ACTION_EQ_BAND, extras = mapOf(MusicService.EXTRA_BAND to index, MusicService.EXTRA_LEVEL to (progress + eqRange[0]).toShort())) }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }
        if (bassSupported) body.addView(effectSlider("Bass", bassLevel, MusicService.ACTION_BASS))
        if (virtualizerSupported) body.addView(effectSlider("Spatial", virtualizerLevel, MusicService.ACTION_VIRTUALIZER))
        AlertDialog.Builder(this).setTitle("Equalizer").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Done", null).show()
    }

    private fun effectSlider(label: String, level: Short, action: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MusicActivity).apply { text = label; setTextColor(getColor(R.color.map_muted)); setPadding(0, dp(8), 0, 0) })
        addView(SeekBar(this@MusicActivity).apply {
            max = 1_000
            progress = level.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) startMusicAction(action, extras = mapOf(MusicService.EXTRA_LEVEL to progress.toShort())) }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
    }

    private fun showSleepDialog() {
        val choices = arrayOf("Off", "After this track", "After this queue", "Custom duration")
        AlertDialog.Builder(this).setTitle("Sleep timer").setItems(choices) { _, which ->
            when (which) {
                0 -> setSleep(MusicService.SLEEP_OFF)
                1 -> setSleep(MusicService.SLEEP_TRACK)
                2 -> setSleep(MusicService.SLEEP_QUEUE)
                else -> {
                    val minutes = EditText(this).apply { hint = "Minutes"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                    AlertDialog.Builder(this).setTitle("Sleep after how many minutes?").setView(minutes).setNegativeButton("Cancel", null).setPositiveButton("Set") { _, _ -> setSleep(MusicService.SLEEP_TIMER, minutes.text.toString().toIntOrNull() ?: 0) }.show()
                }
            }
        }.show()
    }

    private fun setSleep(mode: String, minutes: Int = 0) {
        sleepModeText = when (mode) { MusicService.SLEEP_TRACK -> "Stops after this track"; MusicService.SLEEP_QUEUE -> "Stops after this queue"; MusicService.SLEEP_TIMER -> "Stops in $minutes minutes"; else -> "" }
        startMusicAction(MusicService.ACTION_SLEEP, extras = mapOf(MusicService.EXTRA_SLEEP_MODE to mode, MusicService.EXTRA_MINUTES to minutes))
        updatePlaybackViews()
    }

    private fun showFiltersDialog() {
        val all = database.tracks()
        val fields = listOf(
            "Format" to listOf("All formats") + all.map { it.format }.filter { it.isNotBlank() }.distinct().sorted(),
            "Artist" to listOf("All artists") + all.map { it.artist }.distinct().sorted(),
            "Album" to listOf("All albums") + all.map { it.album }.distinct().sorted(),
            "Genre" to listOf("All genres") + all.map { it.genre }.distinct().sorted(),
            "Folder" to listOf("All folders") + all.map { it.folderName }.distinct().sorted(),
            "Length" to listOf("Any length", "Under 3 minutes", "3–5 minutes", "Over 5 minutes"),
            "Availability" to listOf("All files", "Available", "Unavailable")
        )
        val spinners = fields.map { (_, options) -> Spinner(this).apply { adapter = ArrayAdapter(this@MusicActivity, android.R.layout.simple_spinner_dropdown_item, options); setSelection(options.indexOf(filterValue(options, fields.indexOfFirst { it.second === options })).coerceAtLeast(0)) } }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        fields.forEachIndexed { index, field -> body.addView(TextView(this).apply { text = field.first; setTextColor(getColor(R.color.map_muted)); setPadding(0, dp(8), 0, 0) }); body.addView(spinners[index]) }
        AlertDialog.Builder(this).setTitle("Filter library").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Clear") { _, _ -> filters = MusicFilters(); renderLibrary() }.setPositiveButton("Apply") { _, _ ->
            filters.format = spinners[0].selectedItem.toString(); filters.artist = spinners[1].selectedItem.toString(); filters.album = spinners[2].selectedItem.toString(); filters.genre = spinners[3].selectedItem.toString(); filters.folder = spinners[4].selectedItem.toString(); filters.duration = spinners[5].selectedItem.toString(); filters.availability = spinners[6].selectedItem.toString(); renderLibrary()
        }.show()
    }

    private fun filterValue(options: List<String>, index: Int): String = when (index) {
        0 -> filters.format; 1 -> filters.artist; 2 -> filters.album; 3 -> filters.genre; 4 -> filters.folder; 5 -> filters.duration; else -> filters.availability
    }.let { if (it in options) it else options.first() }

    private fun showSortDialog() {
        val options = arrayOf("Title", "Artist", "Album", "Duration", "Recently played")
        AlertDialog.Builder(this).setTitle("Sort library").setSingleChoiceItems(options, options.indexOf(sort)) { dialog, which -> sort = options[which]; dialog.dismiss(); renderLibrary() }.show()
    }

    private fun showPlaylistPicker(item: AudioItem) {
        val playlists = database.playlists()
        if (playlists.isEmpty()) { promptPlaylist(listOf(item)); return }
        AlertDialog.Builder(this).setTitle("Add to playlist").setItems(playlists.map { it.name }.toTypedArray()) { _, which -> database.addToPlaylist(playlists[which].id, item); Toast.makeText(this, "Added to ${playlists[which].name}", Toast.LENGTH_SHORT).show() }.setPositiveButton("New playlist") { _, _ -> promptPlaylist(listOf(item)) }.setNegativeButton("Cancel", null).show()
    }

    private fun promptPlaylist(items: List<AudioItem> = emptyList()) {
        val input = EditText(this).apply { hint = "Playlist name"; contentDescription = "Playlist name" }
        AlertDialog.Builder(this).setTitle("New playlist").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Create") { _, _ -> runCatching { database.createPlaylist(input.text.toString(), items) }.onSuccess { renderLibrary() }.onFailure { Toast.makeText(this, "Choose a unique playlist name.", Toast.LENGTH_LONG).show() } }.show()
    }

    private fun promptRenamePlaylist(playlist: MusicPlaylist) {
        val input = EditText(this).apply { setText(playlist.name); selectAll(); contentDescription = "Playlist name" }
        AlertDialog.Builder(this).setTitle("Rename playlist").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> runCatching { database.renamePlaylist(playlist.id, input.text.toString()) }.onSuccess { showPlaylist(playlist.id) }.onFailure { Toast.makeText(this, "Choose a unique playlist name.", Toast.LENGTH_LONG).show() } }.show()
    }

    private fun confirmDeletePlaylist(playlist: MusicPlaylist) {
        AlertDialog.Builder(this).setTitle("Delete ${playlist.name}?").setMessage("The audio files will stay on your device.").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> database.deletePlaylist(playlist.id); renderLibrary() }.show()
    }

    private fun showFoldersDialog() {
        val folders = database.folders()
        if (folders.isEmpty()) { Toast.makeText(this, "No folders selected yet.", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Selected folders").setItems(folders.map { it.name }.toTypedArray()) { _, which ->
            val folder = folders[which]
            AlertDialog.Builder(this).setTitle("Remove ${folder.name}?").setMessage("Its tracks will remain in the library as unavailable records.").setNegativeButton("Keep", null).setPositiveButton("Remove") { _, _ -> database.removeFolder(folder.uri); renderLibrary() }.show()
        }.setPositiveButton("Done", null).show()
    }

    private fun requestFolder() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER_REQUEST)

    private fun refreshFolders() {
        if (refreshing) return
        val folders = database.folders()
        if (folders.isEmpty()) { renderLibrary(); return }
        refreshing = true
        renderLibrary()
        executor.execute {
            val failure = runCatching {
                folders.forEach { folder -> database.replaceFolderTracks(folder, scanFolder(folder)) }
            }.exceptionOrNull()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    refreshing = false
                    currentUri = database.current()
                    renderLibrary()
                    if (failure != null) Toast.makeText(this, "Could not refresh a music folder. Existing tracks were kept.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshFolder(folder: MusicFolder) {
        if (refreshing) return
        refreshing = true
        renderLibrary()
        executor.execute {
            val failure = runCatching { database.replaceFolderTracks(folder, scanFolder(folder)) }.exceptionOrNull()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    refreshing = false
                    renderLibrary()
                    if (failure != null) Toast.makeText(this, "Could not refresh this music folder. Existing tracks were kept.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private data class ScanNode(val id: String, val path: String)

    private fun scanFolder(folder: MusicFolder): List<AudioItem> {
        val tree = Uri.parse(folder.uri)
        val pending = ArrayDeque<ScanNode>().apply { add(ScanNode(DocumentsContract.getTreeDocumentId(tree), folder.name)) }
        val items = mutableListOf<AudioItem>()
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, node.id)
            contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "Unnamed"
                    val mime = cursor.getString(2).orEmpty()
                    val child = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(ScanNode(id, "${node.path} / $name"))
                    else if (MusicFormats.isAudio(name, mime)) items += readAudio(child, folder.uri, node.path, name, mime)
                }
            }
        }
        return items
    }

    private fun readAudio(uri: Uri, folderUri: String, folderName: String, name: String, mime: String): AudioItem {
        val fallback = name.substringBeforeLast('.', name)
        val retriever = android.media.MediaMetadataRetriever()
        var title = fallback
        var artist = "Unknown artist"
        var album = folderName.substringAfterLast(" / ")
        var genre = "Unknown genre"
        var duration = 0L
        runCatching {
            retriever.setDataSource(this, uri)
            title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { fallback } ?: fallback
            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { artist } ?: artist
            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.ifBlank { album } ?: album
            genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)?.ifBlank { genre } ?: genre
            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
        retriever.release()
        return AudioItem(uri.toString(), folderUri, folderName, name, title, artist, album, genre, MusicFormats.extension(name, mime), duration)
    }

    private fun header(title: String, player: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(24), dp(16), dp(4))
        addView(ImageButton(this@MusicActivity).apply {
            setImageResource(R.drawable.ic_map_back)
            imageTintList = ColorStateList.valueOf(getColor(R.color.map_text))
            setBackgroundResource(R.drawable.map_button_surface)
            backgroundTintList = null
            contentDescription = "Back"
            setOnClickListener { if (player) renderLibrary() else finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(TextView(this@MusicActivity).apply { text = title; textSize = 18f; setTextColor(getColor(R.color.map_text)); setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun cover(item: AudioItem, size: Int): View {
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "Artwork for ${item.title}" }
        val retriever = android.media.MediaMetadataRetriever()
        val bitmap = runCatching {
            retriever.setDataSource(this, Uri.parse(item.uri))
            retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }.getOrNull()
        retriever.release()
        if (bitmap != null) image.setImageBitmap(bitmap) else {
            image.setBackgroundResource(R.drawable.map_focus_surface)
            image.setImageResource(if (size >= dp(100)) R.drawable.ic_map_music_artwork else R.drawable.ic_map_music)
            image.imageTintList = ColorStateList.valueOf(getColor(R.color.map_accent))
        }
        return image
    }

    private fun actionRow(title: String, detail: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(getColor(R.color.map_card), dp(14))
        setOnClickListener { click() }
        addView(TextView(this@MusicActivity).apply { text = title; textSize = 16f; setTextColor(getColor(R.color.map_text)) })
        addView(TextView(this@MusicActivity).apply { text = detail; textSize = 13f; setTextColor(getColor(R.color.map_muted)); setPadding(0, dp(3), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun actionButton(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        minWidth = dp(48)
        setTextColor(getColor(R.color.map_text))
        setBackgroundResource(R.drawable.map_button_surface)
        backgroundTintList = null
        setOnClickListener { click() }
    }

    private fun iconButton(icon: Int, description: String, click: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        minimumWidth = dp(56)
        minimumHeight = dp(56)
        setBackgroundResource(R.drawable.map_button_surface)
        backgroundTintList = null
        imageTintList = ColorStateList.valueOf(getColor(R.color.map_text))
        setOnClickListener { click() }
    }

    private fun addEmpty(parent: LinearLayout, text: String) = parent.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(getColor(R.color.map_muted)); setPadding(0, dp(16), 0, dp(16)) })
    private fun activeFilterSummary(): String = listOf(filters.format, filters.artist, filters.album, filters.genre, filters.folder, filters.duration, filters.availability).filterNot { it.startsWith("All") || it == "Any length" }.joinToString(" · ")
    private fun formatDuration(ms: Long): String = if (ms <= 0) "–" else "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun startMusicAction(action: String, uri: String? = null, extras: Map<String, Any> = emptyMap()) {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        uri?.let { intent.putExtra(MusicService.EXTRA_URI, it) }
        extras.forEach { (key, value) -> when (value) { is Int -> intent.putExtra(key, value); is Short -> intent.putExtra(key, value); is Boolean -> intent.putExtra(key, value); is String -> intent.putExtra(key, value) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private fun sleepText(mode: String, minutes: Int) = when (mode) { MusicService.SLEEP_TRACK -> "Stops after this track"; MusicService.SLEEP_QUEUE -> "Stops after this queue"; MusicService.SLEEP_TIMER -> "Stops in $minutes minutes"; else -> "" }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        private const val FOLDER_REQUEST = 30
        private const val NOTIFICATION_REQUEST = 31
    }
}
