// Checks that a published patch rebuilds the new APK from the old APK, byte for byte. Run on a JVM:
//   kotlinc -d /tmp/pt app/src/main/java/com/killswitch/sheer/Patch.kt tools/PatchRealTest.kt
//   java -cp /tmp/pt:/opt/kt/lib/kotlin-stdlib.jar PatchRealTestKt old.apk patch.bin rebuilt.apk new.apk
import com.killswitch.sheer.Patch
import java.io.File

fun main(a: Array<String>) {
    val t0 = System.currentTimeMillis()
    val got = Patch.apply(File(a[0]), File(a[1]), File(a[2]))
    val want = Patch.sha256(File(a[3]))
    println("rebuilt sha256 $got")
    println("expected      $want")
    println(if (got == want && File(a[2]).length() == File(a[3]).length()) "MATCH: byte-for-byte identical, ${System.currentTimeMillis() - t0} ms" else "MISMATCH")
}
