package app.map.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicTransitionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private lateinit var mediaFile: File

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        context.stopService(Intent(context, MusicService::class.java))
        context.deleteDatabase("map-music.db")
        mediaFile = File(context.filesDir, "map-music-transition-test.wav")
        writeSilentWav(mediaFile)

        val item = AudioItem(
            uri = Uri.fromFile(mediaFile).toString(),
            folderUri = "test://music",
            folderName = "MAP test media",
            name = mediaFile.name,
            title = "MAP transition test",
            artist = "MAP test",
            album = "MAP test",
            genre = "Test",
            format = "wav",
            durationMs = TEST_DURATION_MS.toLong(),
        )
        MusicDatabase(context).also { database ->
            database.replaceFolderTracks(MusicFolder("test://music", "MAP test media"), listOf(item))
            database.setQueue(listOf(item), item.uri)
            database.close()
        }
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, MusicService::class.java))
        mediaFile.delete()
        context.deleteDatabase("map-music.db")
    }

    @Test
    fun playbackSurvivesLeavingAndReopeningMusic() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        repeat(3) {
            device.pressHome()
            SystemClock.sleep(500)
            launchMap()
            clickText("Tools")
            clickText("Open music")
            assertPlayback()
        }
    }

    private fun launchMap() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP did not reach Home", device.wait(Until.hasObject(By.text("Home")), TIMEOUT))
    }

    private fun clickText(text: String) {
        val selector = By.text(text)
        assertTrue("MAP did not show $text", device.wait(Until.hasObject(selector), TIMEOUT))
        device.findObject(selector)?.click() ?: error("MAP could not click $text")
    }

    private fun clickDescription(description: String) {
        val selector = By.desc(description)
        assertTrue("MAP did not show $description", device.wait(Until.hasObject(selector), TIMEOUT))
        device.findObject(selector)?.click() ?: error("MAP could not click $description")
    }

    private fun assertPlayback() {
        assertTrue("MAP Now Playing did not expose Pause", device.wait(Until.hasObject(By.desc("Pause")), TIMEOUT))
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (SystemClock.elapsedRealtime() < deadline) {
            val database = MusicDatabase(context)
            val playing = database.playing()
            database.close()
            if (playing) return
            SystemClock.sleep(200)
        }
        error("MAP playback state did not become playing")
    }

    private fun dismissNotificationPermission() {
        val allow = By.text("Allow")
        if (device.wait(Until.hasObject(allow), 1_000)) device.findObject(allow)?.click()
    }

    private fun writeSilentWav(file: File) {
        val dataSize = 8_000 * 2 * TEST_DURATION_MS / 1_000
        FileOutputStream(file).use { output ->
            output.write("RIFF".toByteArray())
            writeInt(output, 36 + dataSize)
            output.write("WAVEfmt ".toByteArray())
            writeInt(output, 16)
            writeShort(output, 1)
            writeShort(output, 1)
            writeInt(output, 8_000)
            writeInt(output, 16_000)
            writeShort(output, 2)
            writeShort(output, 16)
            output.write("data".toByteArray())
            writeInt(output, dataSize)
            output.write(ByteArray(dataSize))
        }
    }

    private fun writeInt(output: FileOutputStream, value: Int) {
        output.write(byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte(),
        ))
    }

    private fun writeShort(output: FileOutputStream, value: Int) {
        output.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val TEST_DURATION_MS = 30_000
    }
}
