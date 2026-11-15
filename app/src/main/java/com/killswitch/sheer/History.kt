package com.killswitch.sheer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/**
 * Browsing history, kept in a small database on the phone and nowhere else. The engine does not keep history itself, so Sheer records the
 * pages it is told about. Private tabs never reach this: they do not register a history delegate, so nothing is recorded.
 */
object History {
    class Visit(val url: String, val title: String, val last: Long, val count: Int)

    private class Db(c: Context) : SQLiteOpenHelper(c, "history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE visits (url TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', last INTEGER NOT NULL, count INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX visits_last ON visits(last)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var db: Db? = null

    /** Goes up after every change, so a screen showing the list knows to read it again. */
    var version by mutableIntStateOf(0)
        private set

    private fun db(c: Context): SQLiteDatabase {
        val d = db ?: synchronized(this) { db ?: Db(c.applicationContext).also { db = it } }
        return d.writableDatabase
    }

    private fun changed() { main.post { version++ } }

    /** A page was visited: add it, or move it to the top and count it again. */
    fun record(c: Context, url: String, title: String) {
        io.execute {
            runCatching {
                val d = db(c)
                val now = System.currentTimeMillis()
                d.execSQL("INSERT OR IGNORE INTO visits(url, title, last, count) VALUES(?, ?, ?, 0)", arrayOf(url, title, now))
                d.execSQL("UPDATE visits SET last = ?, count = count + 1 WHERE url = ?", arrayOf(now, url))
                changed()
            }
        }
    }

    /** The title arrives after the visit. */
    fun setTitle(c: Context, url: String, title: String) {
        if (title.isEmpty()) return
        io.execute { runCatching { db(c).execSQL("UPDATE visits SET title = ? WHERE url = ?", arrayOf(title, url)); changed() } }
    }

    /** Newest first. Blocks, so call it off the main thread. [text] filters by address or title. */
    fun query(c: Context, text: String, limit: Int): List<Visit> {
        val out = ArrayList<Visit>()
        runCatching {
            val like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            db(c).rawQuery(
                "SELECT url, title, last, count FROM visits WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY last DESC LIMIT ?",
                arrayOf(like, like, limit.toString()),
            ).use { q -> while (q.moveToNext()) out.add(Visit(q.getString(0), q.getString(1), q.getLong(2), q.getInt(3))) }
        }
        return out
    }

    fun delete(c: Context, url: String) {
        io.execute { runCatching { db(c).execSQL("DELETE FROM visits WHERE url = ?", arrayOf(url)); changed() } }
    }

    fun clear(c: Context) {
        io.execute { runCatching { db(c).execSQL("DELETE FROM visits"); changed() } }
    }

    /** Which of these addresses were visited, for colouring links on a page. [done] runs on a background thread. */
    fun visited(c: Context, urls: Array<String>, done: (BooleanArray) -> Unit) {
        io.execute {
            val r = BooleanArray(urls.size)
            runCatching {
                val d = db(c)
                for (i in urls.indices) d.rawQuery("SELECT 1 FROM visits WHERE url = ? LIMIT 1", arrayOf(urls[i])).use { r[i] = it.moveToFirst() }
            }
            done(r)
        }
    }
}
