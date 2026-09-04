package com.saebm.smsntfy.smtp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class SmtpClientTest {

    /** Records written bytes and replays scripted server responses. */
    private class ScriptedSocket(responses: String) : Socket() {
        val written = ByteArrayOutputStream()
        private val input = ByteArrayInputStream(responses.toByteArray(Charsets.ISO_8859_1))
        override fun getOutputStream() = object : java.io.OutputStream() {
            override fun write(b: Int) { written.write(b) }
        }
        override fun getInputStream() = input
        override fun isConnected() = true
        override fun close() {}
    }

    private fun factory(responses: String): SocketFactory = object : SocketFactory() {
        override fun createSocket() = ScriptedSocket(responses)
        override fun createSocket(host: String?, port: Int) = ScriptedSocket(responses)
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int) = ScriptedSocket(responses)
        override fun createSocket(host: InetAddress?, port: Int) = ScriptedSocket(responses)
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int) = ScriptedSocket(responses)
    }

    private val happyPath = listOf(
        "220 mail.example.com ESMTP",
        "250-mail.example.com", "250 AUTH LOGIN PLAIN",
        "334 ", "334 ", "235 Authentication successful",
        "250 OK", "250 OK",
        "354 End data",
        "250 OK: queued",
        "221 Bye"
    ).joinToString("\r\n", postfix = "\r\n")

    private val authFailed = listOf(
        "220 mail.example.com ESMTP",
        "250-mail.example.com", "250 AUTH LOGIN PLAIN",
        "334 ", "334 ", "535 Authentication failed"
    ).joinToString("\r\n", postfix = "\r\n")

    private fun config() = SmtpConfig(
        enabled = true, host = "mail.example.com", port = 587,
        username = "user@example.com", password = "secret",
        from = "from@example.com", recipient = "to@example.com"
    )

    @Test
    fun sendsMailThroughScriptedSession() {
        val result = runBlocking {
            SmtpClient(socketFactory = factory(happyPath), logWarning = {}, connect = { _, _, _ -> })
                .send(config(), SmtpMessage("Test subject", "Hello\nbody"))
        }
        assertTrue("expected Sent but was $result", result is SmtpSendResult.Sent)
    }

    @Test
    fun authFailureIsRejectedAndNeverLeaksPassword() {
        val result = runBlocking {
            SmtpClient(socketFactory = factory(authFailed), logWarning = {}, connect = { _, _, _ -> })
                .send(config(), SmtpMessage("s", "b"))
        }
        assertTrue(result is SmtpSendResult.Failed)
        assertFalse((result as SmtpSendResult.Failed).reason.contains("secret"))
    }

    @Test
    fun disabledConfigFailsClosed() {
        val result = runBlocking {
            SmtpClient(socketFactory = factory(happyPath), logWarning = {}, connect = { _, _, _ -> })
                .send(config().copy(enabled = false), SmtpMessage("s", "b"))
        }
        assertTrue(result is SmtpSendResult.Failed)
    }
}
