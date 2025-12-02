package com.example.notilogger

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

// --- Constants for Foreground Service ---
private const val NOTIFICATION_CHANNEL_ID = "NotiLoggerServiceChannel"
private const val NOTIFICATION_ID = 101 // Unique ID for the persistent notification

// Key used for SharedPreferences storage
const val PREF_KEY_NOTIFICATIONS = "notification_log_key"

class NotificationLogService : NotificationListenerService() {

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        // 1. Create the notification channel (required for Android O and above)
        createNotificationChannel()

        // 2. Build the persistent notification
        val notification = buildForegroundNotification()

        // 3. Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Notification Logger Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Notification Logger Active")
            .setContentText("Listening for incoming notifications in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    data class InstalledApp(
        val label: String,
        val packageName: String
    )

    @SuppressLint("QueryPermissionsNeeded")
    fun getInstalledApps(): List<InstalledApp> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)

        return apps.map { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().trim()
                .takeIf { !it.isNullOrEmpty() } ?: appInfo.packageName

            InstalledApp(label, appInfo.packageName)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            getInstalledApps().find { it.packageName == packageName }?.label.toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun extractFullContent(extras: Bundle): String {
        val parts = mutableListOf<String>()

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let {
            parts.add(it.toString())
        }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { arr ->
            parts.addAll(arr.map { it.toString() })
        }

        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.let { arr ->
            arr.forEach { obj ->
                try {
                    val b = obj as Bundle
                    val text = b.getString("text")
                    if (!text.isNullOrBlank()) parts.add(text)
                } catch (_: Exception) {}
            }
        }

        return parts.joinToString("\n").ifBlank { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "can't read context" }
    }


    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        sbn?.let { notification ->

            // 🚫 Skip ongoing / non-removable notifications
            if (!notification.isClearable) {
                return
            }

            val extras = notification.notification.extras
            val packageName = notification.packageName
            val postTime = notification.postTime

            // 1. Extract Data
            val appName = getAppName(packageName)
            val senderTitle = extras.getString(Notification.EXTRA_TITLE) ?: "System Notification"
            val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val fullContent = extractFullContent(extras)

            val newEntry = NotificationEntry(
                appName = appName,
                senderTitle = senderTitle,
                content = content,
                fullContent = fullContent,
                timestamp = postTime
            )

            // 2. Save Data
            saveNotification(newEntry)

            // Optionally send a broadcast to update the UI if MainActivity is running
            sendBroadcast(Intent("com.example.notilogger.NEW_NOTIFICATION_EVENT"))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    private fun saveNotification(newEntry: NotificationEntry) {
        val prefs = getSharedPreferences(PREF_KEY_NOTIFICATIONS, MODE_PRIVATE)
        val gson = Gson()

        // 1. Load existing list
        val json = prefs.getString(PREF_KEY_NOTIFICATIONS, null)
        val type = object : TypeToken<MutableList<NotificationEntry>>() {}.type
        val logList: MutableList<NotificationEntry> = if (json != null) {
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }

        // 2. Add new entry at the start (most recent first)
        logList.add(0, newEntry)

        // Limit the list size to prevent excessive storage (e.g., 500 entries)
        while (logList.size > 500) {
            logList.removeAt(logList.size - 1)
        }

        // 3. Save the updated list
        val updatedJson = gson.toJson(logList)
        prefs.edit { putString(PREF_KEY_NOTIFICATIONS, updatedJson) }
    }
}