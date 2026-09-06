package app.map.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DocumentViewerActivity : Activity() {
    private lateinit var database: DocumentDatabase
    private lateinit var uri: Uri
    private var mime = "application/pdf"
    private var name = "Document"

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
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    private fun loadDocument() {
        try {
            val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            if (isPdf) showPdf() else showImage()
        } catch (_: Exception) {
            database.markUnavailable(uri.toString())
            showUnavailable("MAP could not read this file directly.")
        }
    }

    private fun showPdf() {
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: error("File is unavailable")
        descriptor.use { file ->
            PdfRenderer(file).use { renderer ->
                val root = viewerRoot()
                val pages = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(24))
                }
                val width = (resources.displayMetrics.widthPixels - dp(40)).coerceAtLeast(dp(240))
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = width.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(width, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.addView(ImageView(this).apply {
                            setImageBitmap(bitmap)
                            adjustViewBounds = true
                            contentDescription = "Page ${index + 1} of $name"
                            setBackgroundColor(Color.WHITE)
                        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                    }
                }
                root.addView(ScrollView(this).apply { addView(pages) }, LinearLayout.LayoutParams(-1, 0, 1f))
                setContentView(root)
            }
        }
    }

    private fun showImage() {
        val bitmap = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: error("File is unavailable")
        val root = viewerRoot()
        root.addView(ScrollView(this).apply {
            addView(ImageView(this@DocumentViewerActivity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = name
                setPadding(dp(16), dp(8), dp(16), dp(24))
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun viewerRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.map_background))
        addView(LinearLayout(this@DocumentViewerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
            addView(button("Back") { finish() })
            addView(TextView(this@DocumentViewerActivity).apply {
                text = name
                textSize = 18f
                setTextColor(getColor(R.color.map_text))
                setPadding(dp(12), 0, 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

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
        root.addView(button("Open with another app") { openExternally() })
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

    companion object {
        const val EXTRA_URI = "document_uri"
        const val EXTRA_NAME = "document_name"
        const val EXTRA_MIME = "document_mime"
    }
}
