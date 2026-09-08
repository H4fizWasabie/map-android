package app.map.android

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var database: TaskDatabase
    private lateinit var content: LinearLayout
    private var undoState: UndoState? = null
    private var selectedView = "Home"
    private var pendingTaskId: Long? = null
    private var musicPlayButton: Button? = null
    private val preferences by lazy { getSharedPreferences("map-focus", MODE_PRIVATE) }

    private val musicStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val button = musicPlayButton ?: return
            val event = intent ?: return
            if (!event.hasExtra(MusicService.EXTRA_PLAYING)) return
            val playing = event.getBooleanExtra(MusicService.EXTRA_PLAYING, false)
            button.text = if (playing) "Pause" else "Play"
            button.contentDescription = button.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TaskDatabase(this)
        applyNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent)
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, musicStateReceiver, IntentFilter(MusicService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(musicStateReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) {
            if (selectedView == "Tasks") showTasks() else showHome()
            pendingTaskId?.let { id ->
                pendingTaskId = null
                database.openTasks().firstOrNull { it.id == id }?.let(::showTaskDetails)
            }
        }
    }

    private fun applyNavigationIntent(intent: Intent) {
        selectedView = if (intent.getBooleanExtra(EXTRA_OPEN_TASKS, false)) "Tasks" else "Home"
        pendingTaskId = intent.getLongExtra(EXTRA_OPEN_TASK_ID, -1L).takeIf { it != -1L }
        if (selectedView == "Tasks") showTasks() else showHome()
        if (intent.getBooleanExtra(EXTRA_OPEN_COMPOSER, false)) showAddTaskDialog()
    }

    private fun showHome() {
        selectedView = "Home"
        val openTasks = database.openTasks()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfTomorrow = startOfToday + DAY

        render("Today", "Home") { body ->
            body.addView(TextView(this).apply {
                text = java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
                textSize = 14f
                setTextColor(getColor(R.color.map_accent))
                setPadding(0, 0, 0, dp(4))
            })
            body.addView(TextView(this).apply {
                text = "A clear view of what needs your attention."
                textSize = 16f
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, 0, 0, dp(16))
            })
            body.addView(Button(this, null, 0, R.style.MapPrimaryButton).apply {
                text = "Add a task"
                isAllCaps = false
                setOnClickListener { showAddTaskDialog() }
            })
            addUndoBar(body)
            addFocusArea(body, openTasks, startOfToday, startOfTomorrow)
            val inbox = openTasks.filter { it.dueAt == null }
            val overdue = openTasks.filter { it.dueAt != null && it.dueAt!! < startOfToday }
            val today = openTasks.filter { it.dueAt != null && it.dueAt!! in startOfToday until startOfTomorrow }
            val upcoming = openTasks.filter { it.dueAt != null && it.dueAt!! >= startOfTomorrow }
            if (inbox.isNotEmpty()) addTaskSection(body, "Inbox", inbox)
            if (overdue.isNotEmpty()) addTaskSection(body, "Overdue", overdue)
            if (today.isNotEmpty()) addTaskSection(body, "Today", today)
            if (upcoming.isNotEmpty()) addTaskSection(body, "Upcoming", upcoming)
            if (inbox.isEmpty() && overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty()) {
                addEmpty(body, "Nothing is scheduled. Add a task when something needs a place.")
            }
            val completed = database.recentCompleted()
            if (completed.isNotEmpty()) addActivity(body, completed)
            addMusicMiniPlayer(body)
        }
    }

    private fun showTasks() {
        selectedView = "Tasks"
        render("Tasks") { body ->
            body.addView(Button(this, null, 0, R.style.MapPrimaryButton).apply {
                text = "Add a task"
                isAllCaps = false
                setOnClickListener { showAddTaskDialog() }
            })
            addUndoBar(body)
            val tasks = database.openTasks()
            if (tasks.isEmpty()) addEmpty(body, "No tasks yet.") else tasks.forEach { addTaskRow(body, it) }
            addActivity(body, database.recentCompleted())
        }
    }

    private fun render(title: String, selected: String = title, fill: (LinearLayout) -> Unit) {
        musicPlayButton = null
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.map_background))
        }
        content.addView(TextView(this).apply {
            text = "MAP"
            textSize = 14f
            setTextColor(getColor(R.color.map_accent))
            contentDescription = "MAP home"
        })
        content.addView(TextView(this).apply {
            text = title
            textSize = 32f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(8), 0, dp(16))
        })
        fill(content)
        val root = LinearLayout(this).apply {
            setBackgroundColor(getColor(R.color.map_background))
        }
        MapUi.addPrimaryNavigation(
            root,
            ScrollView(this).apply { addView(content) },
            MapUi.bottomNavigation(this, selected, ::navigate),
        )
        MapUi.applySystemBarInsets(root)
        setContentView(root)
    }

    private fun navigate(label: String) {
        when (label) {
            "Home" -> showHome()
            "Calendar" -> startActivity(Intent(this, CalendarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            "Tasks" -> showTasks()
            "Tools" -> startActivity(Intent(this, ToolsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun addTaskSection(parent: LinearLayout, title: String, tasks: List<Task>) {
        addHeading(parent, title)
        if (tasks.isEmpty()) addEmpty(parent, "Nothing here.") else tasks.forEach { addTaskRow(parent, it) }
    }

    private fun addFocusArea(parent: LinearLayout, tasks: List<Task>, startOfToday: Long, startOfTomorrow: Long) {
        val now = System.currentTimeMillis()
        val today = tasks.filter { it.dueAt != null && it.dueAt!! in startOfToday until startOfTomorrow }
        val current = today.filter { task ->
            task.allDay || (task.dueAt!! <= now && task.dueAt!! + HOUR >= now)
        }.minByOrNull { it.dueAt ?: Long.MAX_VALUE }
        val pinned = preferences.getLong(PINNED_FOCUS_ID, -1L).takeIf { it != -1L }?.let { id -> tasks.firstOrNull { it.id == id } }
        if (pinned == null && preferences.contains(PINNED_FOCUS_ID)) preferences.edit().remove(PINNED_FOCUS_ID).apply()
        val focus = pinned ?: current
        val next = tasks.asSequence()
            .filter { it.id != focus?.id && it.dueAt != null && it.dueAt!! >= now }
            .minByOrNull { it.dueAt!! }
        addHeading(parent, "Focus")
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.map_focus_surface)
            addView(focusLine("Now", focus?.let(::focusLabel) ?: "Nothing in progress"))
            addView(focusLine("Next", next?.let(::focusLabel) ?: "Nothing queued"))
            focus?.let { task ->
                addView(Button(this@MainActivity).apply {
                    text = if (pinned == null) "Pin focus" else "Unpin focus"
                    isAllCaps = false
                    contentDescription = text
                    setOnClickListener {
                        if (pinned == null) preferences.edit().putLong(PINNED_FOCUS_ID, task.id).apply()
                        else preferences.edit().remove(PINNED_FOCUS_ID).apply()
                        showHome()
                    }
                })
            }
        })
    }

    private fun focusLine(label: String, value: String) = TextView(this).apply {
        text = "$label  $value"
        textSize = 16f
        setTextColor(getColor(R.color.map_text))
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun focusLabel(task: Task): String = if (task.allDay) task.title else "${task.title} · ${formatDateTime(task.dueAt!!)}"

    private fun addTaskRow(parent: LinearLayout, task: Task) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            contentDescription = "Task: ${task.title}"
            setOnClickListener { showTaskDetails(task) }
        }
        row.addView(CheckBox(this).apply {
            contentDescription = "Complete ${task.title}"
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener { completeTask(task) }
        }, LinearLayout.LayoutParams(dp(48), dp(56)))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(TextView(this@MainActivity).apply {
                text = task.title
                textSize = 17f
                maxLines = 2
                setTextColor(getColor(R.color.map_text))
            })
            val metadata = buildList {
                task.dueAt?.let { add(if (task.allDay) "All day · ${formatDate(it)}" else formatDateTime(it)) }
                if (task.tags.isNotBlank()) add("#${task.tags.replace(',', ' ')}")
            }
            if (metadata.isNotEmpty()) addView(TextView(this@MainActivity).apply {
                text = metadata.joinToString(" · ")
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
        if (selectedView == "Tasks") showTasks() else showHome()
    }

    private fun showTaskDetails(task: Task) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val title = EditText(this).apply {
            setText(task.title)
            contentDescription = "Task title"
            isSingleLine = true
        }
        val notes = EditText(this).apply {
            hint = "Notes (optional)"
            setText(task.notes)
            contentDescription = "Task notes"
        }
        val tags = EditText(this).apply {
            hint = "Tags (optional)"
            setText(task.tags)
            contentDescription = "Task tags"
        }
        fields.addView(title)
        fields.addView(notes)
        fields.addView(tags)
        fields.addView(TextView(this).apply {
            text = task.dueAt?.let { if (task.allDay) "All day · ${formatDate(it)}" else formatDateTime(it) } ?: "No due date"
            textSize = 14f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, dp(8), 0, dp(8))
        })
        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.END
            if (task.dueAt != null) addView(Button(this@MainActivity).apply {
                text = "Snooze"
                isAllCaps = false
                setOnClickListener {
                    ReminderScheduler.cancel(this@MainActivity, task.id)
                    database.snooze(task)?.let {
                        requestNotificationsIfNeeded()
                        ReminderScheduler.schedule(this@MainActivity, task.id, task.title, it)
                    }
                    dialog.dismiss()
                    if (selectedView == "Tasks") showTasks() else showHome()
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "Mark complete"
                isAllCaps = false
                setOnClickListener { dialog.dismiss(); completeTask(task) }
            })
            addView(Button(this@MainActivity, null, 0, R.style.MapPrimaryButton).apply {
                text = "Save"
                isAllCaps = false
                setOnClickListener {
                    val newTitle = title.text.toString().trim()
                    if (newTitle.isEmpty()) {
                        title.error = "Enter a task title"
                        return@setOnClickListener
                    }
                    if (database.updateTask(task, newTitle, notes.text.toString().trim(), tags.text.toString().trim())) {
                        ReminderScheduler.cancel(this@MainActivity, task.id)
                        task.dueAt?.takeIf { it > System.currentTimeMillis() }?.let {
                            ReminderScheduler.schedule(this@MainActivity, task.id, newTitle, it)
                        }
                    }
                    dialog.dismiss()
                    if (selectedView == "Tasks") showTasks() else showHome()
                }
            })
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.map_card))
            addView(TextView(this@MainActivity).apply {
                text = "Task details"
                textSize = 22f
                setTextColor(getColor(R.color.map_text))
                setPadding(dp(24), dp(20), dp(24), dp(4))
            })
            addView(fields)
            addView(actions, LinearLayout.LayoutParams(-1, -2))
        }
        dialog.setContentView(sheet)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(getColor(R.color.map_card)))
            setLayout(-1, -2)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
        }
    }

    private fun addUndoBar(parent: LinearLayout) {
        val state = undoState ?: return
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.map_surface)
            addView(TextView(this@MainActivity).apply {
                text = "Completed ${state.task.title}"
                textSize = 14f
                setTextColor(getColor(R.color.map_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@MainActivity).apply {
                text = "Undo"
                isAllCaps = false
                setOnClickListener {
                    ReminderScheduler.cancel(this@MainActivity, state.nextId ?: state.task.id)
                    if (database.undoComplete(state.task, state.nextId)) {
                        state.task.dueAt?.takeIf { it > System.currentTimeMillis() }?.let {
                            ReminderScheduler.schedule(this@MainActivity, state.task.id, state.task.title, it)
                        }
                    }
                    undoState = null
                    showHome()
                }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun addActivity(parent: LinearLayout, completed: List<Task>) {
        addHeading(parent, "Recent activity")
        if (completed.isEmpty()) addEmpty(parent, "Your recent activity will appear here.")
        completed.forEach { task ->
            parent.addView(TextView(this).apply {
                text = "Completed: ${task.title}"
                textSize = 15f
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, 0, 0, dp(8))
            })
        }
    }

    private fun addMusicMiniPlayer(parent: LinearLayout) {
        val music = MusicDatabase(this)
        val item = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        if (item == null) return
        addHeading(parent, "Music")
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundResource(R.drawable.map_surface)
            contentDescription = "Music: ${item.title}"
            setOnClickListener { startActivity(Intent(this@MainActivity, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = item.title
                    textSize = 15f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.map_text))
                })
                addView(TextView(this@MainActivity).apply {
                    text = item.artist
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.map_muted))
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = Button(this@MainActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    requestNotificationsIfNeeded()
                    val intent = Intent(this@MainActivity, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                }
            }
            musicPlayButton = playButton
            addView(playButton)
        }.also { parent.addView(it) }
    }

    private fun addHeading(parent: LinearLayout, title: String) {
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) }
        })
        parent.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(getColor(R.color.map_text))
            setPadding(0, dp(12), 0, dp(8))
        })
    }

    private fun addEmpty(parent: LinearLayout, message: String) {
        parent.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(4))
        })
    }

    private fun showAddTaskDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val title = EditText(this).apply {
            hint = "Task title"
            contentDescription = "Task title"
        }
        val notes = EditText(this).apply {
            hint = "Notes (optional)"
            contentDescription = "Task notes"
        }
        val tags = EditText(this).apply {
            hint = "Tags (optional)"
            contentDescription = "Task tags"
        }
        val recurrence = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Does not repeat", "Daily", "Weekly", "Monthly")
            )
            contentDescription = "Task recurrence"
        }
        var dueAt: Long? = null
        var allDay = true
        val dueButton = Button(this).apply {
            text = "No due date"
            isAllCaps = false
            setOnClickListener {
                val today = Calendar.getInstance()
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    dueAt = Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    allDay = true
                    text = "%04d-%02d-%02d".format(year, month + 1, day)
                }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val timeButton = Button(this).apply {
            text = "All day"
            isAllCaps = false
            setOnClickListener {
                val selected = dueAt ?: run {
                    Toast.makeText(this@MainActivity, "Choose a date first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (allDay) {
                    val current = Calendar.getInstance().apply { timeInMillis = selected }
                    TimePickerDialog(this@MainActivity, { _, hour, minute ->
                        dueAt = Calendar.getInstance().apply {
                            timeInMillis = selected
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                        }.timeInMillis
                        allDay = false
                        text = "%02d:%02d".format(hour, minute)
                    }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                } else {
                    allDay = true
                    text = "All day"
                    dueAt = Calendar.getInstance().apply { timeInMillis = selected; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }.timeInMillis
                }
            }
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(notes)
            addView(tags)
            addView(dueButton)
            addView(timeButton)
            addView(recurrence)
        }
        val detailsButton = Button(this).apply {
            text = "Add details"
            isAllCaps = false
            setOnClickListener {
                visibility = View.GONE
                details.visibility = View.VISIBLE
            }
        }
        fields.addView(title)
        fields.addView(detailsButton)
        fields.addView(details)

        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.map_card))
            addView(TextView(this@MainActivity).apply {
                text = "New task"
                textSize = 22f
                setTextColor(getColor(R.color.map_text))
                setPadding(dp(24), dp(20), dp(24), dp(4))
            })
            addView(fields)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.END
                addView(Button(this@MainActivity).apply {
                    text = "Cancel"
                    isAllCaps = false
                    setOnClickListener { dialog.dismiss() }
                })
                addView(Button(this@MainActivity, null, 0, R.style.MapPrimaryButton).apply {
                    text = "Save"
                    isAllCaps = false
                    setOnClickListener {
                        val taskTitle = title.text.toString().trim()
                        if (taskTitle.isEmpty()) {
                            title.error = "Enter a task title"
                            return@setOnClickListener
                        }
                        val taskId = database.addTask(
                            taskTitle,
                            notes.text.toString().trim(),
                            dueAt,
                            recurrence.selectedItem.toString(),
                            tags.text.toString().trim(),
                            allDay
                        )
                        dueAt?.let {
                            requestNotificationsIfNeeded()
                            ReminderScheduler.schedule(this@MainActivity, taskId, taskTitle, it)
                        }
                        dialog.dismiss()
                        showHome()
                    }
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        dialog.setContentView(sheet)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(getColor(R.color.map_card)))
            setLayout(-1, -2)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        title.post {
            title.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(title, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun requestNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }
    private fun formatDate(value: Long) = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(value))
    private fun formatDateTime(value: Long) = java.text.SimpleDateFormat("EEE, d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

    companion object {
        const val EXTRA_OPEN_COMPOSER = "open_composer"
        const val EXTRA_OPEN_TASKS = "open_tasks"
        const val EXTRA_OPEN_TASK_ID = "open_task_id"
        private const val HOUR = 60 * 60 * 1000L
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val PINNED_FOCUS_ID = "pinned_focus_id"
        private const val NOTIFICATION_REQUEST = 40
    }

    private data class UndoState(val task: Task, val nextId: Long?)
}
