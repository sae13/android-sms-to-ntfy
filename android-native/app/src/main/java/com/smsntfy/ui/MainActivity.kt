package com.smsntfy.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModel
import com.smsntfy.R
import com.smsntfy.data.EventLog
import com.smsntfy.network.SseClient
import com.smsntfy.receiver.CallReceiver
import com.smsntfy.service.SmsForwardingService
import com.smsntfy.util.WakeLockHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModel()
    private var callReceiver: CallReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        observeViewModel()
        startServiceIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    private fun setupUI() {
        // Toggle service button
        findViewById<View>(R.id.btnToggleService).setOnClickListener {
            viewModel.toggleService()
        }

        // Open settings
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Test connection
        findViewById<View>(R.id.btnTestConnection).setOnClickListener {
            viewModel.testConnection()
        }

        // Battery optimization
        findViewById<View>(R.id.btnBatteryOptimization).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        // View logs
        findViewById<View>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                runOnUiThread {
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: MainViewModel.UiState) {
        val btnToggle = findViewById<View>(R.id.btnToggleService)
        val tvStatus = findViewById<View>(R.id.tvStatus)
        val tvLastEvent = findViewById<View>(R.id.tvLastEvent)
        val tvConnectionStatus = findViewById<View>(R.id.tvConnectionStatus)

        btnToggle.isEnabled = !state.isLoading
        btnToggle.setText(if (state.isServiceRunning) "Stop Service" else "Start Service")

        tvStatus.text = if (state.isServiceRunning) "Service Running" else "Service Stopped"
        tvStatus.setTextColor(if (state.isServiceRunning) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark))

        tvConnectionStatus.text = when (state.connectionState) {
            SseClient.ConnectionState.Connected -> "SSE: Connected"
            SseClient.ConnectionState.Connecting -> "SSE: Connecting..."
            SseClient.ConnectionState.Disconnected -> "SSE: Disconnected"
            is SseClient.ConnectionState.Error -> "SSE: Error - ${it.message}"
        }

        state.lastEvent?.let { event ->
            tvLastEvent.text = "${event.type.toUpperCase()}: ${event.title} - ${event.message.take(80)}"
            tvLastEvent.visibility = View.VISIBLE
        } ?: run {
            tvLastEvent.text = "No events yet"
            tvLastEvent.visibility = View.VISIBLE
        }
    }

    private fun startServiceIfNeeded() {
        val prefs = (application as com.smsntfy.SmsNtfyApplication).preferences
        if (prefs.isServiceRunning) {
            val intent = Intent(this, SmsForwardingService::class.java).apply {
                action = SmsForwardingService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}