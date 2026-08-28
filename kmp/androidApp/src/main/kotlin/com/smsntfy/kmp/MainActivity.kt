package com.smsntfy.kmp
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class MainActivity:AppCompatActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);setContentView(TextView(this).apply{setPadding(48,96,48,48);textSize=22f;text="SMS → ntfy (KMP)\n\nShared Ktor forwarding is active. Configure ntfy_server, ntfy_topic, and ntfy credentials in SharedPreferences."})}}
