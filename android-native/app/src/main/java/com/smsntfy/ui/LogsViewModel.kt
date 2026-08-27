package com.smsntfy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.EventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogsViewModel : ViewModel() {

    private val app by lazy { SmsNtfyApplication() }
    private val database = app.database

    val logs: Flow<List<EventLog>> = database.eventLogDao().getRecentLogs(200)
}