package com.openminis.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class SSHAuthType {
    PASSWORD,
    PRIVATE_KEY;

    val displayName: String
        get() = when (this) {
            PASSWORD -> "Password"
            PRIVATE_KEY -> "Private Key"
        }
}

@Serializable
data class SSHServerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: SSHAuthType = SSHAuthType.PASSWORD,
    val keyType: String = "AUTO", // AUTO, ED25519, RSA, ECDSA
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SSHServerSecret(
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
)
