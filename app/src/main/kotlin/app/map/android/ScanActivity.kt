package app.map.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
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

class ScanActivity : Activity() {
    private lateinit var database: ScanDatabase
    private lateinit var scanner: GmsDocumentScanner
    private var sessionId = 0L
    private lateinit var pages: LinearLayout
    private val selectedPageIds = mutableSetOf<Long>()
    private var selectionInitialized = false
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var exportButton: Button? = null
    private var exporting = false
    private var importing = false
    private var initialResumePending = true
    private var musicPlayButton: Button? = null

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
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.map_background))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageButton(this@ScanActivity).apply {
                setImageResource(R.drawable.ic_map_back)
                imageTintList = ColorStateList.valueOf(getColor(R.color.map_text))
                setBackgroundResource(R.drawable.map_button_surface)
                backgroundTintList = null
                contentDescription = "Back"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@ScanActivity).apply {
                text = "Scan"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.map_text))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, 0, 0)
            })
        })
        root.addView(TextView(this).apply {
            text = "Capture documents"
            textSize = 30f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(16), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Corrected pages stay on this device until you export them."
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(16))
        })
        root.addView(Button(this, null, 0, R.style.MapPrimaryButton).apply {
            text = if (importing) "Importing pages…" else "Scan documents"
            isAllCaps = false
            isEnabled = !importing && !exporting
            setOnClickListener { startScanner() }
        })
        val storedPages = database.pages(sessionId)
        if (!selectionInitialized) {
            selectedPageIds += storedPages.map { it.id }
            selectionInitialized = true
        }
        selectedPageIds.retainAll(storedPages.map { it.id }.toSet())
        val exportButton = Button(this, null, 0, R.style.MapPrimaryButton).apply {
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
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        root.addView(TextView(this).apply {
            text = "Pages · corners corrected in the scanner"
            textSize = 18f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(12), 0, dp(8))
        })
        pages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (storedPages.isEmpty()) pages.addView(TextView(this).apply {
            text = "No pages captured yet."
            setTextColor(getColor(R.color.map_muted))
        })
        storedPages.forEachIndexed { index, page ->
            pages.addView(CheckBox(this).apply {
                val status = if (File(page.path).exists()) "" else " (Unavailable)"
                text = "Page ${index + 1}: ${File(page.path).name}$status"
                setTextColor(if (status.isEmpty()) getColor(R.color.map_text) else getColor(R.color.map_muted))
                isChecked = page.id in selectedPageIds
                isEnabled = !exporting && !importing
                contentDescription = "Include page ${index + 1}"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPageIds.add(page.id) else selectedPageIds.remove(page.id)
                    exportButton.text = "Export PDF (${selectedPageIds.size})"
                    exportButton.isEnabled = selectedPageIds.isNotEmpty()
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
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        parent.addView(TextView(this).apply {
            text = "Music"
            textSize = 18f
            setTextColor(getColor(R.color.map_text))
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
                textSize = 15f
                setTextColor(getColor(R.color.map_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = Button(this@ScanActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    requestNotificationsIfNeeded()
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
                result.onSuccess { file -> sharePdf(file) }
                    .onFailure { Toast.makeText(this, "Could not export the PDF", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun exportPdfFile(selectedIds: Set<Long>): File {
        val document = PdfDocument()
        var exportedPages = 0
        try {
            val selectedPages = database.pages(sessionId).filter { it.id in selectedIds }
            if (selectedPages.isEmpty()) error("No pages selected")
            selectedPages.forEach { page ->
                val bitmap = decodePage(page.path) ?: return@forEach
                val cleaned = ScanImageProcessor.clean(bitmap)
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, exportedPages + 1).create()
                val pdfPage = document.startPage(pageInfo)
                drawBitmap(pdfPage.canvas, cleaned)
                document.finishPage(pdfPage)
                if (cleaned !== bitmap) cleaned.recycle()
                bitmap.recycle()
                exportedPages++
            }
            if (exportedPages == 0) error("No readable pages")
            val output = File(filesDir, "scans/MAP-${timestamp()}.pdf")
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { document.writeTo(it) }
            return output
        } finally {
            document.close()
        }
    }

    private fun sharePdf(output: File) {
        val shareUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", output)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"))
    }

    private fun decodePage(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(Color.WHITE)
        val margin = 48
        val availableWidth = PAGE_WIDTH - margin * 2
        val availableHeight = PAGE_HEIGHT - margin * 2
        val scale = minOf(availableWidth.toFloat() / bitmap.width, availableHeight.toFloat() / bitmap.height)
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        val left = (PAGE_WIDTH - width) / 2
        val top = (PAGE_HEIGHT - height) / 2
        canvas.drawBitmap(bitmap, null, Rect(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun requestNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    companion object {
        private const val SCANNER_REQUEST = 12
        private const val NOTIFICATION_REQUEST = 13
        private const val STATE_SELECTED_PAGE_IDS = "selected_page_ids"
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
    }
}
