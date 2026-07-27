package co.uan.epilepsy.dpa_saam.data.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

class BraceletForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val notificationManager = LocalNotificationManager(this)
            val notification = notificationManager.buildForegroundNotification()
            startForeground(
                LocalNotificationManager.NOTIFICATION_FOREGROUND_ID,
                notification,
            )
            START_STICKY
        } catch (_: Exception) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignorar
        }
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, BraceletForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Servicio opcional: no crashear
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BraceletForegroundService::class.java))
            } catch (_: Exception) {
                // Ignorar
            }
        }
    }
}
