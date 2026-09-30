package app.map.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Static, local-time instrument dial; the adjacent TextClock provides the accessible readout. */
class FieldClockDialView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var receiverRegistered = false
    private val timeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = invalidate()
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, timeChangedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onDetachedFromWindow() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(timeChangedReceiver) }
            receiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - dp(4f)
        if (radius <= 0f) return

        stroke.color = color(R.color.map_instrument_muted)
        stroke.strokeWidth = dp(1f)
        canvas.drawCircle(centerX, centerY, radius, stroke)

        val time = Calendar.getInstance()
        val minuteAngle = Math.toRadians(time.get(Calendar.MINUTE) * 6.0 - 90.0)
        val hourAngle = Math.toRadians(
            ((time.get(Calendar.HOUR) % 12) + time.get(Calendar.MINUTE) / 60.0) * 30.0 - 90.0,
        )
        hand(canvas, centerX, centerY, hourAngle, radius * 0.43f, dp(2f), R.color.map_instrument_ink)
        hand(canvas, centerX, centerY, minuteAngle, radius * 0.65f, dp(1.5f), R.color.map_instrument_muted)
        fill.color = color(R.color.map_instrument_muted)
        canvas.drawCircle(centerX, centerY, dp(3f), fill)
    }

    private fun hand(canvas: Canvas, x: Float, y: Float, angle: Double, length: Float, thickness: Float, tint: Int) {
        stroke.color = color(tint)
        stroke.strokeWidth = thickness
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            x,
            y,
            x + cos(angle).toFloat() * length,
            y + sin(angle).toFloat() * length,
            stroke,
        )
    }

    private fun color(id: Int) = context.getColor(id)
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
