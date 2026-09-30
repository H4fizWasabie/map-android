package app.map.android

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class TaskComposerState(
    val title: String = "",
    val notes: String = "",
    val tags: String = "",
    val dueAt: Long? = null,
    val recurrence: String = "Does not repeat",
    val allDay: Boolean = true,
    val detailsVisible: Boolean = false,
) {
    fun toBundle() = Bundle().apply {
        putString(KEY_TITLE, title)
        putString(KEY_NOTES, notes)
        putString(KEY_TAGS, tags)
        dueAt?.let { putLong(KEY_DUE_AT, it) }
        putString(KEY_RECURRENCE, recurrence)
        putBoolean(KEY_ALL_DAY, allDay)
        putBoolean(KEY_DETAILS_VISIBLE, detailsVisible)
    }

    companion object {
        const val BUNDLE_KEY = "task_composer_state"
        private const val KEY_TITLE = "title"
        private const val KEY_NOTES = "notes"
        private const val KEY_TAGS = "tags"
        private const val KEY_DUE_AT = "due_at"
        private const val KEY_RECURRENCE = "recurrence"
        private const val KEY_ALL_DAY = "all_day"
        private const val KEY_DETAILS_VISIBLE = "details_visible"

        fun from(savedInstanceState: Bundle?): TaskComposerState? {
            val state = savedInstanceState?.getBundle(BUNDLE_KEY) ?: return null
            return TaskComposerState(
                title = state.getString(KEY_TITLE).orEmpty(),
                notes = state.getString(KEY_NOTES).orEmpty(),
                tags = state.getString(KEY_TAGS).orEmpty(),
                dueAt = state.getLong(KEY_DUE_AT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
                recurrence = state.getString(KEY_RECURRENCE) ?: "Does not repeat",
                allDay = state.getBoolean(KEY_ALL_DAY, true),
                detailsVisible = state.getBoolean(KEY_DETAILS_VISIBLE, false),
            )
        }
    }
}

internal class TaskComposer(
    private val activity: Activity,
    private val database: TaskDatabase,
    private val notificationRequest: Int,
    private val onSaved: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var dialog: Dialog? = null
    private var title: EditText? = null
    private var notes: EditText? = null
    private var tags: EditText? = null
    private var recurrence: Spinner? = null
    private var dueAt: Long? = null
    private var allDay = true
    private var detailsVisible = false

    fun savedState(): Bundle? {
        if (dialog?.isShowing != true) return null
        return TaskComposerState(
            title = title?.text?.toString().orEmpty(),
            notes = notes?.text?.toString().orEmpty(),
            tags = tags?.text?.toString().orEmpty(),
            dueAt = dueAt,
            recurrence = recurrence?.selectedItem?.toString() ?: "Does not repeat",
            allDay = allDay,
            detailsVisible = detailsVisible,
        ).toBundle()
    }

    fun show(state: TaskComposerState = TaskComposerState()) {
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val titleInput = EditText(activity).apply {
            hint = "Task title"
            setText(state.title)
            contentDescription = "Task title"
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        val notesInput = EditText(activity).apply {
            hint = "Notes (optional)"
            setText(state.notes)
            contentDescription = "Task notes"
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        val tagsInput = EditText(activity).apply {
            hint = "Tags (optional)"
            setText(state.tags)
            contentDescription = "Task tags"
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        title = titleInput
        notes = notesInput
        tags = tagsInput
        val recurrenceValues = listOf("Does not repeat", "Daily", "Weekly", "Monthly")
        val recurrenceSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, recurrenceValues)
            setSelection(recurrenceValues.indexOf(state.recurrence).coerceAtLeast(0))
            contentDescription = "Task recurrence"
        }
        recurrence = recurrenceSpinner
        dueAt = state.dueAt
        allDay = state.allDay
        detailsVisible = state.detailsVisible

        val timeButton = MapUi.button(activity)
        val dueButton = MapUi.button(activity).apply {
            text = dueAt?.let(::formatDate) ?: "No due date"
            isAllCaps = false
            setOnClickListener {
                val initial = Calendar.getInstance().apply { timeInMillis = dueAt ?: System.currentTimeMillis() }
                DatePickerDialog(activity, { _, year, month, day ->
                    dueAt = Calendar.getInstance().apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    allDay = true
                    timeButton.text = "All day"
                    text = formatDate(dueAt!!)
                }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        timeButton.apply {
            text = if (dueAt == null || allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueAt!!))
            isAllCaps = false
            setOnClickListener {
                val selected = dueAt ?: run {
                    Toast.makeText(activity, "Choose a date first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (allDay) {
                    val current = Calendar.getInstance().apply { timeInMillis = selected }
                    TimePickerDialog(activity, { _, hour, minute ->
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
                    dueAt = Calendar.getInstance().apply {
                        timeInMillis = selected
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                    }.timeInMillis
                }
            }
        }
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (detailsVisible) View.VISIBLE else View.GONE
            addView(notesInput)
            addView(tagsInput)
            addView(dueButton)
            addView(timeButton)
            addView(recurrenceSpinner)
        }
        val composerDialog = Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val detailsButton = MapUi.button(activity).apply {
            text = "Add details"
            isAllCaps = false
            visibility = if (detailsVisible) View.GONE else View.VISIBLE
            setOnClickListener {
                visibility = View.GONE
                details.visibility = View.VISIBLE
                detailsVisible = true
                if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && activity.resources.configuration.fontScale >= 1.5f) fitLargeDialog(composerDialog)
            }
        }
        fields.addView(titleInput)
        fields.addView(detailsButton)
        fields.addView(details)

        val sheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(activity.getColor(R.color.map_card))
            addView(TextView(activity).apply {
                text = "New task"
                MapUi.headline(this)
                setPadding(dp(24), dp(20), dp(24), dp(4))
            })
            addView(ScrollView(activity).apply {
                addView(fields)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(LinearLayout(activity).apply {
                gravity = Gravity.END
                setPadding(dp(24), dp(8), dp(24), dp(8))
                addView(MapUi.button(activity).apply {
                    text = "Cancel"
                    isAllCaps = false
                    setOnClickListener { composerDialog.dismiss() }
                })
                addView(MapUi.primaryButton(activity).apply {
                    text = "Save"
                    isAllCaps = false
                    setOnClickListener {
                        val taskTitle = titleInput.text.toString().trim()
                        if (taskTitle.isEmpty()) {
                            titleInput.error = "Enter a task title"
                            return@setOnClickListener
                        }
                        val taskId = database.addTask(
                            taskTitle,
                            notesInput.text.toString().trim(),
                            dueAt,
                            recurrenceSpinner.selectedItem?.toString() ?: "Does not repeat",
                            tagsInput.text.toString().trim(),
                            allDay,
                        )
                        dueAt?.let {
                            MapUi.requestNotificationsIfNeeded(activity, notificationRequest)
                            ReminderScheduler.schedule(activity, taskId, taskTitle, it)
                        }
                        composerDialog.dismiss()
                        onSaved()
                    }
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        dialog = composerDialog
        composerDialog.setOnDismissListener { onDismiss() }
        composerDialog.setContentView(sheet)
        composerDialog.show()
        composerDialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(activity.getColor(R.color.map_card)))
            setLayout(-1, -2)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            fitLargeDialog(composerDialog)
        }
        titleInput.post {
            titleInput.requestFocus()
            (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(titleInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dp(value: Int): Int = MapUi.dp(activity, value)

    private fun fitLargeDialog(dialog: Dialog) {
        dialog.window?.let { window ->
            window.decorView.post {
                val statusBarHeight = activity.resources.getDimensionPixelSize(activity.resources.getIdentifier("status_bar_height", "dimen", "android"))
                val navigationBarHeight = activity.resources.getDimensionPixelSize(activity.resources.getIdentifier("navigation_bar_height", "dimen", "android"))
                window.attributes = window.attributes.apply { y = navigationBarHeight }
                window.setLayout(-1, activity.resources.displayMetrics.heightPixels - statusBarHeight - navigationBarHeight)
            }
        }
    }

    private fun formatDate(value: Long) = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(value))
}
