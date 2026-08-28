package com.smsntfy.kmp
import android.content.*
import android.provider.Telephony
import com.smsntfy.shared.*
import kotlinx.coroutines.*
class SmsReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();val p=context.getSharedPreferences("sms_ntfy",Context.MODE_PRIVATE);val cfg=NtfyConfig(p.getString("ntfy_server","https://ntfy.sh")!!,p.getString("ntfy_topic","sms-alerts")!!,p.getString("reply_topic","sms-replies")!!,p.getString("username","")!!,p.getString("password","")!!,p.getInt("priority",4));CoroutineScope(Dispatchers.IO).launch{try{Telephony.Sms.Intents.getMessagesFromIntent(intent).groupBy{it.originatingAddress.orEmpty()}.forEach{(sender,parts)->NtfyClient(cfg).use{it.forwardSms(SmsEvent(sender,body=parts.joinToString(""){m->m.messageBody.orEmpty()},timestamp=parts.first().timestampMillis))}}}finally{pending.finish()}}}}
private inline fun <T:NtfyClient,R>T.use(block:(T)->R):R=try{block(this)}finally{close()}
