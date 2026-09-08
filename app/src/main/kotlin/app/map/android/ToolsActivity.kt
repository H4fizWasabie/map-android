package app.map.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ToolsActivity : Activity() {
    private lateinit var documents: DocumentDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documents = DocumentDatabase(this)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::documents.isInitialized) render()
    }

    override fun onDestroy() {
        if (::documents.isInitialized) documents.close()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            setBackgroundColor(getColor(R.color.map_background))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        body.addView(TextView(this).apply {
            text = "Personal tools"
            textSize = 30f
            setTextColor(getColor(R.color.map_text))
        })
        body.addView(TextView(this).apply {
            text = "Simple, local tools for the things you return to every day."
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, dp(4), 0, dp(16))
        })
        addDocuments(body)
        addScan(body)
        addMusic(body)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@ToolsActivity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        MapUi.addPrimaryNavigation(root, content, MapUi.bottomNavigation(this, "Tools", ::navigate))
        MapUi.applySystemBarInsets(root)
        setContentView(root)
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(24), dp(16), dp(4))
        addView(TextView(this@ToolsActivity).apply {
            text = "Tools"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(getColor(R.color.map_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 0)
        })
    }

    private fun navigate(label: String) {
        when (label) {
            "Home" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            "Calendar" -> startActivity(Intent(this, CalendarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            "Tasks" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_TASKS, true))
        }
    }

    private fun addDocuments(parent: LinearLayout) {
        val recent = documents.recent()
        addToolRow(
            parent,
            "Documents",
            if (recent.isEmpty()) "Read local PDFs and images directly." else "${recent.size} saved document${if (recent.size == 1) "" else "s"}",
            "Open document",
            R.drawable.ic_map_documents
        ) { openDocument() }
        recent.take(3).forEach { item ->
            parent.addView(TextView(this).apply {
                text = if (item.available) item.name else "${item.name} · unavailable"
                textSize = 15f
                setTextColor(if (item.available) getColor(R.color.map_text) else getColor(R.color.map_muted))
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(64), dp(6), 0, dp(6))
                contentDescription = "Document: ${item.name}"
                setOnClickListener {
                    if (!item.available) {
                        Toast.makeText(this@ToolsActivity, "This file is no longer available", Toast.LENGTH_SHORT).show()
                    } else openDocument(item)
                }
            })
        }
    }

    private fun addScan(parent: LinearLayout) {
        val scans = ScanDatabase(this)
        val pages = scans.unfinishedPageCount()
        scans.close()
        addToolRow(parent, "Scan", if (pages == 0) "Capture pages into a local PDF." else "$pages unfinished page${if (pages == 1) "" else "s"}", "Open scanner", R.drawable.ic_map_scan) {
            startActivity(Intent(this, ScanActivity::class.java))
        }
    }

    private fun addMusic(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val current = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        addToolRow(parent, "Music", current?.let { if (playing) "Playing ${it.title}" else "Ready with ${it.title}" } ?: "Play music stored on this device", "Open music", R.drawable.ic_map_music) {
            startActivity(Intent(this, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true))
        }
    }

    private fun addToolRow(parent: LinearLayout, title: String, subtitle: String, action: String, icon: Int, click: () -> Unit) {
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val mark = android.widget.ImageView(this).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.map_accent))
            setBackgroundResource(R.drawable.map_focus_surface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = null
        }
        row.addView(mark, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(TextView(this@ToolsActivity).apply {
                text = title
                textSize = 19f
                setTextColor(getColor(R.color.map_text))
            })
            addView(TextView(this@ToolsActivity).apply {
                text = subtitle
                textSize = 14f
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(action, click), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun openDocument() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, DOCUMENT_REQUEST)
    }

    private fun openDocument(item: DocumentItem) {
        startActivity(Intent(this, DocumentViewerActivity::class.java).apply {
            putExtra(DocumentViewerActivity.EXTRA_URI, item.uri)
            putExtra(DocumentViewerActivity.EXTRA_NAME, item.name)
            putExtra(DocumentViewerActivity.EXTRA_MIME, item.mime)
        })
    }

    @Deprecated("Android activity result API retained for this small native app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DOCUMENT_REQUEST || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            if (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: SecurityException) {
            // Some providers grant a temporary read permission only; the viewer can still open it now.
        }
        val name = displayName(uri)
        val mime = contentResolver.getType(uri).orEmpty().ifBlank { "application/pdf" }
        documents.upsert(uri.toString(), name, mime)
        openDocument(DocumentItem(uri.toString(), name, mime, 0, true))
    }

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty().ifBlank { "Document" }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        minWidth = dp(48)
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DOCUMENT_REQUEST = 31
    }
}
