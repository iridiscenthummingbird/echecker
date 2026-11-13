package ua.echecker.data

import java.io.IOException

/** Why the last sync did not fully succeed; shown next to the "Оновити" button. */
data class SyncIssue(
    val kind: Kind,
    /** HTTP status for [Kind.SERVER]. */
    val httpCode: Int? = null,
    /** Set when the primary source failed but this backup source supplied data. */
    val backupSource: String? = null,
) {
    enum class Kind { OFFLINE, NETWORK, SERVER, FORMAT, UNKNOWN }

    /** User-facing text (Ukrainian). */
    fun message(): String {
        val reason = when (kind) {
            Kind.OFFLINE -> "немає інтернету"
            Kind.NETWORK -> "не вдалося з'єднатися"
            Kind.SERVER -> "джерело не відповідає" + (httpCode?.let { " (HTTP $it)" } ?: "")
            Kind.FORMAT -> "не вдалося прочитати канал — схоже, змінився його формат"
            Kind.UNKNOWN -> "невідома помилка"
        }
        return when {
            backupSource != null -> "Основне джерело недоступне ($reason). Дані з резервного: ${sourceLabel(backupSource)}"
            kind == Kind.OFFLINE -> "Немає інтернету — оновимо, щойно з'явиться"
            else -> "Не вдалося оновити: $reason"
        }
    }

    /** A backup note is informational; everything else is an error. */
    val isError: Boolean get() = backupSource == null

    companion object {
        fun from(e: Throwable): SyncIssue = when (e) {
            is SourceFormatException -> SyncIssue(Kind.FORMAT)
            is HttpStatusException -> SyncIssue(Kind.SERVER, httpCode = e.code)
            is IOException -> SyncIssue(Kind.NETWORK)
            else -> SyncIssue(Kind.UNKNOWN)
        }

        fun sourceLabel(id: String): String = when (id) {
            ChannelFetcher.OBLENERGO_CHANNEL -> "Telegram «Запоріжжяобленерго»"
            else -> id
        }
    }
}
