package com.example.notilogger

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

// Key used for SharedPreferences storage
const val PREF_KEY_NOTIFICATIONS = "notification_log_key"

class NotificationLogService : NotificationListenerService() {

    private val TAG = "NotiLoggerService"

    // Helper function to get the app's display name from its package name
    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
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
            // EXTRA_TITLE is the main title (e.g., Sender Name)
            val senderTitle = extras.getString(Notification.EXTRA_TITLE) ?: "System Notification"
            // EXTRA_TEXT is the content of the notification
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
        // You could handle logging removed notifications here if necessary
        Log.d(TAG, "Removed: ${sbn?.packageName}")
    }

    // Persistence Logic using SharedPreferences and GSON
    private fun saveNotification(newEntry: NotificationEntry) {
        val prefs = getSharedPreferences(PREF_KEY_NOTIFICATIONS, Context.MODE_PRIVATE)
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
        prefs.edit().putString(PREF_KEY_NOTIFICATIONS, updatedJson).apply()
    }
}