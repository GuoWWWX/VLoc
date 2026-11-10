package app.locationlab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import app.locationlab.R
import app.locationlab.data.HistoryEntry
import app.locationlab.data.normalizedName
import app.locationlab.platform.parseCoordinateSearch
import app.locationlab.simulation.Coordinate
import java.security.MessageDigest

internal data class SharedLocation(val coordinate: Coordinate, val name: String?)
internal data class ClipboardLocation(val location: SharedLocation, val fingerprint: String)

private const val shareHeader = "VLoc · 位置分享"
private const val legacyHeader = "位置模拟 · 位置分享"
private const val namePrefix = "名称："
private const val coordinatePrefix = "坐标："

internal fun HistoryEntry.shareText(): String {
    val name = placeName?.replace('\n', ' ')?.replace('\r', ' ').orEmpty()
    return "$shareHeader\n$namePrefix$name\n$coordinatePrefix${coordinate.latitude}, ${coordinate.longitude}"
}

internal fun parseSharedLocation(value: String, requireHeader: Boolean = false): SharedLocation? {
    val text = value.trim().takeIf { it.length <= 512 } ?: return null
    if (!requireHeader) parseCoordinateSearch(text)?.let { return SharedLocation(it, null) }
    val lines = text.lines()
    if (lines.size != 3 || (lines[0] != shareHeader && lines[0] != legacyHeader) ||
        !lines[1].startsWith(namePrefix) || !lines[2].startsWith(coordinatePrefix)
    ) return null
    val point = parseCoordinateSearch(lines[2].removePrefix(coordinatePrefix)) ?: return null
    return SharedLocation(point, lines[1].removePrefix(namePrefix).normalizedName())
}

internal fun Context.readSharedClipboard(): ClipboardLocation? {
    val clipboard = getSystemService(ClipboardManager::class.java)
    val text = clipboard?.primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString() ?: return null
    val location = parseSharedLocation(text, requireHeader = true) ?: return null
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(text.trim().toByteArray()).joinToString("") { "%02x".format(it) }
    return ClipboardLocation(location, fingerprint)
}

internal fun Context.copyLocationText(text: String, label: Int) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(label), text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.location_copied, Toast.LENGTH_SHORT).show()
}

internal fun Context.shareLocation(entry: HistoryEntry) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, entry.shareText())
    }
    startActivity(Intent.createChooser(send, getString(R.string.share_location)))
}
