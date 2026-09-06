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

data class DocumentText(
    val page: Int,
    val text: String
)

class DocumentDatabase(context: Context) : SQLiteOpenHelper(context, "map-documents.db", null, 2) {
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
        createTextTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTextTable(db)
    }

    private fun createTextTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS document_text (
                uri TEXT NOT NULL,
                page INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY(uri, page)
            )
            """.trimIndent()
        )
    }

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

    fun saveText(uri: String, page: Int, text: String) {
        writableDatabase.insertWithOnConflict(
            "document_text",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("page", page)
                put("text", text)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun text(uri: String): List<DocumentText> = buildList {
        readableDatabase.query(
            "document_text",
            arrayOf("page", "text"),
            "uri = ?",
            arrayOf(uri),
            null,
            null,
            "page ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) add(DocumentText(cursor.getInt(0), cursor.getString(1)))
        }
    }

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
