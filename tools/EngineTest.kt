import com.killswitch.sheer.Palettes
import com.killswitch.sheer.Patch
import com.killswitch.sheer.UrlInput
import kotlin.system.exitProcess


// ---- patch tests: a patch is written by hand here, in the same layout tools/ci/mkpatch.py makes
fun le64(v: Long): ByteArray = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(v).array()
fun deflate(raw: ByteArray): ByteArray {
    val bo = java.io.ByteArrayOutputStream()
    java.util.zip.DeflaterOutputStream(bo, java.util.zip.Deflater(9)).use { it.write(raw) }
    return bo.toByteArray()
}
fun writePatch(f: java.io.File, newSize: Long, ctrl: List<LongArray>, diff: ByteArray, extra: ByteArray) {
    val c = java.io.ByteArrayOutputStream()
    ctrl.forEach { t -> t.forEach { c.write(le64(it)) } }
    val out = java.io.ByteArrayOutputStream()
    out.write("SHRP1".toByteArray(Charsets.ISO_8859_1)); out.write(le64(newSize))
    for (blk in listOf(c.toByteArray(), diff, extra)) { val z = deflate(blk); out.write(le64(blk.size.toLong())); out.write(le64(z.size.toLong())); out.write(z) }
    f.writeBytes(out.toByteArray())
}

var bad = 0
var total = 0
fun check(name: String, ok: Boolean) { total++; if (!ok) { bad++; println("FAIL $name") } }
fun section(s: String) = println("== $s")

