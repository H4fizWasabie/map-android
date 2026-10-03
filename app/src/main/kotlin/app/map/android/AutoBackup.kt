package app.map.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Weekly JSON copy of all tasks in a folder the user picked once. Only MAP's own `map-auto-YYYYMMDD.json` files are ever deleted. */
object AutoBackup {
    private const val PREFS = "map-auto-backup"
    private const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val KEEP = 5
    private val OWN_FILE = Regex("""map-auto-\d{8}\.json""")
    private val running = AtomicBoolean(false)

    // A last backup in the future means the clock moved; treat it as due so the stored time self-corrects.
    fun isDue(lastAt: Long, now: Long): Boolean = lastAt <= 0 || now - lastAt !in 0 until INTERVAL_MS

    fun fileName(now: Long): String = "map-auto-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))}.json"

    fun staleNames(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.filter { OWN_FILE.matches(it) }.sortedDescending().drop(keep)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString("folder", null)?.let(Uri::parse)
    fun lastBackupAt(context: Context): Long = prefs(context).getLong("last_at", 0L)
    fun lastError(context: Context): String? = prefs(context).getString("last_error", null)

    fun setFolder(context: Context, tree: Uri) {
        folder(context)?.takeIf { it != tree }?.let { release(context, it) }
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs(context).edit().putString("folder", tree.toString()).putLong("last_at", 0L).remove("last_error").apply()
    }

    fun disable(context: Context) {
        folder(context)?.let { release(context, it) }
        prefs(context).edit().clear().apply()
    }

    private fun release(context: Context, tree: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    /** Blocking; call off the main thread. */
    fun runIfDue(context: Context) {
        if (folder(context) != null && isDue(lastBackupAt(context), System.currentTimeMillis())) runNow(context)
    }

    /** Blocking; call off the main thread. Returns an error message, or null on success. The outcome is also stored for Tools. */
    fun runNow(context: Context): String? {
        val tree = folder(context) ?: return "No backup folder is set."
        if (!running.compareAndSet(false, true)) return null
        try {
            val resolver = context.contentResolver
            val now = System.currentTimeMillis()
            val database = TaskDatabase(context)
            val json = try { TaskBackup.toJson(database.allTasks(), now) } finally { database.close() }
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val children = mutableMapOf<String, String>()
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { while (it.moveToNext()) children[it.getString(1)] = it.getString(0) }
            val name = fileName(now)
            val target = children[name]?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, treeId), "application/json", name)
                ?: error("Could not create the backup file")
            resolver.openOutputStream(target, "wt")!!.use { it.write(json.toByteArray()) }
            staleNames(children.keys + name).forEach { stale ->
                children[stale]?.let { runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) } }
            }
            prefs(context).edit().putLong("last_at", now).remove("last_error").apply()
            return null
        } catch (error: Exception) {
            val message = "Could not write to the backup folder. Choose the folder again."
            prefs(context).edit().putString("last_error", message).apply()
            return message
        } finally {
            running.set(false)
        }
    }
}
