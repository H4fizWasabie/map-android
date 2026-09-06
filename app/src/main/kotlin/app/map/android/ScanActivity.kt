package app.map.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanActivity : Activity() {
    private lateinit var database: ScanDatabase
    private var sessionId = 0L
    private lateinit var pages: LinearLayout
    private var cameraPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ScanDatabase(this)
        sessionId = database.activeSession()
        render()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            IMPORT_REQUEST -> {
                val uris = buildList {
                    data?.data?.let(::add)
                    data?.clipData?.let { clip -> repeat(clip.itemCount) { add(clip.getItemAt(it).uri) } }
                }
                uris.forEach { copyPage(it) }
                render()
            }
            CAMERA_REQUEST -> {
                cameraPath?.let { database.addPage(sessionId, it) }
                render()
            }
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.map_background))
        }
        root.addView(TextView(this).apply {
            text = "MAP"
            textSize = 14f
            setTextColor(getColor(R.color.map_accent))
        })
        root.addView(TextView(this).apply {
            text = "Scan"
            textSize = 32f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(8), 0, dp(16))
        })
        root.addView(Button(this).apply {
            text = "Back to Home"
            setOnClickListener { finish() }
        })
        root.addView(Button(this).apply {
            text = "Import images"
            setOnClickListener { importImages() }
        })
        root.addView(Button(this).apply {
            text = "Take photo"
            setOnClickListener { takePhoto() }
        })
        root.addView(Button(this).apply {
            text = "Export PDF"
            isEnabled = database.pages(sessionId).isNotEmpty()
            setOnClickListener { exportPdf() }
        })
        root.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        root.addView(TextView(this).apply {
            text = "Pages"
            textSize = 18f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(12), 0, dp(8))
        })
        pages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val storedPages = database.pages(sessionId)
        if (storedPages.isEmpty()) pages.addView(TextView(this).apply {
            text = "No pages captured yet."
            setTextColor(getColor(R.color.map_muted))
        })
        storedPages.forEachIndexed { index, page ->
            pages.addView(TextView(this).apply {
                val status = if (File(page.path).exists()) "" else " (Unavailable)"
                text = "Page ${index + 1}: ${File(page.path).name}$status"
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, 0, 0, dp(8))
            })
        }
        root.addView(pages)
        addMusicMiniPlayer(root)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addMusicMiniPlayer(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val item = music.track(music.current())
        val playing = music.playing()
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
            setBackgroundColor(getColor(R.color.map_card))
            contentDescription = "Music: ${item.title}"
            setOnClickListener { startActivity(Intent(this@ScanActivity, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)) }
            addView(TextView(this@ScanActivity).apply {
                text = "${item.title}\n${item.artist}"
                textSize = 15f
                setTextColor(getColor(R.color.map_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@ScanActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                setOnClickListener {
                    val intent = Intent(this@ScanActivity, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                }
            })
        }.also { parent.addView(it) }
    }

    private fun importImages() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }, IMPORT_REQUEST)
    }

    private fun takePhoto() {
        val directory = File(filesDir, "scans/$sessionId").apply { mkdirs() }
        cameraPath = File(directory, "camera-${System.currentTimeMillis()}.jpg").absolutePath
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(cameraPath!!))
        startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, CAMERA_REQUEST)
    }

    private fun copyPage(uri: Uri) {
        runCatching {
            val directory = File(filesDir, "scans/$sessionId").apply { mkdirs() }
            val target = File(directory, "import-${System.currentTimeMillis()}-${database.pages(sessionId).size}.jpg")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            database.addPage(sessionId, target.absolutePath)
        }.onFailure {
            Toast.makeText(this, "Could not import that image", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportPdf() {
        val document = PdfDocument()
        var exportedPages = 0
        try {
            database.pages(sessionId).forEachIndexed { index, page ->
                val bitmap = BitmapFactory.decodeFile(page.path) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, exportedPages + 1).create()
                val pdfPage = document.startPage(pageInfo)
                drawBitmap(pdfPage.canvas, bitmap)
                document.finishPage(pdfPage)
                bitmap.recycle()
                exportedPages++
            }
            if (exportedPages == 0) error("No readable pages")
            val output = File(filesDir, "scans/MAP-${timestamp()}.pdf")
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { document.writeTo(it) }
            val shareUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", output)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not export the PDF", Toast.LENGTH_LONG).show()
        } finally {
            document.close()
        }
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

    companion object {
        private const val IMPORT_REQUEST = 10
        private const val CAMERA_REQUEST = 11
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
    }
}
