package io.github.sequring.vrssh

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread

/**
 * Bridge between the xterm.js page and SSH sessions, one per tab.
 * JS -> Kotlin: Android.connect/write/resize/disconnect.
 * Kotlin -> JS: onSshData(id, base64), onSshStatus(id, type, message).
 *
 * Status types: info, connected, error (failed before the shell opened),
 * dropped (connection lost after it opened), exited (remote shell ended normally).
 */
class SshBridge(private val activity: Activity, private val webView: WebView) {
    private val conns = ConcurrentHashMap<Int, Conn>()
    private val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Private keys pushed via adb: /sdcard/Android/data/io.github.sequring.vrssh/files/ssh/ */
    private val keyDir: File? get() = activity.getExternalFilesDir("ssh")

    private class Conn(val id: Int, var cols: Int, var rows: Int) {
        val writer = Executors.newSingleThreadExecutor()
        @Volatile var session: Session? = null
        @Volatile var channel: Channel? = null
        @Volatile var setPtySize: ((Int, Int) -> Unit)? = null
        @Volatile var out: OutputStream? = null
        @Volatile var closedByUser = false

        fun close() {
            channel?.disconnect()
            session?.disconnect()
            writer.shutdown()
        }
    }

    @JavascriptInterface
    fun keyInfo(): String = JSONObject()
        .put("dir", keyDir?.absolutePath ?: "")
        .put("keys", JSONArray(privateKeys().map { it.name }))
        .toString()

    @JavascriptInterface
    fun connect(
        id: Int, host: String, port: Int, user: String, password: String,
        cols: Int, rows: Int, tmux: Boolean, key: String, onlyKey: Boolean,
    ) {
        conns.remove(id)?.let { old -> old.closedByUser = true; thread { old.close() } }
        val c = Conn(id, cols, rows)
        conns[id] = c
        thread(name = "ssh-$id") { run(c, host, port, user, password, tmux, key, onlyKey) }
    }

