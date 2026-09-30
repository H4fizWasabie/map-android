package app.map.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Static, local-time instrument dial; the adjacent TextClock provides the accessible readout. */
class FieldClockDialView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val numeralFace = ResourcesCompat.getFont(context, R.font.archivo) ?: Typeface.DEFAULT_BOLD
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

        fill.color = color(R.color.map_card)
        canvas.drawCircle(centerX, centerY, radius, fill)

        stroke.color = color(R.color.map_text)
        stroke.strokeWidth = dp(3f)
        canvas.drawCircle(centerX, centerY, radius - dp(1.5f), stroke)
        stroke.color = color(R.color.map_divider)
        stroke.strokeWidth = dp(1f)
        canvas.drawCircle(centerX, centerY, radius - dp(6f), stroke)

        for (tick in 0 until 60) {
            val major = tick % 5 == 0
            val angle = Math.toRadians(tick * 6.0 - 90.0)
            val outer = radius - dp(9f)
            val inner = outer - dp(if (major) 8f else 4f)
            stroke.color = color(R.color.map_text)
            stroke.strokeWidth = dp(if (major) 2f else 1f)
            canvas.drawLine(
                centerX + cos(angle).toFloat() * inner,
                centerY + sin(angle).toFloat() * inner,
                centerX + cos(angle).toFloat() * outer,
                centerY + sin(angle).toFloat() * outer,
                stroke,
            )
        }

        fill.color = color(R.color.map_text)
        fill.typeface = numeralFace
        fill.textSize = dp((13f * radius / dp(56f)).coerceIn(8f, 13f))
        fill.textAlign = Paint.Align.CENTER
        val numeralRadius = radius * if (radius < dp(50f)) 0.43f else 0.66f
        listOf(12 to -90.0, 3 to 0.0, 6 to 90.0, 9 to 180.0).forEach { (number, degrees) ->
            val angle = Math.toRadians(degrees)
            val baseline = centerY + sin(angle).toFloat() * numeralRadius - (fill.ascent() + fill.descent()) / 2f
            canvas.drawText(number.toString(), centerX + cos(angle).toFloat() * numeralRadius, baseline, fill)
        }

        val time = Calendar.getInstance()
        val minuteAngle = Math.toRadians(time.get(Calendar.MINUTE) * 6.0 - 90.0)
        val hourAngle = Math.toRadians(
            ((time.get(Calendar.HOUR) % 12) + time.get(Calendar.MINUTE) / 60.0) * 30.0 - 90.0,
        )
        hand(canvas, centerX, centerY, hourAngle, radius * 0.43f, dp(4f), R.color.map_text)
        hand(canvas, centerX, centerY, minuteAngle, radius * 0.57f, dp(2.5f), R.color.map_accent)
        fill.color = color(R.color.map_accent)
        canvas.drawCircle(centerX, centerY, dp(4f), fill)
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
