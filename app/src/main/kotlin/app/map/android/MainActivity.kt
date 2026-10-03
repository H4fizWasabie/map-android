package app.map.android

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.TextClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * THESIS: A calm private workspace makes the next real action clear.
 * OWN-WORLD: selected Colorist Daybook, Botanical Print, or Woven Poster appearance.
 * STORY: read the current moment, act on tasks, and open local tools.
 * FIRST VIEWPORT: map/date/greeting, live clock and outline dial, focus/tasks,
 * native Add task, Home/Calendar/Tasks/Tools navigation. Empty data stays empty.
 * FORM: Operate; preserve local task truth while matching the selected visual theme.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md.
 */
class MainActivity : MapActivity() {
    private lateinit var database: TaskDatabase
    private lateinit var content: LinearLayout
    private var undoState: UndoState? = null
    private var selectedView = "Home"
    private var taskQuery = ""
    private val backupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var pendingTaskId: Long? = null
    private var contentScroll: ScrollView? = null
    private var restoredScrollY = 0
    private var musicPlayButton: MaterialButton? = null
    private var taskComposer: TaskComposer? = null
    private var initialResumePending = true
    private var homePrimaryActionSettled = false
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
        if (savedInstanceState?.containsKey(STATE_SELECTED_VIEW) == true) {
            selectedView = savedInstanceState.getString(STATE_SELECTED_VIEW, "Home")
            pendingTaskId = savedInstanceState.getLong(STATE_PENDING_TASK_ID, -1L).takeIf { it != -1L }
            restoredScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, 0)
            if (selectedView == "Tasks") showTasks() else showHome()
        } else {
            applyNavigationIntent(intent)
        }
        TaskComposerState.from(savedInstanceState)?.let(::showAddTaskDialog)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent)
    }

    override fun onDestroy() {
        backupExecutor.shutdown()
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_VIEW, selectedView)
        pendingTaskId?.let { outState.putLong(STATE_PENDING_TASK_ID, it) }
        outState.putInt(STATE_SCROLL_Y, contentScroll?.scrollY ?: 0)
        taskComposer?.savedState()?.let { outState.putBundle(TaskComposerState.BUNDLE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, musicStateReceiver, IntentFilter(MusicService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(musicStateReceiver) }
        super.onStop()
    }

    private fun updateStatusBar() {
        val coloristHome = selectedView == "Home" && MapAppearance.selected(this) == MapAppearance.COLORIST_DAYBOOK
        window.statusBarColor = mapColor(if (coloristHome) R.color.map_accent else R.color.map_background)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !coloristHome && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()
        updateStatusBar()
        val refresh = !initialResumePending
        initialResumePending = false
        if (::database.isInitialized) {
            val appContext = applicationContext
            backupExecutor.execute { AutoBackup.runIfDue(appContext) }
            if (refresh) {
                if (selectedView == "Tasks") showTasks() else showHome()
            }
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
        if (intent.action == ACTION_ADD_TASK) {
            intent.action = null
            showAddTaskDialog()
        }
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
        val startOfTomorrow = Calendar.getInstance().apply {
            timeInMillis = startOfToday
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        render(
            "Today",
            "Home",
            header = ::addDaybookHeader,
        ) { body ->
            addUndoBar(body)
            addFocusArea(body, openTasks)
            val inbox = openTasks.filter { it.dueAt == null }
            val overdue = openTasks.filter { it.dueAt != null && it.dueAt!! < startOfToday }
            val today = openTasks.filter { it.dueAt != null && it.dueAt!! in startOfToday until startOfTomorrow }
            val upcoming = openTasks.filter { it.dueAt != null && it.dueAt!! >= startOfTomorrow }
            if (overdue.isNotEmpty()) addTaskSection(body, "Overdue", overdue)
            if (today.isNotEmpty()) addTaskSection(body, "Today", today)
            if (upcoming.isNotEmpty()) addTaskSection(body, "Upcoming", upcoming)
            if (inbox.isNotEmpty()) addTaskSection(body, "Inbox", inbox)
            addMusicMiniPlayer(body)
            val completed = database.recentCompleted()
            if (completed.isNotEmpty()) addActivity(body, completed.take(3))
        }
    }

    private fun addDaybookHeader(body: LinearLayout) {
        val appearance = MapAppearance.selected(this)
        val compact = resources.configuration.screenWidthDp < 368 || resources.configuration.fontScale >= 1.3f
        val landscape = resources.configuration.screenHeightDp < 500
        val artworkWidth = dp(if (compact) 104 else 144)
        val hero = FrameLayout(this).apply {
            setBackgroundColor(mapColor(if (appearance == MapAppearance.COLORIST_DAYBOOK) R.color.map_accent else R.color.map_background))
            contentDescription = null
        }
        hero.addView(android.widget.ImageView(this).apply {
            setImageDrawable(MapUi.illustration(this@MainActivity, appearance.artwork))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = if (appearance == MapAppearance.BOTANICAL_PRINT) 0.68f else 1f
        }, FrameLayout.LayoutParams(artworkWidth, dp(if (landscape) 120 else if (compact) 138 else 220), Gravity.END or Gravity.BOTTOM))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (compact) 12 else 18), dp(if (landscape || compact) 10 else 18), artworkWidth + dp(4), dp(if (landscape || compact) 10 else 18))
        }
        MapUi.addBrandMark(this, details)
        details.addView(TextView(this).apply {
            text = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
            MapUi.label(this)
            letterSpacing = 0.08f
        })
        details.addView(TextView(this).apply {
            text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 0..11 -> "Good morning."
                in 12..17 -> "Good afternoon."
                else -> "Good evening."
            }
            MapUi.display(this)
            setPadding(0, dp(2), 0, dp(8))
            maxLines = 2
        })
        // Stack the dial under the clock when large fonts leave the clock too little width.
        val stacked = resources.configuration.fontScale > 1.3f
        val readout = LinearLayout(this).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL
        }
        readout.addView(TextClock(this).apply {
            format24Hour = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm")
            format12Hour = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "hm")
            MapUi.numeral(this, size = if (compact) 22f else 30f, color = R.color.map_instrument_ink)
            maxLines = 1
        }, if (stacked) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        readout.addView(FieldClockDialView(this), LinearLayout.LayoutParams(dp(if (compact) 48 else 68), dp(if (compact) 48 else 68)))
        details.addView(readout)
        if (appearance == MapAppearance.COLORIST_DAYBOOK) setInstrumentInk(details)
        hero.addView(details, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        body.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun setInstrumentInk(view: View) {
        if (view is TextView) view.setTextColor(mapColor(R.color.map_instrument_ink))
        else if (view is android.view.ViewGroup) for (index in 0 until view.childCount) setInstrumentInk(view.getChildAt(index))
    }

    private fun showTasks() {
        selectedView = "Tasks"
        render("Tasks") { body ->
            val tasks = database.openTasks()
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val tomorrow = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            addTaskOverview(body, tasks, today, tomorrow)
            addUndoBar(body)
            if (tasks.isEmpty()) addEmpty(body, "No tasks yet.") else {
                val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                fun showResults() {
                    results.removeAllViews()
                    val shown = tasks.filter { TaskSearch.matches(it, taskQuery) }
                    if (shown.isEmpty()) addEmpty(results, "No tasks match your search.") else listOf(
                        "Overdue" to shown.filter { it.dueAt != null && it.dueAt < today },
                        "Today" to shown.filter { it.dueAt != null && it.dueAt in today until tomorrow },
                        "Upcoming" to shown.filter { it.dueAt != null && it.dueAt >= tomorrow },
                        "Inbox" to shown.filter { it.dueAt == null },
                    ).filter { it.second.isNotEmpty() }.forEach { (label, items) -> addTaskSection(results, label, items) }
                }
                body.addView(EditText(this).apply {
                    hint = "Search tasks, or #tag"
                    setSingleLine(true)
                    setText(taskQuery)
                    contentDescription = "Search tasks"
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { taskQuery = s?.toString().orEmpty(); showResults() }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                }, LinearLayout.LayoutParams(-1, dp(56)))
                body.addView(results)
                showResults()
            }
            database.recentCompleted().takeIf { it.isNotEmpty() }?.let { addActivity(body, it) }
        }
    }

    private fun addTaskOverview(parent: LinearLayout, tasks: List<Task>, today: Long, tomorrow: Long) {
        val overdue = tasks.count { it.dueAt != null && it.dueAt < today }
        val dueToday = tasks.count { it.dueAt != null && it.dueAt in today until tomorrow }
        parent.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(today))
            MapUi.headline(this)
        })
        parent.addView(TextView(this).apply {
            text = "${tasks.size} open · $dueToday today · $overdue overdue"
            MapUi.metadata(this)
            setPadding(0, dp(6), 0, dp(8))
        })
    }

    private fun defaultHeader(title: String): (LinearLayout) -> Unit = { body ->
        MapUi.addBrandMark(this, body)
        body.addView(TextView(this).apply {
            text = title
            MapUi.display(this)
            setPadding(0, dp(4), 0, dp(18))
        })
    }

    private fun render(
        title: String,
        selected: String = title,
        header: (LinearLayout) -> Unit = defaultHeader(title),
        fill: (LinearLayout) -> Unit,
    ) {
        musicPlayButton = null
        updateStatusBar()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shortHeight = resources.configuration.screenHeightDp < 500
            setPadding(dp(24), dp(if (shortHeight) 0 else 24), dp(24), dp(if (shortHeight) 0 else if (resources.configuration.fontScale >= 1.3f) 24 else 96))
            setBackgroundColor(mapColor(if (selected == "Home" && MapAppearance.selected(this@MainActivity) == MapAppearance.COLORIST_DAYBOOK) R.color.map_accent else R.color.map_background))
        }
        header(content)
        fill(content)
        val root = LinearLayout(this).apply {
            setBackgroundColor(mapColor(if (selected == "Home" && MapAppearance.selected(this@MainActivity) == MapAppearance.COLORIST_DAYBOOK) R.color.map_accent else R.color.map_background))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = selected == "Home" && resources.configuration.screenHeightDp >= 500
            contentScroll = this
            addView(content)
        }
        val action = createAddTaskButton()
        val shortHeight = resources.configuration.screenHeightDp < 500
        val home = selected == "Home"
        val inlineAction = shortHeight || resources.configuration.fontScale >= 1.3f
        val pinnedHomeAction = home && !inlineAction
        if (home) {
            val appearance = MapAppearance.selected(this)
            val accentAction = appearance == MapAppearance.WOVEN_POSTER
            action.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (accentAction) mapColor(R.color.map_accent) else MapAppearance.color(this, appearance.highlight),
            )
            action.setTextColor(mapColor(if (accentAction) R.color.map_on_accent else R.color.map_text))
            val label = SpannableString("  Add task")
            val icon = requireNotNull(getDrawable(R.drawable.ic_map_add)).mutate().apply {
                setTint(action.currentTextColor)
                setBounds(0, 0, dp(20), dp(20))
            }
            label.setSpan(ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            action.text = label
            action.minimumHeight = dp(60)
            if (inlineAction) content.addView(action, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(14) })
            if (resources.configuration.screenHeightDp >= 500) addHomePrivacyStatus(content)
        }
        val screen = FrameLayout(this).apply {
            if (pinnedHomeAction) {
                scroll.setPadding(0, 0, 0, dp(80))
            }
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            if (pinnedHomeAction) addView(action, FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
                bottomMargin = dp(10)
            }) else if (!inlineAction) addView(action, FrameLayout.LayoutParams(-2, dp(56), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(24)
                bottomMargin = dp(16)
            })
        }
        if (inlineAction && !home) content.addView(action, LinearLayout.LayoutParams(-1, if (shortHeight) dp(48) else -2))
        MapUi.addPrimaryNavigation(root, screen, MapUi.bottomNavigation(this, selected, ::navigate))
        if (selected == "Home" && !homePrimaryActionSettled) {
            MapUi.settlePrimaryAction(action)
            homePrimaryActionSettled = true
        }
        MapUi.applySystemBarInsets(root)
        setContentView(root)
        val scrollY = restoredScrollY
        restoredScrollY = 0
        contentScroll?.post { contentScroll?.scrollTo(0, scrollY) }
    }

    private fun addHomePrivacyStatus(parent: LinearLayout) {
        parent.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(2))
            addView(View(this@MainActivity).apply {
                setBackgroundResource(R.drawable.map_status_dot)
            }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(10) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "Private by design"
                    MapUi.body(this)
                    textSize = 14f
                    typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
                    if (selectedView == "Home" && MapAppearance.selected(this@MainActivity) == MapAppearance.COLORIST_DAYBOOK) setTextColor(mapColor(R.color.map_instrument_ink))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Your tasks and files stay on this device."
                    MapUi.metadata(this)
                    if (selectedView == "Home" && MapAppearance.selected(this@MainActivity) == MapAppearance.COLORIST_DAYBOOK) setTextColor(mapColor(R.color.map_instrument_muted))
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
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
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundResource(R.drawable.map_surface)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    MapUi.section(this)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = tasks.size.toString()
                    MapUi.numeral(this, size = 15f, color = R.color.map_muted)
                })
            })
            if (tasks.isEmpty()) addEmpty(this, "Nothing here.") else tasks.forEachIndexed { index, task ->
                if (index > 0) addView(View(this@MainActivity).apply {
                    setBackgroundColor(mapColor(R.color.map_divider))
                }, LinearLayout.LayoutParams(-1, dp(1)))
                addTaskRow(this, task, drawDivider = false)
            }
        })
    }

    private fun addSectionRule(parent: LinearLayout, title: String, count: Int? = null) {
        val shortHeight = resources.configuration.screenHeightDp < 500
        if (!shortHeight) parent.addView(View(this).apply {
            setBackgroundColor(mapColor(R.color.map_divider))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) })
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(if (shortHeight) 2 else 10), 0, dp(if (shortHeight) 2 else 8))
            addView(TextView(this@MainActivity).apply {
                text = title
                MapUi.section(this)
                if (selectedView == "Home" && MapAppearance.selected(this@MainActivity) == MapAppearance.COLORIST_DAYBOOK) setTextColor(mapColor(R.color.map_instrument_ink))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (count != null) {
                addView(TextView(this@MainActivity).apply {
                    text = count.toString()
                    MapUi.numeral(this, size = 15f, color = R.color.map_muted)
                })
            }
        })
    }

    private fun createAddTaskButton(): MaterialButton = (if (resources.configuration.screenHeightDp < 500 || resources.configuration.fontScale >= 1.3f) {
        MapUi.primaryButton(this)
    } else {
        ExtendedFloatingActionButton(this).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(mapColor(R.color.map_accent))
            setTextColor(mapColor(R.color.map_on_accent))
        }
    }).apply {
        val addLabel = SpannableString("  Add task")
        val addIcon = requireNotNull(getDrawable(R.drawable.ic_map_add)).mutate().apply {
            setTint(mapColor(R.color.map_on_accent))
            setBounds(0, 0, dp(20), dp(20))
        }
        addLabel.setSpan(ImageSpan(addIcon, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = addLabel
        MapUi.body(this)
        setTextColor(mapColor(R.color.map_on_accent))
        isAllCaps = false
        minimumHeight = dp(56)
        contentDescription = "Add task"
        setOnClickListener { showAddTaskDialog() }
    }

    private fun addFocusArea(parent: LinearLayout, tasks: List<Task>) {
        val selection = FocusPicker.select(tasks, preferences)
        val actionTask = selection.focus ?: selection.next
        val compact = resources.configuration.screenWidthDp < 368 || resources.configuration.fontScale >= 1.25f
        val shortHeight = resources.configuration.screenHeightDp < 500
        addSectionRule(parent, "Now & next")
        parent.addView(LinearLayout(this).apply {
            orientation = if (shortHeight && actionTask == null) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (shortHeight && actionTask == null) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            setPadding(dp(16), dp(if (shortHeight) 4 else 14), dp(16), dp(if (shortHeight) 4 else 14))
            setBackgroundResource(R.drawable.map_surface)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
                bottomMargin = dp(if (shortHeight) 4 else 8)
            }
            actionTask?.let { task ->
                contentDescription = "Open focus task ${task.title}"
                isFocusable = true
                setOnClickListener { showTaskDetails(task) }
            }
            if (actionTask == null) {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = if (shortHeight) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    gravity = if (shortHeight) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
                    addView(TextView(this@MainActivity).apply {
                        text = if (tasks.isEmpty()) "Your day is open." else "No focus task selected."
                        MapUi.headline(this)
                        setPadding(if (shortHeight) dp(12) else 0, if (shortHeight) 0 else dp(3), 0, 0)
                    }, if (shortHeight) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-2, -2))
                })
            } else addView(LinearLayout(this@MainActivity).apply {
                orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                val now = focusBlock("Now", selection.focus, "Nothing in progress")
                val next = focusBlock("Next", selection.next, "Nothing queued")
                val divider = View(this@MainActivity).apply {
                    setBackgroundColor(mapColor(R.color.map_divider))
                }
                if (compact) {
                    addView(now)
                    addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
                        topMargin = dp(8)
                        bottomMargin = dp(8)
                    })
                    addView(next)
                } else {
                    addView(now, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(divider, LinearLayout.LayoutParams(dp(1), -1).apply {
                        marginStart = dp(12)
                        marginEnd = dp(12)
                    })
                    addView(next, LinearLayout.LayoutParams(0, -2, 1f))
                }
            })
            selection.focus?.let { task ->
                addView(MapUi.button(this@MainActivity).apply {
                    text = if (!selection.pinned) "Pin focus" else "Unpin focus"
                    isAllCaps = false
                    contentDescription = "$text task ${task.title}"
                    setOnClickListener {
                        FocusPicker.setPinned(preferences, if (!selection.pinned) task.id else null)
                        showHome()
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
            }
        })
    }

    private fun focusBlock(label: String, task: Task?, empty: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            MapUi.label(this)
            setTextColor(mapColor(R.color.map_muted))
        })
        addView(TextView(this@MainActivity).apply {
            text = task?.title ?: empty
            MapUi.body(this)
            setPadding(0, dp(3), 0, 0)
            task?.let {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                contentDescription = "$label, ${it.title}${it.dueAt?.let { due -> ", ${formatDateTime(due)}" }.orEmpty()}"
            }
        })
        task?.dueAt?.takeIf { !task.allDay }?.let { due ->
            addView(TextView(this@MainActivity).apply {
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(due))
                MapUi.metadata(this)
            })
        }
    }

    private fun addTaskRow(parent: LinearLayout, task: Task, drawDivider: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            contentDescription = "Task: ${task.title}"
            setOnClickListener { showTaskDetails(task) }
        }
        row.addView(CheckBox(this).apply {
            MapUi.tintCheckbox(this)
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
                MapUi.taskTitle(this)
                maxLines = 2
            })
            val metadata = buildList {
                task.dueAt?.let { add(if (task.allDay) "All day · ${formatDate(it)}" else formatDateTime(it)) }
                if (task.tags.isNotBlank()) add("#${task.tags.replace(',', ' ')}")
            }
            if (metadata.isNotEmpty()) addView(TextView(this@MainActivity).apply {
                text = metadata.joinToString(" · ")
                MapUi.metadata(this)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        if (drawDivider) parent.addView(View(this).apply {
            setBackgroundColor(mapColor(R.color.map_divider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        })
    }

    private fun completeTask(task: Task) {
        undoState = TaskActions.complete(this, database, task)
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
        var dueAt = task.dueAt
        var allDay = task.allDay
        val dueButton = MapUi.button(this).apply {
            text = dueAt?.let(::formatDate) ?: "No due date"
            isAllCaps = false
            setOnClickListener {
                val today = Calendar.getInstance().apply { dueAt?.let { timeInMillis = it } }
                val keepAllDay = allDay
                val keepHour = today.get(Calendar.HOUR_OF_DAY)
                val keepMinute = today.get(Calendar.MINUTE)
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    dueAt = Calendar.getInstance().apply {
                        set(year, month, day, if (keepAllDay) 12 else keepHour, if (keepAllDay) 0 else keepMinute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    text = formatDate(dueAt!!)
                }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val timeButton = MapUi.button(this).apply {
            text = if (dueAt == null || allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueAt!!))
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
                    dueAt = Calendar.getInstance().apply {
                        timeInMillis = selected
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                    }.timeInMillis
                }
            }
        }
        val recurrenceValues = listOf("Does not repeat", "Daily", "Weekly", "Monthly")
        val recurrence = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, recurrenceValues)
            setSelection(recurrenceValues.indexOf(task.recurrence).coerceAtLeast(0))
            contentDescription = "Task recurrence"
        }
        fields.addView(dueButton)
        fields.addView(timeButton)
        fields.addView(recurrence)
        val dialog = Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val stackTaskActions = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale >= 1.3f
        val taskActionParams = {
            LinearLayout.LayoutParams(if (stackTaskActions) -1 else -2, -2).apply {
                if (stackTaskActions) topMargin = dp(4)
            }
        }
        val actionButtons = LinearLayout(this).apply {
            orientation = if (stackTaskActions) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (stackTaskActions) Gravity.CENTER_HORIZONTAL else Gravity.END
            if (task.dueAt != null) addView(MapUi.button(this@MainActivity).apply {
                text = "Snooze"
                isAllCaps = false
                minHeight = dp(48)
                setOnClickListener {
                    ReminderScheduler.cancel(this@MainActivity, task.id)
                    database.snooze(task)?.let {
                        requestNotificationsIfNeeded()
                        ReminderScheduler.schedule(this@MainActivity, task.id, task.title, it)
                    }
                    dialog.dismiss()
                    if (selectedView == "Tasks") showTasks() else showHome()
                }
            }, taskActionParams())
            addView(MapUi.button(this@MainActivity).apply {
                text = "Mark complete"
                isAllCaps = false
                minHeight = dp(48)
                setOnClickListener { dialog.dismiss(); completeTask(task) }
            }, taskActionParams())
            addView(MapUi.primaryButton(this@MainActivity).apply {
                text = "Save"
                isAllCaps = false
                minHeight = dp(48)
                setOnClickListener {
                    val newTitle = title.text.toString().trim()
                    if (newTitle.isEmpty()) {
                        title.error = "Enter a task title"
                        return@setOnClickListener
                    }
                    if (database.updateTask(task, newTitle, notes.text.toString().trim(), tags.text.toString().trim(), dueAt, recurrence.selectedItem.toString(), allDay)) {
                        ReminderScheduler.cancel(this@MainActivity, task.id)
                        dueAt?.takeIf { it > System.currentTimeMillis() }?.let {
                            requestNotificationsIfNeeded()
                            ReminderScheduler.schedule(this@MainActivity, task.id, newTitle, it)
                        }
                    }
                    dialog.dismiss()
                    if (selectedView == "Tasks") showTasks() else showHome()
                }
            }, taskActionParams())
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(12))
            addView(MapUi.button(this@MainActivity).apply {
                text = "Delete task"
                contentDescription = "Delete task"
                isAllCaps = false
                setTextColor(mapColor(R.color.map_accent))
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Delete task?")
                        .setMessage("Delete “${task.title}”? This will also cancel its reminder.")
                        .setNegativeButton("Keep task", null)
                        .setPositiveButton("Delete") { _, _ ->
                            if (TaskActions.delete(this@MainActivity, database, task)) {
                                dialog.dismiss()
                                if (selectedView == "Tasks") showTasks() else showHome()
                            } else {
                                Toast.makeText(this@MainActivity, "Task is no longer available", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
            addView(actionButtons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(mapColor(R.color.map_card))
            addView(TextView(this@MainActivity).apply {
                text = "Task details"
                MapUi.headline(this)
                setPadding(dp(24), dp(20), dp(24), dp(4))
            })
            addView(ScrollView(this@MainActivity).apply {
                addView(fields)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(actions, LinearLayout.LayoutParams(-1, -2))
        }
        dialog.setContentView(sheet)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(mapColor(R.color.map_card)))
            setLayout(-1, -2)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
        }
        if (resources.configuration.fontScale >= 1.3f) fitLargeDialog(dialog)
    }

    private fun addUndoBar(parent: LinearLayout) =
        MapUi.addUndoBar(this, parent, database, undoState) { undoState = null; showHome() }

    private fun addActivity(parent: LinearLayout, completed: List<Task>) {
        addSectionRule(parent, "Recent activity")
        completed.forEach { task ->
            parent.addView(TextView(this).apply {
                text = "Completed: ${task.title}"
                MapUi.metadata(this)
                setPadding(0, 0, 0, dp(8))
            })
        }
    }

    private fun addMusicMiniPlayer(parent: LinearLayout) {
        val music = MusicDatabase(this)
        if (!MusicService.isRunning && music.playing()) {
            music.close()
            return
        }
        val item = music.track(music.current())
        val playing = MusicService.isRunning && music.playing()
        music.close()
        if (item == null) return
        addSectionRule(parent, "Music")
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
                    MapUi.body(this)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@MainActivity).apply {
                    text = item.artist
                    MapUi.metadata(this)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val playButton = MapUi.button(this@MainActivity).apply {
                text = if (playing) "Pause" else "Play"
                isAllCaps = false
                contentDescription = text
                setOnClickListener {
                    val action = if (text == "Pause") MusicService.ACTION_PAUSE else MusicService.ACTION_RESUME
                    val intent = Intent(this@MainActivity, MusicService::class.java).setAction(action)
                    if (!startMapMusicService(this@MainActivity, intent)) {
                        Toast.makeText(this@MainActivity, "MAP could not start Music. Try Play again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            musicPlayButton = playButton
            addView(playButton)
        }.also { parent.addView(it) }
    }

    private fun addEmpty(parent: LinearLayout, message: String) {
        parent.addView(TextView(this).apply {
            text = message
            MapUi.body(this)
            setTextColor(mapColor(R.color.map_muted))
            setPadding(0, 0, 0, dp(4))
        })
    }

    private fun showAddTaskDialog(state: TaskComposerState = TaskComposerState()) {
        taskComposer = TaskComposer(
            activity = this,
            database = database,
            notificationRequest = NOTIFICATION_REQUEST,
            onSaved = ::showHome,
            onDismiss = { taskComposer = null },
        ).also { it.show(state) }
    }

    private fun dp(value: Int): Int = MapUi.dp(this, value)

    private fun fitLargeDialog(dialog: Dialog) {
        dialog.window?.let { window ->
            window.decorView.post {
                val statusBarHeight = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))
                val navigationBarHeight = resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height", "dimen", "android"))
                window.attributes = window.attributes.apply { y = navigationBarHeight }
                window.setLayout(-1, resources.displayMetrics.heightPixels - statusBarHeight - navigationBarHeight)
            }
        }
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

    private fun requestNotificationsIfNeeded() = MapUi.requestNotificationsIfNeeded(this, NOTIFICATION_REQUEST)
    private fun formatDate(value: Long) = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(value))
    private fun formatDateTime(value: Long) = java.text.SimpleDateFormat("EEE, d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

    companion object {
        const val ACTION_ADD_TASK = "app.map.android.action.ADD_TASK"
        const val EXTRA_OPEN_TASKS = "open_tasks"
        const val EXTRA_OPEN_TASK_ID = "open_task_id"
        private const val NOTIFICATION_REQUEST = 40
        private const val STATE_SELECTED_VIEW = "selected_view"
        private const val STATE_PENDING_TASK_ID = "pending_task_id"
        private const val STATE_SCROLL_Y = "scroll_y"
    }
}
