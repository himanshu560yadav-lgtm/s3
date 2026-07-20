package com.panda.ai.api.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object CommunicationService {

    fun makeCall(context: Context, contactName: String? = null, phoneNumber: String? = null): String {
        var number = phoneNumber
        if (contactName != null && number.isNullOrEmpty()) {
            number = ContactsService.getPhoneNumber(context, contactName)
            if (number == null) return "Could not find contact \"$contactName\". Try searching contacts first."
        }
        if (number.isNullOrEmpty()) return "No phone number provided."

        return try {
            val uri = Uri.parse("tel:$number")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                "Calling $number${if (contactName != null) " ($contactName)" else ""}..."
            } else "Cannot make calls on this device."
        } catch (e: Exception) { "Error making call: $e" }
    }

    fun sendSms(context: Context, contactName: String? = null, phoneNumber: String? = null, message: String): String {
        var number = phoneNumber
        if (contactName != null && number.isNullOrEmpty()) {
            number = ContactsService.getPhoneNumber(context, contactName)
            if (number == null) return "Could not find contact \"$contactName\"."
        }
        if (number.isNullOrEmpty()) return "No phone number provided."

        return try {
            val uri = Uri.parse("smsto:$number").buildUpon()
                .appendQueryParameter("body", message)
                .build()
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                "Opening SMS to $number${if (contactName != null) " ($contactName)" else ""} with message: \"$message\""
            } else "Cannot send SMS on this device."
        } catch (e: Exception) { "Error sending SMS: $e" }
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
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                "Opening email to $to"
            } else "Cannot send email on this device."
        } catch (e: Exception) { "Error sending email: $e" }
    }
}
