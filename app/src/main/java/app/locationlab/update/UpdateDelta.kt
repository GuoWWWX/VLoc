package app.locationlab.update

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** VLD1 reconstructs the signed APK from byte ranges of the old APK and literal bytes. */
internal suspend fun applyUpdateDelta(base: File, patch: File, output: File, expectedSize: Long) {
    val coroutine = currentCoroutineContext()
    RandomAccessFile(base, "r").use { source ->
        DataInputStream(patch.inputStream().buffered()).use { input ->
            if (input.readInt() != 0x564C4431 || input.readLong() != expectedSize) {
                throw IOException("Invalid update patch header")
            }
            output.outputStream().buffered().use { target ->
                val buffer = ByteArray(64 * 1024)
                var remaining = expectedSize
                while (true) {
                    coroutine.ensureActive()
                    val kind = input.readUnsignedByte()
                    if (kind == 255) break
                    if (kind !in 0..1) throw IOException("Invalid update patch command")
                    val offset = if (kind == 0) input.readLong() else 0
                    val length = input.readInt()
                    if (length <= 0 || length > remaining ||
                        (kind == 0 && (offset < 0 || offset > source.length() - length))
                    ) throw IOException("Invalid update patch range")
                    if (kind == 0) source.seek(offset)
                    var left = length
                    while (left > 0) {
                        coroutine.ensureActive()
                        val count = minOf(left, buffer.size)
                        if (kind == 0) source.readFully(buffer, 0, count) else input.readFully(buffer, 0, count)
                        target.write(buffer, 0, count)
                        left -= count
                    }
                    remaining -= length
                }
                if (remaining != 0L || input.read() != -1) throw IOException("Incomplete update patch")
            }
        }
    }
}
