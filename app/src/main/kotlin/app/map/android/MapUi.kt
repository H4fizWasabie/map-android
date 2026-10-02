package app.map.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.animation.ValueAnimator
import android.provider.Settings
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import kotlin.math.roundToInt

object MapUi {
    private fun expandedNavigation(context: Context): Boolean =
        context.resources.configuration.screenWidthDp >= 600 && context.resources.configuration.screenHeightDp >= 500

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun illustration(activity: Activity, resource: Int): BitmapDrawable = BitmapDrawable(
        activity.resources,
        BitmapFactory.decodeResource(activity.resources, resource, BitmapFactory.Options().apply { inSampleSize = 4 }),
    )

    private fun signage(context: Context): Typeface = ResourcesCompat.getFont(context, MapAppearance.headingFont(context)) ?: Typeface.DEFAULT

    private fun plain(context: Context): Typeface = ResourcesCompat.getFont(context, MapAppearance.bodyFont(context)) ?: Typeface.SANS_SERIF

    private fun measured(context: Context): Typeface = ResourcesCompat.getFont(context, MapAppearance.labelFont(context)) ?: Typeface.MONOSPACE

    private fun textRole(view: TextView, size: Float, color: Int, typeface: Typeface, weight: Int? = null, width: Int? = null) {
        view.textSize = size
        view.typeface = typeface
        view.setTextColor(view.context.mapColor(color))
        if (weight != null) {
            view.fontVariationSettings = if (width != null) "'wght' $weight, 'wdth' $width" else "'wght' $weight"
        }
    }

    fun display(view: TextView) {
        textRole(view, 30f, R.color.map_text, signage(view.context), weight = 800)
        view.letterSpacing = -0.025f
    }

    fun headline(view: TextView) = textRole(view, 22f, R.color.map_text, signage(view.context), weight = 800)

    fun section(view: TextView) = textRole(view, 16f, R.color.map_text, signage(view.context), weight = 700)

    fun taskTitle(view: TextView) = textRole(view, 16f, R.color.map_text, plain(view.context), weight = 700)

    fun body(view: TextView) = textRole(view, 16f, R.color.map_text, plain(view.context), weight = 400)

    fun label(view: TextView) = textRole(view, 14f, R.color.map_text, measured(view.context), weight = 500)

    fun metadata(view: TextView) = textRole(view, 13f, R.color.map_muted, plain(view.context), weight = 500)

    fun caption(view: TextView) = textRole(view, 12f, R.color.map_muted, measured(view.context), weight = 400)

