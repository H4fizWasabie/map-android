package app.map.android

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TaskDatabase(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 40)
        }
        showHome()
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
                setOnClickListener { showAddTaskDialog() }
            })
            addTaskSection(body, "Inbox", openTasks.filter { it.dueAt == null })
            addTaskSection(body, "Overdue", openTasks.filter { it.dueAt != null && it.dueAt!! < startOfToday })
            addTaskSection(body, "Today", openTasks.filter { it.dueAt != null && it.dueAt!! in startOfToday until startOfTomorrow })
            addTaskSection(body, "Upcoming", openTasks.filter { it.dueAt != null && it.dueAt!! >= startOfTomorrow })
            addActivity(body, database.recentCompleted())
            addMusicMiniPlayer(body)
        }
    }

    private fun showTasks() {
        render("Tasks") { body ->
            body.addView(Button(this).apply {
                text = "Add a task"
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
            listOf("Home", "Tasks", "Scan", "Music").forEach { label ->
                addView(Button(this@MainActivity).apply {
                    text = label
                    isEnabled = label != selected
                    setOnClickListener {
                        when (label) {
                            "Home" -> showHome()
                            "Tasks" -> showTasks()
                            "Scan" -> startActivity(Intent(this@MainActivity, ScanActivity::class.java))
                            "Music" -> startActivity(Intent(this@MainActivity, MusicActivity::class.java))
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
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@MainActivity).apply {
                text = "Done"
                setOnClickListener {
                    ReminderScheduler.cancel(this@MainActivity, task.id)
                    val completedAt = System.currentTimeMillis()
                    val nextDueAt = database.nextDueAt(task, completedAt)
                    val nextId = database.complete(task)
                    if (nextId != null && nextDueAt != null) {
                        ReminderScheduler.schedule(this@MainActivity, nextId, task.title, nextDueAt)
                    }
                    showHome()
                }
            })
            if (task.dueAt != null) addView(Button(this@MainActivity).apply {
                text = "Snooze 1 day"
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
        val dueButton = Button(this).apply {
            text = "No due date"
            setOnClickListener {
                val today = Calendar.getInstance()
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    dueAt = Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    text = "%04d-%02d-%02d".format(year, month + 1, day)
                }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        fields.addView(title)
        fields.addView(notes)
        fields.addView(tags)
        fields.addView(dueButton)
        fields.addView(recurrence)

        AlertDialog.Builder(this)
            .setTitle("New task")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                            tags.text.toString().trim()
                        )
                        dueAt?.let { ReminderScheduler.schedule(this@MainActivity, taskId, taskTitle, it) }
                        dialog.dismiss()
                        showHome()
                    }
                }
                dialog.show()
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
