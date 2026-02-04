package com.example.fast.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import com.example.fast.model.AddressInfo
import com.example.fast.model.Contact
import com.example.fast.model.EmailInfo
import com.example.fast.model.ImAccount
import com.example.fast.model.PhoneInfo

object ContactHelper {

    @SuppressLint("Range")
    fun getAllContacts(context: Context): List<Contact> {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val contactsMap = mutableMapOf<String, Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        // Query all contacts with basic info
        val contactsUri = ContactsContract.Contacts.CONTENT_URI
        val contactsProjection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.LAST_TIME_CONTACTED,
            ContactsContract.Contacts.TIMES_CONTACTED,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val contactsCursor = contentResolver.query(
            contactsUri,
            contactsProjection,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        contactsCursor?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val altNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val thumbIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val lastContactedIndex = cursor.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)
            val timesContactedIndex = cursor.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED)
            val starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex) ?: ""
                val altName = cursor.getString(altNameIndex) ?: ""
                val photoUri = cursor.getString(photoIndex)
                val thumbUri = cursor.getString(thumbIndex)
                val lastContacted = cursor.getLong(lastContactedIndex)
                val timesContacted = cursor.getInt(timesContactedIndex)
                val starred = cursor.getInt(starredIndex) == 1
                val hasPhone = cursor.getInt(hasPhoneIndex) == 1

                if (name.isNotBlank() && hasPhone) {
                    // Get phones, emails, addresses, etc. for this contact
                    val phones = getPhones(contentResolver, contactId)
                    val emails = getEmails(contentResolver, contactId)
                    val addresses = getAddresses(contentResolver, contactId)
                    val organization = getOrganization(contentResolver, contactId)
                    val websites = getWebsites(contentResolver, contactId)
                    val imAccounts = getImAccounts(contentResolver, contactId)
                    val events = getEvents(contentResolver, contactId)
                    val notes = getNotes(contentResolver, contactId)
                    val nickname = getNickname(contentResolver, contactId)
                    val phoneticName = getPhoneticName(contentResolver, contactId)

                    // Use first phone number as primary
                    val primaryPhone = phones.firstOrNull()?.number ?: ""
                    val normalizedPhone = normalizePhoneNumber(primaryPhone)

                    if (normalizedPhone.isNotBlank()) {
                        contactsMap[contactId] = Contact(
                            id = contactId,
                            name = name,
                            displayName = if (altName.isNotBlank()) altName else name,
                            phoneNumber = normalizedPhone,
                            phones = phones,
                            emails = emails,
                            addresses = addresses,
                            photoUri = photoUri,
                            thumbnailUri = thumbUri,
                            company = organization?.company,
                            jobTitle = organization?.title,
                            department = organization?.department,
                            websites = websites,
                            imAccounts = imAccounts,
                            birthday = events.birthday,
                            anniversary = events.anniversary,
                            notes = notes,
                            lastContacted = if (lastContacted > 0) lastContacted else null,
                            timesContacted = timesContacted,
                            isStarred = starred,
                            nickname = nickname,
                            phoneticName = phoneticName
                        )
                    }
                }
            }
        }

        return contactsMap.values.sortedBy { it.name.lowercase() }
    }

    @SuppressLint("Range")
    private fun getPhones(contentResolver: ContentResolver, contactId: String): List<PhoneInfo> {
        val phones = mutableListOf<PhoneInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
            val primaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: continue
                val type = cursor.getInt(typeIndex)
                val label = cursor.getString(labelIndex)
                val isPrimary = cursor.getInt(primaryIndex) == 1

                phones.add(PhoneInfo(
                    number = normalizePhoneNumber(number),
                    type = type,
                    typeLabel = getPhoneTypeLabel(type, label),
                    label = label,
                    isPrimary = isPrimary
                ))
            }
        }
        return phones
    }

    @SuppressLint("Range")
    private fun getEmails(contentResolver: ContentResolver, contactId: String): List<EmailInfo> {
        val emails = mutableListOf<EmailInfo>()
        val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.TYPE,
            ContactsContract.CommonDataKinds.Email.LABEL,
            ContactsContract.CommonDataKinds.Email.IS_PRIMARY
        )
        val selection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
            val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL)
            val primaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.IS_PRIMARY)

            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIndex) ?: continue
                val type = cursor.getInt(typeIndex)
                val label = cursor.getString(labelIndex)
                val isPrimary = cursor.getInt(primaryIndex) == 1

                emails.add(EmailInfo(
                    address = address,
                    type = type,
                    typeLabel = getEmailTypeLabel(type, label),
                    label = label,
                    isPrimary = isPrimary
                ))
            }
        }
        return emails
    }

    @SuppressLint("Range")
    private fun getAddresses(contentResolver: ContentResolver, contactId: String): List<AddressInfo> {
        val addresses = mutableListOf<AddressInfo>()
        val uri = ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.StructuredPostal.STREET,
            ContactsContract.CommonDataKinds.StructuredPostal.CITY,
            ContactsContract.CommonDataKinds.StructuredPostal.REGION,
            ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
            ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.LABEL
        )
        val selection = "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val streetIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.STREET)
            val cityIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CITY)
            val regionIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.REGION)
            val postcodeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE)
            val countryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY)
            val formattedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.TYPE)
            val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.LABEL)

            while (cursor.moveToNext()) {
                val street = cursor.getString(streetIndex)
                val city = cursor.getString(cityIndex)
                val region = cursor.getString(regionIndex)
                val postcode = cursor.getString(postcodeIndex)
                val country = cursor.getString(countryIndex)
                val formatted = cursor.getString(formattedIndex)
                val type = cursor.getInt(typeIndex)
                val label = cursor.getString(labelIndex)

                addresses.add(AddressInfo(
                    street = street,
                    city = city,
                    region = region,
                    postcode = postcode,
                    country = country,
                    formattedAddress = formatted,
                    type = type,
                    typeLabel = getAddressTypeLabel(type, label)
                ))
            }
        }
        return addresses
    }

    @SuppressLint("Range")
    private fun getOrganization(contentResolver: ContentResolver, contactId: String): OrganizationInfo? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Organization.COMPANY,
            ContactsContract.CommonDataKinds.Organization.TITLE,
            ContactsContract.CommonDataKinds.Organization.DEPARTMENT
        )
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val companyIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
            val titleIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TITLE)
            val deptIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.DEPARTMENT)

            if (cursor.moveToFirst()) {
                val company = cursor.getString(companyIndex)
                val title = cursor.getString(titleIndex)
                val department = cursor.getString(deptIndex)

                if (company != null || title != null || department != null) {
                    return OrganizationInfo(company, title, department)
                }
            }
        }
        return null
    }

    @SuppressLint("Range")
    private fun getWebsites(contentResolver: ContentResolver, contactId: String): List<String> {
        val websites = mutableListOf<String>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Website.URL)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val urlIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.URL)
            while (cursor.moveToNext()) {
                val url = cursor.getString(urlIndex)
                if (!url.isNullOrBlank()) {
                    websites.add(url)
                }
            }
        }
        return websites
    }

    @SuppressLint("Range")
    private fun getImAccounts(contentResolver: ContentResolver, contactId: String): List<ImAccount> {
        val imAccounts = mutableListOf<ImAccount>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Im.DATA,
            ContactsContract.CommonDataKinds.Im.PROTOCOL,
            ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL
        )
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)
            val protocolIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL)
            val customProtocolIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)

            while (cursor.moveToNext()) {
                val data = cursor.getString(dataIndex) ?: continue
                val protocol = cursor.getInt(protocolIndex)
                val customProtocol = cursor.getString(customProtocolIndex)

                imAccounts.add(ImAccount(
                    data = data,
                    protocol = protocol,
                    protocolLabel = getImProtocolLabel(protocol),
                    customProtocol = customProtocol
                ))
            }
        }
        return imAccounts
    }

    @SuppressLint("Range")
    private fun getEvents(contentResolver: ContentResolver, contactId: String): EventInfo {
        var birthday: String? = null
        var anniversary: String? = null

        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE
        )
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val dateIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE)

            while (cursor.moveToNext()) {
                val date = cursor.getString(dateIndex)
                val type = cursor.getInt(typeIndex)

                when (type) {
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> birthday = date
                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> anniversary = date
                }
            }
        }
        return EventInfo(birthday, anniversary)
    }

    @SuppressLint("Range")
    private fun getNotes(contentResolver: ContentResolver, contactId: String): String? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Note.NOTE)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val noteIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Note.NOTE)
            if (cursor.moveToFirst()) {
                return cursor.getString(noteIndex)
            }
        }
        return null
    }

    @SuppressLint("Range")
    private fun getNickname(contentResolver: ContentResolver, contactId: String): String? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Nickname.NAME)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Nickname.NAME)
            if (cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    @SuppressLint("Range")
    private fun getPhoneticName(contentResolver: ContentResolver, contactId: String): String? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)

        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val phoneticIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME)
            if (cursor.moveToFirst()) {
                return cursor.getString(phoneticIndex)
            }
        }
        return null
    }

    private fun getPhoneTypeLabel(type: Int, label: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Fax Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label ?: "Custom"
            else -> "Unknown"
        }
    }

    private fun getEmailTypeLabel(type: Int, label: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label ?: "Custom"
            else -> "Unknown"
        }
    }

    private fun getAddressTypeLabel(type: Int, label: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> label ?: "Custom"
            else -> "Unknown"
        }
    }

    private fun getImProtocolLabel(protocol: Int): String {
        return when (protocol) {
            ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM -> "AIM"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN -> "MSN"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO -> "Yahoo"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE -> "Skype"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ -> "QQ"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK -> "Google Talk"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ -> "ICQ"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER -> "Jabber"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING -> "NetMeeting"
            ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM -> "Custom"
            else -> "Unknown"
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Normalize US numbers
        return when {
            cleaned.length == 10 -> cleaned
            cleaned.length == 11 && cleaned.startsWith("1") -> cleaned.substring(1)
            cleaned.length > 11 && cleaned.startsWith("+1") -> cleaned.substring(2)
            else -> cleaned
        }
    }

    private data class OrganizationInfo(
        val company: String?,
        val title: String?,
        val department: String?
    )

    private data class EventInfo(
        val birthday: String?,
        val anniversary: String?
    )
}