    /**
     * [key] is tried first (the one that worked last time, or the one the user picked);
     * with [onlyKey] no other keys are offered. Servers drop the connection after ~6 failed
     * attempts, so offering every key blindly breaks once there are several.
     */
    private fun run(
        c: Conn, host: String, port: Int, user: String, password: String,
        tmux: Boolean, key: String, onlyKey: Boolean,
    ) {
        var opened = false
        try {
            status(c, "info", "Connecting to $user@$host:$port …")
            val jsch = JSch()
            val knownHosts = File(activity.filesDir, "known_hosts").apply { if (!exists()) createNewFile() }
            jsch.setKnownHosts(knownHosts.absolutePath)
            val tofu = TofuHostKeys(jsch.hostKeyRepository)
            jsch.hostKeyRepository = tofu

            val files = privateKeys()
                .filter { !onlyKey || it.name == key }
                .sortedByDescending { it.name == key }
            if (onlyKey && files.isEmpty()) error("Key $key not found")
            for (file in files) {
                try {
                    jsch.addIdentity(file.absolutePath, password.ifEmpty { null }?.toByteArray())
                } catch (e: Exception) {
                    status(c, "info", "Key ${file.name} skipped: ${e.message}")
                }
            }
            val tracking = TrackingIdentities(jsch.identityRepository.identities.toList())
            jsch.identityRepository = tracking

            val s = jsch.getSession(user, host, port)
            c.session = s
            if (password.isNotEmpty()) s.setPassword(password)
            s.userInfo = PasswordInfo(password)
            s.setConfig("StrictHostKeyChecking", "yes")
            s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            s.serverAliveInterval = 15_000
            s.serverAliveCountMax = 3
            s.connect(15_000)

            // ChannelSession (the common parent) is package-private in JSch, so configure each type explicitly.
            val ch: Channel = if (tmux) {
                (s.openChannel("exec") as ChannelExec).apply {
                    setPty(true)
                    setPtyType("xterm-256color")
                    setPtySize(c.cols, c.rows, 0, 0)
                    setEnv("LANG", "en_US.UTF-8")
                    setCommand("command -v tmux >/dev/null && exec tmux new-session -A -s quest${c.id} || exec \$SHELL -l")
                    c.setPtySize = { cols, rows -> setPtySize(cols, rows, 0, 0) }
                }
            } else {
                (s.openChannel("shell") as ChannelShell).apply {
                    setPtyType("xterm-256color")
                    setPtySize(c.cols, c.rows, 0, 0)
                    setEnv("LANG", "en_US.UTF-8")
                    c.setPtySize = { cols, rows -> setPtySize(cols, rows, 0, 0) }
                }
            }
            val input = ch.inputStream
            c.out = ch.outputStream
            c.channel = ch
            ch.connect(10_000)
            opened = true
            status(c, "connected", tracking.used?.let { File(it).name } ?: "")
            if (tofu.addedNew) {
                status(c, "info", "New host, key saved: ${s.hostKey.type} ${s.hostKey.getFingerPrint(jsch)}")
            }

            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val b64 = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)
                if (conns[c.id] === c) js("onSshData(${c.id}, '$b64')")
            }
            // OpenSSH sends exit-status after EOF, so wait briefly for the channel to close.
            val deadline = System.currentTimeMillis() + 2_000
            while (!ch.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val code = ch.exitStatus
            if (code >= 0) status(c, "exited", code.toString())
            else status(c, "dropped", "Connection lost")
        } catch (e: Exception) {
            status(c, if (opened) "dropped" else "error", describe(e, host, port))
        } finally {
            c.close()
        }
    }

    @JavascriptInterface
    fun write(id: Int, data: String) {
        val c = conns[id] ?: return
        val bytes = data.toByteArray(Charsets.UTF_8)
        try {
            c.writer.execute {
                try {
                    c.out?.apply { write(bytes); flush() }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // writer already shut down
        }
    }

    @JavascriptInterface
    fun resize(id: Int, cols: Int, rows: Int) {
        val c = conns[id] ?: return
        c.cols = cols
        c.rows = rows
        thread { c.setPtySize?.invoke(cols, rows) }
    }

    /** Keeps the native window background in sync with the page theme (no flash on start). */
    @JavascriptInterface
    fun setBackground(css: String) {
        val (r, g, b) = Regex("""(\d+),\s*(\d+),\s*(\d+)""").find(css)?.destructured ?: return
        val color = Color.rgb(r.toInt(), g.toInt(), b.toInt())
        activity.getPreferences(Context.MODE_PRIVATE).edit().putInt("background", color).apply()
        webView.post { webView.setBackgroundColor(color) }
    }

    @JavascriptInterface
    fun copy(text: String) {
        activity.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text)) }
    }

    @JavascriptInterface
    fun paste(): String {
        val task = FutureTask {
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString() ?: ""
        }
        activity.runOnUiThread(task)
        return task.get()
    }

    @JavascriptInterface
    fun disconnect(id: Int) {
        conns.remove(id)?.let { c -> c.closedByUser = true; thread { c.close() } }
    }

    /** After the headset wakes up, probe every session so dead ones get reported as dropped right away. */
    fun checkAlive() {
        for (c in conns.values) {
            val s = c.session ?: continue
            thread {
                try {
                    s.sendKeepAliveMsg()
                } catch (_: Exception) {
                    s.disconnect()
                }
            }
        }
    }

    fun disconnectAll() {
        for (id in conns.keys) disconnect(id)
    }

    private fun describe(e: Exception, host: String, port: Int): String {
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        val msg = e.message.orEmpty()
        return when {
            chain.any { it is UnknownHostException } -> "Host $host not found"
            chain.any { it is ConnectException } -> "Can't connect to $host:$port — connection refused"
            chain.any { it is SocketTimeoutException } || msg.contains("timeout") -> "$host:$port is not responding (timeout)"
            msg.contains("Too many authentication failures") ->
                "The server gave up after too many key attempts — pick the right key for this host in the Key list"
            msg.startsWith("Auth fail") || msg.startsWith("Auth cancel") -> "Authentication failed: wrong password or key not accepted"
            e is JSchChangedHostKeyException -> "Host key for $host has CHANGED! Possible man-in-the-middle — connection refused"
            else -> msg.ifEmpty { e.toString() }
        }
    }

    private fun privateKeys(): List<File> =
        keyDir?.listFiles()?.filter { it.isFile && !it.name.endsWith(".pub") }?.sortedBy { it.name } ?: emptyList()

    private fun status(c: Conn, type: String, message: String) {
        if (c.closedByUser || conns[c.id] !== c) return
        js("onSshStatus(${c.id}, ${JSONObject.quote(type)}, ${JSONObject.quote(message)})")
    }

    private fun js(code: String) = webView.post { webView.evaluateJavascript(code, null) }

    /**
     * Offers the keys in a fixed order and notes which one the server accepted:
     * the server only asks for a signature from a key it is willing to accept.
     */
    private class TrackingIdentities(private val ids: List<Identity>) : IdentityRepository {
        @Volatile var used: String? = null

        override fun getName() = "tracking"
        override fun getStatus() = IdentityRepository.RUNNING
        override fun getIdentities(): Vector<Identity> = Vector(ids.map { Tracked(it) })
        override fun add(identity: ByteArray) = false
        override fun remove(blob: ByteArray) = false
        override fun removeAll() {}

        private inner class Tracked(private val inner: Identity) : Identity by inner {
            override fun getSignature(data: ByteArray): ByteArray? = inner.getSignature(data).also { used = inner.name }
            override fun getSignature(data: ByteArray, alg: String): ByteArray? = inner.getSignature(data, alg).also { used = inner.name }
        }
    }

    /** Trust on first use: remember unknown hosts, reject changed keys. */
    private class TofuHostKeys(private val inner: HostKeyRepository) : HostKeyRepository by inner {
        var addedNew = false

        override fun check(host: String, key: ByteArray): Int {
            val result = inner.check(host, key)
            if (result == HostKeyRepository.NOT_INCLUDED) {
                inner.add(HostKey(host, key), null)
                addedNew = true
                return HostKeyRepository.OK
            }
            return result
        }
    }

    /** Answers keyboard-interactive password prompts with the entered password. */
    private class PasswordInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase() = password
        override fun getPassword() = password
        override fun promptPassword(message: String) = password.isNotEmpty()
        override fun promptPassphrase(message: String) = password.isNotEmpty()
        override fun promptYesNo(message: String) = false
        override fun showMessage(message: String) {}
        override fun promptKeyboardInteractive(
            destination: String, name: String, instruction: String,
            prompt: Array<String>, echo: BooleanArray,
        ): Array<String>? = if (password.isEmpty()) null else Array(prompt.size) { password }
    }
}
