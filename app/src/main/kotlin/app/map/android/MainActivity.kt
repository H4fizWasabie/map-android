package app.map.android

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {
    private lateinit var database: TaskDatabase
    private lateinit var content: LinearLayout
    private var undoState: UndoState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TaskDatabase(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 40)
        }
        showHome()
        if (intent.getBooleanExtra(EXTRA_OPEN_COMPOSER, false)) showAddTaskDialog()
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) showHome()
    }

    private fun showHome() {
        val openTasks = database.openTasks()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfTomorrow = startOfToday + DAY

        render("Today") { body ->
            body.addView(TextView(this).apply {
                text = "A clear view of what needs your attention."
                textSize = 16f
                setTextColor(getColor(R.color.map_muted))
                setPadding(0, 0, 0, dp(16))
            })
            body.addView(Button(this).apply {
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
        render("Tasks") { body ->
            body.addView(Button(this).apply {
                text = "Add a task"
                isAllCaps = false
                setOnClickListener { showAddTaskDialog() }
            })
            val tasks = database.openTasks()
            if (tasks.isEmpty()) addEmpty(body, "No tasks yet.") else tasks.forEach { addTaskRow(body, it) }
            addActivity(body, database.recentCompleted())
        }
    }

    private fun render(title: String, fill: (LinearLayout) -> Unit) {
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
        addNavigation(content, title)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun addNavigation(parent: LinearLayout, selected: String) {
        addHeading(parent, "Navigate")
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("Home", "Calendar", "Tasks", "Tools").forEach { label ->
                addView(Button(this@MainActivity).apply {
                    text = label
                    isEnabled = label != selected
                    setOnClickListener {
                        when (label) {
                            "Home" -> showHome()
                            "Calendar" -> startActivity(Intent(this@MainActivity, CalendarActivity::class.java))
                            "Tasks" -> showTasks()
                            "Tools" -> startActivity(Intent(this@MainActivity, ToolsActivity::class.java))
                        }
                    }
                })
            }
        }.also { parent.addView(it) }
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
        val next = tasks.asSequence()
            .filter { it.dueAt != null && it.dueAt!! >= now }
            .minByOrNull { it.dueAt!! }
        addHeading(parent, "Focus")
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(getColor(R.color.map_card))
            addView(focusLine("Now", current?.let(::focusLabel) ?: "Nothing in progress"))
            addView(focusLine("Next", next?.let(::focusLabel) ?: "Nothing queued"))
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
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(getColor(R.color.map_card))
            contentDescription = "Task: ${task.title}"
        }
        row.addView(TextView(this).apply {
            text = task.title
            textSize = 17f
            setTextColor(getColor(R.color.map_text))
        })
        if (task.notes.isNotBlank()) row.addView(TextView(this).apply {
            text = task.notes
            textSize = 14f
            setTextColor(getColor(R.color.map_muted))
            setPadding(0, dp(4), 0, 0)
        })
        if (task.tags.isNotBlank()) row.addView(TextView(this).apply {
            text = "#${task.tags.replace(',', ' ')}"
            textSize = 13f
            setTextColor(getColor(R.color.map_accent))
            setPadding(0, dp(4), 0, 0)
        })
        task.dueAt?.let { dueAt -> row.addView(TextView(this).apply {
            text = if (task.allDay) "All day · ${formatDate(dueAt)}" else formatDateTime(dueAt)
            textSize = 13f
            setTextColor(getColor(R.color.map_accent))
            setPadding(0, dp(4), 0, 0)
        }) }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@MainActivity).apply {
                text = "Done"
                isAllCaps = false
                setOnClickListener {
                    ReminderScheduler.cancel(this@MainActivity, task.id)
                    val completedAt = System.currentTimeMillis()
                    val nextDueAt = database.nextDueAt(task, completedAt)
                    val nextId = database.complete(task)
                    undoState = UndoState(task, nextId)
                    if (nextId != null && nextDueAt != null) {
                        ReminderScheduler.schedule(this@MainActivity, nextId, task.title, nextDueAt)
                    }
                    showHome()
                }
            })
            if (task.dueAt != null) addView(Button(this@MainActivity).apply {
                text = "Snooze 1 day"
                isAllCaps = false
                setOnClickListener {
                    val dueAt = database.snooze(task)
                    dueAt?.let { ReminderScheduler.schedule(this@MainActivity, task.id, task.title, it) }
                    Toast.makeText(context, "Task snoozed", Toast.LENGTH_SHORT).show()
                    showHome()
                }
            })
        }.also { row.addView(it) }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun addUndoBar(parent: LinearLayout) {
        val state = undoState ?: return
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setBackgroundColor(getColor(R.color.map_card))
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
        val playing = music.playing()
        music.close()
        if (item == null) return
        addHeading(parent, "Music")
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundColor(getColor(R.color.map_card))
            contentDescription = "Music: ${item.title}"
            setOnClickListener { startActivity(Intent(this@MainActivity, MusicActivity::class.java).putExtra(MusicActivity.EXTRA_OPEN_PLAYER, true)) }
            addView(TextView(this@MainActivity).apply {
                text = "${item.title}\n${item.artist}"
                textSize = 15f
                setTextColor(getColor(R.color.map_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@MainActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    val intent = Intent(this@MainActivity, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                }
            })
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
        fields.addView(title)
        fields.addView(notes)
        fields.addView(tags)
        fields.addView(dueButton)
        fields.addView(timeButton)
        fields.addView(recurrence)

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
                addView(Button(this@MainActivity).apply {
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
                        dueAt?.let { ReminderScheduler.schedule(this@MainActivity, taskId, taskTitle, it) }
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
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun formatDate(value: Long) = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(value))
    private fun formatDateTime(value: Long) = java.text.SimpleDateFormat("EEE, d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

    companion object {
        const val EXTRA_OPEN_COMPOSER = "open_composer"
        private const val HOUR = 60 * 60 * 1000L
        private const val DAY = 24 * 60 * 60 * 1000L
    }

    private data class UndoState(val task: Task, val nextId: Long?)
}
