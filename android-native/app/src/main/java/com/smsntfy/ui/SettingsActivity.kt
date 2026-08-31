package com.smsntfy.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.smsntfy.R
import com.smsntfy.databinding.ActivitySettingsBinding
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.deltachat.DeltaChatSetupResult
import com.smsntfy.deltachat.DeltaChatSendResult
import com.smsntfy.telegram.TelegramSettingsField
import com.smsntfy.telegram.TelegramSettingsPolicy
import com.smsntfy.telegram.TelegramSettingsValidation

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
        binding.cbEnableAether.setOnCheckedChangeListener { _, checked ->
            updateAetherControls(checked)
        }
        binding.cbAetherPublicProxy.setOnCheckedChangeListener { _, _ ->
            updateAetherControls(binding.cbEnableAether.isChecked)
        }
        binding.btnFindFastestAether.setOnClickListener { findFastestAetherRoute() }
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
        binding.btnTestTelegram.setOnClickListener {
            when (val validation = telegramSettingsValidation(requestedEnabled = true)) {
                is TelegramSettingsValidation.Invalid -> showTelegramValidationError(validation.field)
                is TelegramSettingsValidation.Valid -> viewModel.testTelegram(
                    config = validation.config,
                    useAether = binding.cbEnableAether.isChecked,
                    publicProxy = binding.cbAetherPublicProxy.isChecked
                ) { success ->
                    Toast.makeText(
                        this,
                        getString(if (success) R.string.telegram_test_sent else R.string.telegram_test_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
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
        binding.cbEnableSms.isChecked = prefs.enableSms
        binding.cbEnableCalls.isChecked = prefs.enableCalls
        binding.cbUseBase64.isChecked = prefs.useBase64
        binding.cbEnableDeltaChat.isChecked = prefs.deltaChatEnabled
        binding.cbEnableTelegram.isChecked = prefs.telegramEnabled
        binding.etTelegramBotToken.setText(prefs.telegramBotToken)
        binding.etTelegramChatId.setText(prefs.telegramChatId)
        binding.cbEnableAether.isChecked = prefs.aetherEnabled
        binding.cbAetherAlwaysOn.isChecked = prefs.aetherAlwaysOn
        binding.cbAetherPublicProxy.isChecked = prefs.aetherPublicProxy
        updateAetherControls(prefs.aetherEnabled)
        binding.tvAetherStatus.text = "Aether: ${prefs.aetherLastStatus}"

        binding.spinnerPriority.setSelection(prefs.ntfyPriority)
    }

    private fun updateAetherControls(enabled: Boolean) {
        binding.cbAetherAlwaysOn.isEnabled = enabled
        binding.cbAetherPublicProxy.isEnabled = enabled
        binding.btnFindFastestAether.isEnabled = enabled
        binding.tvAetherPublicWarning.visibility = if (enabled && binding.cbAetherPublicProxy.isChecked) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun findFastestAetherRoute() {
        when (val validation = telegramSettingsValidation(requestedEnabled = true)) {
            is TelegramSettingsValidation.Invalid -> showTelegramValidationError(validation.field)
            is TelegramSettingsValidation.Valid -> {
                binding.btnFindFastestAether.isEnabled = false
                binding.btnTestTelegram.isEnabled = false
                viewModel.findFastestAetherRoute(
                    botToken = validation.config.botToken,
                    publicProxy = binding.cbAetherPublicProxy.isChecked,
                    onAttempt = { route, stage ->
                        runOnUiThread {
                            binding.tvAetherStatus.text = when (stage) {
                                com.smsntfy.aether.AetherRouteAttemptStage.STARTING ->
                                    getString(R.string.aether_search_starting, route.id)
                                com.smsntfy.aether.AetherRouteAttemptStage.VERIFYING ->
                                    getString(R.string.aether_search_verifying, route.id)
                                com.smsntfy.aether.AetherRouteAttemptStage.FAILED ->
                                    getString(R.string.aether_search_failed_route, route.id)
                                com.smsntfy.aether.AetherRouteAttemptStage.VERIFIED ->
                                    getString(R.string.aether_search_found, route.id)
                            }
                        }
                    }
                ) { route ->
                    binding.btnFindFastestAether.isEnabled = binding.cbEnableAether.isChecked
                    binding.btnTestTelegram.isEnabled = true
                    val message = if (route != null) {
                        getString(R.string.aether_search_found, route.id)
                    } else {
                        getString(R.string.aether_search_failed)
                    }
                    binding.tvAetherStatus.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSettings() {
        val app = application as SmsNtfyApplication
        val prefs = app.preferences

        // Validate and durably persist the only failure-capable settings batch
        // before changing any of the legacy individually-applied preferences.
        val telegramValidation = telegramSettingsValidation(binding.cbEnableTelegram.isChecked)
        if (telegramValidation is TelegramSettingsValidation.Invalid) {
            showTelegramValidationError(telegramValidation.field)
            return
        }
        val telegramConfig = (telegramValidation as TelegramSettingsValidation.Valid).config
        if (!prefs.saveTelegramSettings(
                telegramConfig.enabled,
                telegramConfig.botToken,
                telegramConfig.chatId,
                binding.cbEnableAether.isChecked,
                binding.cbAetherAlwaysOn.isChecked,
                binding.cbAetherPublicProxy.isChecked
            )
        ) {
            Toast.makeText(this, R.string.telegram_settings_save_failed, Toast.LENGTH_LONG).show()
            return
        }

        prefs.ntfyServer = binding.etServerUrl.text.toString().trim()
        prefs.ntfyTopic = binding.etTopic.text.toString().trim()
        prefs.ntfyUsername = binding.etUsername.text.toString().trim()
        prefs.ntfyPassword = binding.etPassword.text.toString().trim()
        prefs.enableSms = binding.cbEnableSms.isChecked
        prefs.enableCalls = binding.cbEnableCalls.isChecked
        prefs.useBase64 = binding.cbUseBase64.isChecked
        prefs.ntfyPriority = binding.spinnerPriority.selectedItemPosition
        prefs.deltaChatEnabled = binding.cbEnableDeltaChat.isChecked && prefs.deltaChatChatId > 0

        if (prefs.isServiceRunning) {
            val serviceIntent = Intent(this, com.smsntfy.service.SmsForwardingService::class.java).apply {
                action = com.smsntfy.service.SmsForwardingService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun telegramSettingsValidation(requestedEnabled: Boolean): TelegramSettingsValidation =
        TelegramSettingsPolicy.validate(
            requestedEnabled = requestedEnabled,
            botToken = binding.etTelegramBotToken.text?.toString().orEmpty(),
            chatId = binding.etTelegramChatId.text?.toString().orEmpty()
        )

    private fun showTelegramValidationError(field: TelegramSettingsField) {
        val message = when (field) {
            TelegramSettingsField.BOT_TOKEN -> R.string.telegram_invalid_token
            TelegramSettingsField.CHAT_ID -> R.string.telegram_invalid_chat_id
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
