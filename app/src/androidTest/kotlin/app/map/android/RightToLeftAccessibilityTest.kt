package app.map.android

import android.content.Intent
import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RightToLeftAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val localeManager by lazy { context.getSystemService(LocaleManager::class.java) }
    private lateinit var device: UiDevice
    private var originalAppLocales = ""
    private var originalFontScale = "1.0"
    private var originalWmSize = ""
    private var cleanupDocumentUri: String? = null

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        originalFontScale = device.executeShellCommand("settings get system font_scale").trim().ifEmpty { "1.0" }
        originalWmSize = device.executeShellCommand("wm size").lineSequence()
            .firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        originalAppLocales = localeManager.applicationLocales.toLanguageTags()
        setAppLocales("ar")
        launchHome()
    }

    @After
    fun tearDown() {
        cleanupDocumentUri?.let { uri ->
            val database = DocumentDatabase(context)
            try {
                database.writableDatabase.delete("documents", "uri = ?", arrayOf(uri))
            } finally {
                database.close()
            }
        }
        device.executeShellCommand(if (originalWmSize.isEmpty()) "wm size reset" else "wm size $originalWmSize")
        device.executeShellCommand("settings put system font_scale $originalFontScale")
        setAppLocales(originalAppLocales)
        launchHome()
    }

    @Test
    fun rtlMirrorsNavigationAndKeepsCalendarComposerActionsReachable() {
        assertRtlNavigationOrder()
        clickDescription("Calendar navigation")
        assertTrue("RTL Calendar did not open", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))
        assertRtlNavigationOrder()

        val dateTargets = findDateTargets()
        assertEquals("RTL Calendar should show seven date choices", 7, dateTargets.size)
        dateTargets.forEach { target ->
            assertTargetFits(target, "Calendar date ${target.text}")
            assertTrue("RTL date choice lacks a full accessible date: ${target.contentDescription}", target.contentDescription.contains(','))
        }

        clickText("Add task")
        assertTrue("RTL task composer did not open", device.wait(Until.hasObject(By.text("New task")), TIMEOUT))
        assertTargetFits(device.findObject(By.desc("Task title")) ?: error("RTL task title was not accessible"), "Task title")
        val save = device.wait(Until.findObject(By.text("Save")), TIMEOUT) ?: error("Save was not reachable in RTL")
        val cancel = device.wait(Until.findObject(By.text("Cancel")), TIMEOUT) ?: error("Cancel was not reachable in RTL")
        assertTargetFits(save, "Save")
        assertTargetFits(cancel, "Cancel")
        assertTrue("RTL composer did not mirror primary/secondary action order", save.visibleBounds.centerX() < cancel.visibleBounds.centerX())
        cancel.click()

        assertTrue("Cancel did not return to RTL Calendar", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))
        clickDescription("Tools navigation")
        assertTrue("RTL Tools did not open Documents", device.wait(Until.hasObject(By.text("Documents")), TIMEOUT))
        assertRtlNavigationOrder()
        listOf("Open document", "Open scanner", "Open music").forEach { label ->
            assertTargetFits(scrollToVisibleAction(label), label)
        }
    }

    @Test
    fun rtlCalendarKeepsEveryDateTargetReachableAtLargeText() {
        device.executeShellCommand("settings put system font_scale 1.3")
        launchHome()
        clickDescription("Calendar navigation")
        assertTrue("RTL Calendar did not open at large text", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))

        val dateTargets = findDateTargets()
        assertEquals("RTL Calendar should show seven date choices at large text", 7, dateTargets.size)
        dateTargets.forEach { target ->
            assertTargetFits(target, "Large-text RTL date ${target.text}")
            assertTrue("Large-text RTL date lacks a full accessible date: ${target.contentDescription}", target.contentDescription.contains(','))
        }
    }

    @Test
    fun narrowCalendarStacksActionsWithoutClipping() {
        device.executeShellCommand("wm size 900x2856")
        launchHome()
        clickDescription("Calendar navigation")
        assertTrue("Calendar did not open at narrow width", device.wait(Until.hasObject(By.text("Today")), TIMEOUT))

        val actionLabels = listOf("Today", "Week", "Add task")
        val actions = actionLabels.map { label ->
            assertTrue("Narrow Calendar action $label was not visible", device.wait(Until.hasObject(By.text(label)), TIMEOUT))
            device.findObjects(By.clickable(true)).firstOrNull { it.text == label }
                ?: error("Narrow Calendar action $label was not reachable")
        }
        actions.forEachIndexed { index, action -> assertTargetFits(action, "Narrow Calendar action ${actionLabels[index]}") }
        assertTrue("Narrow Calendar actions should stack in reading order", actions[0].visibleBounds.top < actions[1].visibleBounds.top && actions[1].visibleBounds.top < actions[2].visibleBounds.top)
        assertTrue("Add task should use the available narrow width", actions[2].visibleBounds.width() >= (device.displayWidth * 0.7f).toInt())
    }

    @Test
    fun narrowLargeTextToolsKeepsEveryPrimaryActionReachable() {
        device.executeShellCommand("settings put system font_scale 2.0")
        device.executeShellCommand("wm size 960x2133")
        launchHome()
        clickDescription("Tools navigation")
        assertTrue("Tools did not open at large text and narrow width", device.wait(Until.hasObject(By.text("Documents")), TIMEOUT))

        listOf("Open document", "Open scanner", "Open music").forEach { label ->
            assertTargetFits(scrollToVisibleAction(label), label)
        }
    }

    @Test
    fun unavailableDocumentStateIsIncludedInAccessibilityDescription() {
        val uri = "content://map-test/${System.currentTimeMillis()}"
        val name = "Missing document ${System.currentTimeMillis()}.pdf"
        cleanupDocumentUri = uri
        val database = DocumentDatabase(context)
        try {
            database.upsert(uri, name, "application/pdf")
            database.markUnavailable(uri)
        } finally {
            database.close()
        }

        context.startActivity(Intent(context, ToolsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        val document = device.wait(Until.findObject(By.desc("Unavailable document: $name")), TIMEOUT)
            ?: error("Tools did not show the unavailable document")
        assertTrue(
            "TalkBack description omitted unavailable state: ${document.contentDescription}",
            document.contentDescription.orEmpty().contains("unavailable", ignoreCase = true),
        )
        assertTargetFits(document, "Unavailable document")
    }

    private fun setAppLocales(locales: String) {
        localeManager.applicationLocales = LocaleList.forLanguageTags(locales)
    }

    private fun launchHome() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP Home did not open", device.wait(Until.hasObject(By.text("Home")), TIMEOUT))
    }

    private fun assertRtlNavigationOrder() {
        val home = device.wait(Until.findObject(By.desc("Home navigation")), TIMEOUT)
            ?: error("Home navigation was not accessible")
        val tools = device.wait(Until.findObject(By.desc("Tools navigation")), TIMEOUT)
            ?: error("Tools navigation was not accessible")
        assertTargetFits(home, "Home navigation")
        assertTargetFits(tools, "Tools navigation")
        assertTrue(
            "RTL primary navigation was not mirrored: Home=${home.visibleBounds}, Tools=${tools.visibleBounds}",
            home.visibleBounds.centerX() > tools.visibleBounds.centerX()
        )
    }

    private fun findDateTargets(): List<androidx.test.uiautomator.UiObject2> {
        device.wait(Until.hasObject(By.descContains(",")), TIMEOUT)
        return device.findObjects(By.clickable(true)).filter { target ->
            target.className.substringAfterLast('.').endsWith("Button") && target.contentDescription.orEmpty().contains(',')
        }
    }

    private fun assertTargetFits(target: androidx.test.uiautomator.UiObject2, label: String) {
        val bounds = target.visibleBounds
        val minimum = (48 * context.resources.displayMetrics.density).toInt()
        assertTrue("$label was narrower than 48dp: $bounds", bounds.width() >= minimum)
        assertTrue("$label was shorter than 48dp: $bounds", bounds.height() >= minimum)
        assertTrue("$label was clipped horizontally: $bounds", bounds.left >= 0 && bounds.right <= device.displayWidth)
    }

    private fun scrollToVisibleAction(label: String): UiObject2 {
        val minimumHeight = (48 * context.resources.displayMetrics.density).toInt()
        repeat(5) { attempt ->
            val action = device.wait(Until.findObject(By.text(label)), 1_000L)
            if (action != null && action.visibleBounds.height() >= minimumHeight) return action
            if (attempt < 4) device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3, device.displayWidth / 2, device.displayHeight / 3, 350)
        }
        error("$label remained clipped in narrow, large-text Tools")
    }

    private fun clickText(text: String) {
        device.wait(Until.findObject(By.text(text)), TIMEOUT)?.click() ?: error("Could not click $text")
    }

    private fun clickDescription(description: String) {
        device.wait(Until.findObject(By.desc(description)), TIMEOUT)?.click() ?: error("Could not click $description")
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
