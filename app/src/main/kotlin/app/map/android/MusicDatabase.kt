package app.map.android

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

data class AudioItem(
    val uri: String,
    val folderUri: String,
    val folderName: String,
    val name: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val format: String,
    val durationMs: Long,
    val available: Boolean = true,
    val favorite: Boolean = false,
    val lastPlayed: Long = 0
)

data class MusicFolder(val uri: String, val name: String)
data class MusicPlaylist(val id: Long, val name: String, val trackCount: Int)

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, "map-music.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) = createSchema(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion >= 2) return
        db.transaction {
            execSQL("ALTER TABLE audio_items RENAME TO legacy_audio_items")
            createSchema(this)
            execSQL(
                """INSERT OR IGNORE INTO tracks
                    (uri, folder_uri, folder_name, name, title, artist, album, genre, format, duration_ms, available, favorite, last_played)
                    SELECT uri, COALESCE((SELECT value FROM music_state WHERE key = 'folder'), ''), 'Music', name,
                           name, 'Unknown artist', 'Unknown album', 'Unknown genre', '', 0, 1, 0, 0
                    FROM legacy_audio_items"""
            )
            execSQL("INSERT INTO queue_items(position, uri) SELECT position, uri FROM legacy_audio_items ORDER BY position")
            execSQL(
                """INSERT OR IGNORE INTO music_folders(uri, name)
                    SELECT value, 'Music' FROM music_state WHERE key = 'folder' AND value IS NOT NULL AND value != ''"""
            )
            execSQL("DROP TABLE legacy_audio_items")
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS music_folders (uri TEXT PRIMARY KEY, name TEXT NOT NULL, added_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS tracks (
                uri TEXT PRIMARY KEY,
                folder_uri TEXT NOT NULL,
                folder_name TEXT NOT NULL,
                name TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                genre TEXT NOT NULL,
                format TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                available INTEGER NOT NULL DEFAULT 1,
                favorite INTEGER NOT NULL DEFAULT 0,
                last_played INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS queue_items (position INTEGER PRIMARY KEY, uri TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_state (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_items (playlist_id INTEGER NOT NULL, position INTEGER NOT NULL, uri TEXT NOT NULL, PRIMARY KEY (playlist_id, position))")
    }

    fun folders(): List<MusicFolder> = readableDatabase.query(
        "music_folders", arrayOf("uri", "name"), null, null, null, null, "added_at ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(MusicFolder(cursor.getString(0), cursor.getString(1))) } }

    fun addFolder(uri: String, name: String) {
        writableDatabase.insertWithOnConflict(
            "music_folders",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("name", name)
                put("added_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeFolder(uri: String) {
        writableDatabase.transaction {
            delete("music_folders", "uri = ?", arrayOf(uri))
            update("tracks", ContentValues().apply { put("available", 0) }, "folder_uri = ?", arrayOf(uri))
        }
    }

    fun replaceFolderTracks(folder: MusicFolder, items: List<AudioItem>) {
        writableDatabase.transaction {
            update("tracks", ContentValues().apply { put("available", 0) }, "folder_uri = ?", arrayOf(folder.uri))
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("folder_uri", folder.uri)
                    put("folder_name", item.folderName)
                    put("name", item.name)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("genre", item.genre)
                    put("format", item.format)
                    put("duration_ms", item.durationMs)
                    put("available", 1)
                }
                val inserted = insertWithOnConflict(
                    "tracks",
                    null,
                    ContentValues(values).apply {
                        put("uri", item.uri)
                        put("favorite", 0)
                        put("last_played", 0)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (inserted == -1L) update("tracks", values, "uri = ?", arrayOf(item.uri))
            }
        }
    }

    fun tracks(): List<AudioItem> = readableDatabase.query(
        "tracks", TRACK_COLUMNS, null, null, null, null, "title COLLATE NOCASE ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.audioItem()) } }

    fun trackCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    fun track(uri: String?): AudioItem? {
        if (uri == null) return null
        return readableDatabase.query(
            "tracks", TRACK_COLUMNS, "uri = ?", arrayOf(uri), null, null, null, "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.audioItem() else null }
    }

    fun setFavorite(uri: String, favorite: Boolean) {
        writableDatabase.update(
            "tracks", ContentValues().apply { put("favorite", if (favorite) 1 else 0) }, "uri = ?", arrayOf(uri)
        )
    }

    fun markPlayed(uri: String) {
        writableDatabase.update(
            "tracks", ContentValues().apply { put("last_played", System.currentTimeMillis()) }, "uri = ?", arrayOf(uri)
        )
    }

    fun setQueue(items: List<AudioItem>, currentUri: String? = items.firstOrNull()?.uri) {
        writableDatabase.transaction {
            delete("queue_items", null, null)
            items.distinctBy { it.uri }.forEachIndexed { index, item ->
                insert("queue_items", null, ContentValues().apply { put("position", index); put("uri", item.uri) })
            }
            currentUri?.let { setState(this, "current", it) }
        }
    }

    fun queue(): List<AudioItem> = readableDatabase.rawQuery(
        """SELECT ${TRACK_COLUMNS.joinToString(", ") { "t.$it" }}
            FROM queue_items q JOIN tracks t ON t.uri = q.uri ORDER BY q.position""",
        null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.audioItem()) } }

    fun playNext(item: AudioItem) {
        val queue = queue().toMutableList().apply { removeAll { it.uri == item.uri } }
        val currentIndex = queue.indexOfFirst { it.uri == current() }
        queue.add((currentIndex + 1).coerceAtLeast(0), item)
        setQueue(queue, current())
    }

    fun addToQueue(item: AudioItem) {
        val queue = queue().toMutableList()
        if (queue.none { it.uri == item.uri }) queue += item
        setQueue(queue, current())
    }

    fun removeFromQueue(uri: String) {
        val remaining = queue().filterNot { it.uri == uri }
        val nextCurrent = if (current() == uri) remaining.firstOrNull()?.uri else current()
        setQueue(remaining, nextCurrent)
        if (nextCurrent == null) removeState("current")
    }

    fun moveQueue(uri: String, delta: Int) {
        val items = queue().toMutableList()
        val from = items.indexOfFirst { it.uri == uri }
        if (from < 0 || items.isEmpty()) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from != to) {
            items.add(to, items.removeAt(from))
            setQueue(items, current())
        }
    }

    fun clearQueue() {
        writableDatabase.delete("queue_items", null, null)
        removeState("current")
    }

    fun playlists(): List<MusicPlaylist> = readableDatabase.rawQuery(
        """SELECT p.id, p.name, COUNT(i.uri) FROM playlists p
            LEFT JOIN playlist_items i ON i.playlist_id = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE""",
        null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(MusicPlaylist(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))) } }

    fun createPlaylist(name: String, items: List<AudioItem> = emptyList()): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty())
        return writableDatabase.transaction {
            val id = insertOrThrow(
                "playlists", null, ContentValues().apply { put("name", cleanName); put("created_at", System.currentTimeMillis()) }
            )
            replacePlaylistItems(this, id, items)
            id
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty())
        writableDatabase.update("playlists", ContentValues().apply { put("name", cleanName) }, "id = ?", arrayOf(id.toString()))
    }

    fun deletePlaylist(id: Long) {
        writableDatabase.transaction {
            delete("playlist_items", "playlist_id = ?", arrayOf(id.toString()))
            delete("playlists", "id = ?", arrayOf(id.toString()))
        }
    }

    fun playlistTracks(id: Long): List<AudioItem> = readableDatabase.rawQuery(
        """SELECT ${TRACK_COLUMNS.joinToString(", ") { "t.$it" }} FROM playlist_items i
            JOIN tracks t ON t.uri = i.uri WHERE i.playlist_id = ? ORDER BY i.position""",
        arrayOf(id.toString())
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.audioItem()) } }

    fun addToPlaylist(id: Long, item: AudioItem) {
        val items = playlistTracks(id).toMutableList()
        if (items.none { it.uri == item.uri }) items += item
        writableDatabase.transaction { replacePlaylistItems(this, id, items) }
    }

    fun removeFromPlaylist(id: Long, uri: String) {
        writableDatabase.transaction { replacePlaylistItems(this, id, playlistTracks(id).filterNot { it.uri == uri }) }
    }

    fun movePlaylistItem(id: Long, uri: String, delta: Int) {
        val items = playlistTracks(id).toMutableList()
        val from = items.indexOfFirst { it.uri == uri }
        if (from < 0 || items.isEmpty()) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from != to) {
            items.add(to, items.removeAt(from))
            writableDatabase.transaction { replacePlaylistItems(this, id, items) }
        }
    }

    private fun replacePlaylistItems(db: SQLiteDatabase, id: Long, items: List<AudioItem>) {
        db.delete("playlist_items", "playlist_id = ?", arrayOf(id.toString()))
        items.distinctBy { it.uri }.forEachIndexed { index, item ->
            db.insert("playlist_items", null, ContentValues().apply {
                put("playlist_id", id)
                put("position", index)
                put("uri", item.uri)
            })
        }
    }

    fun setCurrent(uri: String) = setState("current", uri)
    fun current(): String? = state("current")
    fun setPlaying(playing: Boolean) = setState("playing", playing.toString())
    fun playing(): Boolean = state("playing").toBoolean()
    fun setPosition(positionMs: Int) = setState("position_ms", positionMs.toString())
    fun position(): Int = state("position_ms")?.toIntOrNull() ?: 0
    fun setShuffle(enabled: Boolean) = setState("shuffle", enabled.toString())
    fun shuffle(): Boolean = state("shuffle").toBoolean()
    fun setRepeat(mode: String) = setState("repeat", mode)
    fun repeat(): String = state("repeat") ?: "off"
    fun setEqualizerEnabled(enabled: Boolean) = setState("equalizer", enabled.toString())
    fun equalizerEnabled(): Boolean = state("equalizer").toBoolean()
    fun setEqualizerPreset(preset: Short) = setState("eq_preset", preset.toString())
    fun equalizerPreset(): Short? = state("eq_preset")?.toShortOrNull()
    fun setEqualizerBand(band: Int, level: Short) = setState("eq_band_$band", level.toString())
    fun equalizerBand(band: Int): Short? = state("eq_band_$band")?.toShortOrNull()
    fun setBassStrength(strength: Short) = setState("bass", strength.toString())
    fun bassStrength(): Short = state("bass")?.toShortOrNull() ?: 0
    fun setVirtualizerStrength(strength: Short) = setState("virtualizer", strength.toString())
    fun virtualizerStrength(): Short = state("virtualizer")?.toShortOrNull() ?: 0

    private fun setState(key: String, value: String) = setState(writableDatabase, key, value)

    private fun setState(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "music_state", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun removeState(key: String) {
        writableDatabase.delete("music_state", "key = ?", arrayOf(key))
    }

    private fun state(key: String): String? = readableDatabase.query(
        "music_state", arrayOf("value"), "key = ?", arrayOf(key), null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun Cursor.audioItem() = AudioItem(
        uri = getString(0),
        folderUri = getString(1),
        folderName = getString(2),
        name = getString(3),
        title = getString(4),
        artist = getString(5),
        album = getString(6),
        genre = getString(7),
        format = getString(8),
        durationMs = getLong(9),
        available = getInt(10) == 1,
        favorite = getInt(11) == 1,
        lastPlayed = getLong(12)
    )

    companion object {
        private val TRACK_COLUMNS = arrayOf(
            "uri", "folder_uri", "folder_name", "name", "title", "artist", "album", "genre", "format",
            "duration_ms", "available", "favorite", "last_played"
        )
    }
}
