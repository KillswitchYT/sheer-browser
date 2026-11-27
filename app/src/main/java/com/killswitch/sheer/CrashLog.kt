package com.killswitch.sheer

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finds out why the app died, with no computer needed. Two sources, both kept on the phone and never sent anywhere:
 * the Java stack trace of an uncaught exception, which we write to a file ourselves, and Android's own record of how each process
 * ended, which also covers crashes in native code (the Firefox engine) that no Java handler can see.
 * The next launch shows the report once, and the person can copy it.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "crash.txt")
    private fun stamp(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))

    /** Call first thing in Application.onCreate, in every process the app runs (the engine has its own). */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val proc = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else "?"
                file(app).appendText("JAVA CRASH ${stamp(System.currentTimeMillis())} process=$proc thread=${thread.name}\n${Log.getStackTraceString(e)}\n")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
        ApplicationExitInfo.REASON_ANR -> "NOT RESPONDING"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "FAILED TO START"
        ApplicationExitInfo.REASON_SIGNALED -> "KILLED BY SIGNAL"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "OUT OF MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "TOO MANY RESOURCES"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY DIED"
        else -> null
    }

    /** The readable pieces of a native crash record (it is a binary format): function names, library names, the abort message. */
    private fun readable(bytes: ByteArray): List<String> {
        val out = LinkedHashSet<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.length >= 7) out.add(sb.toString().take(140)); sb.setLength(0) }
        for (b in bytes) {
            val ch = b.toInt() and 0xFF
            if (ch in 0x20..0x7E) sb.append(ch.toChar()) else flush()
        }
        flush()
        return out.take(70)
    }

    private fun events(c: Context) = File(c.filesDir, "events.txt")

    /** One line in the on-phone log of things that went wrong inside the app. Kept to the last 30 lines. */
    fun event(c: Context, text: String) {
        runCatching {
            val f = events(c)
            val old = if (f.exists()) f.readLines().takeLast(29) else emptyList()
            f.writeText((old + "${stamp(System.currentTimeMillis())}  $text").joinToString("\n") + "\n")
        }
    }

    /** For Settings, About, Recent stops: what the app logged, then how Android says each process ended. Newest first. */
    fun recent(c: Context): String {
        val sb = StringBuilder()
        runCatching {
            val f = events(c)
            if (f.exists()) f.readLines().takeLast(8).reversed().forEach { sb.append(it).append('\n') }
        }
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (e in am.getHistoricalProcessExitReasons(c.packageName, 0, 8)) {
                val name = reasonName(e.reason) ?: "reason ${e.reason}"
                val proc = e.processName.removePrefix(c.packageName).ifEmpty { "main" }
                sb.append("${stamp(e.timestamp)}  Android: $proc, $name, ${e.description.orEmpty()}\n")
            }
        }
        return if (sb.isEmpty()) "Nothing has stopped." else sb.toString().trimEnd()
    }

    /** What went wrong since the last report, or null when nothing did. Marks it as shown. */
    fun pending(c: Context): String? {
        val parts = ArrayList<String>()
        runCatching {
            val f = file(c)
            if (f.exists()) { parts.add(f.readText().take(3500)); f.delete() }
        }
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val seen = Settings.str(c, "exit_seen", "0").toLongOrNull() ?: 0L
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var newest = seen
            for (e in am.getHistoricalProcessExitReasons(c.packageName, 0, 6)) {
                if (e.timestamp <= seen) continue
                if (e.timestamp > newest) newest = e.timestamp
                val name = reasonName(e.reason) ?: continue
                val sb = StringBuilder("ANDROID SAYS ${stamp(e.timestamp)} process=${e.processName} $name status=${e.status}\n${e.description.orEmpty()}\n")
                if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    runCatching { e.traceInputStream?.use { s -> readable(s.readBytes()).forEach { sb.append(it).append('\n') } } }
                }
                if (e.reason == ApplicationExitInfo.REASON_ANR) {
                    // For a freeze, the useful part is what the main thread was busy with.
                    runCatching {
                        e.traceInputStream?.use { s ->
                            val lines = s.bufferedReader().readLines()
                            val i = lines.indexOfFirst { it.startsWith("\"main\" prio=") }
                            if (i >= 0) {
                                sb.append("The main thread was in:\n")
                                lines.drop(i + 1).takeWhile { it.isNotBlank() }.take(40).forEach { sb.append(it.trim().take(130)).append('\n') }
                            }
                        }
                    }
                }
                parts.add(sb.toString().take(3500))
            }
            Settings.setStr(c, "exit_seen", newest.toString())
        }
        return if (parts.isEmpty()) null else parts.joinToString("\n----\n").take(7000)
    }
}
