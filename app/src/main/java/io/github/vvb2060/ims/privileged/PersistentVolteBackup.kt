package io.github.vvb2060.ims.privileged

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** 恢复记录不参加自动备份，SIM 标识仅保存摘要，避免 subId 被复用时恢复到另一张卡。 */
internal class PersistentVolteBackup(context: Context, subId: Int) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "persistent_volte_$subId.json"))

    data class Entry(val identity: String, val values: VolteOriginalValues)

    fun read(): Entry? {
        val text = try {
            file.openRead().bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            check(!file.baseFile.exists()) { "Cannot read VoLTE recovery record" }
            return null
        }
        val json = JSONObject(text)
        check(json.getInt("version") == 1) { "Unsupported VoLTE recovery record" }
        return Entry(json.getString("identity"), VolteOriginalValues(json.getInt("optIn"), json.getInt("user")))
    }

    fun save(entry: Entry) {
        val bytes = JSONObject().put("version", 1).put("identity", entry.identity)
            .put("optIn", entry.values.optIn).put("user", entry.values.userSetting)
            .toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (t: Throwable) {
            file.failWrite(stream)
            throw t
        }
        check(read() == entry) { "VoLTE recovery record verification failed" }
    }

    fun clear() {
        file.delete()
        check(read() == null) { "Cannot remove VoLTE recovery record" }
    }

    companion object {
        fun identityDigest(identity: String): String = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
