package app.locationlab.update

import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDeltaTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun reconstructsCopyAndLiteralRanges() = runBlocking {
        val base = directory.newFile().apply { writeText("OLD APK payload") }
        val patch = patch(11) {
            writeByte(1)
            writeInt(3)
            writeBytes("NEW")
            writeByte(0)
            writeLong(3)
            writeInt(8)
        }
        val output = directory.newFile()
        applyUpdateDelta(base, patch, output, 11)
        assertEquals("NEW APK pay", output.readText())
    }

    @Test(expected = IOException::class)
    fun rejectsCopyOutsideBase() = runBlocking {
        val base = directory.newFile().apply { writeText("short") }
        val patch = patch(3) {
            writeByte(0)
            writeLong(4)
            writeInt(3)
        }
        applyUpdateDelta(base, patch, directory.newFile(), 3)
    }

    private fun patch(length: Long, commands: DataOutputStream.() -> Unit): File =
        directory.newFile().apply {
            DataOutputStream(outputStream()).use { stream ->
                stream.writeInt(0x564C4431)
                stream.writeLong(length)
                stream.commands()
                stream.writeByte(255)
            }
        }
}
