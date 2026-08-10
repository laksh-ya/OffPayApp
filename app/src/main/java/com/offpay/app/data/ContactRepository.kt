package com.offpay.app.data

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val name: String,
    val phoneNumber: String
)

class ContactRepository(private val context: Context) {

    suspend fun fetchContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contactList = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex).filter { char -> char.isDigit() }
                
                
                if (number.length >= 10) {
                    val cleanedNumber = number.takeLast(10)
                    contactList.add(Contact(name, cleanedNumber))
                }
            }
        }
        
        contactList.distinctBy { it.phoneNumber }
    }
}
