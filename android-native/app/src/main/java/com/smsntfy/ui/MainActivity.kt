package com.smsntfy.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smsntfy.R
import com.smsntfy.network.SseClient
import com.smsntfy.service.SmsForwardingService
import com.smsntfy.update.ReleaseUpdateChecker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingPermissionAction: (() -> Unit)? = null
    private var initialPermissionRequestInFlight = false
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val prefs = (application as com.smsntfy.SmsNtfyApplication).preferences
        if (initialPermissionRequestInFlight || !prefs.initialPermissionRequested) {
            (application as com.smsntfy.SmsNtfyApplication)
                .preferences.initialPermissionRequested = true
            initialPermissionRequestInFlight = false
        }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (hasAllRequiredPermissions()) {
            action?.invoke()
        } else {
            Toast.makeText(
                this,
                "Required permissions were denied. The service was not started.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lifecycleScope.launch { ReleaseUpdateChecker(applicationContext).checkAndNotify() }

        setupUI()
        observeViewModel()
        handleInitialPermissionsAndSavedService()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    private fun setupUI() {
        // Toggle service button
        findViewById<View>(R.id.btnToggleService).setOnClickListener {
            if (viewModel.uiState.value.isServiceRunning) {
                viewModel.toggleService()
            } else {
                ensurePermissionsAndStartService()
            }
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

        findViewById<View>(R.id.btnFooterGithub).setOnClickListener {
            openExternalLink(FooterLinks.github)
        }
        findViewById<View>(R.id.btnFooterTelegram).setOnClickListener {
            openExternalLink(FooterLinks.telegram)
        }
        findViewById<View>(R.id.btnFooterEmail).setOnClickListener {
            openExternalLink(FooterLinks.email)
        }
    }

    private fun openExternalLink(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show()
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
        val btnToggle = findViewById<Button>(R.id.btnToggleService)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvLastEvent = findViewById<TextView>(R.id.tvLastEvent)
        val tvConnectionStatus = findViewById<TextView>(R.id.tvConnectionStatus)

        btnToggle.isEnabled = !state.isLoading
        btnToggle.setText(if (state.isServiceRunning) "Stop Service" else "Start Service")

        tvStatus.text = if (state.isServiceRunning) "Service Running" else "Service Stopped"
        tvStatus.setTextColor(if (state.isServiceRunning) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark))

        tvConnectionStatus.text = when (state.connectionState) {
            SseClient.ConnectionState.Connected -> "SSE: Connected"
            SseClient.ConnectionState.Connecting -> "SSE: Connecting..."
            SseClient.ConnectionState.Disconnected -> "SSE: Disconnected"
            is SseClient.ConnectionState.Error -> "SSE: Error - ${state.connectionState.message}"
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

    private fun handleInitialPermissionsAndSavedService() {
        val prefs = (application as com.smsntfy.SmsNtfyApplication).preferences
        val hasMissingPermissions = !hasAllRequiredPermissions()
        if (PermissionPolicy.shouldRequestOnLaunch(
                prefs.initialPermissionRequested,
                hasMissingPermissions
            )
        ) {
            initialPermissionRequestInFlight = true
            val launched = requestMissingPermissions { startServiceIfNeeded() }
            if (!launched) {
                initialPermissionRequestInFlight = false
                prefs.initialPermissionRequested = true
            }
        } else if (!hasMissingPermissions) {
            prefs.initialPermissionRequested = true
            startServiceIfNeeded()
        }
    }

    private fun ensurePermissionsAndStartService() {
        requestMissingPermissions { viewModel.toggleService() }
    }

    private fun requestMissingPermissions(onGranted: () -> Unit): Boolean {
        val missingPermissions = PermissionPolicy.missingPermissions(Build.VERSION.SDK_INT) {
            ContextCompat.checkSelfPermission(this, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onGranted()
            return false
        } else {
            pendingPermissionAction = onGranted
            requestPermissions.launch(missingPermissions.toTypedArray())
            return true
        }
    }

    private fun hasAllRequiredPermissions(): Boolean =
        PermissionPolicy.hasAllRequiredPermissions(Build.VERSION.SDK_INT) {
            ContextCompat.checkSelfPermission(this, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}