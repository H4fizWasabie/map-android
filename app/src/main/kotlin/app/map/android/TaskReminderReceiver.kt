package app.map.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESTORE_ACTIONS) {
            restoreReminders(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val channelId = "map_tasks"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "MAP Tasks", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("map://task/$taskId")
                putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "Task reminder" }
        context.getSystemService(NotificationManager::class.java).notify(
            taskId.hashCode(),
            android.app.Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setColor(context.resources.getColor(MapAppearance.selected(context).accent, context.theme))
                .setContentTitle("MAP reminder")
                .setContentText(title)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun restoreReminders(context: Context) {
        val now = System.currentTimeMillis()
        val database = TaskDatabase(context)
        try {
            reminderTargets(database.openTasks(), now).forEach { (task, dueAt) ->
                ReminderScheduler.schedule(context, task.id, task.title, dueAt)
            }
        } finally {
            database.close()
        }
    }

    companion object {
        // Alarms are lost on reboot and app update, and stale after a clock or timezone change.
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )

        fun reminderTargets(tasks: List<Task>, now: Long): List<Pair<Task, Long>> =
            tasks.mapNotNull { task -> task.dueAt?.takeIf { it > now }?.let { task to it } }
    }
}
