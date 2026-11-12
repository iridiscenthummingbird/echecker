package ua.echecker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloads a release APK into the app cache and hands it to the system installer. */
object UpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    /** @throws IOException on network/storage errors or a URL outside this repo's releases. */
    suspend fun download(context: Context, client: OkHttpClient, release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        if (!release.apkUrl.startsWith(UpdateChecker.DOWNLOAD_PREFIX)) throw IOException("Unexpected APK URL")
        val dir = dir(context).apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "echecker-${release.version}.apk")
        val partial = File(dir, target.name + ".part")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "echecker-android").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading update")
            val body = resp.body ?: throw IOException("Empty update download")
            partial.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        if (partial.length() == 0L || !partial.renameTo(target)) throw IOException("Could not save update")
        target
    }

    /** Android 8+ asks per app; until allowed, the installer intent would be refused. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
