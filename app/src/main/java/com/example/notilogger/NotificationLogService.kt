package com.example.notilogger

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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

    private val TAG = "NotiLoggerService"

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        // 1. Create the notification channel (required for Android O and above)
        createNotificationChannel()

        // 2. Build the persistent notification
        val notification = buildForegroundNotification()

        // 3. Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "NotificationLogService started in foreground.")
    }

    // --- NEW ADDITION: Ensure the service restarts after a hard kill ---
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called. Returning START_STICKY for resilience.")
        // Ensure the service is in the foreground upon restart,
        // though onCreate usually handles this on a cold restart.
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // START_STICKY tells the OS: "If I am killed by the OS, please try to restart me
        // as soon as resources are available."
        return START_STICKY
    }
    // ------------------------------------------------------------------

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

    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        sbn?.let { notification ->
            val extras = notification.notification.extras
            val packageName = notification.packageName
            val postTime = notification.postTime

            // 1. Extract Data
            val appName = getAppName(packageName)
            val senderTitle = extras.getString(Notification.EXTRA_TITLE) ?: "System Notification"
            val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            val newEntry = NotificationEntry(
                appName = appName,
                senderTitle = senderTitle,
                content = content,
                timestamp = postTime
            )

            Log.d(TAG, "Posted: $newEntry")

            // 2. Save Data
            saveNotification(newEntry)

            // Optionally send a broadcast to update the UI if MainActivity is running
            sendBroadcast(Intent("com.example.notilogger.NEW_NOTIFICATION_EVENT"))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Removed: ${sbn?.packageName}")
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