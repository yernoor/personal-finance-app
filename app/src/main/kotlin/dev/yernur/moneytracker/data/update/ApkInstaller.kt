package dev.yernur.moneytracker.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow

/** What the update screen needs from the installer; a fake stands in for it in tests. */
interface UpdateInstaller {
    /** Hands the file to Android's installer. The system confirms, installs and restarts the app. */
    fun install(file: File)
}

/** The install result arrives in a broadcast, after the screen that asked may be gone; a failure is parked here for it to show. */
object InstallStatus {
    val failure = MutableStateFlow<String?>(null)
}

/** A response body; [resumed] is true when the server honoured the Range request and the body starts at the requested offset. */
class Opened(val input: InputStream, val resumed: Boolean) : java.io.Closeable {
    override fun close() = input.close()
}

/**
 * Downloads the release APK into the cache and installs it through [PackageInstaller], as an update of this very app. Android only accepts it
 * when it is signed with the same key as the installed one, so a tampered file cannot replace the app; the sha256 check on top catches a bad download early.
 * A download that is cut off keeps its `.part` file, and the next call continues from there (HTTP Range).
 */
class ApkInstaller(
    private val context: Context,
    private val open: (url: String, offset: Long) -> Opened = { url, offset ->
        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        Opened(connection.inputStream, connection.responseCode == 206)
    },
) : UpdateInstaller {
    /** Blocking. Downloads and checks the APK; [progress] goes 0..1. An IOException leaves the partial file for the next try. */
    fun download(apk: ApkAsset, progress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, apk.name)
        val part = File(dir, apk.name + ".part")
        dir.listFiles()?.filter { it != file && it != part }?.forEach { it.delete() } // one update at a time: drop any older download
        if (file.exists()) {
            runCatching { verify(file, apk) }.onSuccess { return file }
            file.delete()
        }
        try {
            fetch(apk, part, progress)
            verify(part, apk)
        } catch (e: UpdateException) {
            part.delete()
            throw e
        }
        if (!part.renameTo(file)) throw UpdateException("Could not save the downloaded file.")
        return file
    }

    private fun fetch(apk: ApkAsset, part: File, progress: (Float) -> Unit) {
        var offset = if (apk.size > 0 && part.length() in 1 until apk.size) part.length() else 0L
        open(apk.url, offset).use { opened ->
            if (!opened.resumed) offset = 0 // the server sent the whole file again
            java.io.FileOutputStream(part, offset > 0).use { out ->
                var bytes = offset
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = opened.input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    bytes += n
                    if (apk.size > 0) progress((bytes.toFloat() / apk.size).coerceAtMost(1f))
                }
            }
        }
    }

    private fun verify(file: File, apk: ApkAsset) {
        if (apk.size > 0 && file.length() != apk.size) {
            // A cut-off stream is not a failure to throw away: the next call resumes. Only a full-length file that is wrong gets deleted.
            if (file.length() < apk.size) throw java.io.IOException("The download is incomplete (${file.length()} of ${apk.size} bytes).")
            throw UpdateException("The downloaded file has the wrong size.")
        }
        if (apk.sha256 == null) return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(apk.sha256, ignoreCase = true)) throw UpdateException("The downloaded file does not match GitHub's checksum.")
    }

    override fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Asks Android not to show its confirmation. It may still ask (the first time, or on a phone that insists), see InstallResultReceiver.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("money-tracker.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // FLAG_MUTABLE: the system adds the result extras to this intent
            val result = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallResultReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
    }
}

/** Android's own message says it all but cryptically; the signature mismatch is the one the owner will meet (debug build vs release build). */
fun describeInstallFailure(status: Int, message: String?): String = when {
    message?.contains("UPDATE_INCOMPATIBLE") == true ->
        "Android refused the update: it is signed with a different key than the installed app (a debug build and a release build cannot replace each other). " +
            "Sync now first (Google Sheets screen), or data since the last sync is lost. Then uninstall the app, which deletes its local data, and install the release."
    message != null -> message
    else -> "The install failed (code $status)."
}

/** Receives the installer's answer: shows Android's confirmation when it wants one, parks a failure for the screen. Success restarts the app. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> InstallStatus.failure.value = describeInstallFailure(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
