package com.panda.ai.api.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

object CommunicationService {

    fun makeCall(context: Context, contactName: String? = null, phoneNumber: String? = null): String {
        val number = phoneNumber ?: contactName ?: return "No contact or number provided"
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent); return "Calling $number" }
        catch (e: Exception) { return "Could not place call: $e" }
    }

    fun sendSms(contactName: String? = null, phoneNumber: String? = null, message: String): String {
        val number = phoneNumber ?: contactName ?: return "No contact or number provided"
        return try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            "Sent SMS to $number"
        } catch (e: Exception) { "Could not send SMS: $e" }
    }

    fun sendEmail(to: String, subject: String?, body: String?): String {
        return "Email requires manual app selection."
    }
}
