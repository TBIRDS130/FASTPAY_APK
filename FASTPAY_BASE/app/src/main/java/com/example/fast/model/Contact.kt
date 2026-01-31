package com.example.fast.model

data class Contact(
    val id: String,
    val name: String,
    val displayName: String,
    val phoneNumber: String,
    val phones: List<PhoneInfo> = emptyList(),
    val emails: List<EmailInfo> = emptyList(),
    val addresses: List<AddressInfo> = emptyList(),
    val photoUri: String? = null,
    val thumbnailUri: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
    val websites: List<String> = emptyList(),
    val imAccounts: List<ImAccount> = emptyList(),
    val birthday: String? = null,
    val anniversary: String? = null,
    val notes: String? = null,
    val lastContacted: Long? = null,
    val timesContacted: Int = 0,
    val isStarred: Boolean = false,
    val nickname: String? = null,
    val phoneticName: String? = null
)

data class PhoneInfo(
    val number: String,
    val type: Int,
    val typeLabel: String,
    val label: String? = null,
    val isPrimary: Boolean = false
)

data class EmailInfo(
    val address: String,
    val type: Int,
    val typeLabel: String,
    val label: String? = null,
    val isPrimary: Boolean = false
)

data class AddressInfo(
    val street: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val formattedAddress: String? = null,
    val type: Int,
    val typeLabel: String
)

data class ImAccount(
    val data: String,
    val protocol: Int,
    val protocolLabel: String,
    val customProtocol: String? = null
)

