package app.map.android

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object MapUi {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

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
        if (root.resources.configuration.screenWidthDp >= 600) {
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
        val expanded = activity.resources.configuration.screenWidthDp >= 600
        val destinations = LinearLayout(activity).apply {
            orientation = if (expanded) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (expanded) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            setPadding(
                dp(activity, if (expanded) 6 else 8),
                dp(activity, if (expanded) 12 else 6),
                dp(activity, if (expanded) 6 else 8),
                dp(activity, if (expanded) 12 else 8),
            )
            listOf("Home", "Calendar", "Tasks", "Tools").forEach { label ->
                addView(Button(activity, null, 0, R.style.MapNavigationButton).apply {
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
                        setCompoundDrawablesWithIntrinsicBounds(null, it, null, null)
                    }
                    compoundDrawablePadding = dp(activity, 2)
                    setOnClickListener { if (label != selected) onNavigate(label) }
                }, if (expanded) {
                    LinearLayout.LayoutParams(-1, dp(activity, 80)).apply {
                        topMargin = dp(activity, 4)
                        bottomMargin = dp(activity, 4)
                    }
                } else {
                    LinearLayout.LayoutParams(0, dp(activity, 56), 1f)
                })
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
