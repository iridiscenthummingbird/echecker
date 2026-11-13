package ua.echecker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

data class RawPost(val id: Long, val publishedAt: Instant, val text: String)

interface PostSource {
    /** Stable id, stored with each schedule to know which source it came from. */
    val id: String

    /**
     * Returns posts sorted by id ascending. [before] = load posts older than this id
     * (a source that cannot page returns an empty list then).
     */
    suspend fun fetch(before: Long? = null): List<RawPost>
}

/** The source answered, but not in a format we can read (page layout or API changed). */
class SourceFormatException(message: String) : IOException(message)

/** A non-2xx HTTP response. */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

class ChannelFetcher(
    private val client: OkHttpClient,
    private val channel: String = PRIMARY_CHANNEL,
) : PostSource {
    override val id: String = channel

    override suspend fun fetch(before: Long?): List<RawPost> = withContext(Dispatchers.IO) {
        val url = "https://t.me/s/$channel" + (before?.let { "?before=$it" } ?: "")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) echecker")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, url)
            parseHtml(resp.body?.string() ?: throw IOException("Empty body for $url"))
        }
    }

    companion object {
        const val PRIMARY_CHANNEL = "zoe_alarm"
        const val OBLENERGO_CHANNEL = "Zaporizhzhyaoblenergo_news"

        /** @throws SourceFormatException if the page has no channel messages at all (layout changed or not a channel page). */
        fun parseHtml(html: String): List<RawPost> {
            val messages = Jsoup.parse(html).select("div.tgme_widget_message[data-post]")
            if (messages.isEmpty()) throw SourceFormatException("No channel messages found (page layout changed?)")
            return messages.mapNotNull { msg ->
                val id = msg.attr("data-post").substringAfterLast('/').toLongOrNull()
                    ?: return@mapNotNull null
                val textEl = msg.selectFirst(".tgme_widget_message_text.js-message_text")
                    ?: return@mapNotNull null
                val time = msg.selectFirst("time[datetime]")?.attr("datetime")
                    ?: return@mapNotNull null
                val publishedAt = runCatching { OffsetDateTime.parse(time).toInstant() }.getOrNull()
                    ?: return@mapNotNull null
                textEl.select("br").forEach { it.replaceWith(TextNode("\n")) }
                RawPost(id, publishedAt, textEl.wholeText())
            }.sortedBy { it.id }
        }
    }
}
