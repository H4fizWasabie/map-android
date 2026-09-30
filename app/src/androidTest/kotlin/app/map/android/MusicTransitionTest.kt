package app.map.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var secondMediaFile: File
    private lateinit var databaseSnapshot: List<DatabaseBackup>
    private var folderAccessTestName: String? = null
    private var folderAccessTestUri: Uri? = null
    private val testContext = instrumentation.context

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        finishMapActivities()
        stopMusicService()
        snapshotMusicDatabase()
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
        if (device.currentPackageName == testContext.packageName) device.pressBack()
        finishMapActivities()
        stopMusicService()
        testFolderUri()?.let { uri ->
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, document) }
        }
        releaseFolderAccessTestGrant()
        mediaFile.delete()
        if (::secondMediaFile.isInitialized) secondMediaFile.delete()
        context.deleteDatabase("map-music.db")
        databaseSnapshot.forEach { backup ->
            if (backup.existed) backup.copy.copyTo(backup.original, overwrite = true)
            backup.copy.delete()
        }
    }

    @Test
    fun firstUseLibraryOffersFolderSetupWithoutBrowseControls() {
        context.deleteDatabase("map-music.db")
        context.startActivity(Intent(context, MusicActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })

        assertTrue("MAP did not show the first-use folder action", device.wait(Until.hasObject(By.text("Add folder")), TIMEOUT))
        assertTrue("MAP did not explain the local music setup", device.wait(Until.hasObject(By.textContains("Your files stay on this device")), TIMEOUT))
        assertFalse("An empty library showed search", device.hasObject(By.desc("Search music")))
        assertFalse("An empty library showed filters", device.hasObject(By.text("Filters")))
        assertFalse("An empty library showed sorting", device.hasObject(By.text("Sort: Title")))
        assertFalse("An empty library showed track categories", device.hasObject(By.text("Albums")))
        assertFalse("An empty library showed folder management before a folder was added", device.hasObject(By.text("Manage folders")))
        assertFalse("An empty library showed refresh before a folder was added", device.hasObject(By.text("Refresh")))
    }

    @Test
    fun removingSelectedFolderReleasesItsPersistedGrant() {
        val name = "MAP-QA-${SystemClock.elapsedRealtime()}"
        folderAccessTestName = name
        context.deleteDatabase("map-music.db")

        context.startActivity(Intent(context, MusicActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP Music library did not open", device.wait(Until.hasObject(By.text("Add folder")), TIMEOUT))
        clickText("Add folder")
        if (!device.wait(Until.hasObject(By.text("Download")), 3_000)) {
            clickSelector(By.desc("Show roots"), "storage locations")
            clickSelector(By.textContains("sdk_gphone"), "emulator storage")
        }
        clickText("Download")
        clickText("CREATE NEW FOLDER")
        val folderNameInput = By.clazz("android.widget.EditText")
        assertTrue("Storage picker did not ask for a folder name", device.wait(Until.hasObject(folderNameInput), TIMEOUT))
        device.findObject(folderNameInput)?.setText(name) ?: error("Storage picker could not accept a folder name")
        clickText("OK")
        val testFolder = By.text(name)
        if (!device.wait(Until.hasObject(testFolder), TIMEOUT)) {
            error("Storage picker did not show $name (package ${device.currentPackageName})")
        }
        clickSelector(testFolder, name)
        clickText("USE THIS FOLDER")
        clickText("ALLOW")

        assertTrue("MAP did not return to its music library", device.wait(Until.hasObject(By.text("Add folder")), TIMEOUT))
        assertTrue("Selected empty folders lost their refresh action", device.hasObject(By.text("Refresh")))
        assertTrue("Selected empty folders lost folder management", device.hasObject(By.text("Manage folders")))
        assertTrue(
            "MAP did not explain how to recover an empty selected folder",
            device.wait(Until.hasObject(By.textContains("No audio tracks found in the selected folders")), TIMEOUT)
        )
        assertFalse("An empty selected folder showed search", device.hasObject(By.desc("Search music")))
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        var folder: MusicFolder? = null
        while (folder == null && SystemClock.elapsedRealtime() < deadline) {
            val database = MusicDatabase(context)
            folder = database.folders().firstOrNull { it.uri.contains(name) }
            database.close()
            if (folder == null) SystemClock.sleep(100)
        }
        assertTrue("MAP did not retain the selected folder", folder != null)
        val uri = Uri.parse(folder!!.uri)
        folderAccessTestUri = uri
        assertTrue(
            "MAP did not retain its SAF grant",
            context.contentResolver.persistedUriPermissions.any { it.uri == uri }
        )
        val testDocument = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

        device.findObjects(By.text("Manage folders")).firstOrNull()?.click()
            ?: error("Selected folders action was unavailable")
        clickText(folder.name)
        clickText("Remove")
        val releaseDeadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (context.contentResolver.persistedUriPermissions.any { it.uri == uri } && SystemClock.elapsedRealtime() < releaseDeadline) {
            SystemClock.sleep(100)
        }
        val checkDatabase = MusicDatabase(context)
        val remainingFolders = checkDatabase.folders()
        checkDatabase.close()
        val grantRemains = context.contentResolver.persistedUriPermissions.any { it.uri == uri }
        assertTrue(
            "Removing the folder left its SAF grant behind: $grantRemains",
            !grantRemains
        )
        assertTrue("Removing the folder left it in the Music library", remainingFolders.none { it.uri == uri.toString() })
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, testDocument) }
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

    @Test
    fun equalizerPresetAppliesToActiveTrackAndSurvivesDatabaseReopen() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        clickText("Equalizer")
        val presetSpinner = device.wait(Until.findObject(By.desc("Equalizer preset")), TIMEOUT)
            ?: error("Equalizer preset control was not available during playback")
        presetSpinner.click()
        var selectedPreset = false
        for (attempt in 0 until 5) {
            val presets = device.findObjects(By.clazz("android.widget.CheckedTextView"))
            if (presets.size > 1) {
                try {
                    presets[1].click()
                    selectedPreset = true
                    break
                } catch (_: StaleObjectException) {
                    SystemClock.sleep(100)
                }
            } else {
                SystemClock.sleep(100)
            }
        }
        assertTrue("Equalizer did not expose a selectable hardware preset", selectedPreset)
        clickText("Apply preset")

        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        var presetApplied = false
        while (SystemClock.elapsedRealtime() < deadline && !presetApplied) {
            val database = MusicDatabase(context)
            presetApplied = database.equalizerPreset() == 1.toShort() && database.equalizerBand(0) != null
            database.close()
            if (!presetApplied) SystemClock.sleep(100)
        }
        assertTrue("Applying a hardware preset did not update the active equalizer and its saved bands", presetApplied)

        MusicDatabase(context).also { database ->
            assertEquals(1.toShort(), database.equalizerPreset())
            database.close()
        }
    }

    @Test
    fun customSleepTimerCanBeSetAndCleared() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        clickText("Sleep")
        clickText("Custom duration")
        val minutes = device.wait(Until.findObject(By.clazz("android.widget.EditText")), TIMEOUT)
            ?: error("Sleep timer did not ask for a custom duration")
        minutes.click()
        minutes.setText("1")
        device.pressBack()
        assertTrue("Sleep duration dialog closed while hiding the numeric keyboard", device.wait(Until.hasObject(By.text("Sleep after how many minutes?")), TIMEOUT))
        val setButton = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)set"))), TIMEOUT)
            ?: error("Sleep duration dialog did not show Set")
        setButton.click()
        val countdown = device.wait(Until.findObject(By.textContains("Stops in")), TIMEOUT)?.text
            ?: error("One-minute timer did not show its countdown")
        assertTrue("Unexpected one-minute timer countdown: $countdown", countdown.matches(Regex("Stops in (?:1:00|0:[0-5][0-9])")))

        clickText("Sleep")
        clickText("Off")
        assertTrue("Turning the sleep timer off left a countdown visible", device.wait(Until.gone(By.textContains("Stops in")), TIMEOUT))
    }

    @Test
    fun queueReorderClosesThePreviousDialogBeforeShowingUpdatedQueue() {
        secondMediaFile = File(context.filesDir, "map-music-transition-test-second.wav")
        mediaFile.copyTo(secondMediaFile, overwrite = true)
        val database = MusicDatabase(context)
        val first = database.tracks().first()
        val second = first.copy(
            uri = Uri.fromFile(secondMediaFile).toString(),
            name = secondMediaFile.name,
            title = "MAP second transition test",
        )
        database.replaceFolderTracks(MusicFolder("test://music", "MAP test media"), listOf(first, second))
        database.setQueue(listOf(first, second), first.uri)
        database.close()

        context.startActivity(Intent(context, MusicActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)
        })
        assertTrue("MAP Now Playing did not open from its current queue item", device.wait(Until.hasObject(By.text("Now Playing")), TIMEOUT))
        clickText("Queue")
        assertTrue("Queue did not show its Done action", device.wait(Until.hasObject(By.text("Done")), TIMEOUT))
        var downBounds: android.graphics.Rect? = null
        val actionDeadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (downBounds == null && SystemClock.elapsedRealtime() < actionDeadline) {
            downBounds = device.findObjects(By.clickable(true)).mapNotNull { control ->
                runCatching { control.visibleBounds.takeIf { control.text == "Down" } }.getOrNull()
            }.minByOrNull { it.top }
            if (downBounds == null) SystemClock.sleep(100)
        }
        val bounds = downBounds ?: error("Queue reorder action was unavailable for ${first.title}")
        device.click(bounds.centerX(), bounds.centerY())

        val expectedOrder = listOf(second.uri, first.uri)
        val reorderDeadline = SystemClock.elapsedRealtime() + TIMEOUT
        var actualOrder = emptyList<String>()
        while (actualOrder != expectedOrder && SystemClock.elapsedRealtime() < reorderDeadline) {
            val queue = MusicDatabase(context)
            actualOrder = try {
                queue.queue().map { it.uri }
            } finally {
                queue.close()
            }
            if (actualOrder != expectedOrder) SystemClock.sleep(100)
        }
        assertEquals("Down did not move the first track behind the second", expectedOrder, actualOrder)

        SystemClock.sleep(250)
        val done = device.wait(Until.findObject(By.text("Done")), TIMEOUT)
        assertTrue("Queue action hid the dialog controls: ${device.findObjects(By.clickable(true)).map { it.text ?: it.contentDescription }}", done != null)
        done?.click()
        assertTrue("An older queue dialog remained open after Done", device.wait(Until.gone(By.text("Done")), TIMEOUT))
        assertTrue("Closing the queue did not return to Now Playing", device.wait(Until.hasObject(By.text("Now Playing")), TIMEOUT))
    }

    @Test
    fun homeMiniPlayerCanPauseAndResumePlayback() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        device.pressHome()
        SystemClock.sleep(500)
        launchMap()
        clickDescription("Pause")
        assertTrue("Home mini-player did not pause", device.wait(Until.hasObject(By.desc("Play")), TIMEOUT))
        clickDescription("Play")
        assertTrue("Home mini-player did not resume", device.wait(Until.hasObject(By.desc("Pause")), TIMEOUT))
    }

    @Test
    fun permanentAudioFocusLossWaitsForAnExplicitPlayAction() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        testContext.startActivity(Intent(testContext, AudioFocusProbeActivity::class.java)
            .putExtra(AudioFocusProbeActivity.EXTRA_PERMANENT_FOCUS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("The temporary audio focus owner did not open", device.wait(Until.hasObject(By.text("Temporary audio focus owner")), TIMEOUT))
        assertTrue("MAP did not pause after permanent audio focus loss", waitForPlaying(false))

        device.pressBack()
        assertTrue("MAP restarted after permanent focus loss without user input", device.wait(Until.hasObject(By.desc("Play")), TIMEOUT))
        assertTrue("MAP's playback state resumed after permanent focus loss", waitForPlaying(false))
        clickDescription("Play")
        assertPlayback()
    }

    @Test
    fun transientAudioFocusLossResumesPlaybackWhenFocusReturns() {
        launchMap()
        clickText("Tools")
        clickText("Open music")
        clickDescription("Play")
        dismissNotificationPermission()
        assertPlayback()

        testContext.startActivity(Intent(testContext, AudioFocusProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("The temporary audio focus owner did not open", device.wait(Until.hasObject(By.text("Temporary audio focus owner")), TIMEOUT))
        assertTrue("MAP did not pause while a temporary audio focus owner was active", waitForPlaying(false))

        device.pressBack()
        assertTrue("MAP did not resume after temporary audio focus returned", device.wait(Until.hasObject(By.desc("Pause")), TIMEOUT))
        assertTrue("MAP's playback state did not resume after focus returned", waitForPlaying(true))
    }

    private fun launchMap() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        assertTrue("MAP did not reach Home", device.wait(Until.hasObject(By.text("Home")), TIMEOUT))
    }

    private fun finishMapActivities() {
        repeat(3) {
            val packageName = device.currentPackageName
            if (packageName != context.packageName && packageName?.contains("documentsui") != true) return
            device.pressBack()
            SystemClock.sleep(100)
        }
    }

    private fun releaseFolderAccessTestGrant() {
        val uri = testFolderUri() ?: return
        val permission = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags != 0) context.contentResolver.releasePersistableUriPermission(uri, flags)
    }

    private fun testFolderUri(): Uri? = folderAccessTestUri ?: folderAccessTestName?.let { name ->
            context.contentResolver.persistedUriPermissions.firstOrNull { it.uri.lastPathSegment?.endsWith(name) == true }?.uri
        }

    private fun stopMusicService() {
        context.stopService(Intent(context, MusicService::class.java))
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (MusicService.isRunning && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        check(!MusicService.isRunning) { "MAP Music service did not stop before database isolation" }
        SystemClock.sleep(250)
    }

    private fun snapshotMusicDatabase() {
        val names = listOf("map-music.db", "map-music.db-wal", "map-music.db-shm", "map-music.db-journal")
        databaseSnapshot = names.mapIndexed { index, name ->
            val original = context.getDatabasePath(name)
            val backup = File(context.cacheDir, "music-transition-db-$index")
            val existed = original.exists()
            if (existed) original.copyTo(backup, overwrite = true) else backup.delete()
            DatabaseBackup(original, backup, existed)
        }
    }

    private fun clickText(text: String) {
        clickSelector(By.text(text), text)
    }

    private fun clickDescription(description: String) {
        clickSelector(By.desc(description), description)
    }

    private fun clickSelector(selector: BySelector, label: String) {
        if (!device.wait(Until.hasObject(selector), TIMEOUT)) {
            error("MAP did not show $label")
        }
        repeat(3) {
            val target = device.findObject(selector) ?: return@repeat
            try {
                target.click()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(200)
            }
        }
        error("MAP could not click $label after the screen updated")
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

    private fun waitForPlaying(expected: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT
        while (SystemClock.elapsedRealtime() < deadline) {
            val database = MusicDatabase(context)
            val playing = database.playing()
            database.close()
            if (playing == expected) return true
            SystemClock.sleep(100)
        }
        return false
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

    private data class DatabaseBackup(val original: File, val copy: File, val existed: Boolean)
}
