package com.smsntfy.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.smsntfy.R
import com.smsntfy.databinding.ActivitySettingsBinding
import com.smsntfy.SmsNtfyApplication

class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        loadSettings()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupUI() {
        // Priority spinner
        val priorityAdapter = ArrayAdapter.createFromResource(
            this, R.array.ntfy_priorities, android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerPriority.adapter = priorityAdapter

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTestConnection.setOnClickListener { viewModel.testConnection() }
    }

    private fun loadSettings() {
        val app = application as SmsNtfyApplication
        val prefs = app.preferences

        binding.etServerUrl.setText(prefs.ntfyServer)
        binding.etTopic.setText(prefs.ntfyTopic)
        binding.etUsername.setText(prefs.ntfyUsername)
        binding.etPassword.setText(prefs.ntfyPassword)
        binding.etReplyTopic.setText(prefs.replyTopic)
        binding.cbEnableSms.isChecked = prefs.enableSms
        binding.cbEnableCalls.isChecked = prefs.enableCalls
        binding.cbEnableSse.isChecked = prefs.enableSse
        binding.cbUseBase64.isChecked = prefs.useBase64

        binding.spinnerPriority.setSelection(prefs.ntfyPriority)
    }

    private fun saveSettings() {
        val app = application as SmsNtfyApplication
        val prefs = app.preferences

        prefs.ntfyServer = binding.etServerUrl.text.toString().trim()
        prefs.ntfyTopic = binding.etTopic.text.toString().trim()
        prefs.ntfyUsername = binding.etUsername.text.toString().trim()
        prefs.ntfyPassword = binding.etPassword.text.toString().trim()
        prefs.replyTopic = binding.etReplyTopic.text.toString().trim()
        prefs.enableSms = binding.cbEnableSms.isChecked
        prefs.enableCalls = binding.cbEnableCalls.isChecked
        prefs.enableSse = binding.cbEnableSse.isChecked
        prefs.useBase64 = binding.cbUseBase64.isChecked
        prefs.ntfyPriority = binding.spinnerPriority.selectedItemPosition

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}