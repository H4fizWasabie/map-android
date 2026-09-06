package app.map.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DocumentItem(
    val uri: String,
    val name: String,
    val mime: String,
    val lastOpened: Long,
    val available: Boolean
)

class DocumentDatabase(context: Context) : SQLiteOpenHelper(context, "map-documents.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE documents (
                uri TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                mime TEXT NOT NULL,
                last_opened INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                available INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun upsert(uri: String, name: String, mime: String) {
        writableDatabase.insertWithOnConflict(
            "documents",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("name", name)
                put("mime", mime)
                put("added_at", System.currentTimeMillis())
                put("available", 1)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun recent(): List<DocumentItem> {
        val items = mutableListOf<DocumentItem>()
        readableDatabase.query("documents", null, null, null, null, null, "last_opened DESC, added_at DESC").use { cursor ->
            while (cursor.moveToNext()) items += cursor.toDocument()
        }
        return items
    }

    fun markOpened(uri: String) = update(uri, ContentValues().apply {
        put("last_opened", System.currentTimeMillis())
        put("available", 1)
    })

    fun markUnavailable(uri: String) = update(uri, ContentValues().apply { put("available", 0) })

    private fun update(uri: String, values: ContentValues) {
        writableDatabase.update("documents", values, "uri = ?", arrayOf(uri))
    }

    private fun android.database.Cursor.toDocument() = DocumentItem(
        uri = getString(getColumnIndexOrThrow("uri")),
        name = getString(getColumnIndexOrThrow("name")),
        mime = getString(getColumnIndexOrThrow("mime")),
        lastOpened = getLong(getColumnIndexOrThrow("last_opened")),
        available = getInt(getColumnIndexOrThrow("available")) == 1
    )
}
