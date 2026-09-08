package app.map.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class DocumentViewerActivity : Activity() {
    private lateinit var database: DocumentDatabase
    private lateinit var uri: Uri
    private var mime = "application/pdf"
    private var name = "Document"
    private var isPdf = false
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdfScroll: ScrollView? = null
    private var pdfPages = emptyList<PdfPageView>()
    private var pdfPagesContainer: LinearLayout? = null
    private var pdfBaseWidth = 0
    private var pdfZoom = 1f
    private var pdfRenderGeneration = 0L
    private val pdfRenderLock = Any()
    private val pdfExecutor = Executors.newSingleThreadExecutor()
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private lateinit var ocrButton: Button
    private lateinit var searchPanel: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = DocumentDatabase(this)
        uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: Uri.EMPTY
        if (uri == Uri.EMPTY) {
            showUnavailable("This document link is missing.")
            return
        }
        name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Document" }
        mime = intent.getStringExtra(EXTRA_MIME).orEmpty().ifBlank {
            contentResolver.getType(uri).orEmpty().ifBlank { "application/pdf" }
        }
        database.markOpened(uri.toString())
        loadDocument()
    }

    override fun onDestroy() {
        pdfRenderGeneration++
        pdfExecutor.shutdownNow()
        synchronized(pdfRenderLock) {
            pdfRenderer?.close()
            pdfDescriptor?.close()
        }
        ocrExecutor.shutdownNow()
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    private fun loadDocument() {
        try {
            isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            if (isPdf) showPdf() else showImage()
        } catch (_: Exception) {
            database.markUnavailable(uri.toString())
            showUnavailable("MAP could not read this file directly.")
        }
    }

    private fun showPdf() {
        pdfDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: error("File is unavailable")
        pdfRenderer = PdfRenderer(pdfDescriptor!!)
        val dimensions = buildList {
            repeat(pdfRenderer!!.pageCount) { index ->
                pdfRenderer!!.openPage(index).use { page -> add(page.width to page.height) }
            }
        }
        pdfBaseWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(240))
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = ViewGroup.LayoutParams(pdfBaseWidth, -2)
        }
        pdfPages = dimensions.mapIndexed { index, size ->
            PdfPageView(index, size.first, size.second).also { page ->
                pages.addView(page, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        pdfPagesContainer = pages
        val vertical = ScrollView(this).apply {
            pdfScroll = this
            isFillViewport = true
            setOnScrollChangeListener { _, _, _, _, _ -> renderVisiblePages() }
        }
        vertical.addView(HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(pages)
        })
        val root = viewerRoot()
        root.addView(vertical, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        vertical.post { renderVisiblePages() }
    }

    private fun renderVisiblePages() {
        val scroll = pdfScroll ?: return
        val top = scroll.scrollY - scroll.height
        val bottom = scroll.scrollY + scroll.height * 2
        val width = (pdfBaseWidth * pdfZoom).roundToInt().coerceAtMost(dp(2400))
        val generation = pdfRenderGeneration
        pdfPages.forEach { page ->
            if (page.bottom >= top && page.top <= bottom) {
                if (page.renderedWidth != width && page.renderingWidth != width) {
                    page.renderingWidth = width
                    pdfExecutor.execute {
                        val bitmap = runCatching { renderPdfPage(page.index, width) }.getOrNull()
                        val delivered = page.post {
                            page.renderingWidth = 0
                            if (bitmap != null && generation == pdfRenderGeneration && !isFinishing && !isDestroyed && page in pdfPages) {
                                page.setBitmap(bitmap, width)
                            } else {
                                bitmap?.recycle()
                            }
                        }
                        if (!delivered) bitmap?.recycle()
                    }
                }
            } else page.clearBitmap()
        }
    }

    private fun renderPdfPage(index: Int, width: Int): Bitmap = synchronized(pdfRenderLock) {
        val renderer = pdfRenderer ?: error("PDF renderer is unavailable")
        renderer.openPage(index).use { source ->
            val scale = width.toFloat() / source.width
            val bitmap = Bitmap.createBitmap(width, (source.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private fun setPdfZoom(value: Float) {
        pdfZoom = value.coerceIn(0.75f, 2.5f)
        pdfRenderGeneration++
        pdfPagesContainer?.layoutParams = ViewGroup.LayoutParams((pdfBaseWidth * pdfZoom).roundToInt(), -2)
        pdfPages.forEach { it.zoom = pdfZoom; it.clearBitmap() }
        pdfPagesContainer?.requestLayout()
        pdfScroll?.post { renderVisiblePages() }
        status.text = "Zoom ${(pdfZoom * 100).roundToInt()}%"
        status.visibility = View.VISIBLE
    }

    private fun showImage() {
        val bitmap = decodeImage() ?: error("File is unavailable")
        val root = viewerRoot()
        root.addView(ScrollView(this).apply {
            addView(ZoomImageView(this@DocumentViewerActivity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = name
                setPadding(dp(16), dp(8), dp(16), dp(24))
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        MapUi.applySystemBarInsets(root)
        setContentView(root)
    }

    private fun decodeImage(): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun viewerRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.map_background))
        MapUi.applySystemBarInsets(this)
        addView(LinearLayout(this@DocumentViewerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(4))
            addView(ImageButton(this@DocumentViewerActivity).apply {
                setImageResource(R.drawable.ic_map_back)
                imageTintList = ColorStateList.valueOf(getColor(R.color.map_text))
                setBackgroundResource(R.drawable.map_button_surface)
                backgroundTintList = null
                contentDescription = "Back"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@DocumentViewerActivity).apply {
                text = name
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(getColor(R.color.map_text))
                setPadding(dp(12), 0, dp(12), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        val zoomControls = LinearLayout(this@DocumentViewerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@DocumentViewerActivity).apply {
                text = "-"
                isAllCaps = false
                contentDescription = "Zoom out"
                setOnClickListener { if (isPdf) setPdfZoom(pdfZoom - 0.25f) }
            })
            addView(Button(this@DocumentViewerActivity).apply {
                text = "100%"
                isAllCaps = false
                contentDescription = "Reset zoom"
                setOnClickListener { if (isPdf) setPdfZoom(1f) }
            })
            addView(Button(this@DocumentViewerActivity).apply {
                text = "+"
                isAllCaps = false
                contentDescription = "Zoom in"
                setOnClickListener { if (isPdf) setPdfZoom(pdfZoom + 0.25f) }
            })
        }
        ocrButton = button("Read text") { runOcr() }
        val searchButton = button("Search") { toggleSearch() }
        addView(LinearLayout(this@DocumentViewerActivity).apply {
            orientation = if (resources.configuration.screenWidthDp < 360) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (resources.configuration.screenWidthDp < 360) {
                addView(zoomControls)
                addView(ocrButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                addView(searchButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            } else {
                addView(HorizontalScrollView(this@DocumentViewerActivity).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(LinearLayout(this@DocumentViewerActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(zoomControls)
                        addView(ocrButton)
                        addView(searchButton)
                    })
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this@DocumentViewerActivity).apply {
            textSize = 14f
            setTextColor(getColor(R.color.map_muted))
            setPadding(dp(16), 0, dp(16), dp(4))
            visibility = View.GONE
        }
        addView(status)
        searchPanel = LinearLayout(this@DocumentViewerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        searchInput = EditText(this@DocumentViewerActivity).apply {
            hint = "Search extracted text"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        searchPanel.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f))
        searchPanel.addView(button("Find") { findText() })
        addView(searchPanel)
    }

    private fun toggleSearch() {
        searchPanel.visibility = if (searchPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (searchPanel.visibility == View.VISIBLE) searchInput.requestFocus()
    }

    private fun findText() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) return
        val match = database.text(uri.toString()).firstOrNull { it.text.contains(query, ignoreCase = true) }
        if (match == null) {
            status.text = "No extracted text matches \"${query}\"."
        } else {
            status.text = "Found on page ${match.page + 1}."
            if (isPdf) pdfScroll?.post {
                val page = pdfPages.getOrNull(match.page) ?: return@post
                val scroll = pdfScroll ?: return@post
                val scrollLocation = IntArray(2)
                val pageLocation = IntArray(2)
                scroll.getLocationOnScreen(scrollLocation)
                page.getLocationOnScreen(pageLocation)
                val target = (scroll.scrollY + pageLocation[1] - scrollLocation[1] - dp(16)).coerceAtLeast(0)
                scroll.smoothScrollTo(0, target)
            }
        }
        status.visibility = View.VISIBLE
    }

    private fun runOcr() {
        if (database.text(uri.toString()).isNotEmpty()) {
            status.text = "Text is already available. Search is ready."
            status.visibility = View.VISIBLE
            return
        }
        ocrButton.isEnabled = false
        status.text = "Reading this document locally…"
        status.visibility = View.VISIBLE
        ocrExecutor.execute {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                if (isPdf) ocrPdf(recognizer) else ocrImage(recognizer)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        ocrButton.isEnabled = true
                        ocrButton.text = "Text ready"
                        status.text = "Text is ready. Search is available."
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        ocrButton.isEnabled = true
                        status.text = "Could not read text from this document."
                    }
                }
            } finally {
                recognizer.close()
            }
        }
    }

    private fun ocrImage(recognizer: TextRecognizer) {
        val bitmap = decodeImage() ?: error("File is unavailable")
        try {
            database.saveText(uri.toString(), 0, recognize(recognizer, bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun ocrPdf(recognizer: TextRecognizer) {
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: error("File is unavailable")
        descriptor.use { file ->
            PdfRenderer(file).use { renderer ->
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        val width = page.width.coerceAtMost(1600)
                        val scale = width.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(width, (page.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            database.saveText(uri.toString(), index, recognize(recognizer, bitmap))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): String =
        Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text

    private fun showUnavailable(message: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.map_background))
        }
        root.addView(button("Back") { finish() })
        root.addView(TextView(this).apply {
            text = "Document unavailable"
            textSize = 28f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(24), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(16))
        })
        root.addView(Button(this, null, 0, R.style.MapPrimaryButton).apply {
            text = "Open with another app"
            isAllCaps = false
            setOnClickListener { openExternally() }
        })
        MapUi.applySystemBarInsets(root)
        setContentView(root)
    }

    private fun openExternally() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class PdfPageView(
        val index: Int,
        private val pageWidth: Int,
        private val pageHeight: Int
    ) : View(this@DocumentViewerActivity) {
        var zoom = 1f
        var renderedWidth = 0
            private set
        var renderingWidth = 0
        private var bitmap: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = (pdfBaseWidth * zoom).roundToInt()
            val height = (width * pageHeight.toFloat() / pageWidth).roundToInt()
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            bitmap?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint) }
                ?: run {
                    paint.color = getColor(R.color.map_muted)
                    paint.textSize = dp(14).toFloat()
                    canvas.drawText("Page ${index + 1}", dp(16).toFloat(), dp(28).toFloat(), paint)
                }
        }

        fun setBitmap(value: Bitmap, width: Int) {
            bitmap?.recycle()
            bitmap = value
            renderedWidth = width
            invalidate()
        }

        fun clearBitmap() {
            if (bitmap == null) return
            bitmap?.recycle()
            bitmap = null
            renderedWidth = 0
            invalidate()
        }
    }

    @Suppress("AppCompatCustomView")
    private class ZoomImageView(context: android.content.Context) : ImageView(context) {
        private var zoom = 1f
        private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 3f)
                scaleX = zoom
                scaleY = zoom
                return true
            }
        })

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            detector.onTouchEvent(event)
            return if (event.pointerCount > 1) true else super.onTouchEvent(event)
        }
    }

    companion object {
        const val EXTRA_URI = "document_uri"
        const val EXTRA_NAME = "document_name"
        const val EXTRA_MIME = "document_mime"
    }
}
