package app.map.android

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object MapUi {
    private fun expandedNavigation(context: Context): Boolean = context.resources.configuration.screenWidthDp >= 600

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun signage(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.archivo) ?: Typeface.DEFAULT

    private fun plain(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.work_sans) ?: Typeface.SANS_SERIF

    private fun textRole(view: TextView, size: Float, color: Int, typeface: Typeface, weight: Int? = null, width: Int? = null) {
        view.textSize = size
        view.typeface = typeface
        view.setTextColor(view.context.getColor(color))
        if (weight != null) {
            view.fontVariationSettings = if (width != null) "'wght' $weight, 'wdth' $width" else "'wght' $weight"
        }
    }

    fun display(view: TextView) = textRole(view, 34f, R.color.map_text, signage(view.context), weight = 900, width = 112)

    fun headline(view: TextView) = textRole(view, 22f, R.color.map_text, signage(view.context), weight = 800)

    fun section(view: TextView) = textRole(view, 16f, R.color.map_text, signage(view.context), weight = 700)

    fun body(view: TextView) = textRole(view, 16f, R.color.map_text, plain(view.context), weight = 400)

    fun label(view: TextView) = textRole(view, 14f, R.color.map_text, signage(view.context), weight = 700)

    fun metadata(view: TextView) = textRole(view, 13f, R.color.map_muted, plain(view.context), weight = 500)

    fun caption(view: TextView) = textRole(view, 12f, R.color.map_muted, plain(view.context), weight = 400)

    /** Large numeric readouts: clock, group totals — set in the display face at full weight. */
    fun numeral(view: TextView, size: Float = 16f, color: Int = R.color.map_text) =
        textRole(view, size, color, signage(view.context), weight = 900)

    fun markPrimaryAction(button: Button) {
        // Signal design carries identity through type and hairline rules, not iconography.
    }

    fun settlePrimaryAction(view: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.post {
            if (!view.isAttachedToWindow) return@post
            view.translationY = dp(view.context, 8).toFloat()
            view.animate()
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                .start()
        }
    }

    fun applySystemBarInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun addPrimaryNavigation(root: LinearLayout, content: View, navigation: View) {
        if (expandedNavigation(root.context)) {
            root.orientation = LinearLayout.HORIZONTAL
            root.addView(navigation, LinearLayout.LayoutParams(dp(root.context, 104), -1))
            root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(navigation)
        }
    }

    fun bottomNavigation(activity: Activity, selected: String, onNavigate: (String) -> Unit): View {
        val expanded = expandedNavigation(activity)
        val landscapePhone = !expanded && activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val largePhoneText = !expanded && activity.resources.configuration.fontScale >= 1.3f
        val labels = listOf("Home", "Calendar", "Tasks", "Tools")
        fun destinationButton(label: String) = Button(activity, null, 0, R.style.MapNavigationButton).apply {
            text = label
            isSelected = label == selected
            contentDescription = "$label navigation"
            val color = activity.getColor(if (label == selected) R.color.map_accent else R.color.map_muted)
            setTextColor(color)
            setBackgroundResource(R.drawable.map_nav_button)
            backgroundTintList = null
            val icon = when (label) {
                "Home" -> R.drawable.ic_map_home
                "Calendar" -> R.drawable.ic_map_calendar
                "Tasks" -> R.drawable.ic_map_tasks
                else -> R.drawable.ic_map_tools
            }
            activity.getDrawable(icon)?.mutate()?.apply { setTint(color) }?.let {
                setCompoundDrawablesWithIntrinsicBounds(
                    if (largePhoneText && !landscapePhone) it else null,
                    if (largePhoneText) null else it,
                    null,
                    null,
                )
            }
            if (largePhoneText || landscapePhone) gravity = Gravity.CENTER
            compoundDrawablePadding = dp(activity, 2)
            setOnClickListener { if (label != selected) onNavigate(label) }
        }
        val destinations = LinearLayout(activity).apply {
            val grid = largePhoneText && !landscapePhone
            orientation = if (expanded || grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (expanded || grid) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            setPadding(
                dp(activity, if (expanded) 6 else if (landscapePhone) 0 else 8),
                dp(activity, if (expanded) 12 else if (landscapePhone) 0 else 6),
                dp(activity, if (expanded) 6 else if (landscapePhone) 0 else 8),
                dp(activity, if (expanded) 12 else if (landscapePhone) 0 else 8),
            )
            if (grid) {
                labels.chunked(2).forEach { rowLabels ->
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        rowLabels.forEach { addView(destinationButton(it), LinearLayout.LayoutParams(0, -2, 1f)) }
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            } else labels.forEach { label ->
                addView(destinationButton(label), if (expanded) {
                    LinearLayout.LayoutParams(-1, dp(activity, 80)).apply {
                        topMargin = dp(activity, 4)
                        bottomMargin = dp(activity, 4)
                    }
                } else LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        return LinearLayout(activity).apply {
            orientation = if (expanded) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(activity.getColor(R.color.map_nav_background))
            addView(View(activity).apply {
                setBackgroundColor(activity.getColor(R.color.map_divider))
                layoutParams = if (expanded) {
                    LinearLayout.LayoutParams(dp(activity, 1), -1)
                } else {
                    LinearLayout.LayoutParams(-1, dp(activity, 1))
                }
            })
            addView(
                if (expanded) ScrollView(activity).apply {
                    isVerticalScrollBarEnabled = false
                    isFillViewport = true
                    addView(destinations, android.widget.FrameLayout.LayoutParams(-1, -2))
                } else destinations,
                if (expanded) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, -2)
            )
        }
    }
}
