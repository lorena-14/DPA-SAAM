package co.uan.epilepsy.dpa_saam.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import co.uan.epilepsy.dpa_saam.MainActivity
import co.uan.epilepsy.dpa_saam.R
import co.uan.epilepsy.dpa_saam.data.ble.BleConstants
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LocalNotificationManager(
    private val context: Context,
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        createChannels()
    }

    fun showAlertNotification(reading: BraceletReading) {
        try {
            val time = timeFormatter.format(reading.receivedAt)
            val body = "SpO2: ${reading.spo2}% · BPM: ${reading.bpm} · $time"
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_alert_title))
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(mainPendingIntent())
                .build()

            notificationManager.notify(NOTIFICATION_ALERT_ID, notification)
        } catch (_: Exception) {
            // Notificación no debe crashear la app
        }
    }

    fun showLowBatteryNotification(percent: Int) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_BATTERY)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_battery_title))
                .setContentText(
                    context.getString(R.string.notification_battery_body, percent),
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(mainPendingIntent())
                .build()

            notificationManager.notify(NOTIFICATION_BATTERY_ID, notification)
        } catch (_: Exception) {
            // Notificación no debe crashear la app
        }
    }

    fun buildForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_foreground_title))
            .setContentText(context.getString(R.string.notification_foreground_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                CHANNEL_BATTERY,
                context.getString(R.string.channel_battery_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
            NotificationChannel(
                CHANNEL_FOREGROUND,
                context.getString(R.string.channel_foreground_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "alertas_manilla"
        const val CHANNEL_BATTERY = "bateria_manilla"
        const val CHANNEL_FOREGROUND = "conexion_manilla"
        const val NOTIFICATION_ALERT_ID = 1001
        const val NOTIFICATION_BATTERY_ID = 1002
        const val NOTIFICATION_FOREGROUND_ID = 1003
        const val LOW_BATTERY_PREFS = "battery_notifications"
        const val KEY_LOW_BATTERY_NOTIFIED = "low_battery_notified"
    }
}
