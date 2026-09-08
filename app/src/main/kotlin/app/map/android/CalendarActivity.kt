package app.map.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarActivity : Activity() {
    private lateinit var database: TaskDatabase
    private var selectedDay = dayStart(System.currentTimeMillis())
    private var weekMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TaskDatabase(this)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::database.isInitialized) render()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.map_background))
        }
        root.addView(header())
        val scroll = ScrollView(this)
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
        if (weekMode) addWeek(body) else addDay(body)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(MapUi.bottomNavigation(this, "Calendar", ::navigate))
        setContentView(root)
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
            setOnClickListener {
                startActivity(Intent(this@CalendarActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply {
                    putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                    putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
                })
            }
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
        if (nextId != null && nextDueAt != null) ReminderScheduler.schedule(this, nextId, task.title, nextDueAt)
        render()
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
}
