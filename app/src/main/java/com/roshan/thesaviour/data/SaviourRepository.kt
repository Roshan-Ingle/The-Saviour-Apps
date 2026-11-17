package com.roshan.thesaviour.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String
)

class SaviourRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isProtectionEnabled(): Boolean = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)

    fun setProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    fun isFakeShutdownEnabled(): Boolean = prefs.getBoolean(KEY_FAKE_SHUTDOWN_ENABLED, true)

    fun setFakeShutdownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAKE_SHUTDOWN_ENABLED, enabled).apply()
    }

    fun isFakeShutdownActive(): Boolean = prefs.getBoolean(KEY_FAKE_SHUTDOWN_ACTIVE, false)

    fun setFakeShutdownActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_FAKE_SHUTDOWN_ACTIVE, active).apply()
    }

    fun getSecretPin(): String = prefs.getString(KEY_SECRET_PIN, "2580") ?: "2580"

    fun setSecretPin(pin: String) {
        prefs.edit().putString(KEY_SECRET_PIN, pin).apply()
    }

    fun getLastSosStatus(): String = prefs.getString(KEY_LAST_SOS_STATUS, "Ready & Armed") ?: "Ready & Armed"

    fun setLastSosStatus(status: String) {
        prefs.edit().putString(KEY_LAST_SOS_STATUS, status).apply()
    }

    fun getContacts(): List<EmergencyContact> {
        val raw = prefs.getString(KEY_CONTACTS_JSON, "[]") ?: "[]"
        val list = mutableListOf<EmergencyContact>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EmergencyContact(
                        id = obj.optString("id", i.toString()),
                        name = obj.optString("name", "Emergency"),
                        phone = obj.optString("phone", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun addContact(name: String, phone: String): List<EmergencyContact> {
        val cleanedPhone = phone.trim()
        val cleanedName = name.trim().ifEmpty { "Emergency Contact" }
        val updated = getContacts().toMutableList()
        updated.add(
            EmergencyContact(
                id = System.currentTimeMillis().toString(),
                name = cleanedName,
                phone = cleanedPhone
            )
        )
        saveContacts(updated)
        return updated
    }

    fun removeContact(id: String): List<EmergencyContact> {
        val updated = getContacts().filterNot { it.id == id }
        saveContacts(updated)
        return updated
    }

    private fun saveContacts(contacts: List<EmergencyContact>) {
        val arr = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("phone", contact.phone)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CONTACTS_JSON, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "the_saviour_prefs"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_FAKE_SHUTDOWN_ENABLED = "fake_shutdown_enabled"
        private const val KEY_FAKE_SHUTDOWN_ACTIVE = "fake_shutdown_active"
        private const val KEY_SECRET_PIN = "secret_pin"
        private const val KEY_CONTACTS_JSON = "contacts_json"
        private const val KEY_LAST_SOS_STATUS = "last_sos_status"
    }
}
