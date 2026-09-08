package com.openminis.app.data

import com.openminis.app.data.model.SSHAuthType
import com.openminis.app.data.model.SSHServerEntry
import com.openminis.app.data.model.SSHServerSecret
import com.openminis.app.tools.AgentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SSHServerModelTest {

    @Test
    fun sshServerModel_holdsAttributes() {
        val server = SSHServerEntry(
            id = "server-1",
            name = "Test VPS",
            host = "192.168.1.100",
            port = 2222,
            username = "admin",
            authType = SSHAuthType.PRIVATE_KEY,
            keyType = "ED25519",
            note = "Demo node",
        )
        assertEquals("server-1", server.id)
        assertEquals("Test VPS", server.name)
        assertEquals("192.168.1.100", server.host)
        assertEquals(2222, server.port)
        assertEquals("admin", server.username)
        assertEquals(SSHAuthType.PRIVATE_KEY, server.authType)
        assertEquals("ED25519", server.keyType)
        assertEquals("Demo node", server.note)

        val secret = SSHServerSecret(
            password = null,
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nfake\n-----END OPENSSH PRIVATE KEY-----",
            passphrase = "secret-passphrase",
        )
        assertNotNull(secret.privateKey)
        assertEquals("secret-passphrase", secret.passphrase)
    }

    @Test
    fun agentTools_includesSshServerTool() {
        val tools = AgentTools.makeAgentTools()
        val sshTool = tools.find { it.name == "ssh_server" }
        assertNotNull("ssh_server tool must be present in makeAgentTools", sshTool)
        assertTrue(sshTool!!.parameters.containsKey("action"))
        assertTrue(sshTool.parameters.containsKey("host"))
        assertTrue(sshTool.parameters.containsKey("auth_type"))
        assertTrue(sshTool.parameters.containsKey("private_key"))
        assertTrue(sshTool.parameters.containsKey("password"))
    }
}
