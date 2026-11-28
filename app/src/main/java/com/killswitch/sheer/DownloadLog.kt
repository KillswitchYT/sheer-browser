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

/** A list of what was downloaded, on the phone only. The files themselves are in the phone's Downloads folder. Same shape as [Bookmarks]. */
object DownloadLog {
    class Item(val id: Long, val name: String, val mime: String, val size: Long, val uri: String, val source: String, val time: Long)

    /** 1 images, 2 videos, 3 documents, 4 everything else. */
    fun kindOf(mime: String, name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> 1
            mime.startsWith("video/") -> 2
            mime.startsWith("text/") || mime == "application/pdf" || mime.contains("word") || mime.contains("sheet") || mime.contains("presentation") ||
                mime.contains("officedocument") || ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "rtf", "epub") -> 3
            else -> 4
        }
    }

    private class Db(c: Context) : SQLiteOpenHelper(c, "downloads.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, mime TEXT NOT NULL DEFAULT '', " +
                    "size INTEGER NOT NULL DEFAULT 0, uri TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '', time INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX items_time ON items(time)")
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

    fun add(c: Context, name: String, mime: String, size: Long, uri: String, source: String) {
        io.execute {
            runCatching {
                db(c).execSQL(
                    "INSERT INTO items(name, mime, size, uri, source, time) VALUES(?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(name, mime, size, uri, source, System.currentTimeMillis()),
                )
                changed()
            }
        }
    }

    fun remove(c: Context, id: Long) {
        io.execute { runCatching { db(c).execSQL("DELETE FROM items WHERE id = ?", arrayOf<Any>(id)); changed() } }
    }

    /** Newest first. [text] filters on the name and the site; [kind] is 0 for all or one of [kindOf]. Call from a background thread. */
    fun query(c: Context, text: String, kind: Int, limit: Int): List<Item> {
        val out = ArrayList<Item>()
        runCatching {
            val like = "%" + text.replace("%", "").replace("_", "") + "%"
            val sql = if (text.isEmpty()) "SELECT id, name, mime, size, uri, source, time FROM items ORDER BY time DESC LIMIT ?"
            else "SELECT id, name, mime, size, uri, source, time FROM items WHERE name LIKE ? OR source LIKE ? ORDER BY time DESC LIMIT ?"
            val args = if (text.isEmpty()) arrayOf(limit.toString()) else arrayOf(like, like, limit.toString())
            db(c).rawQuery(sql, args).use { cur ->
                while (cur.moveToNext()) {
                    val it = Item(cur.getLong(0), cur.getString(1), cur.getString(2), cur.getLong(3), cur.getString(4), cur.getString(5), cur.getLong(6))
                    if (kind == 0 || kindOf(it.mime, it.name) == kind) out.add(it)
                }
            }
        }
        return out
    }
}
