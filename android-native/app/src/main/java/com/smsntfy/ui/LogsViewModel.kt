package com.smsntfy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smsntfy.SmsNtfyApplication
import com.smsntfy.data.EventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsNtfyApplication
    private val database = app.database

    val logs: Flow<List<EventLog>> = database.eventLogDao().getRecentLogs(200)
}