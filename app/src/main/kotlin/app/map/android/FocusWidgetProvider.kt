package app.map.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home-screen widget showing the task Home's Focus area is currently pointing at. */
class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        private const val WIDGET_REQUEST = 51

        /** Called after any task mutation so placed widgets reflect the change immediately, without waiting for the periodic update. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val database = TaskDatabase(context)
            val selection = try {
                val preferences = context.getSharedPreferences("map-focus", Context.MODE_PRIVATE)
                FocusPicker.select(database.openTasks(), preferences)
            } finally {
                database.close()
            }
            val task = selection.focus ?: selection.next
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            views.setTextViewText(R.id.widget_title, task?.title ?: "Nothing scheduled")
            views.setTextViewText(
                R.id.widget_subtitle,
                task?.let { subtitle(it, isNow = selection.focus != null) } ?: "Add a task to see it here",
            )
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                task?.let { putExtra(MainActivity.EXTRA_OPEN_TASK_ID, it.id) }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                WIDGET_REQUEST,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            return views
        }

        private fun subtitle(task: Task, isNow: Boolean): String {
            val label = if (isNow) "Now" else "Next"
            val time = if (task.allDay) "All day" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(task.dueAt ?: 0))
            return "$label · $time"
        }
    }
}
