package com.example.notilogger

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

// --- 1. Data Model ---
data class NotificationEntry(
    val appName: String,
    val senderTitle: String,
    val content: String,
    val timestamp: Long
) {
    // Formats the timestamp for display
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var permissionStatusText: TextView
    private lateinit var enableButton: Button
    private lateinit var clearButton: Button
    private val TAG = "NotiLogger"

    // Broadcast Receiver to update UI when a new notification is posted
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.notilogger.NEW_NOTIFICATION_EVENT") {
                loadAndDisplayLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Apply insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        permissionStatusText = findViewById(R.id.permission_status)
        enableButton = findViewById(R.id.enable_button)
        clearButton = findViewById(R.id.clear_button)
        recyclerView = findViewById(R.id.log_recycler_view)

        // Setup RecyclerView
        adapter = NotificationAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup Button Listeners
        enableButton.setOnClickListener {
            requestNotificationPermission()
        }

        clearButton.setOnClickListener {
            clearLogs()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        checkPermissionStatus()
        loadAndDisplayLogs()

        // Register the broadcast receiver with API level compatibility checks
        val intentFilter = IntentFilter("com.example.notilogger.NEW_NOTIFICATION_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use RECEIVER_EXPORTED for apps targeting API 33+
            registerReceiver(notificationUpdateReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            // Fallback for older Android versions
            @Suppress("DEPRECATION")
            registerReceiver(notificationUpdateReceiver, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister the receiver when the activity is paused
        unregisterReceiver(notificationUpdateReceiver)
    }

    private fun checkPermissionStatus() {
        if (isNotificationServiceEnabled()) {
            permissionStatusText.text = "Status: Listening for notifications."
            // Use programmatic rounded background with green color
            setStatusBarBackground(Color.parseColor("#81C784")) // Green: Success
            enableButton.visibility = View.GONE
            clearButton.visibility = View.VISIBLE
        } else {
            permissionStatusText.text = "Status: ACCESS REQUIRED. Tap below."
            // Use programmatic rounded background with red color
            setStatusBarBackground(Color.parseColor("#E57373")) // Red: Warning
            enableButton.visibility = View.VISIBLE
            clearButton.visibility = View.GONE
        }
    }

    // Creates a rounded background drawable programmatically
    private fun setStatusBarBackground(color: Int) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 8f
        drawable.setColor(color)
        permissionStatusText.background = drawable
    }

    // --- Permission Check and Request ---

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    private fun requestNotificationPermission() {
        // Directs the user to the system settings page to grant Notification Access
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    // --- Persistence (Load/Clear) ---

    private fun loadNotificationLogs(): List<NotificationEntry> {
        val prefs = getSharedPreferences(PREF_KEY_NOTIFICATIONS, Context.MODE_PRIVATE)
        val json = prefs.getString(PREF_KEY_NOTIFICATIONS, null)
        val gson = Gson()
        val type = object : TypeToken<List<NotificationEntry>>() {}.type

        return if (json != null) {
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun loadAndDisplayLogs() {
        val logs = loadNotificationLogs()
        adapter.updateData(logs)
    }

    private fun clearLogs() {
        getSharedPreferences(PREF_KEY_NOTIFICATIONS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_NOTIFICATIONS)
            .apply()
        loadAndDisplayLogs()
    }


    // --- 2. Adapter Class ---

    private class NotificationAdapter(private val logList: MutableList<NotificationEntry>) :
        RecyclerView.Adapter<NotificationAdapter.LogViewHolder>() {

        class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appName: TextView = view.findViewById(R.id.tv_app_name)
            val senderTitle: TextView = view.findViewById(R.id.tv_sender_title)
            val content: TextView = view.findViewById(R.id.tv_content)
            val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.log_item, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val entry = logList[position]
            holder.appName.text = entry.appName
            holder.senderTitle.text = entry.senderTitle
            holder.content.text = entry.content
            holder.timestamp.text = entry.getFormattedTime()
        }

        override fun getItemCount() = logList.size

        fun updateData(newLogs: List<NotificationEntry>) {
            logList.clear()
            logList.addAll(newLogs)
            notifyDataSetChanged()
        }
    }
}