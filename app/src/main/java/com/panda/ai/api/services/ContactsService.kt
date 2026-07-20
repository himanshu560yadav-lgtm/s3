package com.panda.ai.api.services

import android.content.Context
import android.provider.ContactsContract

object ContactsService {
    fun searchAndFormat(context: Context, query: String): String {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        val found = mutableListOf<Pair<String, String>>()
        try {
            context.contentResolver.query(uri, projection, sel, args, null)?.use { c ->
                val n = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val p = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) found.add(c.getString(n) to c.getString(p))
            }
        } catch (e: Exception) { return "Error searching contacts: $e" }
        if (found.isEmpty()) return "No contacts found for '$query'."
        return found.joinToString("\n") { "${it.first}: ${it.second}" }
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
