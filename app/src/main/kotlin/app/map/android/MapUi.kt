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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object MapUi {
    private fun expandedNavigation(context: Context): Boolean = context.resources.configuration.screenWidthDp >= 600

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun textRole(view: TextView, size: Float, weight: Int, color: Int, family: String = "sans-serif") {
        view.textSize = size
        view.typeface = Typeface.create(family, weight)
        view.setTextColor(view.context.getColor(color))
    }

    fun display(view: TextView) = textRole(view, 30f, Typeface.NORMAL, R.color.map_text, "sans-serif-medium")

    fun headline(view: TextView) = textRole(view, 22f, Typeface.NORMAL, R.color.map_text, "sans-serif-medium")

    fun section(view: TextView) = textRole(view, 18f, Typeface.NORMAL, R.color.map_text, "sans-serif-medium")

    fun body(view: TextView) = textRole(view, 16f, Typeface.NORMAL, R.color.map_text)

    fun label(view: TextView) = textRole(view, 14f, Typeface.NORMAL, R.color.map_text, "sans-serif-medium")

    fun metadata(view: TextView) = textRole(view, 13f, Typeface.NORMAL, R.color.map_muted)

    fun caption(view: TextView) = textRole(view, 12f, Typeface.NORMAL, R.color.map_muted)

    fun markPrimaryAction(button: Button) {
        val mark = button.context.getDrawable(R.drawable.ic_map_coordinate)?.mutate() ?: return
        mark.setTint(button.context.getColor(R.color.map_on_accent))
        button.setCompoundDrawablesWithIntrinsicBounds(mark, null, null, null)
        button.compoundDrawablePadding = dp(button.context, 8)
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
