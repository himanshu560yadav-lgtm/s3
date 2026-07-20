package com.panda.ai.api.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

object CommunicationService {

    fun makeCall(context: Context, contactName: String? = null, phoneNumber: String? = null): String {
        var number = phoneNumber
        if (contactName != null && number.isNullOrEmpty()) {
            number = ContactsService.getPhoneNumber(context, contactName)
            if (number == null) return "Could not find contact \"$contactName\". Try searching contacts first."
        }
        if (number.isNullOrEmpty()) return "No phone number provided."

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Calling $number${if (contactName != null) " ($contactName)" else ""}..."
        } catch (e: Exception) { "Could not place call: $e" }
    }

    fun sendSms(context: Context, contactName: String? = null, phoneNumber: String? = null, message: String): String {
        var number = phoneNumber
        if (contactName != null && number.isNullOrEmpty()) {
            number = ContactsService.getPhoneNumber(context, contactName)
            if (number == null) return "Could not find contact \"$contactName\"."
        }
        if (number.isNullOrEmpty()) return "No phone number provided."

        return try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            "Sent SMS to $number${if (contactName != null) " ($contactName)" else ""}: \"$message\""
        } catch (e: Exception) { "Could not send SMS: $e" }
    }

    fun sendEmail(context: Context, to: String, subject: String?, body: String?): String {
        if (to.isEmpty()) return "No email address provided."
        val uri = Uri.parse("mailto:$to").buildUpon().apply {
            if (!subject.isNullOrEmpty()) appendQueryParameter("subject", subject)
            if (!body.isNullOrEmpty()) appendQueryParameter("body", body)
        }.build()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opening email to $to"
        } catch (e: Exception) { "Could not open email: $e" }
    }
}
