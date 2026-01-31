package com.example.fast.util

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract

object ContactResolver {

    fun getContactName(context: Context, phoneNumber: String): String {
        val contentResolver: ContentResolver = context.contentResolver

        // Clean the phone number
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Try to find contact by phone number
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(phoneNumber, cleanNumber)

        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val contactName = it.getString(nameIndex)
                    if (!contactName.isNullOrBlank()) {
                        return contactName
                    }
                }
            }
        }

        // If no contact found, format the phone number nicely
        return formatPhoneNumber(phoneNumber)
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        return when {
            phoneNumber.length == 10 -> {
                "(${phoneNumber.substring(0, 3)}) ${phoneNumber.substring(3, 6)}-${phoneNumber.substring(6)}"
            }
            phoneNumber.length == 11 && phoneNumber.startsWith("1") -> {
                val withoutCountryCode = phoneNumber.substring(1)
                "(${withoutCountryCode.substring(0, 3)}) ${withoutCountryCode.substring(3, 6)}-${withoutCountryCode.substring(6)}"
            }
            else -> phoneNumber
        }
    }
}
