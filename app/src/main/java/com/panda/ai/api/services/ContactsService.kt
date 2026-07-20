package com.panda.ai.api.services

import android.content.Context
import android.provider.ContactsContract

object ContactsService {
    data class ContactMatch(val name: String, val number: String?, val email: String?)

    fun searchContacts(context: Context, query: String): List<ContactMatch> {
        val found = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val sel = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$query%")
            context.contentResolver.query(uri, projection, sel, args, null)?.use { c ->
                val n = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val p = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    found.add(ContactMatch(c.getString(n) ?: "", c.getString(p), null))
                }
            }
        } catch (_: Exception) {}
        return found
    }

    fun searchAndFormat(context: Context, query: String): String {
        val contacts = searchContacts(context, query)
        if (contacts.isEmpty()) return "No contacts found matching \"$query\"."

        val emailMap = mutableMapOf<String, String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            val sel = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$query%")
            context.contentResolver.query(uri, projection, sel, args, null)?.use { c ->
                val n = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                val a = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (c.moveToNext()) {
                    val name = c.getString(n) ?: ""
                    val addr = c.getString(a) ?: ""
                    if (!emailMap.containsKey(name)) emailMap[name] = addr
                }
            }
        } catch (_: Exception) {}

        val sb = StringBuilder("Found ${contacts.size} contact(s):\n")
        contacts.take(5).forEach { contact ->
            sb.append("• ${contact.name}")
            if (!contact.number.isNullOrEmpty()) sb.append(" - ${contact.number}")
            val email = emailMap[contact.name]
            if (!email.isNullOrEmpty()) sb.append(" - $email")
            sb.appendLine()
        }
        if (contacts.size > 5) sb.appendLine("...and ${contacts.size - 5} more")
        return sb.toString()
    }

    fun getPhoneNumber(context: Context, contactName: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$contactName%")
        var best: String? = null
        try {
            context.contentResolver.query(uri, projection, sel, args, null)?.use { c ->
                val n = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val p = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = c.getString(n) ?: ""
                    val num = c.getString(p)
                    if (name.equals(contactName, ignoreCase = true)) return num
                    if (best == null) best = num
                }
            }
        } catch (_: Exception) {}
        return best
    }
}
