package ua.echecker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class ReleaseInfo(val version: String, val apkUrl: String, val pageUrl: String)

/** Reads the latest published GitHub release of this app. */
class UpdateChecker(private val client: OkHttpClient) {

    /** @return the latest release, or null if there is none with a usable APK. @throws IOException on network errors. */
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "echecker-android")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@use null // no releases yet
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from GitHub releases")
            parse(resp.body?.string() ?: throw IOException("Empty GitHub response"))
        }
    }

    companion object {
        const val REPO = "iridiscenthummingbird/echecker"

        /** Only APKs attached to this repo's own releases are ever downloaded. */
        const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

        fun parse(json: String): ReleaseInfo? = try {
            val o = JSONObject(json)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) {
                null
            } else {
                val tag = o.getString("tag_name")
                val assets = o.optJSONArray("assets")
                val apkUrl = (0 until (assets?.length() ?: 0))
                    .map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.startsWith(DOWNLOAD_PREFIX) }
                if (AppVersion.parse(tag) == null || apkUrl == null) {
                    null
                } else {
                    ReleaseInfo(
                        version = tag.trim().removePrefix("v").removePrefix("V"),
                        apkUrl = apkUrl,
                        pageUrl = o.optString("html_url"),
                    )
                }
            }
        } catch (e: JSONException) {
            null
        }
    }
}
