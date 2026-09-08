package app.map.android

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

object MapUi {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun bottomNavigation(activity: Activity, selected: String, onNavigate: (String) -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(activity.getColor(R.color.map_nav_background))
            addView(View(activity).apply {
                setBackgroundColor(activity.getColor(R.color.map_divider))
                layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 1))
            })
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 8))
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
                    }, LinearLayout.LayoutParams(0, dp(activity, 56), 1f))
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
}
