package com.example.notilogger

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notilogger.MainActivity.NotificationAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LogsByAppActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var permissionStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        permissionStatusText = findViewById(R.id.permission_status)
        recyclerView = findViewById(R.id.log_recycler_view)

        permissionStatusText.text = intent.getStringExtra(EXTRA_APP_NAME_FILTER)
        permissionStatusText.setTextColor("cyan".toColorInt())
        permissionStatusText.setShadowLayer(9f, 0f, 0f, "cyan".toColorInt())
        permissionStatusText.textSize = 20f
        permissionStatusText.background = null

        adapter = NotificationAdapter(mutableListOf()) { entry ->
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_detail, null)

            // Populate the TextViews in the custom dialog view
            dialogView.findViewById<TextView>(R.id.dialog_app_name).text = entry.appName
            dialogView.findViewById<TextView>(R.id.dialog_sender_title).text = entry.senderTitle ?: "No Title"
            dialogView.findViewById<TextView>(R.id.dialog_content).text = entry.fullContent ?: "No Content"
            dialogView.findViewById<TextView>(R.id.dialog_timestamp).text = entry.getFormattedTime()

            AlertDialog.Builder(this)
                .setView(dialogView)
                .show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadAndDisplayLogs()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayLogs()
    }

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
        val uniqueLogs = logs.filter { it.appName == intent.getStringExtra(EXTRA_APP_NAME_FILTER) }
        adapter.updateData(uniqueLogs)
    }
}