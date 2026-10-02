package app.map.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ScanActivity : MapActivity() {
    private lateinit var database: ScanDatabase
    private lateinit var scanner: GmsDocumentScanner
    private var sessionId = 0L
    private lateinit var pages: LinearLayout
    private val selectedPageIds = mutableSetOf<Long>()
    private var selectionInitialized = false
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var exportButton: MaterialButton? = null
    private var exporting = false
    private var importing = false
    private var initialResumePending = true
    private var musicPlayButton: MaterialButton? = null

    private val musicStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val button = musicPlayButton ?: return
            val event = intent ?: return
            if (!event.hasExtra(MusicService.EXTRA_PLAYING)) return
            button.text = if (event.getBooleanExtra(MusicService.EXTRA_PLAYING, false)) "Pause" else "Play"
            button.contentDescription = button.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ScanDatabase(this)
        scanner = GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
        sessionId = database.activeSession()
        savedInstanceState?.getLongArray(STATE_SELECTED_PAGE_IDS)?.let {
            selectedPageIds += it.toList()
            selectionInitialized = true
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray(STATE_SELECTED_PAGE_IDS, selectedPageIds.toLongArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        exportExecutor.shutdownNow()
        database.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (initialResumePending) {
            initialResumePending = false
            return
        }
        if (::database.isInitialized) render()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, musicStateReceiver, IntentFilter(MusicService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(musicStateReceiver) }
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SCANNER_REQUEST || resultCode != RESULT_OK || data == null) return
        val uris = runCatching {
            GmsDocumentScanningResult.fromActivityResultIntent(data)?.getPages().orEmpty().map { it.getImageUri() }
        }.getOrElse {
            Toast.makeText(this, "Could not import the scanned pages", Toast.LENGTH_LONG).show()
            return
        }
        if (uris.isEmpty()) return
        importing = true
        render()
        exportExecutor.execute {
            val imported = mutableListOf<Long>()
            var failures = 0
            uris.forEach { uri ->
                runCatching { copyPage(uri) }
                    .onSuccess(imported::add)
                    .onFailure { failures++ }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                selectedPageIds += imported
                importing = false
                render()
                if (failures > 0) Toast.makeText(this, "Could not import $failures scanned page${if (failures == 1) "" else "s"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), dp(24))
            setBackgroundColor(mapColor(R.color.map_background))
        }
        MapUi.addBrandMark(this, root)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageButton(this@ScanActivity).apply {
                setImageResource(R.drawable.ic_map_back)
                imageTintList = ColorStateList.valueOf(mapColor(R.color.map_text))
                setBackgroundResource(R.drawable.map_button_surface)
                backgroundTintList = null
                contentDescription = "Back"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@ScanActivity).apply {
                text = "Scan"
                MapUi.section(this)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, 0, 0)
            })
        })
        root.addView(TextView(this).apply {
            text = "Your scans"
            MapUi.display(this)
            setPadding(0, dp(16), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Corrected pages stay on this device until you export them."
            MapUi.body(this)
            setTextColor(mapColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(16))
        })
        val storedPages = database.pages(sessionId)
        root.addView((if (storedPages.isEmpty()) MapUi.primaryButton(this) else MapUi.button(this)).apply {
            text = if (importing) "Importing pages…" else "Scan documents"
            isAllCaps = false
            isEnabled = !importing && !exporting
            setOnClickListener { startScanner() }
        })
        if (!selectionInitialized) {
            selectedPageIds += storedPages.filter { File(it.path).isFile }.map { it.id }
            selectionInitialized = true
        }
        selectedPageIds.retainAll(storedPages.filter { File(it.path).isFile }.map { it.id }.toSet())
        val exportButton = (if (selectedPageIds.isNotEmpty()) MapUi.primaryButton(this) else MapUi.button(this)).apply {
            text = when {
                importing -> "Importing pages…"
                exporting -> "Exporting PDF…"
                else -> "Export PDF (${selectedPageIds.size})"
            }
            isAllCaps = false
            isEnabled = selectedPageIds.isNotEmpty() && !exporting && !importing
            setOnClickListener { exportPdf() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        this.exportButton = exportButton
        root.addView(exportButton)
        root.addView(View(this).apply {
            setBackgroundColor(mapColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        root.addView(TextView(this).apply {
            text = "Pages · corners corrected in the scanner"
            MapUi.section(this)
            setPadding(0, dp(12), 0, dp(8))
        })
        pages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (storedPages.isEmpty()) pages.addView(TextView(this).apply {
            text = "No pages captured yet."
            MapUi.body(this)
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundResource(R.drawable.map_focus_surface)
            setTextColor(mapColor(R.color.map_muted))
        })
        storedPages.forEachIndexed { index, page ->
            pages.addView(CheckBox(this).apply {
                MapUi.tintCheckbox(this)
                MapUi.body(this)
                minHeight = dp(56)
                val available = File(page.path).isFile
                val status = if (available) "" else " (Unavailable)"
                text = "Page ${index + 1}: ${File(page.path).name}$status"
                setTextColor(if (status.isEmpty()) mapColor(R.color.map_text) else mapColor(R.color.map_muted))
                isChecked = page.id in selectedPageIds
                isEnabled = available && !exporting && !importing
                contentDescription = "Include page ${index + 1}"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPageIds.add(page.id) else selectedPageIds.remove(page.id)
                    exportButton.text = "Export PDF (${selectedPageIds.size})"
                    exportButton.isEnabled = selectedPageIds.isNotEmpty() && !exporting && !importing
                }
            })
        }
        root.addView(pages)
        addMusicMiniPlayer(root)
        val scroll = ScrollView(this).apply { addView(root) }
        MapUi.applySystemBarInsets(scroll)
        setContentView(scroll)
    }

    private fun addMusicMiniPlayer(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val item = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        if (item == null) return
        parent.addView(View(this).apply {
            setBackgroundColor(mapColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        parent.addView(TextView(this).apply {
            text = "Music"
            MapUi.section(this)
            setPadding(0, dp(12), 0, dp(8))
        })
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundResource(R.drawable.map_surface)
            contentDescription = "Music: ${item.title}"
            setOnClickListener { startActivity(Intent(this@ScanActivity, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)) }
            addView(TextView(this@ScanActivity).apply {
                text = "${item.title}\n${item.artist}"
                MapUi.body(this)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = MapUi.button(this@ScanActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    val intent = Intent(this@ScanActivity, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE)
                    if (!startMapMusicService(this@ScanActivity, intent)) {
                        Toast.makeText(this@ScanActivity, "MAP could not start Music. Try Play again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            musicPlayButton = playButton
            addView(playButton)
        }.also { parent.addView(it) }
    }

    private fun startScanner() {
        val activeSessionId = database.activeSession()
        if (activeSessionId != sessionId) {
            sessionId = activeSessionId
            selectedPageIds.clear()
            selectionInitialized = true
            render()
        }
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                try {
                    startIntentSenderForResult(intentSender, SCANNER_REQUEST, null, 0, 0, 0)
                } catch (_: IntentSender.SendIntentException) {
                    Toast.makeText(this, "Could not open the document scanner", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Document scanner is not available on this device", Toast.LENGTH_LONG).show()
            }
    }

    private fun copyPage(uri: Uri): Long {
        val directory = File(filesDir, "scans/$sessionId").apply { mkdirs() }
        val target = File(directory, "import-${System.currentTimeMillis()}-${database.pages(sessionId).size}.jpg")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return database.addPage(sessionId, target.absolutePath)
    }

    private fun exportPdf() {
        if (exporting) return
        val selectedIds = selectedPageIds.toSet()
        if (selectedIds.isEmpty()) return
        exporting = true
        exportButton?.apply {
            text = "Exporting PDF…"
            isEnabled = false
        }
        exportExecutor.execute {
            val result = runCatching { exportPdfFile(selectedIds) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                exporting = false
                exportButton?.apply {
                    text = "Export PDF (${selectedPageIds.size})"
                    isEnabled = selectedPageIds.isNotEmpty()
                }
                result.onSuccess { file ->
                    val recordFailure = runCatching { rememberPdf(file) }.exceptionOrNull()
                    val hasUnavailablePages = database.pages(sessionId).any { !File(it.path).isFile }
                    val sessionFailure = if (recordFailure == null) {
                        if (hasUnavailablePages) null else runCatching { database.markExported(sessionId) }.exceptionOrNull()
                    } else null
                    if (recordFailure != null) {
                        Toast.makeText(this, "PDF created, but MAP could not add it to Documents.", Toast.LENGTH_LONG).show()
                    } else if (hasUnavailablePages) {
                        Toast.makeText(this, "PDF saved. An unavailable page keeps this scan open.", Toast.LENGTH_LONG).show()
                    } else if (sessionFailure != null) {
                        Toast.makeText(this, "PDF is in Documents, but this scan could not be closed.", Toast.LENGTH_LONG).show()
                    }
                    val shareFailure = runCatching { sharePdf(file) }.exceptionOrNull()
                    if (shareFailure != null) {
                        val message = if (recordFailure == null) "PDF is in Documents, but sharing could not open." else "PDF was created, but sharing could not open."
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }.onFailure { failure ->
                    if (failure.message == ScanPdfExporter.UNREADABLE_PAGE) render()
                    val message = if (failure.message == ScanPdfExporter.UNREADABLE_PAGE) {
                        "A selected page is unavailable. Uncheck it and export again."
                    } else {
                        "Could not export the PDF. Check the selected pages and available storage."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportPdfFile(selectedIds: Set<Long>): File {
        val selectedPaths = database.pages(sessionId).filter { it.id in selectedIds }.map { it.path }
        val directory = File(filesDir, "scans")
        val baseName = "MAP-${timestamp()}"
        var output = File(directory, "$baseName.pdf")
        var suffix = 2
        while (output.exists()) output = File(directory, "$baseName-$suffix.pdf").also { suffix++ }
        return ScanPdfExporter.export(selectedPaths, output)
    }

    private fun sharePdf(output: File) {
        val shareUri = outputUri(output)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"))
    }

    private fun rememberPdf(output: File) {
        val documents = DocumentDatabase(this)
        try {
            documents.upsert(outputUri(output).toString(), output.name, "application/pdf")
        } finally {
            documents.close()
        }
    }

    private fun outputUri(output: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", output)

    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private fun dp(value: Int) = MapUi.dp(this, value)

    companion object {
        private const val SCANNER_REQUEST = 12
        private const val STATE_SELECTED_PAGE_IDS = "selected_page_ids"
    }
}
