package com.openminis.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.openminis.app.data.model.SSHAuthType
import com.openminis.app.data.model.SSHServerEntry
import com.openminis.app.data.model.SSHServerSecret
import com.openminis.app.util.EncryptedPrefsFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages stored SSH servers and credentials.
 * Metadata (host, port, username, labels, authType) stored in ssh-servers.json.
 * Sensitive values (passwords, private keys, passphrases) stored in EncryptedSharedPreferences (AES256-GCM).
 */
class SSHServerRepository(private val context: Context) {

    companion object {
        private const val TAG = "SSHServerRepository"
        private const val METADATA_FILE = "ssh-servers.json"
        private const val ENCRYPTED_PREFS_NAME = "ssh_server_secrets"
    }

    private val _servers = MutableStateFlow<List<SSHServerEntry>>(emptyList())
    val servers: StateFlow<List<SSHServerEntry>> = _servers.asStateFlow()

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedPrefsFactory.safeCreate(context, ENCRYPTED_PREFS_NAME)
    }

    private val metadataFile: File
        get() = File(context.filesDir, METADATA_FILE)

    init {
        loadMetadata()
    }

    @Synchronized
    fun loadMetadata() {
        if (!metadataFile.exists()) {
            _servers.value = emptyList()
            return
        }
        try {
            val content = metadataFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<SSHServerEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
                val name = obj.optString("name", "")
                val host = obj.optString("host", "")
                val port = obj.optInt("port", 22)
                val username = obj.optString("username", "root")
                val authTypeStr = obj.optString("authType", SSHAuthType.PASSWORD.name)
                val authType = runCatching { SSHAuthType.valueOf(authTypeStr) }.getOrDefault(SSHAuthType.PASSWORD)
                val keyType = obj.optString("keyType", "AUTO")
                val note = obj.optString("note", "")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

                if (name.isNotEmpty() && host.isNotEmpty()) {
                    list.add(
                        SSHServerEntry(
                            id = id,
                            name = name,
                            host = host,
                            port = port,
                            username = username,
                            authType = authType,
                            keyType = keyType,
                            note = note,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                        )
                    )
                }
            }
            _servers.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SSH servers metadata", e)
            _servers.value = emptyList()
        }
    }

    @Synchronized
    private fun persistMetadata(list: List<SSHServerEntry>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("host", item.host)
                    put("port", item.port)
                    put("username", item.username)
                    put("authType", item.authType.name)
                    put("keyType", item.keyType)
                    put("note", item.note)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
                array.put(obj)
            }
            metadataFile.writeText(array.toString(2))
            _servers.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SSH servers metadata", e)
        }
    }

    fun getServer(id: String): SSHServerEntry? =
        _servers.value.find { it.id == id }

    fun getServerByName(name: String): SSHServerEntry? =
        _servers.value.find { it.name.equals(name, ignoreCase = true) }

    fun getSecret(id: String): SSHServerSecret {
        return try {
            val pwd = encryptedPrefs.getString("${id}_password", null)
            val key = encryptedPrefs.getString("${id}_private_key", null)
            val pass = encryptedPrefs.getString("${id}_passphrase", null)
            SSHServerSecret(password = pwd, privateKey = key, passphrase = pass)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read encrypted secrets for $id", e)
            SSHServerSecret()
        }
    }

    @Synchronized
    fun saveServer(entry: SSHServerEntry, secret: SSHServerSecret): SSHServerEntry {
        val updatedList = _servers.value.toMutableList()
        val index = updatedList.indexOfFirst { it.id == entry.id }
        val now = System.currentTimeMillis()
        val finalEntry = entry.copy(updatedAt = now)

        if (index >= 0) {
            updatedList[index] = finalEntry
        } else {
            updatedList.add(finalEntry)
        }

        try {
            val editor = encryptedPrefs.edit()
            if (secret.password != null) {
                editor.putString("${entry.id}_password", secret.password)
            } else if (entry.authType != SSHAuthType.PASSWORD) {
                editor.remove("${entry.id}_password")
            }

            if (secret.privateKey != null) {
                editor.putString("${entry.id}_private_key", secret.privateKey)
            } else if (entry.authType != SSHAuthType.PRIVATE_KEY) {
                editor.remove("${entry.id}_private_key")
            }

            if (secret.passphrase != null) {
                editor.putString("${entry.id}_passphrase", secret.passphrase)
            } else if (entry.authType != SSHAuthType.PRIVATE_KEY) {
                editor.remove("${entry.id}_passphrase")
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store encrypted secret for ${entry.id}", e)
        }

        persistMetadata(updatedList)
        return finalEntry
    }

    @Synchronized
    fun deleteServer(id: String): Boolean {
        val updatedList = _servers.value.toMutableList()
        val removed = updatedList.removeAll { it.id == id }
        if (removed) {
            try {
                encryptedPrefs.edit()
                    .remove("${id}_password")
                    .remove("${id}_private_key")
                    .remove("${id}_passphrase")
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove encrypted secrets for $id", e)
            }
            persistMetadata(updatedList)
        }
        return removed
    }
}
