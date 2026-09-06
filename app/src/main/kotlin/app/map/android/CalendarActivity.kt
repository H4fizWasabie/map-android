package app.map.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
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
            text = "Your schedule"
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
            addView(actionButton("Add task") {
                startActivity(Intent(this@CalendarActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_COMPOSER, true))
            })
        }.also { body.addView(it) }
        if (weekMode) addWeek(body) else addDay(body)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))
        addView(actionButton("Back") { finish() })
        addView(TextView(this@CalendarActivity).apply {
            text = "Calendar"
            textSize = 18f
            setTextColor(getColor(R.color.map_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
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
        val tasks = database.openTasks().filter { it.dueAt?.let(::dayStart) == selectedDay }.sortedWith(compareByDescending<Task> { it.allDay }.thenBy { it.dueAt })
        addHeading(parent, if (tasks.isEmpty()) "Nothing scheduled" else "Agenda")
        if (tasks.isEmpty()) addEmpty(parent, "Your day is open. Add a task when something needs a place.")
        tasks.forEach { addTaskRow(parent, it) }
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
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(getColor(R.color.map_card))
            contentDescription = "Calendar task: ${task.title}"
        }
        row.addView(TextView(this).apply {
            text = task.title
            textSize = 16f
            setTextColor(getColor(R.color.map_text))
        })
        row.addView(TextView(this).apply {
            text = if (task.allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(task.dueAt ?: 0))
            textSize = 13f
            setTextColor(getColor(R.color.map_accent))
            setPadding(0, dp(3), 0, 0)
        })
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Done") {
                ReminderScheduler.cancel(this@CalendarActivity, task.id)
                val completedAt = System.currentTimeMillis()
                val nextDueAt = database.nextDueAt(task, completedAt)
                val nextId = database.complete(task)
                if (nextId != null && nextDueAt != null) ReminderScheduler.schedule(this@CalendarActivity, nextId, task.title, nextDueAt)
                render()
            })
            if (task.dueAt != null) addView(actionButton("Snooze") {
                database.snooze(task)?.let { ReminderScheduler.schedule(this@CalendarActivity, task.id, task.title, it) }
                render()
            })
        }.also { row.addView(it) }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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

    companion object {
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
