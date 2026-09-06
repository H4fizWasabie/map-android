package app.map.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "map_tasks"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "MAP Tasks", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "Task reminder" }
        context.getSystemService(NotificationManager::class.java).notify(
            title.hashCode(),
            android.app.Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MAP reminder")
                .setContentText(title)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }
}