    fun tintCheckbox(view: android.widget.CompoundButton) {
        view.buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(view.context.mapColor(R.color.map_accent), view.context.mapColor(R.color.map_muted)),
        )
    }

    /** Large numeric readouts: clock, group totals — set in the display face at full weight. */
    fun numeral(view: TextView, size: Float = 16f, color: Int = R.color.map_text) =
        textRole(view, size, color, signage(view.context), weight = 900)

    fun brandMark(activity: Activity): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "MAP. Private data stays on this device."
            addView(TextView(activity).apply {
                text = "MAP"
                textRole(this, 18f, R.color.map_text, signage(activity), weight = 800)
                setTextColor(activity.mapColor(R.color.map_text))
            })
            addView(View(activity).apply {
                setBackgroundResource(R.drawable.map_status_dot)
            }, LinearLayout.LayoutParams(dp(activity, 7), dp(activity, 7)).apply { marginStart = dp(activity, 3) })
        }

    fun addBrandMark(activity: Activity, parent: LinearLayout) {
        parent.addView(brandMark(activity), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 18) })
    }

    /** Shared by Home and Calendar: shows the last completed task with an Undo action. */
    fun addUndoBar(activity: Activity, parent: LinearLayout, database: TaskDatabase, state: UndoState?, onChanged: () -> Unit) {
        if (state == null) return
        parent.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setBackgroundResource(R.drawable.map_surface)
            addView(TextView(activity).apply {
                text = "Completed ${state.task.title}"
                label(this)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button(activity).apply {
                text = "Undo"
                isAllCaps = false
                setOnClickListener {
                    TaskActions.undo(activity, database, state)
                    onChanged()
                }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 12) })
    }

    fun requestNotificationsIfNeeded(activity: Activity, requestCode: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), requestCode)
        }
    }

    fun handleNotificationPermissionResult(
        activity: Activity,
        requestCode: Int,
        expectedRequestCode: Int,
        grantResults: IntArray,
        message: String,
    ) {
        if (requestCode != expectedRequestCode || grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) return
        MaterialAlertDialogBuilder(activity)
            .setTitle("Notifications are off")
            .setMessage(message)
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open settings") { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                )
            }
            .show()
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
        val activity = view.context as? Activity
        val lightTheme = view.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightNavigationBars = lightTheme }
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
        view.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    fun addPrimaryNavigation(root: LinearLayout, content: View, navigation: View) {
        if (navigation is NavigationRailView) {
            root.orientation = LinearLayout.HORIZONTAL
            val railWidth = if (root.context.resources.configuration.fontScale >= 1.3f) 208 else 112
            root.addView(navigation, LinearLayout.LayoutParams(dp(root.context, railWidth), -1))
            root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(navigation)
        }
    }

    fun bottomNavigation(activity: Activity, selected: String, onNavigate: (String) -> Unit): View {
        val expanded = expandedNavigation(activity)
        val navigation = if (expanded) NavigationRailView(activity) else BottomNavigationView(activity)
        navigation.inflateMenu(R.menu.map_primary_navigation)
        for (index in 0 until navigation.menu.size()) {
            val item = navigation.menu.getItem(index)
            item.contentDescription = "${item.title} navigation"
        }
        navigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        navigation.itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(activity.mapColor(R.color.map_nav_background))
        navigation.backgroundTintList = android.content.res.ColorStateList.valueOf(activity.mapColor(R.color.map_nav_background))
        val navigationColors = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(MapAppearance.color(activity, MapAppearance.selected(activity).highlight), activity.mapColor(R.color.map_nav_muted)),
        )
        navigation.itemIconTintList = navigationColors
        navigation.itemTextColor = navigationColors
        navigation.elevation = 0f
        if (navigation is NavigationRailView && activity.resources.configuration.fontScale >= 1.3f) {
            navigation.itemIconGravity = NavigationBarView.ITEM_ICON_GRAVITY_START
        }
        navigation.selectedItemId = when (selected) {
            "Calendar" -> R.id.nav_calendar
            "Tasks" -> R.id.nav_tasks
            "Tools" -> R.id.nav_tools
            else -> R.id.nav_home
        }
        navigation.setOnItemSelectedListener { item ->
            val destination = when (item.itemId) {
                R.id.nav_home -> "Home"
                R.id.nav_calendar -> "Calendar"
                R.id.nav_tasks -> "Tasks"
                R.id.nav_tools -> "Tools"
                else -> return@setOnItemSelectedListener false
            }
            if (destination != selected) onNavigate(destination)
            true
        }
        return navigation
    }

    fun button(activity: Activity): MaterialButton = MaterialButton(activity).apply {
        isAllCaps = false
        minHeight = dp(activity, 48)
        minWidth = dp(activity, 48)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(activity, 16)
        backgroundTintList = android.content.res.ColorStateList.valueOf(activity.mapColor(R.color.map_card))
        strokeColor = android.content.res.ColorStateList.valueOf(activity.mapColor(R.color.map_divider))
        strokeWidth = dp(activity, 1)
        setTextColor(activity.mapColor(R.color.map_text))
        typeface = plain(activity)
        textSize = 14f
    }

    fun primaryButton(activity: Activity): MaterialButton = button(activity).apply {
        backgroundTintList = android.content.res.ColorStateList.valueOf(activity.mapColor(R.color.map_accent))
        strokeWidth = 0
        setTextColor(activity.mapColor(R.color.map_on_accent))
        typeface = plain(activity)
        textSize = 16f
    }
}
