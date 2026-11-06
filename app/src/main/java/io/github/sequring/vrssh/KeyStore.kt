package io.github.sequring.vrssh

import android.util.Base64
import android.webkit.JavascriptInterface
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

/**
 * Manages private keys in the app's key folder (the same folder SshBridge loads keys from,
 * so keys pushed with adb show up here too).
 * Exposed to JS as `Keys`. Every method returns JSON.
 */
class KeyStore(private val dir: () -> File?) {

    @JavascriptInterface
    fun list(): String {
        val arr = JSONArray()
        val folder = dir() ?: return arr.toString()
        folder.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".pub") }
            ?.sortedBy { it.name }
            ?.forEach { file -> arr.put(describe(file)) }
        return arr.toString()
    }

    @JavascriptInterface
    fun importKey(name: String, text: String): String = result {
        val bytes = text.trim().replace("\r\n", "\n").plus("\n").toByteArray()
        val kp = try {
            KeyPair.load(JSch(), bytes, null)
        } catch (e: Exception) {
            error("This doesn't look like a private key: ${e.message}")
        }
        val file = target(name)
        file.writeBytes(bytes)
        writePublic(kp, file, kp.publicKeyComment.orEmpty())
        kp.dispose()
        describe(file)
    }

    @JavascriptInterface
    fun generate(name: String, type: String, comment: String): String = result {
        val file = target(name)
        if (type == "rsa") {
            val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 3072)
            val out = ByteArrayOutputStream()
            kp.writePrivateKey(out)
            file.writeBytes(out.toByteArray())
            writePublic(kp, file, comment)
            kp.dispose()
        } else {
            // JSch can read Ed25519 keys but not write them, so build the OpenSSH file ourselves.
            val priv = Ed25519PrivateKeyParameters(SecureRandom())
            val seed = priv.encoded
            val pub = priv.generatePublicKey().encoded
            val c = cleanComment(comment)
            file.writeText(openSshEd25519(seed, pub, c))
            val blob = sshBlob { str("ssh-ed25519"); bytes(pub) }
            File(file.parentFile, file.name + ".pub").writeText(
                listOf("ssh-ed25519", Base64.encodeToString(blob, Base64.NO_WRAP), c).filter { it.isNotEmpty() }.joinToString(" ") + "\n"
            )
        }
        describe(file)
    }

    /** Unencrypted "openssh-key-v1" private key, the format `ssh-keygen -t ed25519` produces. */
    private fun openSshEd25519(seed: ByteArray, pub: ByteArray, comment: String): String {
        val publicBlob = sshBlob { str("ssh-ed25519"); bytes(pub) }
        val check = SecureRandom().nextInt()
        val private = sshBlob {
            int(check); int(check)
            str("ssh-ed25519"); bytes(pub); bytes(seed + pub); str(comment)
            var pad = 1
            while (size() % 8 != 0) write(pad++)
        }
        val body = sshBlob {
            write("openssh-key-v1".toByteArray()); write(0)
            str("none"); str("none"); str(""); int(1)
            bytes(publicBlob); bytes(private)
        }
        val b64 = Base64.encodeToString(body, Base64.NO_WRAP).chunked(70).joinToString("\n")
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$b64\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    private class SshWriter : ByteArrayOutputStream() {
        fun int(v: Int) { write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v) }
        fun bytes(b: ByteArray) { int(b.size); write(b) }
        fun str(s: String) = bytes(s.toByteArray())
    }

    private inline fun sshBlob(block: SshWriter.() -> Unit): ByteArray = SshWriter().apply(block).toByteArray()

    @JavascriptInterface
    fun delete(name: String): String = result {
        val folder = dir() ?: error("Key folder is unavailable")
        val file = File(folder, name)
        require(file.parentFile == folder && file.exists()) { "Key $name not found" }
        file.delete()
        File(folder, "$name.pub").delete()
        JSONObject()
    }

    private fun target(rawName: String): File {
        val folder = dir() ?: error("Key folder is unavailable")
        folder.mkdirs()
        val name = rawName.trim().ifEmpty { "id_quest" }
        require(name.matches(Regex("[A-Za-z0-9._-]{1,64}")) && !name.endsWith(".pub") && !name.startsWith(".")) {
            "Name may contain only letters, digits, dot, dash and underscore"
        }
        val file = File(folder, name)
        require(!file.exists()) { "A key named $name already exists" }
        return file
    }

    private fun writePublic(kp: KeyPair, file: File, comment: String) {
        val blob = kp.publicKeyBlob ?: return
        val line = listOf(kp.keyTypeString, Base64.encodeToString(blob, Base64.NO_WRAP), cleanComment(comment))
            .filter { it.isNotEmpty() }.joinToString(" ")
        File(file.parentFile, file.name + ".pub").writeText(line + "\n")
    }

    private fun describe(file: File): JSONObject {
        val o = JSONObject().put("name", file.name)
        try {
            val kp = KeyPair.load(JSch(), file.absolutePath)
            val pubFile = File(file.parentFile, file.name + ".pub")
            val pubLine = pubFile.takeIf { it.exists() }?.readText()?.trim()
            val blob = kp.publicKeyBlob
                ?: pubLine?.split(" ")?.getOrNull(1)?.let { Base64.decode(it, Base64.DEFAULT) }
            val type = kp.keyTypeString.takeIf { it.isNotEmpty() && it != "UNKNOWN" }
                ?: pubLine?.substringBefore(" ")
                ?: "unknown"
            o.put("type", type)
            o.put("encrypted", kp.isEncrypted)
            if (blob != null) {
                val comment = pubLine?.split(" ", limit = 3)?.getOrNull(2) ?: cleanComment(kp.publicKeyComment.orEmpty())
                o.put("fingerprint", "SHA256:" + Base64.encodeToString(sha256(blob), Base64.NO_WRAP or Base64.NO_PADDING))
                o.put("comment", comment)
                o.put("publicKey", listOf(type, Base64.encodeToString(blob, Base64.NO_WRAP), comment)
                    .filter { it.isNotEmpty() }.joinToString(" "))
            }
            kp.dispose()
        } catch (e: Exception) {
            o.put("error", "Unreadable key: ${e.message}")
        }
        return o
    }

    private fun cleanComment(c: String) = c.replace(Regex("[^A-Za-z0-9@._+-]"), "").take(64)

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private inline fun result(block: () -> JSONObject): String = try {
        JSONObject().put("ok", true).put("key", block()).toString()
    } catch (e: Exception) {
        JSONObject().put("ok", false).put("error", e.message ?: e.toString()).toString()
    }
}
