package app.locationlab.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.locationlab.BuildConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal data class UpdateAsset(val file: String, val sha256: String, val size: Long)
internal data class UpdateDelta(val sourceSha256: String, val asset: UpdateAsset)
internal data class AppUpdate(
    val version: String,
    val code: Long,
    val apk: UpdateAsset,
    val deltas: List<UpdateDelta>,
)

internal class UpdateRepository(private val context: Context) {
    private val manager = context.packageManager
    private val installed = File(context.applicationInfo.sourceDir)
    private val origin = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases"
    private val directory = File(context.cacheDir, "updates")

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val response = connect(URL("$origin/latest/download/vloc-update.json"))
        val manifest = try {
            if (response.responseCode != 200) throw IOException("Update manifest unavailable")
            val bytes = ByteArray(64 * 1024)
            val size = response.inputStream.use { it.readNBytesCompat(bytes) }
            JSONObject(String(bytes, 0, size, Charsets.UTF_8))
        } catch (error: JSONException) {
            throw IOException("Invalid update manifest", error)
        } finally {
            response.disconnect()
        }
        try {
            if (manifest.getInt("schema") != 1) throw IOException("Unknown update manifest schema")
            val code = manifest.getLong("versionCode")
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val version = manifest.getString("versionName")
            if (!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) throw IOException("Invalid update version")
            val abi = installedAbi(installed) ?: throw IOException("Unsupported architecture")
            val channel = manifest.getJSONObject("packages").getJSONObject(abi)
            val apk = channel.asset(".apk")
            val deltas = channel.optJSONArray("deltas")
            val candidates = (0 until (deltas?.length() ?: 0)).map { index ->
                val delta = requireNotNull(deltas).getJSONObject(index)
                UpdateDelta(delta.sha256("fromSha256"), delta.asset(".vld"))
            }
            AppUpdate(version, code, apk, candidates)
        } catch (error: JSONException) {
            throw IOException("Invalid update manifest", error)
        }
    }

    suspend fun prepare(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        downloadLock.withLock {
            val installDir = File(directory, "install").also { it.mkdirs() }
            val target = File(installDir, update.apk.file)
            if (target.matches(update.apk)) {
                verifyPackage(target, update.code)
                return@withLock target
            }
            target.delete()
            val build = File(directory, "reconstruct.apk")
            if (update.deltas.isNotEmpty()) {
                val fingerprint = installed.sha256()
                val delta = update.deltas.firstOrNull { it.sourceSha256 == fingerprint && it.asset.size < update.apk.size }
                if (delta != null) {
                    try {
                        val patch = download(update, delta.asset, onProgress)
                        applyUpdateDelta(installed, patch, build, update.apk.size)
                        if (!build.matches(update.apk)) throw IOException("Patched APK hash mismatch")
                        verifyPackage(build, update.code)
                        return@withLock moveToInstall(build, target)
                    } catch (_: IOException) {
                        // The complete APK is also independently resumable.
                    } finally {
                        build.delete()
                    }
                }
            }
            val apk = download(update, update.apk, onProgress)
            verifyPackage(apk, update.code)
            moveToInstall(apk, target)
        }
    }

    fun install(file: File) {
        if (!manager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private suspend fun download(update: AppUpdate, asset: UpdateAsset, progress: (Float) -> Unit): File {
        val part = File(directory, "${asset.file}.${asset.sha256}.part")
        val coroutine = currentCoroutineContext()
        if (part.length() > asset.size) part.delete()
        val existing = part.length()
        if (existing < asset.size) {
            val url = URL("$origin/download/v${update.version}/${asset.file}")
            val response = connect(url, existing)
            try {
                val offset = when (response.responseCode) {
                    200 -> 0L
                    206 -> {
                        val expected = "bytes $existing-${asset.size - 1}/${asset.size}"
                        if (response.getHeaderField("Content-Range") != expected) throw IOException("Invalid download range")
                        existing
                    }
                    else -> throw IOException("Download failed: ${response.responseCode}")
                }
                RandomAccessFile(part, "rw").use { out ->
                    if (offset == 0L) out.setLength(0)
                    out.seek(offset)
                    response.inputStream.use { input ->
                        var total = offset
                        val buffer = ByteArray(64 * 1024)
                        progress((total.toDouble() / asset.size).toFloat())
                        while (true) {
                            coroutine.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (total + read > asset.size) {
                                out.setLength(0)
                                throw IOException("Download is larger than declared")
                            }
                            out.write(buffer, 0, read)
                            total += read
                            progress((total.toDouble() / asset.size).toFloat())
                        }
                    }
                }
            } finally {
                response.disconnect()
            }
        }
        if (part.length() != asset.size) throw IOException("Download interrupted")
        if (!part.matches(asset)) {
            part.delete()
            throw IOException("Download hash mismatch")
        }
        return part
    }

    private fun moveToInstall(file: File, target: File): File {
        if (!file.renameTo(target)) throw IOException("Could not save verified APK")
        return target
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, expectedCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val candidate = manager.getPackageArchiveInfo(file.path, flags)
            ?: throw IOException("Invalid APK")
        val version = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val current = manager.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != current.packageName || version != expectedCode ||
            installedAbi(file) != installedAbi(installed) || current.signers().isEmpty() ||
            candidate.signers() != current.signers()
        ) throw IOException("APK package, architecture, or signature mismatch")
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers() =
        (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures).orEmpty().toSet()

    private companion object {
        val downloadLock = Mutex()
        val hosts = setOf("github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    }

    private fun connect(start: URL, offset: Long = 0): HttpURLConnection {
        var url = start
        repeat(6) {
            if (url.protocol != "https" || url.host !in hosts || url.userInfo != null ||
                (url.port != -1 && url.port != 443)
            ) throw IOException("Untrusted update host")
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val next = connection.getHeaderField("Location") ?: throw IOException("Invalid download redirect")
                url = URL(url, next)
            } catch (error: IOException) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw IOException("Too many download redirects")
    }
}

private fun installedAbi(file: File): String? = ZipFile(file).use { zip ->
    listOf("arm64-v8a", "armeabi-v7a").singleOrNull { abi ->
        zip.getEntry("lib/$abi/libmaplibre.so") != null
    }
}

private fun JSONObject.asset(extension: String): UpdateAsset {
    val file = getString("file")
    val size = getLong("size")
    if (!file.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) ||
        !file.endsWith(extension) || size !in 1..150_000_000
    ) throw IOException("Invalid update asset")
    return UpdateAsset(file, sha256("sha256"), size)
}

private fun JSONObject.sha256(key: String): String = getString(key).also {
    if (!it.matches(Regex("[0-9a-f]{64}"))) throw IOException("Invalid update digest")
}

private suspend fun File.matches(asset: UpdateAsset) = length() == asset.size && sha256() == asset.sha256

private suspend fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val coroutine = currentCoroutineContext()
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            coroutine.ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
    var size = 0
    while (size < buffer.size) {
        val read = read(buffer, size, buffer.size - size)
        if (read == -1) return size
        size += read
    }
    if (read() != -1) throw IOException("Update manifest is too large")
    return size
}
