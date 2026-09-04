package com.saebm.smsntfy.smtp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.net.SocketFactory

sealed interface SmtpSendResult {
    data object Sent : SmtpSendResult
    data class Failed(val reason: String) : SmtpSendResult
}

/**
 * Minimal SMTP client with AUTH LOGIN and STARTTLS-less plaintext delivery
 * (callers should use port 465 via TLS socket factory or an internal relay).
 * Fail-closed: any unexpected reply aborts the session without retry loops.
 */
class SmtpClient(
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val logWarning: (String) -> Unit = { Log.w(TAG, it) },
    private val connect: (Socket, String, Int) -> Unit = { socket, host, port ->
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
    }
) {

    suspend fun send(config: SmtpConfig, message: SmtpMessage): SmtpSendResult =
        withContext(Dispatchers.IO) {
            if (!config.enabled) return@withContext SmtpSendResult.Failed("SMTP destination is not configured")
            try {
                openSession(config, message)
                SmtpSendResult.Sent
            } catch (error: Exception) {
                logWarning("SMTP send failed: ${error.javaClass.simpleName}")
                SmtpSendResult.Failed(safeCategory(error))
            } catch (error: LinkageError) {
                logWarning("SMTP send linkage failure: ${error.javaClass.simpleName}")
                SmtpSendResult.Failed("SMTP transport unavailable")
            }
        }

    private fun openSession(config: SmtpConfig, message: SmtpMessage) {
        val socket = socketFactory.createSocket()
        connect(socket, config.host, config.port)
        socket.soTimeout = readTimeoutMs
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val out = s.getOutputStream()

            fun readReply(): Pair<Int, String> {
                var line = reader.readLine() ?: throw SmtpProtocolException("connection closed")
                var code = line.take(3).toIntOrNull() ?: throw SmtpProtocolException("invalid reply")
                while (line.length >= 4 && line[3] == '-') {
                    line = reader.readLine() ?: throw SmtpProtocolException("connection closed")
                }
                return code to line
            }

            fun command(cmd: String) {
                out.write((cmd + "\r\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }

            fun expect(cmd: String, vararg ok: Int): Pair<Int, String> {
                command(cmd)
                val (code, line) = readReply()
                if (code !in ok) throw SmtpProtocolException("rejected: $code")
                return code to line
            }

            // Server greeting arrives before any command
            readReply()

            expect("EHLO smsntfy", 250)
            // Advertise-only AUTH LOGIN
            out.write(("AUTH LOGIN\r\n").toByteArray(Charsets.UTF_8)); out.flush()
            readReply() // 334
            out.write((b64(config.username) + "\r\n").toByteArray(Charsets.UTF_8)); out.flush()
            readReply() // 334
            out.write((b64(config.password) + "\r\n").toByteArray(Charsets.UTF_8)); out.flush()
            val authReply = readReply()
            if (authReply.first != 235) throw SmtpProtocolException("authentication rejected")

            expect("MAIL FROM:<${config.from}>", 250)
            expect("RCPT TO:<${config.recipient}>", 250)
            expect("DATA", 354)
            val data = buildString {
                append("From: <${config.from}>\r\n")
                append("To: <${config.recipient}>\r\n")
                append("Subject: ${sanitizeHeader(message.subject)}\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("\r\n")
                append(message.body.replace("\r\n", "\n").replace("\n", "\r\n"))
                append("\r\n.\r\n")
            }
            expect(data, 250)
            expect("QUIT", 221)
        }
    }

    private fun b64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun sanitizeHeader(value: String): String =
        value.replace(Regex("[\\r\\n\\u0000-\\u001F\\u007F]"), " ").trim()

    private fun safeCategory(error: Throwable): String = when (error) {
        is SmtpProtocolException -> error.message ?: "SMTP protocol failure"
        is java.net.UnknownHostException -> "SMTP host not found"
        is java.io.IOException -> "SMTP connection failed"
        else -> "SMTP send failed"
    }

    private class SmtpProtocolException(message: String) : Exception(message)

    private companion object {
        const val TAG = "SmsNtfySmtp"
    }
}
