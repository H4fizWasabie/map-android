package app.map.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarActivity : Activity() {
    private lateinit var database: TaskDatabase
    private var selectedDay = dayStart(System.currentTimeMillis())
    private var weekMode = false
    private var contentScroll: ScrollView? = null
    private var restoredScrollY = 0
    private var initialResumePending = true
    private var musicPlayButton: Button? = null
    private var undoState: UndoState? = null

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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_SELECTED_DAY, selectedDay)
        outState.putBoolean(STATE_WEEK_MODE, weekMode)
        outState.putInt(STATE_SCROLL_Y, contentScroll?.scrollY ?: 0)
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
        val root = LinearLayout(this).apply {
            setBackgroundColor(getColor(R.color.map_background))
        }
        val scroll = ScrollView(this).also { contentScroll = it }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(24)) }
        body.addView(TextView(this).apply {
            text = if (weekMode) "This week" else "Today"
            textSize = 30f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(8), 0, dp(4))
        })
        body.addView(TextView(this).apply {
            text = if (weekMode) "A quiet view of the week ahead." else formatDate(selectedDay)
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(12))
        })
        body.addView(dateStrip())
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Today") { selectedDay = dayStart(System.currentTimeMillis()); weekMode = false; render() })
            addView(actionButton(if (weekMode) "Today agenda" else "Week") { weekMode = !weekMode; render() })
            addView(Button(this@CalendarActivity, null, 0, R.style.MapPrimaryButton).apply {
                text = "Add task"
                isAllCaps = false
                minHeight = dp(48)
                minWidth = dp(48)
                setOnClickListener {
                    startActivity(Intent(this@CalendarActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_COMPOSER, true))
                }
            })
        }.also { body.addView(it) }
        addUndoBar(body)
        if (weekMode) addWeek(body) else addDay(body)
        scroll.addView(body)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
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

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(24), dp(16), dp(4))
        addView(TextView(this@CalendarActivity).apply {
            text = "Calendar"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(getColor(R.color.map_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun navigate(label: String) {
        when (label) {
            "Home" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            "Tasks" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_TASKS, true))
            "Tools" -> startActivity(Intent(this, ToolsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun dateStrip(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@CalendarActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            val start = addDays(selectedDay, -3)
            (0..6).forEach { offset ->
                val day = addDays(start, offset)
                addView(actionButton(SimpleDateFormat("EEE\nd", Locale.getDefault()).format(Date(day))) {
                    selectedDay = day
                    weekMode = false
                    render()
                }.apply { isEnabled = day != selectedDay })
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
            textSize = 12f
            setTextColor(getColor(R.color.map_muted))
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

    private fun addWeek(parent: LinearLayout) {
        val start = monday(selectedDay)
        val tasks = database.openTasks()
        addHeading(parent, "Week of ${formatDate(start)}")
        (0..6).forEach { offset ->
            val day = addDays(start, offset)
            val dayTasks = tasks.filter { it.dueAt?.let(::dayStart) == day }.sortedWith(compareByDescending<Task> { it.allDay }.thenBy { it.dueAt })
            parent.addView(TextView(this).apply {
                text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(day))
                textSize = 17f
                setTextColor(getColor(R.color.map_text))
                setPadding(0, dp(12), 0, dp(6))
            })
            if (dayTasks.isEmpty()) addEmpty(parent, "Open") else dayTasks.forEach { addTaskRow(parent, it) }
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
                textSize = 16f
                maxLines = 2
                setTextColor(getColor(R.color.map_text))
            })
            addView(TextView(this@CalendarActivity).apply {
                text = if (task.allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(task.dueAt ?: 0))
                textSize = 13f
                setTextColor(getColor(R.color.map_muted))
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
        ReminderScheduler.cancel(this, task.id)
        val completedAt = System.currentTimeMillis()
        val nextDueAt = database.nextDueAt(task, completedAt)
        val nextId = database.complete(task)
        undoState = UndoState(task, nextId)
        if (nextId != null && nextDueAt != null) ReminderScheduler.schedule(this, nextId, task.title, nextDueAt)
        render()
    }

    private fun addUndoBar(parent: LinearLayout) {
        val state = undoState ?: return
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.map_surface)
            addView(TextView(this@CalendarActivity).apply {
                text = "Completed ${state.task.title}"
                textSize = 14f
                setTextColor(getColor(R.color.map_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@CalendarActivity).apply {
                text = "Undo"
                isAllCaps = false
                setOnClickListener {
                    ReminderScheduler.cancel(this@CalendarActivity, state.nextId ?: state.task.id)
                    if (database.undoComplete(state.task, state.nextId)) {
                        state.task.dueAt?.takeIf { it > System.currentTimeMillis() }?.let {
                            ReminderScheduler.schedule(this@CalendarActivity, state.task.id, state.task.title, it)
                        }
                    }
                    undoState = null
                    render()
                }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

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
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.map_text))
                setPadding(0, 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = Button(this@CalendarActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    requestNotificationsIfNeeded()
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

    private fun requestNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    private fun actionButton(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(48)
        minWidth = dp(48)
        setTextColor(getColor(R.color.map_text))
        setOnClickListener { click() }
    }

    private fun addHeading(parent: LinearLayout, text: String) = parent.addView(TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(getColor(R.color.map_text))
        setPadding(0, dp(16), 0, dp(8))
    })

    private fun addEmpty(parent: LinearLayout, text: String) = parent.addView(TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(getColor(R.color.map_muted))
        setPadding(0, 0, 0, dp(8))
    })

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

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
        private fun formatDate(value: Long) = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(value))
    }

    private data class UndoState(val task: Task, val nextId: Long?)
}
