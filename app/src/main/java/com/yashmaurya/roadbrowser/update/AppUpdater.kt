package com.yashmaurya.roadbrowser.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import com.yashmaurya.roadbrowser.AppConstants
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Updates RoadBrowser from its official GitHub releases.
 *
 * The APK is downloaded only from this repository's release assets, then checked before it is
 * handed to Android's installer: same package, a newer version code, and signed with exactly the
 * certificate the installed copy was signed with. Android asks the user to confirm every install,
 * and the first time it asks them to allow RoadBrowser to install updates at all.
 */
object AppUpdater {

    data class Release(val tag: String, val version: String, val pageUrl: String, val apkUrl: String?)

    const val LATEST_RELEASE_API = "https://api.github.com/repos/Yash-v-maurya/RoadBrowser/releases/latest"
    private const val ASSET_PATH_PREFIX = "/Yash-v-maurya/RoadBrowser/releases/download/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Reads GitHub's "latest release" JSON; prefers the versioned APK over the fixed-name copy. */
    fun parseRelease(json: String): Release {
        val root = JSONObject(json)
        val tag = root.getString("tag_name")
        val assets = root.optJSONArray("assets")
        val apkUrls = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .sortedByDescending { it.optString("name").contains('-') }
            .map { it.optString("browser_download_url") }
            .filter { isOfficialAssetUrl(it) }
        return Release(tag, tag.trim().removePrefix("v"), root.optString("html_url", AppConstants.GITHUB_REPO_URL + "/releases"), apkUrls.firstOrNull())
    }

    /** True when [latest] is a higher dotted version than [current] ("2.3.1" > "2.3"). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Only https downloads from this repository's own release assets are accepted. */
    fun isOfficialAssetUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "github.com" && uri.path.orEmpty().startsWith(ASSET_PATH_PREFIX)
    }

    /** Blocking; call off the main thread. */
    fun fetchLatest(): Release {
        val request = Request.Builder().url(LATEST_RELEASE_API).header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub answered ${response.code}" }
            return parseRelease(response.body.string())
        }
    }

    /** Blocking; call off the main thread. Reports progress from 0 to 100 when the size is known. */
    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File {
        val url = release.apkUrl
        require(url != null && isOfficialAssetUrl(url)) { "No official APK in this release" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "RoadBrowser-${release.version}.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = response.body
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
        }
        return target
    }

    /** Null when [apk] may be installed over this copy; otherwise why not. */
    fun verify(context: Context, apk: File): String? {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val candidate = context.packageManager.getPackageArchiveInfo(apk.path, flags) ?: return "not a valid APK"
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != context.packageName) return "it is a different app"
        if (candidate.longVersionCode <= installed.longVersionCode) return "it is not newer than this version"
        if (signers(candidate) != signers(installed) || signers(candidate).isEmpty()) return "it is not signed by the RoadBrowser key"
        return null
    }

    /** Hands [apk] to Android's installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("RoadBrowser.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(callback.intentSender)
        }
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
