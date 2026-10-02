package app.map.android

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity

enum class MapAppearance(
    val title: String,
    val description: String,
    val style: Int,
    val artwork: Int,
    val accent: Int,
    val highlight: Int,
    val paper: Int,
    val ink: Int,
    val muted: Int,
) {
    COLORIST_DAYBOOK("Colorist Daybook", "Forest green, celadon, and marigold", R.style.Theme_MAP_ColoristDaybook,
        R.drawable.theme_colorist_art, R.color.daybook_accent, R.color.daybook_highlight, R.color.daybook_background, R.color.daybook_text, R.color.daybook_muted),
    BOTANICAL_PRINT("Botanical Print", "Evergreen ink and luminous paper", R.style.Theme_MAP_BotanicalPrint,
        R.drawable.theme_botanical_art, R.color.botanical_accent, R.color.botanical_highlight, R.color.botanical_background, R.color.botanical_text, R.color.botanical_muted),
    WOVEN_POSTER("Woven Poster", "Rust, cobalt, and woven-paper warmth", R.style.Theme_MAP_WovenPoster,
        R.drawable.theme_woven_art, R.color.woven_accent, R.color.woven_highlight, R.color.woven_background, R.color.woven_text, R.color.woven_muted);

    companion object {
        private const val PREFS = "map-appearance"
        private const val SELECTED = "selected"

        fun selected(context: Context): MapAppearance = entries.firstOrNull {
            it.name == context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SELECTED, COLORIST_DAYBOOK.name)
        } ?: COLORIST_DAYBOOK

        fun select(context: Context, appearance: MapAppearance) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, appearance.name).apply()
            FocusWidgetProvider.refresh(context)
            if (MusicService.isRunning) startMapMusicService(context, android.content.Intent(context, MusicService::class.java).setAction(MusicService.ACTION_APPEARANCE_CHANGED))
        }

        fun colorAttribute(id: Int): Int? = when (id) {
            R.color.map_background -> R.attr.mapColorBackground
            R.color.map_text -> R.attr.mapColorText
            R.color.map_muted -> R.attr.mapColorMuted
            R.color.map_accent -> R.attr.mapColorAccent
            R.color.map_accent_pressed -> R.attr.mapColorAccentPressed
            R.color.map_selection -> R.attr.mapColorSelection
            R.color.map_on_accent -> R.attr.mapColorOnAccent
            R.color.map_divider -> R.attr.mapColorDivider
            R.color.map_card -> R.attr.mapColorCard
            R.color.map_focus -> R.attr.mapColorFocus
            R.color.map_nav_background -> R.attr.mapColorNavBackground
            R.color.map_status -> R.attr.mapColorAccent
            R.color.map_disabled -> R.attr.mapColorDisabled
            R.color.map_instrument -> R.attr.mapColorInstrument
            R.color.map_instrument_ink -> R.attr.mapColorInstrumentInk
            R.color.map_instrument_muted -> R.attr.mapColorInstrumentMuted
            R.color.map_tool_icon -> R.attr.mapColorToolIcon
            R.color.map_nav_muted -> R.attr.mapColorNavMuted
            else -> null
        }

        fun color(context: Context, id: Int): Int {
            val attribute = colorAttribute(id) ?: return context.resources.getColor(id, context.theme)
            val resolved = TypedValue()
            if (!context.theme.resolveAttribute(attribute, resolved, true)) return context.resources.getColor(id, context.theme)
            return if (resolved.resourceId != 0) context.resources.getColor(resolved.resourceId, context.theme) else resolved.data
        }

        fun headingFont(context: Context): Int = when (selected(context)) {
            COLORIST_DAYBOOK, BOTANICAL_PRINT -> R.font.playfair_display
            WOVEN_POSTER -> R.font.barlow_condensed
        }

        fun bodyFont(context: Context): Int = R.font.dm_sans
        fun labelFont(context: Context): Int = R.font.dm_mono
    }
}

abstract class MapActivity : AppCompatActivity() {
    private var appliedAppearance: MapAppearance? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        appliedAppearance = MapAppearance.selected(this)
        setTheme(appliedAppearance!!.style)
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = MapAppearance.color(this, R.color.map_background)
        window.navigationBarColor = MapAppearance.color(this, R.color.map_nav_background)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (appliedAppearance != MapAppearance.selected(this)) recreate()
    }
}

fun Context.mapColor(id: Int): Int = MapAppearance.color(this, id)
