package com.killswitch.sheer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One small update file: it turns exactly the build numbered [from] (whose APK has the fingerprint [fromSha]) into the newest build. */
class PatchInfo(val from: Long, val fromSha: String, val name: String, val size: Long)

/** What CI publishes next to every build as update.json. */
class Manifest(val code: Long, val name: String, val apkName: String, val apkSize: Long, val apkSha: String, val patches: List<PatchInfo>)

/**
 * Updates the app without downloading all of it. The phone is told about the newest build, downloads a patch of a few MB, and rebuilds the
 * new APK from the APK it already has. The result must match the published fingerprint, and Android only installs it if it is signed with
 * the same key as the installed app, so a tampered download cannot get in. It goes online only when the person taps, and only to github.com.
 */
object Updater {
    private const val BASE = "https://github.com/KillswitchYT/sheer-browser/releases/download/latest/"
    private val main = Handler(Looper.getMainLooper())

    sealed interface State {
        data object Idle : State
        data object Checking : State
        class UpToDate(val version: String) : State
        class Available(val manifest: Manifest, val patch: PatchInfo?) : State
        class Working(val label: String, val progress: Float) : State
        data object NeedPermission : State
        data object Confirming : State
        class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    private fun set(s: State) { main.post { state = s } }
    fun failed(message: String) = set(State.Failed(message))

    fun installedCode(c: Context): Long = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

    fun mb(bytes: Long): String = if (bytes >= 10_000_000) "${bytes / 1_000_000} MB" else "%.1f MB".format(bytes / 1_000_000.0)

    private fun open(url: String, from: Long = 0): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Sheer-updater")
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        return c
    }

    fun parse(json: String): Manifest {
        val o = JSONObject(json)
        val apk = o.getJSONObject("apk")
        val ps = o.optJSONArray("patches")
        val patches = ArrayList<PatchInfo>()
        if (ps != null) for (i in 0 until ps.length()) {
            val p = ps.getJSONObject(i)
            patches.add(PatchInfo(p.getLong("from"), p.getString("fromSha"), p.getString("name"), p.getLong("size")))
        }
        return Manifest(o.getLong("versionCode"), o.getString("versionName"), apk.getString("name"), apk.getLong("size"), apk.getString("sha256"), patches)
    }

    /** Asks GitHub what the newest build is. A few hundred bytes. */
    fun check(c: Context) {
        val app = c.applicationContext
        set(State.Checking)
        Thread {
            try {
                val conn = open(BASE + "update.json")
                if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
                val m = parse(conn.inputStream.bufferedReader().use { it.readText() })
                val mine = installedCode(app)
                if (m.code <= mine) set(State.UpToDate(app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"))
                else set(State.Available(m, m.patches.firstOrNull { it.from == mine }))
            } catch (e: Exception) {
                failed("Could not check: ${e.message ?: e.javaClass.simpleName}")
            }
        }.start()
    }

    /** Downloads [name] into [dest], carrying on from where it stopped if the connection drops (mobile data does that). */
    private fun download(name: String, dest: File, size: Long, label: String) {
        var tries = 0
        while (dest.length() < size) {
            try {
                val have = dest.length()
                val conn = open(BASE + name, have)
                val code = conn.responseCode
                if (code != 200 && code != 206) throw IOException("GitHub answered $code")
                val append = code == 206
                if (!append) dest.delete() // the server sent everything again
                conn.inputStream.use { ins ->
                    FileOutputStream(dest, append).use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = dest.length()
                        var last = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 300_000) { last = done; set(State.Working(label, done.toFloat() / size)) }
                        }
                    }
                }
            } catch (e: IOException) {
                if (++tries > 6) throw IOException("the connection keeps dropping (${e.message})")
                Thread.sleep(1500)
            }
        }
        if (dest.length() != size) throw IOException("the download is the wrong size")
    }

    /** Does the whole update. [full] downloads all of the APK instead of a patch; it is only ever started by an explicit tap. */
    fun update(c: Context, a: State.Available, full: Boolean) {
        val app = c.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            set(State.NeedPermission)
            c.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val m = a.manifest
        val patch = if (full) null else a.patch
        set(State.Working("Starting", 0f))
        Thread {
            try {
                val dir = File(app.cacheDir, "update").apply { mkdirs() }
                if (app.cacheDir.usableSpace < m.apkSize * 22 / 10) throw IOException("not enough free space (about ${mb(m.apkSize * 22 / 10)} needed)")
                // Anything left from an earlier try that is not part of this one goes.
                dir.listFiles()?.forEach { if (it.name != (patch?.name ?: m.apkName) && it.name != "new.apk") it.delete() }
                val newApk = File(dir, "new.apk")
                if (patch != null) {
                    val pf = File(dir, patch.name)
                    download(patch.name, pf, patch.size, "Downloading the update")
                    set(State.Working("Checking this app", 0f))
                    val installed = File(app.applicationInfo.sourceDir)
                    if (Patch.sha256(installed) != patch.fromSha) throw IOException("this copy of the app is not the one the patch is for, use the full download")
                    set(State.Working("Building the new version", 0f))
                    val got = Patch.apply(installed, pf, newApk) { d, t -> set(State.Working("Building the new version", d.toFloat() / t)) }
                    if (got != m.apkSha) throw IOException("the rebuilt app does not match, use the full download")
                    pf.delete()
                } else {
                    download(m.apkName, newApk, m.apkSize, "Downloading the whole app")
                    set(State.Working("Checking the download", 0f))
                    if (Patch.sha256(newApk) != m.apkSha) { newApk.delete(); throw IOException("the download is damaged, try again") }
                }
                set(State.Working("Handing it to Android", 1f))
                install(app, newApk)
            } catch (e: Exception) {
                failed("Update failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }.start()
    }

    private fun install(app: Context, apk: File) {
        val pi = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        params.setSize(apk.length())
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("Sheer.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 1 shl 16) }
                s.fsync(out)
            }
            // The result comes back to InstallReceiver. The flag has to be mutable, or Android cannot add its answer to the intent.
            val pending = PendingIntent.getBroadcast(
                app, id, Intent(app, InstallReceiver::class.java),
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            set(State.Confirming)
            s.commit(pending.intentSender)
        }
    }
}

/** Gets Android's answer to the install: show its confirmation screen, or report why it refused. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is being replaced right now
            else -> Updater.failed("Android refused the update: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "no reason given"}")
        }
    }
}
