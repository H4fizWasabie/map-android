package app.map.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object ReminderScheduler {
    fun schedule(context: Context, taskId: Long, title: String, atMillis: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            atMillis,
            pendingIntent(context, taskId, title)
        )
    }

    fun cancel(context: Context, taskId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, taskId, ""))
    }

    private fun pendingIntent(context: Context, taskId: Long, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TaskReminderReceiver::class.java)
                .setData(Uri.parse("map://task-reminder/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TITLE, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "task_title"
}
