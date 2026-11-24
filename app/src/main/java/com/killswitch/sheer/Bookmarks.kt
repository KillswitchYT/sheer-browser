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

/** Pages the person chose to keep, in a small database on the phone and nowhere else. Same shape as [History], separate file. */
object Bookmarks {
    class Mark(val url: String, val title: String, val added: Long)

    private class Db(c: Context) : SQLiteOpenHelper(c, "bookmarks.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE marks (url TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', added INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX marks_added ON marks(added)")
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

    fun add(c: Context, url: String, title: String) {
        io.execute {
            runCatching {
                db(c).execSQL("INSERT OR REPLACE INTO marks(url, title, added) VALUES(?, ?, ?)", arrayOf<Any>(url, title, System.currentTimeMillis()))
                changed()
            }
        }
    }

    fun remove(c: Context, url: String) {
        io.execute { runCatching { db(c).execSQL("DELETE FROM marks WHERE url = ?", arrayOf<Any>(url)); changed() } }
    }

    /** Is this page kept? The answer comes back on the main thread. */
    fun has(c: Context, url: String, done: (Boolean) -> Unit) {
        io.execute {
            val yes = runCatching {
                db(c).rawQuery("SELECT 1 FROM marks WHERE url = ? LIMIT 1", arrayOf(url)).use { it.moveToFirst() }
            }.getOrDefault(false)
            main.post { done(yes) }
        }
    }

    /** Newest first. [text] filters on the title and the address. Call from a background thread. */
    fun query(c: Context, text: String, limit: Int): List<Mark> {
        val out = ArrayList<Mark>()
        runCatching {
            val like = "%" + text.replace("%", "").replace("_", "") + "%"
            val sql = if (text.isEmpty()) "SELECT url, title, added FROM marks ORDER BY added DESC LIMIT ?"
            else "SELECT url, title, added FROM marks WHERE title LIKE ? OR url LIKE ? ORDER BY added DESC LIMIT ?"
            val args = if (text.isEmpty()) arrayOf(limit.toString()) else arrayOf(like, like, limit.toString())
            db(c).rawQuery(sql, args).use { cur -> while (cur.moveToNext()) out.add(Mark(cur.getString(0), cur.getString(1), cur.getLong(2))) }
        }
        return out
    }
}
