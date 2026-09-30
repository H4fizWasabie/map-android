package app.map.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarActivity : AppCompatActivity() {
    private lateinit var database: TaskDatabase
    private var selectedDay = dayStart(System.currentTimeMillis())
    private var weekMode = false
    private var contentScroll: ScrollView? = null
    private var restoredScrollY = 0
    private var initialResumePending = true
    private var musicPlayButton: MaterialButton? = null
    private var undoState: UndoState? = null
    private var taskComposer: TaskComposer? = null

    private val musicStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val button = musicPlayButton ?: return
            val event = intent ?: return
            if (!event.hasExtra(MusicService.EXTRA_PLAYING)) return
            button.text = if (event.getBooleanExtra(MusicService.EXTRA_PLAYING, false)) "Pause" else "Play"
            button.contentDescription = button.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TaskDatabase(this)
        savedInstanceState?.takeIf { it.containsKey(STATE_SELECTED_DAY) }?.getLong(STATE_SELECTED_DAY)?.let { selectedDay = dayStart(it) }
        weekMode = savedInstanceState?.getBoolean(STATE_WEEK_MODE, false) ?: false
        restoredScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y, 0) ?: 0
        render()
        TaskComposerState.from(savedInstanceState)?.let(::showTaskComposer)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_SELECTED_DAY, selectedDay)
        outState.putBoolean(STATE_WEEK_MODE, weekMode)
        outState.putInt(STATE_SCROLL_Y, contentScroll?.scrollY ?: 0)
        taskComposer?.savedState()?.let { outState.putBundle(TaskComposerState.BUNDLE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (initialResumePending) {
            initialResumePending = false
            return
        }
        if (::database.isInitialized) render()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, musicStateReceiver, IntentFilter(MusicService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(musicStateReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun render() {
        val weekStart = monday(selectedDay)
        val root = LinearLayout(this).apply {
            setBackgroundColor(getColor(R.color.map_background))
        }
        val scroll = ScrollView(this).also { contentScroll = it }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        MapUi.addBrandMark(this, body)
        body.addView(TextView(this).apply {
            text = if (weekMode) "Week of ${formatWeekStart(weekStart)}" else "Your calendar"
            MapUi.display(this)
            setPadding(0, 0, 0, dp(18))
        })
        body.addView(dateReadout())
        body.addView(dateStrip())
        val todayButton = actionButton("Today") { selectedDay = dayStart(System.currentTimeMillis()); weekMode = false; render() }
        val weekButton = actionButton(if (weekMode) "Agenda" else "Week") { weekMode = !weekMode; render() }
        val addTaskButton = MapUi.primaryButton(this).apply {
            text = "Add task"
            isAllCaps = false
            minHeight = dp(48)
            minWidth = dp(48)
            setOnClickListener { showTaskComposer() }
        }
        val stackActions = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale >= 1.3f
        if (stackActions) {
            listOf(todayButton, weekButton, addTaskButton).forEach { button ->
                body.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        } else {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf(todayButton, weekButton, addTaskButton).forEachIndexed { index, button ->
                    addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = dp(8)
                    })
                }
            }.also { body.addView(it) }
        }
        addUndoBar(body)
        if (weekMode) addWeek(body, weekStart) else addDay(body)
        scroll.addView(body)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addMusicMiniPlayer(this)
        }
        MapUi.addPrimaryNavigation(root, content, MapUi.bottomNavigation(this, "Calendar", ::navigate))
        MapUi.applySystemBarInsets(root)
        setContentView(root)
        val scrollY = restoredScrollY
        restoredScrollY = 0
        contentScroll?.post { contentScroll?.scrollTo(0, scrollY) }
    }

    private fun navigate(label: String) {
        when (label) {
            "Home" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            "Tasks" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_TASKS, true))
            "Tools" -> startActivity(Intent(this, ToolsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun dateReadout(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setBackgroundResource(R.drawable.map_focus_surface)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(TextView(this@CalendarActivity).apply {
            text = Calendar.getInstance().apply { timeInMillis = selectedDay }.get(Calendar.DAY_OF_MONTH).toString()
            MapUi.numeral(this, size = 30f, color = R.color.map_accent)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), -2))
        addView(LinearLayout(this@CalendarActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@CalendarActivity).apply {
                text = if (weekMode) "Selected week" else "Day agenda"
                MapUi.caption(this)
            })
            addView(TextView(this@CalendarActivity).apply {
                text = formatDate(selectedDay)
                MapUi.section(this)
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun dateStrip(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@CalendarActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            val railWidth = when {
                resources.configuration.screenWidthDp < 600 -> 0
                resources.configuration.fontScale >= 1.3f -> 184
                else -> 80
            }
            val targetWidth = dp(((resources.configuration.screenWidthDp - railWidth - 48) / 7).coerceAtLeast(48))
            val dayPattern = DateTimeFormatterBuilder()
                .appendText(
                    ChronoField.DAY_OF_WEEK,
                    if (resources.configuration.fontScale >= 1.3f) TextStyle.NARROW else TextStyle.SHORT,
                )
                .appendLiteral('\n')
                .appendValue(ChronoField.DAY_OF_MONTH)
                .toFormatter(Locale.getDefault())
            val zone = ZoneId.systemDefault()
            val start = addDays(selectedDay, -3)
            (0..6).forEach { offset ->
                val day = addDays(start, offset)
                val label = dayPattern.format(Instant.ofEpochMilli(day).atZone(zone).toLocalDate())
                val selected = day == selectedDay
                addView(actionButton(label) {
                    selectedDay = day
                    weekMode = false
                    render()
                }.apply {
                    contentDescription = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(day))
                    setPadding(0, paddingTop, 0, paddingBottom)
                    isSelected = selected
                    cornerRadius = dp(24)
                    strokeWidth = 0
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                    if (selected) {
                        backgroundTintList = ContextCompat.getColorStateList(this@CalendarActivity, R.color.map_accent)
                        setTextColor(getColor(R.color.map_on_accent))
                    }
                }, LinearLayout.LayoutParams(targetWidth, -2))
            }
        })
    }

    private fun addDay(parent: LinearLayout) {
        val tasks = database.openTasks().filter { it.dueAt?.let(::dayStart) == selectedDay }
        val allDayTasks = tasks.filter { it.allDay }.sortedBy { it.dueAt }
        val timedTasks = tasks.filterNot { it.allDay }.sortedBy { it.dueAt }
        if (allDayTasks.isNotEmpty()) {
            addHeading(parent, "All day")
            allDayTasks.forEach { addTaskRow(parent, it) }
        }
        addHeading(parent, if (timedTasks.isEmpty()) "Nothing scheduled" else "Timeline")
        if (timedTasks.isEmpty()) {
            addEmpty(parent, if (allDayTasks.isEmpty()) "Your day is open. Add a task when something needs a place." else "No timed tasks today.")
            return
        }
        val visible = timedTasks.filter { hourOfDay(it.dueAt!!) in FIRST_HOUR..LAST_HOUR }
        (FIRST_HOUR..LAST_HOUR).forEach { hour ->
            addTimeRow(parent, hour, visible.filter { hourOfDay(it.dueAt!!) == hour })
        }
        timedTasks.filter { hourOfDay(it.dueAt!!) !in FIRST_HOUR..LAST_HOUR }
            .forEach { addTaskRow(parent, it) }
    }

    private fun addTimeRow(parent: LinearLayout, hour: Int, tasks: List<Task>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            minimumHeight = dp(64)
        }
        row.addView(TextView(this).apply {
            text = "%02d:00".format(hour)
            MapUi.caption(this)
            setPadding(0, dp(8), dp(8), 0)
        }, LinearLayout.LayoutParams(dp(58), -1))
        val slot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), 0, dp(2))
            addView(View(this@CalendarActivity).apply {
                setBackgroundColor(getColor(R.color.map_divider))
                layoutParams = LinearLayout.LayoutParams(-1, dp(1))
            })
        }
        tasks.forEach { addTaskRow(slot, it) }
        row.addView(slot, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
    }

    private fun addWeek(parent: LinearLayout, start: Long) {
        val tasks = database.openTasks()
        (0..6).forEach { offset ->
            val day = addDays(start, offset)
            val dayTasks = tasks.filter { it.dueAt?.let(::dayStart) == day }.sortedWith(compareByDescending<Task> { it.allDay }.thenBy { it.dueAt })
            parent.addView(TextView(this).apply {
                text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(day))
                MapUi.section(this)
                setPadding(0, dp(12), 0, dp(6))
            })
            if (dayTasks.isEmpty()) addEmpty(parent, "Nothing scheduled") else dayTasks.forEach { addTaskRow(parent, it) }
        }
    }

    private fun addTaskRow(parent: LinearLayout, task: Task) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            contentDescription = "Calendar task: ${task.title}"
            setOnClickListener {
                startActivity(Intent(this@CalendarActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply {
                    putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                    putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
                })
            }
        }
        row.addView(CheckBox(this).apply {
            contentDescription = "Complete ${task.title}"
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { completeTask(task) }
        }, LinearLayout.LayoutParams(dp(48), dp(56)))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(TextView(this@CalendarActivity).apply {
                text = task.title
                maxLines = 2
                MapUi.body(this)
            })
            addView(TextView(this@CalendarActivity).apply {
                text = if (task.allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(task.dueAt ?: 0))
                MapUi.metadata(this)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        })
    }

    private fun completeTask(task: Task) {
        undoState = TaskActions.complete(this, database, task)
        render()
    }

    private fun showTaskComposer(state: TaskComposerState? = null) {
        val initialState = state ?: TaskComposerState(
            dueAt = Calendar.getInstance().apply {
                timeInMillis = selectedDay
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis,
        )
        taskComposer = TaskComposer(
            activity = this,
            database = database,
            notificationRequest = NOTIFICATION_REQUEST,
            onSaved = ::render,
            onDismiss = { taskComposer = null },
        ).also { it.show(initialState) }
    }

    private fun addUndoBar(parent: LinearLayout) =
        MapUi.addUndoBar(this, parent, database, undoState) { undoState = null; render() }

    private fun addMusicMiniPlayer(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val item = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        if (item == null) return
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        })
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.map_surface)
            contentDescription = "Music: ${item.title}"
            setOnClickListener { startActivity(Intent(this@CalendarActivity, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)) }
            addView(TextView(this@CalendarActivity).apply {
                text = "${item.title}\n${item.artist}"
                MapUi.body(this)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = MapUi.button(this@CalendarActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    val intent = Intent(this@CalendarActivity, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE)
                    if (!startMapMusicService(this@CalendarActivity, intent)) {
                        Toast.makeText(this@CalendarActivity, "MAP could not start Music. Try Play again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            musicPlayButton = playButton
            addView(playButton)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MapUi.handleNotificationPermissionResult(
            this,
            requestCode,
            NOTIFICATION_REQUEST,
            grantResults,
            "Task reminders won't appear until MAP notifications are enabled. You can turn them on in Settings.",
        )
    }

    private fun actionButton(label: String, click: () -> Unit): MaterialButton = MapUi.button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        minWidth = dp(48)
        setTextColor(getColor(R.color.map_text))
        setOnClickListener { click() }
    }

    private fun addHeading(parent: LinearLayout, text: String) {
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) })
        parent.addView(TextView(this).apply {
            this.text = text
            MapUi.section(this)
            setPadding(0, dp(10), 0, dp(8))
        })
    }

    private fun addEmpty(parent: LinearLayout, text: String) = parent.addView(TextView(this).apply {
        this.text = text
        MapUi.body(this)
        setTextColor(getColor(R.color.map_muted))
        setPadding(0, 0, 0, dp(8))
    })

    private fun dp(value: Int) = MapUi.dp(this, value)

    private fun hourOfDay(value: Long): Int = Calendar.getInstance().apply { timeInMillis = value }.get(Calendar.HOUR_OF_DAY)

    companion object {
        private const val FIRST_HOUR = 6
        private const val LAST_HOUR = 22
        private const val NOTIFICATION_REQUEST = 14
        private const val STATE_SELECTED_DAY = "selected_day"
        private const val STATE_WEEK_MODE = "week_mode"
        private const val STATE_SCROLL_Y = "scroll_y"
        private fun dayStart(value: Long): Long = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        private fun addDays(value: Long, amount: Int): Long = Calendar.getInstance().apply { timeInMillis = value; add(Calendar.DAY_OF_YEAR, amount) }.timeInMillis
        private fun monday(value: Long): Long {
            val calendar = Calendar.getInstance().apply { timeInMillis = value }
            val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
            return dayStart(calendar.timeInMillis)
        }
        private fun formatWeekStart(value: Long) = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(value))
        private fun formatDate(value: Long) = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(value))
    }
}
