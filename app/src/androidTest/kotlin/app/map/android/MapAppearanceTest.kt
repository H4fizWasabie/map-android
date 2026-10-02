package app.map.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapAppearanceTest {
    @Test
    fun everyAppearanceChoicePersistsAndUnknownValuesFallBackToDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = MapAppearance.selected(context)
        try {
            MapAppearance.entries.forEach { appearance ->
                MapAppearance.select(context, appearance)
                assertEquals(appearance, MapAppearance.selected(context))
            }

            context.getSharedPreferences("map-appearance", 0).edit()
                .putString("selected", "removed-theme")
                .commit()
            assertEquals(MapAppearance.COLORIST_DAYBOOK, MapAppearance.selected(context))
        } finally {
            MapAppearance.select(context, original)
        }
    }
}
