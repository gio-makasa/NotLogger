package com.example.notilogger

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.graphics.toColorInt

// Key used for SharedPreferences storage (MUST be defined here as well)
//const val PREF_KEY_NOTIFICATIONS = "notification_log_key"
const val EXTRA_APP_NAME_FILTER = "com.example.notilogger.APP_NAME_FILTER"

// --- 1. Data Model ---
data class NotificationEntry(
    val appName: String,
    val senderTitle: String,
    val content: String,
    val fullContent: String,
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        permissionStatusText = findViewById(R.id.permission_status)
        enableButton = findViewById(R.id.enable_button)
        recyclerView = findViewById(R.id.log_recycler_view)

        // Setup RecyclerView
        adapter = NotificationAdapter(mutableListOf()) { entry ->
            val intent = Intent(this, LogsByAppActivity::class.java)
            intent.putExtra(EXTRA_APP_NAME_FILTER, entry.appName)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup Button Listeners
        enableButton.setOnClickListener {
            requestNotificationPermission()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        if (isNotificationServiceEnabled()) {
            toggleNotificationListenerService()
        }

        checkPermissionStatus()
        loadAndDisplayLogs()

        // Register the broadcast receiver with API level compatibility checks
        val intentFilter = IntentFilter("com.example.notilogger.NEW_NOTIFICATION_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use RECEIVER_EXPORTED for apps targeting API 33+
            registerReceiver(notificationUpdateReceiver, intentFilter, RECEIVER_EXPORTED)
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

    @SuppressLint("SetTextI18n")
    private fun checkPermissionStatus() {
        if (isNotificationServiceEnabled()) {
            permissionStatusText.text = "Status: Listening for notifications. \n To make sure the app works correctly allow auto-start."
            setStatusBarBackground("#81C784".toColorInt()) // Green: Success
            enableButton.visibility = View.GONE
        } else {
            permissionStatusText.text = "Status: ACCESS REQUIRED. Tap below."
            setStatusBarBackground("#E57373".toColorInt()) // Red: Warning
            enableButton.visibility = View.VISIBLE
        }
    }

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
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun toggleNotificationListenerService() {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            // Use the full package name and class name of the listener service
            ComponentName(this, NotificationLogService::class.java),
            // Temporarily disable the service
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            // Re-enable the service
            ComponentName(this, NotificationLogService::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    // --- Persistence (Load) ---

    private fun loadNotificationLogs(): List<NotificationEntry> {
        val prefs = getSharedPreferences(PREF_KEY_NOTIFICATIONS, MODE_PRIVATE)
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
        val uniqueLogs = logs.distinctBy { it.appName }
        adapter.updateData(uniqueLogs)
    }


    // --- 2. Adapter Class ---

     class NotificationAdapter(private val logList: MutableList<NotificationEntry>,
                                      private val clickListener: (NotificationEntry) -> Unit ) :
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

            holder.itemView.setOnClickListener {
                clickListener(entry)
            }
        }

        override fun getItemCount() = logList.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newLogs: List<NotificationEntry>) {
            logList.clear()
            logList.addAll(newLogs)
            notifyDataSetChanged()
        }
    }
}