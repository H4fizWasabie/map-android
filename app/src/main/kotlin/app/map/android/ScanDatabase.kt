package app.map.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ScanPage(val id: Long, val path: String)

class ScanDatabase(context: Context) : SQLiteOpenHelper(context, "map-scans.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, exported INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE pages (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, path TEXT NOT NULL, position INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun activeSession(): Long {
        readableDatabase.query(
            "sessions", arrayOf("id"), "exported = 0", null, null, null, "created_at DESC", "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
            put("created_at", System.currentTimeMillis())
        })
    }

    fun addPage(sessionId: Long, path: String) {
        val position = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM pages WHERE session_id = ?", arrayOf(sessionId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        writableDatabase.insertOrThrow("pages", null, ContentValues().apply {
            put("session_id", sessionId)
            put("path", path)
            put("position", position)
        })
    }

    fun pages(sessionId: Long): List<ScanPage> {
        val pages = mutableListOf<ScanPage>()
        readableDatabase.query("pages", arrayOf("id", "path"), "session_id = ?", arrayOf(sessionId.toString()), null, null, "position ASC").use { cursor ->
            while (cursor.moveToNext()) pages += ScanPage(cursor.getLong(0), cursor.getString(1))
        }
        return pages
    }
}
