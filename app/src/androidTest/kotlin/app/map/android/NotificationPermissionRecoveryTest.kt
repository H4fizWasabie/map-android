package app.map.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun deniedTaskNotificationsOfferSettingsRecovery() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onRequestPermissionsResult(
                    40,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    intArrayOf(PackageManager.PERMISSION_GRANTED),
                )
            }
            assertFalse("Granted notifications unexpectedly opened a recovery dialog", device.wait(Until.hasObject(By.text("Notifications are off")), 300))

            scenario.onActivity { activity ->
                activity.onRequestPermissionsResult(
                    40,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    intArrayOf(PackageManager.PERMISSION_DENIED),
                )
            }
            assertTrue("Denied notifications did not explain the task reminder state", device.wait(Until.hasObject(By.text("Notifications are off")), 5_000))
            assertTrue("Denied notifications did not offer Settings recovery", device.hasObject(By.text(Pattern.compile("(?i)open settings"))))
            device.findObject(By.text(Pattern.compile("(?i)not now"))).click()
        }
    }
}
