package com.killswitch.sheer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/**
 * Rebuilds a new file from an old file and a small patch (bsdiff's method, with deflate instead of bzip2, written by tools/ci/mkpatch.py).
 * Plain Kotlin and the Java standard library only, so tools/EngineTest.kt can test it on a normal JVM.
 */
object Patch {
    class BadPatch(message: String) : Exception(message)

    private const val CHUNK = 1 shl 16

    fun sha256(f: File, progress: ((Long) -> Unit)? = null): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(CHUNK)
        var done = 0L
        FileInputStream(f).use { s ->
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                progress?.invoke(done)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFully(s: InputStream, b: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val n = s.read(b, off, len - off)
            if (n < 0) throw BadPatch("the patch ends too early")
            off += n
        }
    }

    private class Block(val offset: Long, val rawLength: Long, val compressedLength: Long)

    /** Writes the new file to [out] and returns its SHA-256. [progress] gets (bytes written, total). */
    fun apply(old: File, patch: File, out: File, progress: ((Long, Long) -> Unit)? = null): String {
        val blocks = ArrayList<Block>()
        var newSize = 0L
        RandomAccessFile(patch, "r").use { raf ->
            val head = ByteArray(5)
            raf.readFully(head)
            if (String(head, Charsets.ISO_8859_1) != "SHRP1") throw BadPatch("this is not a Sheer patch")
            val hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            raf.readFully(hdr.array())
            newSize = hdr.getLong(0)
            if (newSize < 0 || newSize > 2_000_000_000L) throw BadPatch("the patch asks for an impossible size")
            repeat(3) {
                val h = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                raf.readFully(h.array())
                val raw = h.getLong(0)
                val comp = h.getLong(8)
                if (raw < 0 || comp < 0 || raf.filePointer + comp > raf.length()) throw BadPatch("the patch is damaged")
                blocks.add(Block(raf.filePointer, raw, comp))
                raf.seek(raf.filePointer + comp)
            }
        }
        val (ctrlB, diffB, extraB) = blocks
        if (ctrlB.rawLength % 24 != 0L || ctrlB.rawLength > 200_000_000L) throw BadPatch("the patch is damaged")

        fun section(b: Block): InputStream {
            val fis = FileInputStream(patch)
            var left = b.offset
            while (left > 0) { val n = fis.skip(left); if (n <= 0) throw BadPatch("the patch is damaged"); left -= n }
            return InflaterInputStream(BufferedInputStream(fis, CHUNK), java.util.zip.Inflater(), CHUNK)
        }

        val ctrlBytes = ByteArray(ctrlB.rawLength.toInt())
        section(ctrlB).use { readFully(it, ctrlBytes, ctrlBytes.size) }
        val ctrl = ByteBuffer.wrap(ctrlBytes).order(ByteOrder.LITTLE_ENDIAN)

        val md = MessageDigest.getInstance("SHA-256")
        val oldSize = old.length()
        FileInputStream(old).channel.use { ch ->
            val map = ch.map(FileChannel.MapMode.READ_ONLY, 0, oldSize)
            section(diffB).use { diffS ->
                section(extraB).use { extraS ->
                    BufferedOutputStream(FileOutputStream(out), CHUNK).use { os ->
                        val buf = ByteArray(CHUNK)
                        val tmp = ByteArray(CHUNK)
                        var newPos = 0L
                        var oldPos = 0L
                        var lastReport = 0L
                        while (newPos < newSize) {
                            if (ctrl.remaining() < 24) throw BadPatch("the patch ends too early")
                            val add = ctrl.getLong()
                            val copy = ctrl.getLong()
                            val seek = ctrl.getLong()
                            if (add < 0 || copy < 0 || newPos + add + copy > newSize) throw BadPatch("the patch is damaged")

                            var left = add
                            while (left > 0) {
                                val n = minOf(left, CHUNK.toLong()).toInt()
                                readFully(diffS, buf, n)
                                // The new bytes are the stored difference plus the old bytes at the same place. Outside the old file the old byte is 0.
                                if (oldPos >= 0 && oldPos + n <= oldSize) {
                                    map.position(oldPos.toInt()); map.get(tmp, 0, n)
                                    for (i in 0 until n) buf[i] = (buf[i] + tmp[i]).toByte()
                                } else {
                                    for (i in 0 until n) {
                                        val o = oldPos + i
                                        if (o in 0 until oldSize) buf[i] = (buf[i] + map.get(o.toInt())).toByte()
                                    }
                                }
                                os.write(buf, 0, n); md.update(buf, 0, n)
                                oldPos += n; newPos += n; left -= n
                            }
                            left = copy
                            while (left > 0) {
                                val n = minOf(left, CHUNK.toLong()).toInt()
                                readFully(extraS, buf, n)
                                os.write(buf, 0, n); md.update(buf, 0, n)
                                newPos += n; left -= n
                            }
                            oldPos += seek
                            if (progress != null && newPos - lastReport > 2_000_000) { lastReport = newPos; progress(newPos, newSize) }
                        }
                    }
                }
            }
        }
        progress?.invoke(newSize, newSize)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