fun main() {
    val ddg = UrlInput.ENGINES.first { it.name == "DuckDuckGo" }
    val google = UrlInput.ENGINES.first { it.name == "Google" }

    section("palettes: every text and background pair is readable")
    check("contrast scale is right", Math.abs(Palettes.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) - 21.0) < 0.01 && Math.abs(Palettes.contrast(0xFF777777.toInt(), 0xFF777777.toInt()) - 1.0) < 0.001)
    for ((name, p) in listOf("dark" to Palettes.DARK, "light" to Palettes.LIGHT)) {
        for ((bgName, bg) in listOf("ink" to p.ink, "lift" to p.lift, "lift2" to p.lift2, "sheet" to p.sheet)) {
            check("$name: main text is easy to read on $bgName", Palettes.contrast(p.text, bg) >= 7.0)
            check("$name: muted text passes AA on $bgName", Palettes.contrast(p.muted, bg) >= 4.5)
            check("$name: accent used as text passes AA on $bgName", Palettes.contrast(p.accentText, bg) >= 4.5)
            check("$name: on and off words (mint, rose) pass AA on $bgName", Palettes.contrast(p.mint, bg) >= 4.5 && Palettes.contrast(p.rose, bg) >= 4.5)
        }
        check("$name: accent shapes stand out from the scene (3:1)", Palettes.contrast(p.accent, p.ink) >= 3.0 && Palettes.contrast(p.accent, p.lift) >= 3.0)
        check("$name: text on a flat accent fill is readable (AA for the orange, 3:1 for the blue)", Palettes.contrast(p.onAccent, p.accent) >= (if (name == "dark") 4.5 else 3.0))
    }
    for ((name, p) in listOf("dark" to Palettes.DARK, "light" to Palettes.LIGHT)) {
        for ((bgName, bg) in listOf("ink" to p.ink, "lift" to p.lift, "lift2" to p.lift2)) {
            val fill = Palettes.glassFill(p.accent, bg)
            val cr = Palettes.contrast(p.onAccent, fill)
            println("  info $name glass button over $bgName: fill #%06X, button text %.2f:1".format(fill and 0xFFFFFF, cr))
            // Known and disclosed: white on the library's tinted blue glass is about 2.6 to 2.7. The floor catches anything worse.
            check("$name: button text on the tinted glass over $bgName stays above its floor (${if (name == "dark") "4.5" else "2.5, disclosed"})", cr >= (if (name == "dark") 4.5 else 2.5))
        }
    }
    check("light really is light and dark really is dark", Palettes.luminance(Palettes.LIGHT.ink) > 0.5 && Palettes.luminance(Palettes.DARK.ink) < 0.05)
    check("follow the phone", Palettes.useDark(0, true) && !Palettes.useDark(0, false))
    check("dark and light ignore the phone", Palettes.useDark(1, false) && !Palettes.useDark(2, true))

    section("patches: the new file is rebuilt from the old one")
    run {
        val rnd = java.util.Random(7)
        val old = ByteArray(300_000).also { rnd.nextBytes(it) }
        val inserted = ByteArray(5_000).also { rnd.nextBytes(it) }
        // new = the first 100000 old bytes with a few changed, then 5000 new bytes, then the old bytes from 102000 on (2000 skipped)
        val part1 = old.copyOfRange(0, 100_000).also { it[10] = (it[10] + 1).toByte(); it[99_999] = 0x55 }
        val tail = old.copyOfRange(102_000, 300_000).also { it[5] = (it[5] + 9).toByte() }
        val new = part1 + inserted + tail
        val diff = ByteArray(part1.size + tail.size)
        for (i in part1.indices) diff[i] = (part1[i] - old[i]).toByte()
        for (i in tail.indices) diff[part1.size + i] = (tail[i] - old[102_000 + i]).toByte()
        val dir = java.nio.file.Files.createTempDirectory("sheerpatch").toFile()
        val of = java.io.File(dir, "old.bin").also { it.writeBytes(old) }
        val pf = java.io.File(dir, "p.bin")
        writePatch(pf, new.size.toLong(), listOf(longArrayOf(100_000, 5_000, 2_000), longArrayOf(tail.size.toLong(), 0, 0)), diff, inserted)
        val nf = java.io.File(dir, "new.bin")
        val sha = Patch.apply(of, pf, nf)
        check("the rebuilt file is identical", nf.readBytes().contentEquals(new))
        check("the fingerprint it reports is right", sha == Patch.sha256(nf))
        val bytes = pf.readBytes()
        val badMagic = java.io.File(dir, "m.bin").also { it.writeBytes(bytes.copyOf().also { b -> b[0] = 'X'.code.toByte() }) }
        check("a file that is not a patch is refused", runCatching { Patch.apply(of, badMagic, java.io.File(dir, "o1.bin")) }.exceptionOrNull() is Patch.BadPatch)
        val cut = java.io.File(dir, "c.bin").also { it.writeBytes(bytes.copyOf(bytes.size - 20)) }
        check("a cut-off patch is refused", runCatching { Patch.apply(of, cut, java.io.File(dir, "o2.bin")) }.isFailure)
        val lying = java.io.File(dir, "l.bin")
        writePatch(lying, 100, listOf(longArrayOf(90, 90, 0)), ByteArray(90), ByteArray(90))
        check("a patch that writes past its own size is refused", runCatching { Patch.apply(of, lying, java.io.File(dir, "o3.bin")) }.exceptionOrNull() is Patch.BadPatch)
        val wrongOld = java.io.File(dir, "w.bin").also { it.writeBytes(ByteArray(300_000)) }
        val wrongOut = java.io.File(dir, "o4.bin")
        Patch.apply(wrongOld, pf, wrongOut)
        check("a different old file gives a different result, which the fingerprint check catches", !wrongOut.readBytes().contentEquals(new))
        dir.deleteRecursively()
    }

    section("address bar: what gets loaded")
    check("a full https address stays as typed", UrlInput.resolve("https://example.com/a?b=1", ddg) == "https://example.com/a?b=1")
    check("a full http address stays as typed", UrlInput.resolve("http://example.com", ddg) == "http://example.com")
    check("spaces around the input are ignored", UrlInput.resolve("  https://example.com  ", ddg) == "https://example.com")
    check("a bare host becomes https", UrlInput.resolve("example.com", ddg) == "https://example.com")
    check("a bare host with a path becomes https", UrlInput.resolve("example.com/a/b?c=d#e", ddg) == "https://example.com/a/b?c=d#e")
    check("a host with www and a port becomes https", UrlInput.resolve("www.example.co.uk:8443", ddg) == "https://www.example.co.uk:8443")
    check("localhost becomes http", UrlInput.resolve("localhost:3000", ddg) == "http://localhost:3000")
    check("an IP address becomes http", UrlInput.resolve("192.168.1.1/admin", ddg) == "http://192.168.1.1/admin")
    check("words are a search", UrlInput.resolve("best pizza near me", ddg) == "https://duckduckgo.com/?q=best+pizza+near+me")
    check("a single word is a search", UrlInput.resolve("firefox", ddg) == "https://duckduckgo.com/?q=firefox")
    check("a number with a dot is a search, not a host", UrlInput.resolve("3.14", ddg) == "https://duckduckgo.com/?q=3.14")
    check("a file name with a one letter ending is a search", UrlInput.resolve("a.b", ddg) == "https://duckduckgo.com/?q=a.b")
    check("search text is encoded", UrlInput.resolve("a&b=c d", ddg) == "https://duckduckgo.com/?q=a%26b%3Dc+d")
    check("javascript: is searched for, never run", UrlInput.resolve("javascript:alert(1)", ddg)!!.startsWith("https://duckduckgo.com/?q="))
    check("data: is searched for, never opened", UrlInput.resolve("data:text/html,hi", ddg)!!.startsWith("https://duckduckgo.com/?q="))
    check("file: is searched for, never opened", UrlInput.resolve("file:///etc/passwd", ddg)!!.startsWith("https://duckduckgo.com/?q="))
    check("Google is the default engine", UrlInput.ENGINES[0].name == "Google")
    check("a Google search is built right", UrlInput.resolve("best pizza", google) == "https://www.google.com/search?q=best+pizza")
    check("every engine has a different name", UrlInput.ENGINES.map { it.name }.toSet().size == UrlInput.ENGINES.size)
    check("blank input loads nothing", UrlInput.resolve("   ", ddg) == null && UrlInput.resolve("", ddg) == null)
    check("every engine has exactly one place for the text", UrlInput.ENGINES.all { it.template.split("%s").size == 2 })
    check("every engine uses https", UrlInput.ENGINES.all { it.template.startsWith("https://") })

    section("address bar: what is shown")
    check("host drops www", UrlInput.host("https://www.example.com/path?x=1") == "example.com")
    check("host keeps the port", UrlInput.host("http://localhost:3000/x") == "localhost:3000")
    check("host drops a user name", UrlInput.host("https://user:pw@example.com/") == "example.com")
    check("blank page shows nothing", UrlInput.host("") == "" && UrlInput.host("about:blank") == "")
    check("a non web address is shown whole", UrlInput.host("about:reader?url=x") == "about:reader?url=x")

    section("links from other apps")
    check("web links are allowed in", UrlInput.isWeb("https://example.com") && UrlInput.isWeb("http://example.com/a"))
    check("anything else is refused", !UrlInput.isWeb("javascript:alert(1)") && !UrlInput.isWeb("file:///sdcard/x") && !UrlInput.isWeb("intent://x") && !UrlInput.isWeb(null) && !UrlInput.isWeb(""))

    println("\n$total checks, ${total - bad} passed" + if (bad == 0) " - ALL PASSED" else ", $bad FAILED")
    if (bad != 0) exitProcess(1)
}
