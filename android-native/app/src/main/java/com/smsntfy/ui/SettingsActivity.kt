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
import com.smsntfy.deltachat.DeltaChatSetupResult
import com.smsntfy.deltachat.DeltaChatSendResult

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
        binding.btnSetupDeltaChat.setOnClickListener { setupDeltaChat() }
        binding.btnTestDeltaChat.setOnClickListener {
            viewModel.testDeltaChat { result ->
                val message = when (result) {
                    DeltaChatSendResult.Sent -> getString(R.string.deltachat_test_sent)
                    is DeltaChatSendResult.Failed ->
                        getString(R.string.deltachat_test_failed) + ": " + result.reason
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
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
        binding.cbEnableDeltaChat.isChecked = prefs.deltaChatEnabled

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
        prefs.deltaChatEnabled = binding.cbEnableDeltaChat.isChecked && prefs.deltaChatChatId > 0

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupDeltaChat() {
        val loginCode = binding.etDeltaChatLogin.text?.toString().orEmpty()
        val invite = binding.etDeltaChatInvite.text?.toString().orEmpty()
        binding.btnSetupDeltaChat.isEnabled = false
        viewModel.setupDeltaChat(loginCode, invite) { result ->
            // The login payload must leave the view immediately after submission and is never persisted.
            binding.etDeltaChatLogin.text?.clear()
            binding.btnSetupDeltaChat.isEnabled = true
            val message = when (result) {
                is DeltaChatSetupResult.Ready -> {
                    binding.cbEnableDeltaChat.isChecked = true
                    R.string.deltachat_ready
                }
                DeltaChatSetupResult.InvalidLogin -> R.string.deltachat_invalid_login
                DeltaChatSetupResult.InvalidInvite -> R.string.deltachat_invalid_invite
                is DeltaChatSetupResult.Failed -> null
            }
            val displayMessage = if (result is DeltaChatSetupResult.Failed) {
                getString(R.string.deltachat_setup_failed) + ": " + result.reason
            } else {
                getString(checkNotNull(message))
            }
            Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show()
        }
        binding.etDeltaChatLogin.text?.clear()
    }
}