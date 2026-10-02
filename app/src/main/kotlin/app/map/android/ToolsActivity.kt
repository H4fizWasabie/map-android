package app.map.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.addCallback
import androidx.core.widget.doAfterTextChanged

class ToolsActivity : MapActivity() {
    private lateinit var documents: DocumentDatabase
    private var contentScroll: ScrollView? = null
    private var restoredScrollY = 0
    private var initialResumePending = true
    private var documentsMode = false
    private var documentQuery = ""
    private var documentFilter = "Recent"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documents = DocumentDatabase(this)
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y, 0) ?: 0
        documentsMode = savedInstanceState?.getBoolean("documents_mode") ?: false
        documentQuery = savedInstanceState?.getString("document_query").orEmpty()
        documentFilter = savedInstanceState?.getString("document_filter") ?: "Recent"
        onBackPressedDispatcher.addCallback(this) {
            if (documentsMode) { documentsMode = false; render() } else finish()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("documents_mode", documentsMode)
        outState.putString("document_query", documentQuery)
        outState.putString("document_filter", documentFilter)
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
            setBackgroundColor(mapColor(R.color.map_background))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        if (documentsMode) body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(MapUi.brandMark(this@ToolsActivity), LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Tools") { documentsMode = false; render() }.apply {
                icon = getDrawable(R.drawable.ic_map_back)
                contentDescription = "Back to tools"
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        else MapUi.addBrandMark(this, body)
        body.addView(TextView(this).apply {
            text = if (documentsMode) "Your documents" else "Your tools"
            MapUi.display(this)
            setPadding(0, 0, 0, dp(8))
        })
        body.addView(TextView(this).apply {
            text = if (documentsMode) "Your files stay where you saved them." else "Documents, scanning, and music. All on this device."
            MapUi.metadata(this)
            setPadding(0, 0, 0, dp(16))
        })
        if (documentsMode) addDocumentLibrary(body) else {
            val compact = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale >= 1.3f
            if (!compact) addAppearanceSelector(body)
            addDocuments(body)
            addScan(body)
            addMusic(body)
            if (compact) addAppearanceSelector(body)
        }
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
        addDocumentRows(parent, recent.take(3))
    }

    private fun addDocumentRows(parent: LinearLayout, items: List<DocumentItem>) {
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(72)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundResource(R.drawable.map_signal_row)
                isFocusable = true
                contentDescription = if (item.available) "Document: ${item.name}" else "Unavailable document: ${item.name}"
                setOnClickListener { openDocument(item) }
            }
            row.addView(toolIcon(R.drawable.ic_map_documents), LinearLayout.LayoutParams(dp(40), dp(48)))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(TextView(this@ToolsActivity).apply {
                    text = item.name
                    MapUi.section(this)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@ToolsActivity).apply {
                    text = when {
                        !item.available -> "Unavailable · tap to recover"
                        item.mime == "application/pdf" -> "PDF · on this device"
                        else -> "Image · on this device"
                    }
                    MapUi.metadata(this)
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            parent.addView(row)
            parent.addView(View(this).apply { setBackgroundColor(mapColor(R.color.map_divider)) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun addDocumentLibrary(parent: LinearLayout) {
        // ponytail: filter small local libraries in memory; use SQLite paging if lists grow large.
        val recent = documents.recent()
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun refreshRows() {
            rows.removeAllViews()
            val items = recent.filter { item ->
                val pdf = item.mime == "application/pdf" || item.name.endsWith(".pdf", ignoreCase = true)
                item.name.contains(documentQuery.trim(), ignoreCase = true) &&
                    (documentFilter == "Recent" || (documentFilter == "PDFs" && pdf) || (documentFilter == "Images" && !pdf))
            }
            if (items.isEmpty()) rows.addView(TextView(this).apply {
                text = if (recent.isEmpty()) "No documents yet. Open a PDF or image to keep it here." else "No documents match. Try another name or file type."
                MapUi.body(this)
                setPadding(0, dp(24), 0, dp(24))
            }) else addDocumentRows(rows, items)
        }
        parent.addView(EditText(this).apply {
            hint = "Search documents"
            setHintTextColor(mapColor(R.color.map_muted))
            contentDescription = "Search documents"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            MapUi.body(this)
            minHeight = dp(48)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundResource(R.drawable.map_surface)
            setText(documentQuery)
            doAfterTextChanged { documentQuery = it.toString(); refreshRows() }
        })
        val stackFilters = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale >= 1.3f
        parent.addView(LinearLayout(this).apply {
            orientation = if (stackFilters) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            listOf("Recent", "PDFs", "Images").forEachIndexed { index, filter ->
                addView(button(filter) {
                    documentFilter = filter
                    for (i in 0 until childCount) {
                        val control = getChildAt(i) as com.google.android.material.button.MaterialButton
                        control.isSelected = control.text.toString() == filter
                        control.backgroundTintList = android.content.res.ColorStateList.valueOf(mapColor(if (control.isSelected) R.color.map_selection else R.color.map_card))
                    }
                    refreshRows()
                }.apply {
                    cornerRadius = dp(24)
                    isSelected = documentFilter == filter
                    backgroundTintList = android.content.res.ColorStateList.valueOf(mapColor(if (isSelected) R.color.map_selection else R.color.map_card))
                }, LinearLayout.LayoutParams(if (stackFilters) -1 else 0, -2, if (stackFilters) 0f else 1f).apply {
                    if (index > 0) { if (stackFilters) topMargin = dp(8) else marginStart = dp(8) }
                })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(18) })
        parent.addView(TextView(this).apply { text = "Recently opened"; MapUi.section(this) })
        parent.addView(rows)
        refreshRows()
        parent.addView(MapUi.primaryButton(this).apply {
            text = "Open document"
            setOnClickListener { openDocument() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
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

    private fun addAppearanceSelector(parent: LinearLayout) {
        parent.addView(TextView(this).apply {
            text = "Appearance"
            MapUi.section(this)
            setPadding(0, dp(22), 0, dp(4))
        })
        parent.addView(TextView(this).apply {
            text = "Choose a daybook style for MAP."
            MapUi.metadata(this)
            setPadding(0, 0, 0, dp(6))
        })
        val selected = MapAppearance.selected(this)
        MapAppearance.entries.forEach { appearance ->
            val isSelected = appearance == selected
            val title = TextView(this).apply {
                text = appearance.title
                MapUi.headline(this)
            }
            val detail = TextView(this).apply {
                text = appearance.description
                MapUi.metadata(this)
                setPadding(0, dp(2), 0, 0)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(84)
                setPadding(dp(10), dp(8), dp(8), dp(8))
                setBackgroundResource(if (isSelected) R.drawable.map_focus_surface else R.drawable.map_surface)
                contentDescription = "${appearance.title}. ${appearance.description}${if (isSelected) ". Current appearance" else ". Select appearance"}"
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    if (!isSelected) {
                        MapAppearance.select(this@ToolsActivity, appearance)
                        recreate()
                    }
                }
            }
            row.addView(android.widget.ImageView(this).apply {
                setImageDrawable(MapUi.illustration(this@ToolsActivity, appearance.artwork))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(54), dp(68)))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(detail)
                if (isSelected) addView(TextView(this@ToolsActivity).apply {
                    text = "Selected"
                    MapUi.label(this)
                    setTextColor(mapColor(R.color.map_accent))
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            row.addView(android.widget.RadioButton(this).apply {
                isChecked = isSelected
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                buttonTintList = android.content.res.ColorStateList.valueOf(mapColor(R.color.map_accent))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
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
            if (title == "Documents") {
                contentDescription = "Browse documents"
                isFocusable = true
                setOnClickListener { documentsMode = true; render() }
            }
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
        imageTintList = android.content.res.ColorStateList.valueOf(mapColor(R.color.map_accent))
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
        minHeight = dp(56)
        isAllCaps = false
        minWidth = dp(48)
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = MapUi.dp(this, value)

    companion object {
        private const val DOCUMENT_REQUEST = 31
        private const val STATE_SCROLL_Y = "scroll_y"
    }
}
