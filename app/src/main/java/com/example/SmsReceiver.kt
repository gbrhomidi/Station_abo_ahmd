package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val db = DatabaseHelper(context)

            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: continue
                val msgBody = sms.displayMessageBody?.lowercase() ?: continue

                db.logSms(sender, msgBody, "received", "success")

                when {
                    msgBody.contains("رصيد") || msgBody.contains("حساب") || msgBody.contains("balance") -> {
                        handleBalanceQuery(db, sender)
                    }
                    msgBody.contains("دفع") || msgBody.contains("تسديد") -> {
                        sendReply(context, db, sender, "شكراً لتواصلك. يرجى زيارة المحطة لإتمام عملية الدفع وتزويدنا بوصل الاستلام.")
                    }
                    msgBody.contains("استعلام") || msgBody.contains("help") -> {
                        sendReply(context, db, sender, "مرحباً بك في محطة أبو أحمد لمشتقات الديزل.
الخدمات المتاحة:
1. الاستعلام عن الرصيد (أرسل: رصيد)
2. معرفة العروض (أرسل: عروض)
3. الموقع (أرسل: موقع)")
                    }
                    msgBody.contains("عروض") || msgBody.contains("offer") -> {
                        sendReply(context, db, sender, "عروض اليوم:
- سعر اللتر: 950 د.ع
- خصم الولاء: 5% للعملاء الذهبيين
- توصيل مجاني للطلبات +5000 لتر")
                    }
                    msgBody.contains("موقع") || msgBody.contains("location") -> {
                        sendReply(context, db, sender, "موقع محطة أبو أحمد:
شارع تعز - المنصورة
أوقات العمل: 6 ص - 12 ص")
                    }
                }
            }
        }
    }

    private fun handleBalanceQuery(db: DatabaseHelper, sender: String) {
        val customers = db.getCustomers()
        var found = false
        for (i in 0 until customers.length()) {
            val c = customers.getJSONObject(i)
            val cPhone = c.optString("phone", "")
            if (cPhone.isNotEmpty() && (sender.contains(cPhone) || cPhone.contains(sender))) {
                val bal = c.optDouble("current_balance", 0.0)
                val points = c.optInt("loyalty_points", 0)
                val vip = c.optInt("vip_level", 0)
                val vipText = when(vip) { 3 -> "ذهبي 🥇" 2 -> "فضي 🥈" else -> "عادي" }
                val reply = "مرحباً ${c.optString("full_name")},
رصيدك: $bal د.ع
نقاط الولاء: $points
العضوية: $vipText"
                sendReply(context, db, sender, reply)
                found = true
                break
            }
        }
        if (!found) {
            sendReply(context, db, sender, "عذراً، لم يتم العثور على حساب مرتبط بهذا الرقم. يرجى التسجيل في المحطة.")
        }
    }

    private fun sendReply(context: Context, db: DatabaseHelper, phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
            db.logSms(phone, message, "auto_reply", "sent")
        } catch (e: Exception) {
            e.printStackTrace()
            db.logSms(phone, message, "auto_reply", "failed: ${e.message}")
        }
    }
}