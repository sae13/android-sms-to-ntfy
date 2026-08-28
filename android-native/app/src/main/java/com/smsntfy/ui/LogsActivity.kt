package com.smsntfy.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smsntfy.R
import com.smsntfy.data.EventLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class LogsActivity : AppCompatActivity() {

    private val viewModel: LogsViewModel by viewModels()
    private val adapter = EventLogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        setupToolbar()
        setupRecyclerView()
        observeLogs()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.logs_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvLogs)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            viewModel.logs.collect { logs ->
                adapter.submitList(logs)
            }
        }
    }

    class EventLogAdapter : RecyclerView.Adapter<EventLogAdapter.ViewHolder>() {

        private var logs = emptyList<EventLog>()

        fun submitList(newLogs: List<EventLog>) {
            logs = newLogs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(logs[position])
        }

        override fun getItemCount(): Int = logs.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvType = view.findViewById<TextView>(R.id.tvType)
            private val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
            private val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            private val tvTime = view.findViewById<TextView>(R.id.tvTime)

            fun bind(event: EventLog) {
                tvType.text = event.type.uppercase()
                tvTitle.text = event.title
                tvMessage.text = event.message
                tvTime.text = android.text.format.DateFormat.format("HH:mm:ss dd/MM", event.timestamp)

                // Color code by type
                val color = when (event.type) {
                    "sms" -> android.graphics.Color.parseColor("#22D3EE") // cyan
                    "call" -> android.graphics.Color.parseColor("#F472B6") // pink
                    "sse" -> android.graphics.Color.parseColor("#A5F3FC") // light cyan
                    "sent" -> android.graphics.Color.parseColor("#4ADE80") // green
                    "error" -> android.graphics.Color.parseColor("#F87171") // red
                    else -> android.graphics.Color.parseColor("#94A3B8") // slate
                }
                tvType.setTextColor(color)
            }
        }
    }
}