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
            setBackgroundColor(activity.getColor(R.color.map_background))
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
                        isAllCaps = false
                        isClickable = label != selected
                        contentDescription = "$label navigation"
                        setTextColor(activity.getColor(if (label == selected) R.color.map_accent else R.color.map_muted))
                        setBackgroundResource(if (label == selected) R.drawable.map_focus_surface else android.R.color.transparent)
                        setOnClickListener { onNavigate(label) }
                    }, LinearLayout.LayoutParams(0, dp(activity, 56), 1f))
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
}
