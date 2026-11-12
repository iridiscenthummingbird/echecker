package ua.echecker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Latest posts of a WordPress site via its public REST API (zoe.com.ua runs WordPress).
 * Backup source only: it serves the newest page and does not page back.
 */
class WordPressSource(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.zoe.com.ua",
    private val perPage: Int = 15,
) : PostSource {
    override val id: String = baseUrl.substringAfter("://").removePrefix("www.").trimEnd('/')

    override suspend fun fetch(before: Long?): List<RawPost> {
        if (before != null) return emptyList()
        return withContext(Dispatchers.IO) {
            val url = "${baseUrl.trimEnd('/')}/wp-json/wp/v2/posts?per_page=$perPage&_fields=id,date_gmt,title,content"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) echecker")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw HttpStatusException(resp.code, url)
                parseJson(resp.body?.string() ?: throw IOException("Empty body for $url"))
            }
        }
    }

    companion object {
        /** @throws SourceFormatException if the body is not a WordPress posts array. */
        fun parseJson(json: String): List<RawPost> {
            val array = try {
                JSONArray(json)
            } catch (e: JSONException) {
                throw SourceFormatException("Not a WordPress posts array: ${e.message}")
            }
            return (0 until array.length()).mapNotNull { i ->
                val post = array.optJSONObject(i) ?: return@mapNotNull null
                val id = post.optLong("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
                val publishedAt = runCatching {
                    LocalDateTime.parse(post.getString("date_gmt")).toInstant(ZoneOffset.UTC)
                }.getOrNull() ?: return@mapNotNull null
                val title = htmlToText(post.optJSONObject("title")?.optString("rendered").orEmpty())
                val content = htmlToText(post.optJSONObject("content")?.optString("rendered").orEmpty())
                val text = listOf(title, content).filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isBlank()) null else RawPost(id, publishedAt, text)
            }.sortedBy { it.id }
        }

        /** Rendered post HTML → plain text with one line per <br>, paragraph, list item or table row. */
        internal fun htmlToText(html: String): String {
            if (html.isBlank()) return ""
            val body = Jsoup.parseBodyFragment(html).body()
            // Raw newlines in HTML source are just whitespace; only tags break lines.
            body.allElements.forEach { el -> el.textNodes().forEach { it.text(it.wholeText.replace('\n', ' ')) } }
            body.select("br").forEach { it.replaceWith(TextNode("\n")) }
            body.select("p, div, li, tr, h1, h2, h3, h4, h5, h6").forEach { it.appendChild(TextNode("\n")) }
            body.select("td, th").forEach { it.appendChild(TextNode(" ")) }
            return body.wholeText()
                .replace(' ', ' ')
                .lines()
                .joinToString("\n") { it.trim() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }
    }
}
