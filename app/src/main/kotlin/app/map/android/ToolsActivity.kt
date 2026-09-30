package app.map.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ToolsActivity : AppCompatActivity() {
    private lateinit var documents: DocumentDatabase
    private var contentScroll: ScrollView? = null
    private var restoredScrollY = 0
    private var initialResumePending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documents = DocumentDatabase(this)
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y, 0) ?: 0
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SCROLL_Y, contentScroll?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (initialResumePending) {
            initialResumePending = false
            return
        }
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
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        MapUi.addBrandMark(this, body)
        body.addView(TextView(this).apply {
            text = "ON THIS DEVICE"
            MapUi.label(this)
            setTextColor(getColor(R.color.map_accent))
            letterSpacing = 0.12f
            setPadding(0, dp(18), 0, 0)
        })
        body.addView(TextView(this).apply {
            text = "Tools"
            MapUi.display(this)
            setPadding(0, dp(4), 0, dp(4))
        })
        body.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(16) })
        body.addView(TextView(this).apply {
            text = "Files, scanning, and music stay in your local workspace."
            MapUi.body(this)
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(8))
        })
        body.addView(TextView(this).apply {
            text = "YOUR WORKBENCH"
            MapUi.caption(this)
            letterSpacing = 0.14f
            setPadding(0, dp(16), 0, dp(8))
        })
        addDocuments(body)
        addScan(body)
        addMusic(body)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@ToolsActivity).apply {
                contentScroll = this
                addView(body)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        MapUi.addPrimaryNavigation(root, content, MapUi.bottomNavigation(this, "Tools", ::navigate))
        MapUi.applySystemBarInsets(root)
        setContentView(root)
        val scrollY = restoredScrollY
        restoredScrollY = 0
        contentScroll?.post { contentScroll?.scrollTo(0, scrollY) }
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
        ) { openDocument() }
        recent.take(3).forEach { item ->
            parent.addView(TextView(this).apply {
                text = if (item.available) item.name else "${item.name} · unavailable"
                MapUi.body(this)
                setTextColor(if (item.available) getColor(R.color.map_text) else getColor(R.color.map_muted))
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                contentDescription = if (item.available) "Document: ${item.name}" else "Unavailable document: ${item.name}"
                setOnClickListener { openDocument(item) }
            })
        }
    }

    private fun addScan(parent: LinearLayout) {
        val scans = ScanDatabase(this)
        val pages = scans.unfinishedPageCount()
        scans.close()
        addToolRow(parent, "Scan", if (pages == 0) "Capture pages into a local PDF." else "$pages unfinished page${if (pages == 1) "" else "s"}", "Open scanner") {
            startActivity(Intent(this, ScanActivity::class.java))
        }
    }

    private fun addMusic(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val current = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        addToolRow(parent, "Music", current?.let { if (playing) "Playing ${it.title}" else "Ready with ${it.title}" } ?: "Play music stored on this device", "Open music") {
            startActivity(Intent(this, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true))
        }
    }

    private fun addToolRow(parent: LinearLayout, title: String, subtitle: String, action: String, click: () -> Unit) {
        val iconResource = when (title) {
            "Documents" -> R.drawable.ic_map_documents
            "Scan" -> R.drawable.ic_map_scan
            else -> R.drawable.ic_map_music
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ToolsActivity).apply {
                text = title
                MapUi.headline(this)
            })
            addView(TextView(this@ToolsActivity).apply {
                text = subtitle
                MapUi.metadata(this)
                setPadding(0, dp(4), 0, 0)
            })
        }
        val actionView = button(action, click)
        val stacked = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale >= 1.3f
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(R.drawable.map_tool_surface)
            if (stacked) {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                addView(LinearLayout(this@ToolsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(toolIcon(iconResource), LinearLayout.LayoutParams(dp(44), dp(44)))
                    addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
                }, LinearLayout.LayoutParams(-1, -2))
                addView(actionView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            } else {
                addView(toolIcon(iconResource), LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
                addView(actionView, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            }
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun toolIcon(resource: Int) = android.widget.ImageView(this).apply {
        setImageResource(resource)
        imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.map_accent))
        background = getDrawable(R.drawable.map_tool_icon)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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

    private fun button(label: String, click: () -> Unit) = MapUi.button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        minWidth = dp(48)
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = MapUi.dp(this, value)

    companion object {
        private const val DOCUMENT_REQUEST = 31
        private const val STATE_SCROLL_Y = "scroll_y"
    }
}
