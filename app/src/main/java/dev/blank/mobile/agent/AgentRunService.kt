package dev.blank.mobile.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.blank.mobile.MainActivity
import dev.blank.mobile.R

class AgentRunService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCallback?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, getString(R.string.channel_run), NotificationManager.IMPORTANCE_LOW),
        )
        val content = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AgentRunService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(
            ID_RUN,
            NotificationCompat.Builder(this, CHANNEL_RUN)
                .setSmallIcon(R.drawable.ic_mark)
                .setContentTitle(getString(R.string.notification_running_title))
                .setContentText(getString(R.string.notification_running_text))
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(0, getString(R.string.stop), stop)
                .build(),
        )
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_STOP = "dev.blank.mobile.action.STOP_AGENT"
        private const val CHANNEL_RUN = "agent_run"
        private const val CHANNEL_ATTENTION = "agent_attention"
        private const val ID_RUN = 1
        private const val ID_ATTENTION = 2

        var stopCallback: (() -> Unit)? = null
        var pendingAttention: Pair<Int, Int>? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AgentRunService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentRunService::class.java))
        }

        fun notifyAttention(context: Context, title: Int, text: Int) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ATTENTION, context.getString(R.string.channel_attention), NotificationManager.IMPORTANCE_HIGH),
            )
            val content = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
            nm.notify(
                ID_ATTENTION,
                NotificationCompat.Builder(context, CHANNEL_ATTENTION)
                    .setSmallIcon(R.drawable.ic_mark)
                    .setContentTitle(context.getString(title))
                    .setContentText(context.getString(text))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .build(),
            )
        }

        fun cancelAttention(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ID_ATTENTION)
        }
    }
}